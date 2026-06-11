package com.vr2xr.app

import io.onexr.XrSessionState

internal enum class TrackingSetupCompletionTarget {
    MAIN,
    READY
}

internal fun shouldLaunchTrackingSetupForSource(sessionState: XrSessionState): Boolean {
    return sessionState !is XrSessionState.Streaming
}

internal fun trackingSetupCompletionTarget(hasSource: Boolean): TrackingSetupCompletionTarget {
    return if (hasSource) {
        TrackingSetupCompletionTarget.READY
    } else {
        TrackingSetupCompletionTarget.MAIN
    }
}
