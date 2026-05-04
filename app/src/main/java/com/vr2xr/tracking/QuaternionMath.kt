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
    val cy = cos(yaw * 0.5f)
    val sy = sin(yaw * 0.5f)
    val cp = cos(pitch * 0.5f)
    val sp = sin(pitch * 0.5f)
    val cr = cos(roll * 0.5f)
    val sr = sin(roll * 0.5f)
    return Quaternion(
        x = (sr * cp * cy) - (cr * sp * sy),
        y = (cr * sp * cy) + (sr * cp * sy),
        z = (cr * cp * sy) - (sr * sp * cy),
        w = (cr * cp * cy) + (sr * sp * sy)
    ).normalized()
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
    return floatArrayOf(
        1f - 2f * (yy + zz), 2f * (xy - wz), 2f * (xz + wy),
        2f * (xy + wz), 1f - 2f * (xx + zz), 2f * (yz - wx),
        2f * (xz - wy), 2f * (yz + wx), 1f - 2f * (xx + yy)
    )
}
