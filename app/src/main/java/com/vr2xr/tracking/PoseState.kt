package com.vr2xr.tracking

/**
 * Quaternion fields are the canonical pose representation for control and rendering.
 * Euler fields are derived/debug values and must not be used as primary control inputs.
 */
data class PoseState(
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val qx: Float = 0f,
    val qy: Float = 0f,
    val qz: Float = 0f,
    val qw: Float = 1f,
    val trackingAvailable: Boolean = false
) {
    fun orientationQuaternion(): Quaternion = Quaternion(qx, qy, qz, qw).normalized()

    fun withQuaternion(quaternion: Quaternion): PoseState {
        val q = quaternion.normalized()
        return copy(qx = q.x, qy = q.y, qz = q.z, qw = q.w)
    }
}
