package com.thrive.pleos.driving.location

import android.content.Context
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class LocationState(
    val sequence: Long = 0,
    val fix: LocationFix? = null,
    val message: String = "위치 수신을 시작하지 않았어요.",
)

/** Latest-value stream; the recorder explicitly samples at up to one stored fix per second. */
internal class LocationConnection(context: Context, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private val mutableState = MutableStateFlow(LocationState())
    val state = mutableState.asStateFlow()
    private var client: FusedLocation? = null
    private var listener: FusedLocationListener? = null
    private var generation = 0
    private var registered = false

    fun start() {
        stop()
        val token = generation
        if (!context.hasFusedLocationPermission()) {
            mutableState.value = LocationState(message = "위치 사용 권한이 필요해요. 권한을 허용한 후 다시 연결해 주세요.")
            return
        }
        mutableState.value = LocationState(message = "위치 서비스 지원 여부를 확인하고 있어요.")
        try {
            val service = FusedLocation(context)
            if (!service.isSupported()) {
                mutableState.value = LocationState(message = "현재 환경은 위치 SDK를 지원하지 않아요. 위치·거리는 미측정으로 남아요.")
                return
            }
            client = service
            val callback = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    val receivedElapsed = SystemClock.elapsedRealtime()
                    val receivedEpoch = System.currentTimeMillis()
                    scope.launch {
                        if (token != generation) return@launch
                        val fix = location?.let {
                            LocationFix(it.latitude, it.longitude, it.timestampMillis, receivedElapsed, receivedEpoch, it.confidence.toDouble())
                        } ?: LocationFix(null, null, null, receivedElapsed, receivedEpoch)
                        mutableState.value = LocationState(state.value.sequence + 1, fix,
                            if (location == null) "위치 데이터가 없어요. 거리를 임의로 채우지 않아요." else "SDK 위치 업데이트를 수신하고 있어요.")
                    }
                }
                override fun onError(exception: Exception) {
                    val receivedElapsed = SystemClock.elapsedRealtime()
                    scope.launch {
                        if (token != generation) return@launch
                        mutableState.value = LocationState(state.value.sequence + 1,
                            LocationFix(null, null, null, receivedElapsed, System.currentTimeMillis()),
                            if (exception is SecurityException) "위치 사용 권한을 확인해 주세요. 미수신 구간은 계산하지 않아요."
                            else "위치 수신 오류 (${exception.javaClass.simpleName}) · 누락 구간으로 처리해요.")
                    }
                }
            }
            listener = callback
            service.initialize()
            mutableState.value = LocationState(message = "위치 업데이트를 기다리고 있어요.")
            service.registerFusedLocationCallback(callback)
            registered = true
        } catch (error: Exception) {
            stop()
            mutableState.value = LocationState(message = if (error is SecurityException)
                "위치 사용 권한을 확인해 주세요. 권한을 허용한 후 다시 연결할 수 있어요."
                else "위치를 연결하지 못했어요 (${error.javaClass.simpleName}). 위치·거리는 미측정으로 남아요.")
        }
    }

    fun stop() {
        generation++
        val service = client
        val callback = listener
        val wasRegistered = registered
        registered = false
        client = null; listener = null
        if (service != null) {
            try { if (callback != null && wasRegistered) service.unregisterFusedLocationCallback(callback) }
            catch (error: Exception) { Log.w("ThriveLocation", "Unregister failed: ${error.javaClass.simpleName}") }
            try { service.release() }
            catch (error: Exception) { Log.w("ThriveLocation", "Release failed: ${error.javaClass.simpleName}") }
        }
    }
}
