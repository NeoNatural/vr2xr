package com.vr2xr.app

import com.vr2xr.render.ProjectionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectionFovConfigTest {
    @Test
    fun clampDegreesLimitsToConfiguredRange() {
        assertEquals(ProjectionFovConfig.MIN_DEGREES, ProjectionFovConfig.clampDegrees(-100f), EPSILON)
        assertEquals(ProjectionFovConfig.MAX_DEGREES, ProjectionFovConfig.clampDegrees(1000f), EPSILON)
    }

    @Test
    fun normalizeDegreesFallsBackToDefaultForNonFiniteInputs() {
        assertEquals(
            ProjectionFovConfig.DEFAULT_DEGREES,
            ProjectionFovConfig.normalizeDegrees(Float.NaN),
            EPSILON
        )
        assertEquals(
            ProjectionFovConfig.DEFAULT_DEGREES,
            ProjectionFovConfig.normalizeDegrees(Float.POSITIVE_INFINITY),
            EPSILON
        )
        assertEquals(
            ProjectionFovConfig.DEFAULT_DEGREES,
            ProjectionFovConfig.normalizeDegrees(Float.NEGATIVE_INFINITY),
            EPSILON
        )
    }

    @Test
    fun normalizeDegreesClampsFiniteValues() {
        assertEquals(ProjectionFovConfig.MIN_DEGREES, ProjectionFovConfig.normalizeDegrees(0f), EPSILON)
        assertEquals(ProjectionFovConfig.MAX_DEGREES, ProjectionFovConfig.normalizeDegrees(1000f), EPSILON)
        assertEquals(99f, ProjectionFovConfig.normalizeDegrees(99f), EPSILON)
    }

    @Test
    fun pinchScaleGreaterThanOneLowersFovForZoomIn() {
        assertEquals(80f, ProjectionFovConfig.fovAfterPinchScale(100f, 1.25f), EPSILON)
    }

    @Test
    fun pinchScaleLessThanOneRaisesFovForZoomOut() {
        assertEquals(125f, ProjectionFovConfig.fovAfterPinchScale(100f, 0.8f), EPSILON)
    }

    @Test
    fun pinchScaleReturnsNormalizedCurrentFovForInvalidScaleFactors() {
        assertEquals(100f, ProjectionFovConfig.fovAfterPinchScale(100f, 0f), EPSILON)
        assertEquals(100f, ProjectionFovConfig.fovAfterPinchScale(100f, -1f), EPSILON)
        assertEquals(100f, ProjectionFovConfig.fovAfterPinchScale(100f, Float.NaN), EPSILON)
        assertEquals(100f, ProjectionFovConfig.fovAfterPinchScale(100f, Float.POSITIVE_INFINITY), EPSILON)
    }

    @Test
    fun pinchScaleClampsToConfiguredRange() {
        assertEquals(ProjectionFovConfig.MIN_DEGREES, ProjectionFovConfig.fovAfterPinchScale(100f, 10f), EPSILON)
        assertEquals(ProjectionFovConfig.MAX_DEGREES, ProjectionFovConfig.fovAfterPinchScale(100f, 0.1f), EPSILON)
    }

    @Test
    fun normalizeFisheyeSourceFovFallsBackToDefaultForNonFiniteInputs() {
        assertEquals(
            ProjectionFovConfig.DEFAULT_FISHEYE_SOURCE_FOV_DEGREES,
            ProjectionFovConfig.normalizeFisheyeSourceFovDegrees(Float.NaN),
            EPSILON
        )
        assertEquals(
            ProjectionFovConfig.DEFAULT_FISHEYE_SOURCE_FOV_DEGREES,
            ProjectionFovConfig.normalizeFisheyeSourceFovDegrees(Float.POSITIVE_INFINITY),
            EPSILON
        )
        assertEquals(
            ProjectionFovConfig.DEFAULT_FISHEYE_SOURCE_FOV_DEGREES,
            ProjectionFovConfig.normalizeFisheyeSourceFovDegrees(Float.NEGATIVE_INFINITY),
            EPSILON
        )
    }

    @Test
    fun normalizeFisheyeSourceFovClampsFiniteValues() {
        assertEquals(
            ProjectionFovConfig.MIN_FISHEYE_SOURCE_FOV_DEGREES,
            ProjectionFovConfig.normalizeFisheyeSourceFovDegrees(100f),
            EPSILON
        )
        assertEquals(
            ProjectionFovConfig.MAX_FISHEYE_SOURCE_FOV_DEGREES,
            ProjectionFovConfig.normalizeFisheyeSourceFovDegrees(300f),
            EPSILON
        )
        assertEquals(190f, ProjectionFovConfig.normalizeFisheyeSourceFovDegrees(190f), EPSILON)
    }

    @Test
    fun projectionModeFromPreferenceFallsBackToVr180ForUnknownValues() {
        assertEquals(ProjectionMode.VR180, ProjectionFovConfig.projectionModeFromPreference(null))
        assertEquals(ProjectionMode.VR180, ProjectionFovConfig.projectionModeFromPreference(""))
        assertEquals(ProjectionMode.VR180, ProjectionFovConfig.projectionModeFromPreference("unknown"))
    }

    @Test
    fun projectionModeFromPreferenceParsesKnownValues() {
        assertEquals(
            ProjectionMode.VR180_FISHEYE_EQUIDISTANT,
            ProjectionFovConfig.projectionModeFromPreference("VR180_FISHEYE_EQUIDISTANT")
        )
        assertEquals(
            ProjectionMode.VR180_FISHEYE_EQUISOLID,
            ProjectionFovConfig.projectionModeFromPreference("VR180_FISHEYE_EQUISOLID")
        )
    }
}

private const val EPSILON = 1e-6f
