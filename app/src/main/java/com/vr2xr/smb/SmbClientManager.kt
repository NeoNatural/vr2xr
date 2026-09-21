package com.vr2xr.smb

import java.io.Closeable
import java.io.IOException

class SmbClientManager(
    private val profileStore: SmbProfileStore
) : Closeable {
    private val lock = Any()
    private var activeStorage: SmbStorage? = null

    fun connect(profile: SmbProfile, remember: Boolean = true): List<MediaEntry> {
        val candidate = SmbStorage.connect(profile)
        val rootEntries = try {
            candidate.list(candidate.rootUri)
        } catch (error: Throwable) {
            candidate.close()
            throw error
        }
        if (remember) {
            try {
                profileStore.save(profile)
            } catch (error: Throwable) {
                candidate.close()
                throw error
            }
        }
        val previous = synchronized(lock) {
            val old = activeStorage
            activeStorage = candidate
            old
        }
        previous?.close()
        return rootEntries
    }

    fun list(profileId: String, uri: String): List<MediaEntry> =
        storageFor(profileId).list(uri)

    fun openRandomAccess(profileId: String, uri: String): RandomAccessSource =
        storageFor(profileId).openRandomAccess(uri)

    fun profileFor(profileId: String): SmbProfile {
        synchronized(lock) {
            activeStorage?.profile?.takeIf { it.id == profileId }?.let { return it }
        }
        return profileStore.find(profileId)
            ?: throw IOException("Saved SMB account is unavailable; connect again")
    }

    override fun close() {
        val storage = synchronized(lock) {
            val old = activeStorage
            activeStorage = null
            old
        }
        storage?.close()
    }

    private fun storageFor(profileId: String): SmbStorage {
        synchronized(lock) {
            activeStorage?.takeIf { it.profile.id == profileId }?.let { return it }
        }
        val profile = profileStore.find(profileId)
            ?: throw IOException("Saved SMB account is unavailable; connect again")
        val candidate = SmbStorage.connect(profile)
        try {
            candidate.list(candidate.rootUri)
        } catch (error: Throwable) {
            candidate.close()
            throw error
        }
        val previous = synchronized(lock) {
            activeStorage?.takeIf { it.profile.id == profileId }?.let {
                candidate.close()
                return it
            }
            val old = activeStorage
            activeStorage = candidate
            old
        }
        previous?.close()
        return candidate
    }
}
