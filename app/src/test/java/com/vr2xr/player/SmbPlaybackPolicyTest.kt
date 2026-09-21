package com.vr2xr.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbPlaybackPolicyTest {
    @Test
    fun usesBoundedByteFirstMediaBuffer() {
        assertEquals(48 * 1024 * 1024, VrPlayerEngine.SMB_TARGET_BUFFER_BYTES)
        assertEquals(10_000, VrPlayerEngine.SMB_MIN_BUFFER_MS)
        assertEquals(30_000, VrPlayerEngine.SMB_MAX_BUFFER_MS)
        assertEquals(3_000, VrPlayerEngine.SMB_PLAYBACK_START_BUFFER_MS)
        assertEquals(5_000, VrPlayerEngine.SMB_REBUFFER_MS)
    }

    @Test
    fun nativeReadAheadGrowsAfterProbeReads() {
        assertEquals(128 * 1024, SmbDataSource.PROBE_READ_SIZE_BYTES)
        assertEquals(1024 * 1024, SmbDataSource.CONTINUOUS_READ_SIZE_BYTES)
        assertTrue(SmbDataSource.CONTINUOUS_READ_SIZE_BYTES > SmbDataSource.PROBE_READ_SIZE_BYTES)
        assertFalse(SmbDataSource.PROBE_READ_SIZE_BYTES == 0)
    }
}
