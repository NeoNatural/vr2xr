package com.vr2xr.app

import io.onexr.XrConnectionInfo
import io.onexr.XrSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingLaunchPolicyTest {
    @Test
    fun streamingSourceSkipsTrackingSetup() {
        assertFalse(
            shouldLaunchTrackingSetupForSource(
                XrSessionState.Streaming(connectionInfo())
            )
        )
    }

    @Test
    fun nonStreamingSourceLaunchesTrackingSetup() {
        assertTrue(shouldLaunchTrackingSetupForSource(XrSessionState.Idle))
    }

    @Test
    fun setupWithoutSourceCompletesToMain() {
        assertEquals(
            TrackingSetupCompletionTarget.MAIN,
            trackingSetupCompletionTarget(hasSource = false)
        )
    }

    @Test
    fun setupWithSourceCompletesToReady() {
        assertEquals(
            TrackingSetupCompletionTarget.READY,
            trackingSetupCompletionTarget(hasSource = true)
        )
    }

    private fun connectionInfo(): XrConnectionInfo {
        return XrConnectionInfo(
            networkHandle = 1L,
            interfaceName = "test",
            localSocket = "local",
            remoteSocket = "remote",
            connectMs = 1L
        )
    }
}
