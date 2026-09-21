package com.vr2xr.smb

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.IOException
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

class SmbStorage private constructor(
    val profile: SmbProfile,
    private val context: CIFSContext
) : MediaStorage {
    val rootUri: String = "smb://${profile.host}/${profile.share}/"
    private val closed = AtomicBoolean(false)

    override fun list(uri: String): List<MediaEntry> {
        checkOpen()
        return openCheckedFile(uri).use { directory ->
            if (!directory.isDirectory) {
                throw IOException("SMB path is not a directory")
            }
            directory.listFiles().map { child ->
                child.use { child.toEntry() }
            }
        }
    }

    override fun stat(uri: String): MediaEntry {
        checkOpen()
        return openCheckedFile(uri).use { it.toEntry() }
    }

    override fun openRandomAccess(uri: String): RandomAccessSource {
        checkOpen()
        val file = openCheckedFile(uri)
        try {
            if (!file.isFile) {
                throw IOException("SMB path is not a file")
            }
            val access = file.openRandomAccess("r")
            return SmbRandomAccessSource(file, access)
        } catch (error: Throwable) {
            file.close()
            throw error
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            context.close()
        }
    }

    private fun openCheckedFile(uri: String): SmbFile {
        require(uri.startsWith(rootUri, ignoreCase = true)) {
            "SMB path is outside the connected share"
        }
        val file = SmbFile(uri, context)
        try {
            val canonicalRoot = SmbFile(rootUri, context).use { it.canonicalPath.ensureDirectorySuffix() }
            val canonicalPath = file.canonicalPath
            require(
                canonicalPath.equals(canonicalRoot.removeSuffix("/"), ignoreCase = true) ||
                    canonicalPath.startsWith(canonicalRoot, ignoreCase = true)
            ) { "SMB path is outside the connected share" }
            return file
        } catch (error: Throwable) {
            file.close()
            throw error
        }
    }

    private fun SmbFile.toEntry(): MediaEntry {
        val directory = isDirectory
        return MediaEntry(
            uri = canonicalPath.let { if (directory) it.ensureDirectorySuffix() else it },
            name = name.removeSuffix("/"),
            directory = directory,
            size = if (directory) 0L else length(),
            modifiedTime = lastModified()
        )
    }

    private fun checkOpen() {
        check(!closed.get()) { "SMB session is closed" }
    }

    companion object {
        private val INVALID_TARGET_CHARACTERS = setOf('/', '\\', '@', ':')

        fun connect(profile: SmbProfile): SmbStorage {
            validateTargetPart(profile.host, "host")
            validateTargetPart(profile.share, "share")
            val properties = Properties().apply {
                setProperty("jcifs.smb.client.useLargeReadWrite", "false")
                setProperty("jcifs.smb.client.responseTimeout", "5000")
                setProperty("jcifs.smb.client.soTimeout", "10000")
                setProperty("jcifs.smb.client.connTimeout", "5000")
                setProperty("jcifs.smb.client.sessionTimeout", "10000")
                setProperty("jcifs.smb.client.maxRequestRetries", "1")
                setProperty("jcifs.smb.client.tcpNoDelay", "true")
            }
            val base = BaseContext(PropertyConfiguration(properties))
            val context = base.withCredentials(
                NtlmPasswordAuthenticator(profile.domain, profile.username, profile.password)
            )
            return SmbStorage(profile, context)
        }

        internal fun validateTargetPart(value: String, label: String) {
            require(value.isNotBlank()) { "SMB $label is required" }
            require(value.none { it in INVALID_TARGET_CHARACTERS }) {
                "SMB $label contains an unsupported character"
            }
        }
    }
}

private class SmbRandomAccessSource(
    private val file: SmbFile,
    private val access: SmbRandomAccessFile
) : RandomAccessSource {
    private var closed = false

    @Synchronized
    override fun size(): Long {
        check(!closed) { "SMB file is closed" }
        return access.length()
    }

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        check(!closed) { "SMB file is closed" }
        require(position >= 0L) { "position must be non-negative" }
        access.seek(position)
        return access.read(buffer, offset, length)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        var failure: Throwable? = null
        try {
            access.close()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            file.close()
        } catch (error: Throwable) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        failure?.let { throw it }
    }
}

private fun String.ensureDirectorySuffix(): String = if (endsWith('/')) this else "$this/"
