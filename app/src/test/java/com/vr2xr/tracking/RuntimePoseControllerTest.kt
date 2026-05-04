package com.vr2xr.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RuntimePoseControllerTest {
    @Test
    fun lyingDownRecenterAllowsCrossingNinetyWithoutLock() {
        val controller = RuntimePoseController(initialSensitivity = 1f)
        controller.onTrackingPoseUpdated(pose(pitch = (Math.PI / 2.0).toFloat()))
        controller.setReferenceOrientationFromCurrentPose()

        val beyond = controller.onTrackingPoseUpdated(pose(pitch = (Math.PI * 0.75).toFloat()))
        val relative = beyond.orientationQuaternion()
        assertTrue(abs(relative.w) < 1f)
    }

    @Test
    fun yawRemainsContinuousNearHighPitch() {
        val controller = RuntimePoseController(initialSensitivity = 1f)
        controller.setReferenceOrientationFromCurrentPose()

        val a = controller.onTrackingPoseUpdated(pose(yaw = 0.2f, pitch = 1.55f))
        val b = controller.onTrackingPoseUpdated(pose(yaw = 0.4f, pitch = 1.55f))

        assertTrue(abs(b.qz - a.qz) < 0.5f)
    }

    @Test
    fun imuFreezeAndResumeKeepsQuaternionSemantics() {
        val controller = RuntimePoseController(initialSensitivity = 1f)
        controller.onTrackingPoseUpdated(pose(yaw = 0.3f, pitch = 0.1f))
        val frozen = controller.setImuTrackingEnabled(false)
        controller.onTrackingPoseUpdated(pose(yaw = 1.5f, pitch = 0.8f))
        val stillFrozen = controller.currentPose()
        assertEquals(frozen.qw, stillFrozen.qw, EPSILON)

        val resumed = controller.setImuTrackingEnabled(true)
        assertTrue(abs(resumed.qw - frozen.qw) > 1e-3f)
    }

    @Test
    fun touchpadCommitAndResetStillWork() {
        val controller = RuntimePoseController(initialSensitivity = 1f)
        controller.applyTouchpadBiasDelta(0.2f, -0.1f)
        val committed = controller.commitTouchpadBias()
        assertEquals(0.2f, committed.yaw, EPSILON)
        val reset = controller.resetTouchpadBias()
        assertEquals(0f, reset.yaw, EPSILON)
        assertEquals(0f, reset.pitch, EPSILON)
    }

    @Test
    fun sensitivityIsClamped() {
        val controller = RuntimePoseController(initialSensitivity = 0.5f)
        controller.setImuSensitivity(100f)
        assertEquals(MAX_IMU_SENSITIVITY, controller.imuSensitivity(), EPSILON)
    }
}

private fun pose(yaw: Float = 0f, pitch: Float = 0f, roll: Float = 0f): PoseState {
    val q = quaternionFromEuler(yaw, pitch, roll)
    return PoseState(yaw = yaw, pitch = pitch, roll = roll, qx = q.x, qy = q.y, qz = q.z, qw = q.w, trackingAvailable = true)
}

private const val EPSILON = 1e-5f
