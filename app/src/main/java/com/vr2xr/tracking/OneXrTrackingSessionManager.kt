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
    val quaternion = quaternionFromEuler(yawRad, pitchRad, rollRad)
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

private fun Float.toRadians(): Float {
    return Math.toRadians(toDouble()).toFloat()
}

