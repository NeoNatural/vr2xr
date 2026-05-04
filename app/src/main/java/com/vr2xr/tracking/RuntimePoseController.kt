package com.vr2xr.tracking

data class TouchpadBias(
    val yawRad: Float = 0f,
    val pitchRad: Float = 0f
)

class RuntimePoseController(
    initialSensitivity: Float,
    private val minSensitivity: Float = MIN_IMU_SENSITIVITY,
    private val maxSensitivity: Float = MAX_IMU_SENSITIVITY
) {
    private var imuSensitivity = initialSensitivity.coerceIn(minSensitivity, maxSensitivity)
    private var imuTrackingEnabled = true
    private var latestTrackingPose = PoseState()
    private var referenceOrientation = Quaternion(0f, 0f, 0f, 1f)
    private var frozenRelativeOrientation = Quaternion(0f, 0f, 0f, 1f)
    private var accumulatedTouchpadBias = TouchpadBias()
    private var touchpadGestureBias = TouchpadBias()
    private var appliedPose = composePose()

    fun imuSensitivity(): Float = imuSensitivity
    fun isImuTrackingEnabled(): Boolean = imuTrackingEnabled
    fun currentPose(): PoseState = appliedPose

    fun onTrackingPoseUpdated(pose: PoseState): PoseState {
        latestTrackingPose = pose
        if (!imuTrackingEnabled) return appliedPose
        appliedPose = composePose()
        return appliedPose
    }

    fun setReferenceOrientationFromCurrentPose(): PoseState {
        return setReferenceOrientation(latestTrackingPose.orientationQuaternion())
    }

    fun setReferenceOrientation(orientation: Quaternion): PoseState {
        referenceOrientation = orientation.normalized()
        appliedPose = composePose()
        return appliedPose
    }

    fun setImuSensitivity(value: Float): PoseState {
        imuSensitivity = value.coerceIn(minSensitivity, maxSensitivity)
        if (!imuTrackingEnabled) return appliedPose
        appliedPose = composePose()
        return appliedPose
    }

    fun setImuTrackingEnabled(enabled: Boolean): PoseState {
        if (imuTrackingEnabled == enabled) return appliedPose
        if (!enabled) frozenRelativeOrientation = scaledRelativeOrientation()
        imuTrackingEnabled = enabled
        appliedPose = composePose()
        return appliedPose
    }

    fun applyTouchpadBiasDelta(yawDeltaRad: Float, pitchDeltaRad: Float): PoseState {
        touchpadGestureBias = normalizeTouchpadBias(TouchpadBias(
            yawRad = touchpadGestureBias.yawRad + yawDeltaRad,
            pitchRad = touchpadGestureBias.pitchRad + pitchDeltaRad
        ))
        appliedPose = composePose()
        return appliedPose
    }

    fun commitTouchpadBias(): PoseState {
        accumulatedTouchpadBias = normalizeTouchpadBias(combinedTouchpadBias())
        touchpadGestureBias = TouchpadBias()
        appliedPose = composePose()
        return appliedPose
    }

    fun resetTouchpadBias(): PoseState {
        accumulatedTouchpadBias = TouchpadBias()
        touchpadGestureBias = TouchpadBias()
        appliedPose = composePose()
        return appliedPose
    }

    private fun composePose(): PoseState {
        val relative = if (imuTrackingEnabled) scaledRelativeOrientation() else frozenRelativeOrientation
        val bias = combinedTouchpadBias()
        val touchBiasQuat = quaternionFromEuler(bias.yawRad, bias.pitchRad, 0f)
        val finalOrientation = touchBiasQuat.multiply(relative)
        val dbg = combinedTouchpadBias()
        return latestTrackingPose.withQuaternion(finalOrientation).copy(
            yaw = dbg.yawRad,
            pitch = dbg.pitchRad,
            roll = 0f
        )
    }

    private fun scaledRelativeOrientation(): Quaternion {
        val current = latestTrackingPose.orientationQuaternion()
        val relative = referenceOrientation.inverse().multiply(current)
        return relative.scaledAngle(imuSensitivity)
    }

    private fun combinedTouchpadBias(): TouchpadBias = normalizeTouchpadBias(
        TouchpadBias(
            yawRad = accumulatedTouchpadBias.yawRad + touchpadGestureBias.yawRad,
            pitchRad = accumulatedTouchpadBias.pitchRad + touchpadGestureBias.pitchRad
        )
    )

    private fun normalizeTouchpadBias(bias: TouchpadBias): TouchpadBias {
        return TouchpadBias(normalizeRadians(bias.yawRad), normalizeRadians(bias.pitchRad))
    }
}

private fun normalizeRadians(value: Float): Float {
    var normalized = value % TWO_PI_RADIANS
    if (normalized > PI_RADIANS) normalized -= TWO_PI_RADIANS
    else if (normalized < -PI_RADIANS) normalized += TWO_PI_RADIANS
    return normalized
}

const val MIN_IMU_SENSITIVITY = 0.1f
const val MAX_IMU_SENSITIVITY = 0.9f
const val TOUCHPAD_DRAG_FULL_TRAVEL_RADIANS = 0.30f
private const val PI_RADIANS = 3.1415927f
private const val TWO_PI_RADIANS = PI_RADIANS * 2f
