package com.vr2xr.smb

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.vr2xr.R
import java.io.Closeable
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SmbThumbnailLoader(
    private val manager: SmbClientManager,
    private val profileId: String,
    private val thumbnailSizePx: Int
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val workers = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = Collections.synchronizedSet(mutableSetOf<String>())
    private val activeSources = Collections.synchronizedSet(mutableSetOf<SmbMediaDataSource>())
    private val cache = object : LruCache<String, Bitmap>(BITMAP_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun bind(entry: MediaEntry, target: ImageView) {
        val key = entry.thumbnailKey()
        target.tag = key
        target.setImageResource(R.drawable.ic_smb_video)
        cache.get(key)?.let {
            target.setImageBitmap(it)
            return
        }
        if (closed.get() || !pending.add(key)) return
        workers.execute {
            val bitmap = runCatching { load(entry) }.getOrNull()
            pending.remove(key)
            if (bitmap != null && !closed.get()) {
                cache.put(key, bitmap)
                mainHandler.post {
                    if (!closed.get() && target.tag == key) target.setImageBitmap(bitmap)
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        workers.shutdownNow()
        synchronized(activeSources) {
            activeSources.toList().forEach { runCatching { it.close() } }
            activeSources.clear()
        }
        pending.clear()
        cache.evictAll()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun load(entry: MediaEntry): Bitmap? {
        if (closed.get()) return null
        val source = manager.openRandomAccess(profileId, entry.uri)
        val mediaSource = SmbMediaDataSource(source)
        activeSources.add(mediaSource)
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(mediaSource)
                retriever.getScaledFrameAtTime(
                    THUMBNAIL_TIME_US,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    thumbnailSizePx,
                    thumbnailSizePx
                )
            }
        } finally {
            activeSources.remove(mediaSource)
            mediaSource.close()
        }
    }

    private fun MediaEntry.thumbnailKey(): String = "$uri|$size|$modifiedTime"

    companion object {
        private const val BITMAP_CACHE_BYTES = 12 * 1024 * 1024
        private const val THUMBNAIL_TIME_US = 1_000_000L
    }
}

private class SmbMediaDataSource(
    private val source: RandomAccessSource
) : MediaDataSource() {
    private val closed = AtomicBoolean(false)

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
        if (closed.get()) -1 else source.readAt(position, buffer, offset, size)

    override fun getSize(): Long = source.size()

    override fun close() {
        if (closed.compareAndSet(false, true)) source.close()
    }
}
