package com.vr2xr.app

import android.app.Application
import com.vr2xr.player.AppPlaybackSessionOwner
import com.vr2xr.player.PlaybackCoordinator
import com.vr2xr.player.PlaybackSessionOwner
import com.vr2xr.smb.SmbClientManager
import com.vr2xr.smb.SmbProfileStore
import com.vr2xr.tracking.OneXrTrackingSessionManager

class Vr2xrApplication : Application() {
    val smbProfileStore: SmbProfileStore by lazy {
        SmbProfileStore(this)
    }

    val smbClientManager: SmbClientManager by lazy {
        SmbClientManager(smbProfileStore)
    }
    val trackingSessionManager: OneXrTrackingSessionManager by lazy {
        OneXrTrackingSessionManager(this)
    }

    val playbackCoordinator: PlaybackCoordinator by lazy {
        PlaybackCoordinator()
    }

    val playbackSessionOwner: PlaybackSessionOwner by lazy {
        AppPlaybackSessionOwner(playbackCoordinator)
    }

}
