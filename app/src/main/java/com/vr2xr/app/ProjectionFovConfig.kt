package com.vr2xr.app

import com.vr2xr.render.ProjectionMode

object ProjectionFovConfig {
    const val DEFAULT_DEGREES = 95f
    const val MIN_DEGREES = 25f
    const val MAX_DEGREES = 175f
    const val STEP_DEGREES = 5f
    const val DEFAULT_FISHEYE_SOURCE_FOV_DEGREES = 180f
    const val MIN_FISHEYE_SOURCE_FOV_DEGREES = 160f
    const val MAX_FISHEYE_SOURCE_FOV_DEGREES = 220f
    const val PREFERENCES_FILE = "projection_settings"
    const val PREFERENCE_KEY_FOV_DEGREES = "fov_degrees"
    const val PREFERENCE_KEY_PROJECTION_MODE = "projection_mode"
    const val PREFERENCE_KEY_FISHEYE_SOURCE_FOV_DEGREES = "fisheye_source_fov_degrees"

    fun clampDegrees(value: Float): Float {
        return value.coerceIn(MIN_DEGREES, MAX_DEGREES)
    }

    fun normalizeDegrees(value: Float): Float {
        if (!value.isFinite()) {
            return DEFAULT_DEGREES
        }
        return clampDegrees(value)
    }

    fun fovAfterPinchScale(currentDegrees: Float, scaleFactor: Float): Float {
        if (!scaleFactor.isFinite() || scaleFactor <= 0f) {
            return normalizeDegrees(currentDegrees)
        }
        return normalizeDegrees(currentDegrees / scaleFactor)
    }

    fun normalizeFisheyeSourceFovDegrees(value: Float): Float {
        if (!value.isFinite()) {
            return DEFAULT_FISHEYE_SOURCE_FOV_DEGREES
        }
        return value.coerceIn(MIN_FISHEYE_SOURCE_FOV_DEGREES, MAX_FISHEYE_SOURCE_FOV_DEGREES)
    }

    fun projectionModeFromPreference(value: String?): ProjectionMode {
        if (value.isNullOrBlank()) {
            return ProjectionMode.VR180
        }
        return runCatching { ProjectionMode.valueOf(value) }.getOrDefault(ProjectionMode.VR180)
    }

    fun projectionModeToPreference(mode: ProjectionMode): String {
        return mode.name
    }
}
