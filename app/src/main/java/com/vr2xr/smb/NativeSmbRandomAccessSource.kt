package com.vr2xr.smb

import java.io.IOException

class NativeSmbRandomAccessSource private constructor(
    private var nativeHandle: Long
) : RandomAccessSource {
    @Synchronized
    override fun size(): Long {
        check(nativeHandle != 0L) { "Native SMB2 file is closed" }
        return nativeSize(nativeHandle).also {
            if (it < 0L) throw IOException("Native SMB2 file size is unavailable")
        }
    }

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        check(nativeHandle != 0L) { "Native SMB2 file is closed" }
        return nativeReadAt(nativeHandle, position, buffer, offset, length)
    }

    @Synchronized
    override fun close() {
        val handle = nativeHandle
        nativeHandle = 0L
        if (handle != 0L) nativeClose(handle)
    }

    private external fun nativeOpen(
        uri: String,
        domain: String,
        username: String,
        password: String
    ): Long

    private external fun nativeSize(handle: Long): Long

    private external fun nativeReadAt(
        handle: Long,
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int

    private external fun nativeClose(handle: Long)

    companion object {
        private val nativeAvailable = runCatching {
            System.loadLibrary("smb2-jni")
        }.isSuccess

        fun open(profile: SmbProfile, uri: String): NativeSmbRandomAccessSource {
            if (!nativeAvailable) throw IOException("Native SMB2 library is unavailable")
            val source = NativeSmbRandomAccessSource(0L)
            val handle = source.nativeOpen(
                uri = uri,
                domain = profile.domain,
                username = profile.username,
                password = profile.password
            )
            if (handle == 0L) throw IOException("Native SMB2 open failed")
            source.nativeHandle = handle
            return source
        }
    }
}
