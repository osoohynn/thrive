package com.thrive.pleos.driving.navigation

import android.content.Context
import android.util.Log
import android.os.SystemClock
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class NavigationState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val querying: Boolean = false,
    val message: String = "아직 연결하지 않았어요.",
    val destination: LiveDestination? = null,
    val routeMode: String? = null,
    val confirmedAtElapsedMs: Long? = null,
    val arrivalEvents: Int = 0,
    val destinationChanges: Int = 0,
) {
    val destinationName: String? get() = destination?.name
    fun freshDestination(nowElapsedMs: Long): LiveDestination? {
        val confirmed = confirmedAtElapsedMs ?: return null
        if (!connected || querying || nowElapsedMs - confirmed !in 0..30_000) return null
        if (routeMode !in listOf("ROUTE_GUIDING", "SIMULATION_GUIDING")) return null
        return destination?.takeIf { it.valid }
    }
}

/** Read-only navigation connection. A confirmed snapshot may start an SDK-sourced session. */
internal class NavigationConnection(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(NavigationState())
    val state = mutableState.asStateFlow()
    private var helper: NaviHelper? = null
    private var listener: NaviHelperEventListener? = null
    private var timeout: Job? = null
    private var generation = 0

    fun connect() {
        if (state.value.connecting) return
        release()
        val requestGeneration = generation
        mutableState.value = NavigationState(connecting = true, message = "기본 내비게이션 서비스에 연결 중이에요.")
        try {
            val client = NaviHelper(appContext)
            helper = client
            val events = object : NaviHelperEventListener {
                override fun onDestinationChanged() {
                    scope.launch {
                        if (requestGeneration != generation) return@launch
                        mutableState.update { it.copy(destinationChanges = it.destinationChanges + 1) }
                        queryDestination()
                    }
                }

                override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                    scope.launch {
                        if (requestGeneration != generation) return@launch
                        timeout?.cancel()
                        val label = destinationInfo.name.ifBlank { destinationInfo.address }
                        val destination = LiveDestination(label, destinationInfo.latitude, destinationInfo.longitude).takeIf { it.valid }
                        mutableState.update { it.copy(querying = false, destination = destination, confirmedAtElapsedMs = SystemClock.elapsedRealtime(),
                            message = if (destination == null) "사용할 수 있는 목적지가 없어요. 기본 내비게이션에서 경로를 설정해 주세요." else "기본 내비게이션의 목적지 정보를 수신했어요.") }
                    }
                }

                override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
                    scope.launch {
                        if (requestGeneration == generation) mutableState.update { it.copy(routeMode = routeStateInfo.naviMode.name) }
                    }
                }

                override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
                    scope.launch {
                        if (requestGeneration == generation) mutableState.update { it.copy(arrivalEvents = it.arrivalEvents + 1, message = "도착 이벤트를 수신했어요. 기록 종료는 직접 확인해 주세요.") }
                    }
                }

                override fun onRouteCancelled() {
                    scope.launch {
                        if (requestGeneration != generation) return@launch
                        timeout?.cancel()
                        mutableState.update { it.copy(querying = false, destination = null, message = "내비게이션 경로가 취소됐어요.") }
                    }
                }

                override fun onError(errorCode: NaviErrorCode) {
                    scope.launch {
                        if (requestGeneration != generation) return@launch
                        timeout?.cancel()
                        mutableState.update { it.copy(querying = false, destination = null, message = "내비게이션 응답 오류: ${errorCode.name}") }
                    }
                }
            }
            listener = events
            timeout = scope.launch {
                delay(10_000)
                if (requestGeneration == generation && state.value.connecting) failed("서비스 연결 응답이 없어 중단했어요. 다시 연결해 주세요.")
            }
            client.initialize(onConnected = {
                scope.launch {
                    if (requestGeneration != generation) return@launch
                    timeout?.cancel()
                    try {
                        client.addListener(events)
                        mutableState.value = NavigationState(connected = true, message = "NaviHelper 3.0.5 서비스 연결 성공")
                        queryDestination()
                    } catch (error: Exception) { failed("이벤트 등록 실패: ${error.javaClass.simpleName}") }
                }
            }, onFailed = { error ->
                scope.launch { if (requestGeneration == generation) failed("서비스 연결 실패: ${error.javaClass.simpleName}") }
            })

        } catch (error: Exception) { failed("SDK 초기화 실패: ${error.javaClass.simpleName}") }
    }

    fun queryDestination() {
        if (!state.value.connected) return
        timeout?.cancel()
        mutableState.update { it.copy(querying = true, destination = null, routeMode = null, confirmedAtElapsedMs = null, message = "현재 목적지를 조회 중이에요.") }
        val requestGeneration = generation
        try {
            helper?.getRouteStateInfo()
            helper?.getDestinationInfo()
            timeout = scope.launch {
                delay(8_000)
                if (requestGeneration == generation && state.value.querying) mutableState.update {
                    it.copy(querying = false, message = "목적지 응답을 받지 못했어요. 기본 내비에서 목적지를 설정하고 다시 조회해 주세요.")
                }
            }
        } catch (error: Exception) {
            mutableState.update { it.copy(querying = false, message = "목적지 조회 실패: ${error.javaClass.simpleName}") }
        }
    }

    private fun failed(message: String) {
        release()
        mutableState.value = NavigationState(message = message)
    }

    fun release() {
        generation++
        timeout?.cancel()
        val client = helper
        val events = listener
        helper = null
        listener = null
        if (client != null) {
            try { if (events != null) client.removeListener(events) }
            catch (error: Exception) { Log.w("ThriveNavi", "Listener cleanup failed: ${error.javaClass.simpleName}") }
            try { client.release() }
            catch (error: Exception) { Log.w("ThriveNavi", "SDK cleanup failed: ${error.javaClass.simpleName}") }
        }
    }
}
