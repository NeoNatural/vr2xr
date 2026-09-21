package com.vr2xr.player

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.vr2xr.source.SourceDescriptor
import com.vr2xr.source.SourceType
import com.vr2xr.smb.SmbClientManager

class VrPlayerEngine(
    context: Context,
    private val smbClientManager: SmbClientManager
) {
    private val appContext = context.applicationContext
    private var loadedSourceKey: String? = null

    val player: Player get() = exoPlayer

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    SMB_MIN_BUFFER_MS,
                    SMB_MAX_BUFFER_MS,
                    SMB_PLAYBACK_START_BUFFER_MS,
                    SMB_REBUFFER_MS
                )
                .setTargetBufferBytes(SMB_TARGET_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()
        )
        .build()

    fun loadSource(source: SourceDescriptor, autoPlay: Boolean, forceReset: Boolean = false) {
        val sourceKey = source.normalized
        if (forceReset || loadedSourceKey != sourceKey) {
            exoPlayer.setMediaSource(createMediaSource(source), true)
            exoPlayer.prepare()
            loadedSourceKey = sourceKey
        }
        exoPlayer.playWhenReady = autoPlay
    }

    fun bindVideoSurface(surface: Surface) {
        exoPlayer.setVideoSurface(surface)
    }

    fun clearVideoSurface(surface: Surface) {
        exoPlayer.clearVideoSurface(surface)
    }

    fun clearVideoSurface() {
        exoPlayer.clearVideoSurface()
    }

    fun play() {
        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun release() {
        loadedSourceKey = null
        exoPlayer.release()
    }

    private fun createMediaSource(source: SourceDescriptor): ProgressiveMediaSource {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory: DataSource.Factory = when (source.type) {
            SourceType.SMB_URI -> {
                val profileId = requireNotNull(source.profileId) { "SMB source is missing its profile" }
                DataSource.Factory { SmbDataSource(smbClientManager, profileId) }
            }

            SourceType.LOCAL_URI,
            SourceType.HTTP_URL -> DefaultDataSource.Factory(appContext, httpFactory)
        }
        val mediaItem = MediaItem.fromUri(Uri.parse(source.normalized))
        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }

    companion object {
        internal const val SMB_TARGET_BUFFER_BYTES = 48 * 1024 * 1024
        internal const val SMB_MIN_BUFFER_MS = 10_000
        internal const val SMB_MAX_BUFFER_MS = 30_000
        internal const val SMB_PLAYBACK_START_BUFFER_MS = 3_000
        internal const val SMB_REBUFFER_MS = 5_000
    }
}
