package com.vr2xr.tracking

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Quaternion(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float
)

data class AxisAngle(
    val axisX: Float,
    val axisY: Float,
    val axisZ: Float,
    val angleRad: Float
)

fun Quaternion.normalized(): Quaternion {
    val mag = sqrt((x * x) + (y * y) + (z * z) + (w * w))
    if (mag <= 1e-8f) return Quaternion(0f, 0f, 0f, 1f)
    return Quaternion(x / mag, y / mag, z / mag, w / mag)
}

fun Quaternion.inverse(): Quaternion {
    val n = normalized()
    return Quaternion(-n.x, -n.y, -n.z, n.w)
}

fun Quaternion.multiply(other: Quaternion): Quaternion {
    return Quaternion(
        x = (w * other.x) + (x * other.w) + (y * other.z) - (z * other.y),
        y = (w * other.y) - (x * other.z) + (y * other.w) + (z * other.x),
        z = (w * other.z) + (x * other.y) - (y * other.x) + (z * other.w),
        w = (w * other.w) - (x * other.x) - (y * other.y) - (z * other.z)
    ).normalized()
}

fun quaternionFromEuler(yaw: Float, pitch: Float, roll: Float): Quaternion {
    // Coordinate convention: +X right, +Y up, +Z forward.
    // Yaw around Y axis, pitch around X axis, roll around Z axis.
    val qYaw = quaternionFromAxisAngle(0f, 1f, 0f, yaw)
    val qPitch = quaternionFromAxisAngle(1f, 0f, 0f, pitch)
    val qRoll = quaternionFromAxisAngle(0f, 0f, 1f, roll)
    return qYaw.multiply(qPitch).multiply(qRoll)
}

fun Quaternion.toAxisAngle(): AxisAngle {
    val q = normalized()
    val angle = 2f * kotlin.math.acos(q.w.coerceIn(-1f, 1f))
    val s = sqrt((1f - (q.w * q.w)).coerceAtLeast(0f))
    return if (s < 1e-6f) AxisAngle(0f, 1f, 0f, 0f) else AxisAngle(q.x / s, q.y / s, q.z / s, angle)
}

fun quaternionFromAxisAngle(axisX: Float, axisY: Float, axisZ: Float, angleRad: Float): Quaternion {
    val half = angleRad * 0.5f
    val s = sin(half)
    return Quaternion(axisX * s, axisY * s, axisZ * s, cos(half)).normalized()
}

fun Quaternion.scaledAngle(scale: Float): Quaternion {
    val aa = toAxisAngle()
    return quaternionFromAxisAngle(aa.axisX, aa.axisY, aa.axisZ, aa.angleRad * scale)
}

fun Quaternion.toRotationMatrix3x3(): FloatArray {
    val q = normalized()
    val xx = q.x * q.x
    val yy = q.y * q.y
    val zz = q.z * q.z
    val xy = q.x * q.y
    val xz = q.x * q.z
    val yz = q.y * q.z
    val wx = q.w * q.x
    val wy = q.w * q.y
    val wz = q.w * q.z
    val m00 = 1f - 2f * (yy + zz)
    val m01 = 2f * (xy - wz)
    val m02 = 2f * (xz + wy)
    val m10 = 2f * (xy + wz)
    val m11 = 1f - 2f * (xx + zz)
    val m12 = 2f * (yz - wx)
    val m20 = 2f * (xz - wy)
    val m21 = 2f * (yz + wx)
    val m22 = 1f - 2f * (xx + yy)
    // OpenGL expects column-major data when transpose=false.
    return floatArrayOf(
        m00, m10, m20,
        m01, m11, m21,
        m02, m12, m22
    )
}
