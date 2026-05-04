package com.vr2xr.tracking

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import io.onexr.OneXrClient
import io.onexr.OneXrEndpoint
import io.onexr.XrBiasState
import io.onexr.XrConnectionInfo
import io.onexr.XrPoseDataMode
import io.onexr.XrPoseSnapshot
import io.onexr.XrSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OneXrConnectionProbe(
    val connected: Boolean,
    val detail: String
)

class OneXrTrackingSessionManager(
    context: Context,
    endpoint: OneXrEndpoint = OneXrEndpoint()
) {
    private val client = OneXrClient(context.applicationContext, endpoint)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _pose = MutableStateFlow<PoseState?>(null)

    val pose: StateFlow<PoseState?> = _pose.asStateFlow()
    val sessionState: StateFlow<XrSessionState> = client.sessionState
    val biasState: StateFlow<XrBiasState> = client.biasState

    init {
        client.setPoseDataMode(XrPoseDataMode.SMOOTH_IMU)
        scope.launch {
            client.poseData.collect { snapshot ->
                _pose.value = snapshot?.toPoseState()
            }
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun probeConnection(): OneXrConnectionProbe {
        return runCatching {
            val routing = client.describeRouting()
            val candidates = routing.networkCandidates
            val addresses = routing.addressCandidates
            val connected = candidates.isNotEmpty() || addresses.isNotEmpty()
            val detail = if (connected) {
                val names = candidates.map { it.interfaceName }.distinct()
                if (names.isEmpty()) {
                    "connected"
                } else {
                    "connected via ${names.joinToString(",")}"
                }
            } else {
                "not connected"
            }
            OneXrConnectionProbe(connected = connected, detail = detail)
        }.getOrElse { error ->
            OneXrConnectionProbe(
                connected = false,
                detail = "error ${error.javaClass.simpleName}:${error.message ?: "no-message"}"
            )
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun runCalibration(): Result<XrConnectionInfo> {
        return runCatching { client.start() }
    }

    suspend fun stop(): Result<Unit> {
        return runCatching { client.stop() }
    }

    suspend fun zeroView(): Result<Unit> {
        return runCatching { client.zeroView() }
    }

    suspend fun recalibrate(): Result<Unit> {
        return runCatching { client.recalibrate() }
    }

}


private fun XrPoseSnapshot.toPoseState(): PoseState {
    val yawRad = relativeOrientation.yaw.toRadians()
    val pitchRad = relativeOrientation.pitch.toRadians()
    val rollRad = relativeOrientation.roll.toRadians()
    val quaternion = tryExtractSdkQuaternion() ?: quaternionFromEuler(yawRad, pitchRad, rollRad)
    return PoseState(
        yaw = yawRad,
        pitch = pitchRad,
        roll = rollRad,
        qx = quaternion.x,
        qy = quaternion.y,
        qz = quaternion.z,
        qw = quaternion.w,
        trackingAvailable = isCalibrated
    )
}

private fun XrPoseSnapshot.tryExtractSdkQuaternion(): Quaternion? {
    val roots = listOf(this) + listOfNotNull(readMember(this, "relativeOrientation"), readMember(this, "orientation"), readMember(this, "pose"))
    for (root in roots) {
        val direct = readQuaternionMembers(root, "qx", "qy", "qz", "qw")
            ?: readQuaternionMembers(root, "x", "y", "z", "w")
        if (direct != null) return direct

        val nestedCandidates = listOf("quaternion", "rotationQuaternion", "orientationQuaternion", "rot")
        for (name in nestedCandidates) {
            val nested = readMember(root, name) ?: continue
            val nestedQuaternion = readQuaternionMembers(nested, "x", "y", "z", "w")
                ?: readQuaternionMembers(nested, "qx", "qy", "qz", "qw")
            if (nestedQuaternion != null) return nestedQuaternion
        }
    }
    return null
}

private fun readQuaternionMembers(source: Any, x: String, y: String, z: String, w: String): Quaternion? {
    val qx = readFloatMember(source, x) ?: return null
    val qy = readFloatMember(source, y) ?: return null
    val qz = readFloatMember(source, z) ?: return null
    val qw = readFloatMember(source, w) ?: return null
    return Quaternion(qx, qy, qz, qw).normalized()
}

private fun readMember(source: Any, name: String): Any? {
    val cls = source.javaClass
    val getterName = "get" + name.replaceFirstChar { it.uppercase() }
    val getter = cls.methods.firstOrNull { it.parameterCount == 0 && it.name == getterName }
    if (getter != null) return runCatching { getter.invoke(source) }.getOrNull()
    val field = cls.fields.firstOrNull { it.name == name }
    if (field != null) return runCatching { field.get(source) }.getOrNull()
    return null
}

private fun readFloatMember(source: Any, name: String): Float? {
    return when (val v = readMember(source, name)) {
        is Float -> v
        is Double -> v.toFloat()
        is Number -> v.toFloat()
        else -> null
    }
}

private fun Float.toRadians(): Float {
    return Math.toRadians(toDouble()).toFloat()
}

