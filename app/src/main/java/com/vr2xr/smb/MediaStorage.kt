package com.vr2xr.smb

import java.io.Closeable

interface MediaStorage : Closeable {
    fun list(uri: String): List<MediaEntry>
    fun stat(uri: String): MediaEntry
    fun openRandomAccess(uri: String): RandomAccessSource
}

data class MediaEntry(
    val uri: String,
    val name: String,
    val directory: Boolean,
    val size: Long,
    val modifiedTime: Long
)

interface RandomAccessSource : Closeable {
    fun size(): Long
    fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}
