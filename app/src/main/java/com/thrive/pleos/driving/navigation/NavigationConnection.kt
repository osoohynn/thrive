package com.thrive.pleos.driving.navigation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.navi.constants.*
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One SDK connection owns snapshots and explicit, user-approved route requests. */
internal class NavigationConnection(context: Context, private val scope: CoroutineScope,
    private val onObservation: (NavigationObservation) -> Unit = {}) {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(NavigationState())
    val state = mutableState.asStateFlow()
    private var helper: NaviHelper? = null
    private var listener: NaviHelperEventListener? = null
    private var timeout: Job? = null
    private var courseTimeout: Job? = null
    private var generation = 0
    private var refreshAgain = false

    fun connect() {
        if (state.value.connecting) return
        release()
        val token = generation
        mutableState.value = NavigationState(connecting = true, message = "기본 내비게이션에 연결 중이에요.")
        try {
            val client = NaviHelper(appContext)
            helper = client
            val events = object : NaviHelperEventListener {
                override fun onRouteStarted(info: RouteStartInfo) = dispatch(token) { observe(NavigationEventKind.RouteSearchStarted) }
                override fun onDestinationChanged() = dispatch(token) {
                    observe(NavigationEventKind.DestinationChanged)
                    mutableState.update { it.copy(destinationChanges = it.destinationChanges + 1) }
                    queryDestination()
                }
                override fun onDestinationInfo(info: DestinationInfo) = dispatch(token) {
                    Log.d("ThriveNavi", "remainingTime=${info.remainTime} remainingDistance=${info.remainDistance}")
                    val place = LiveDestination(info.name.ifBlank { info.address }, info.latitude, info.longitude,
                        info.address, info.poiId.orEmpty(), info.poiSubId.orEmpty().ifBlank { "0" }, info.poiProvider.orEmpty()).takeIf { it.valid }
                    mutableState.update { it.copy(destination = place, confirmedAtElapsedMs = now(),
                        estimate = NativeRouteEstimate(info.remainTime, info.remainDistance).takeIf { value -> value.valid },
                        message = if (place == null) "기본 내비에서 목적지를 설정해 주세요." else "기본 내비게이션의 목적지 정보를 수신했어요.") }
                    finishQueryIfReady()
                }
                override fun onRouteStateInfo(info: RouteStateInfo) = dispatch(token) {
                    Log.d("ThriveNavi", "mode=${info.naviMode.name} option=${info.routeOption.name}")
                    mutableState.update { it.copy(routeMode = info.naviMode.name, routeOption = info.routeOption.name, routeConfirmedAtMs = now()) }
                    finishQueryIfReady()
                }
                override fun onCurrentLocationInfo(info: CurrentLocationInfo) = dispatch(token) {
                    val point = LiveDestination("출발지로 돌아오기", info.latitude, info.longitude, info.address).takeIf { it.valid }
                    mutableState.update { it.copy(currentPoint = point, currentPointAtMs = now()) }
                }
                override fun onRecentDestinationInfo(info: List<RecentDestinationInfo>) = dispatch(token) {
                    val places = info.map { LiveDestination(it.name.ifBlank { it.address }, it.latitude, it.longitude,
                        it.address, it.poiId.orEmpty(), it.poiSubId.orEmpty().ifBlank { "0" }, it.poiProvider.orEmpty()) }.filter { it.valid }
                    mutableState.update { it.copy(recentDestinations = places.take(10)) }
                }
                override fun onWayPointInfo(info: List<WaypointInfo>) = dispatch(token) {
                    mutableState.update { it.copy(waypoints = info.map { point ->
                        LiveDestination(point.name.ifBlank { point.address }, point.latitude, point.longitude, point.address,
                            point.poiId.orEmpty(), point.poiSubId.orEmpty().ifBlank { "0" }, point.poiProvider.orEmpty())
                    }.filter { it.valid }) }
                    evaluateCourse()
                }
                override fun onTBTInfo(info: List<TBTInfo>) = dispatch(token) {
                    info.firstOrNull()?.let { turn -> observe(NavigationEventKind.TbtReceived, turn.description, turn.type.name) }
                    mutableState.update { it.copy(turns = info.take(4).map { turn ->
                        NativeTurn(turn.type.name, turn.distance.takeIf { value -> value.isFinite() && value >= 0 }, turn.description.take(200))
                    }, turnsAtMs = now()) }
                }
                override fun onRoutingCompleted() = dispatch(token) { routingCompleted() }
                override fun onNavigationStatus(status: NavigationStatus) = dispatch(token) { queryDestination() }
                override fun onRouteOptionChanged(option: RouteOption) = dispatch(token) { queryDestination() }
                @Suppress("DEPRECATION")
                override fun onRouteEnded() = dispatch(token) { routingCompleted() }
                override fun onReRouteEnded() = dispatch(token) { observe(NavigationEventKind.Rerouted); queryDestination() }
                override fun onDestinationArrived(info: DestinationArrivedInfo) = dispatch(token) {
                    observe(NavigationEventKind.DestinationArrived)
                    mutableState.update { it.copy(arrivalEvents = it.arrivalEvents + 1, message = "도착 이벤트를 수신했어요. 기록 종료를 확인해 주세요.") }
                }
                override fun onRouteCancelled() = dispatch(token) {
                    observe(NavigationEventKind.RouteCancelled)
                    timeout?.cancel()
                    mutableState.update { it.copy(querying = false, destination = null, confirmedAtElapsedMs = null,
                        routeMode = "SAFE_GUIDING", turns = emptyList(), message = "내비게이션 경로가 취소됐어요.") }
                    if (state.value.course?.phase != CoursePhase.Calculating) failCourse("경로가 취소됐어요. 다시 계산해 주세요.")
                }
                override fun onError(errorCode: NaviErrorCode) = dispatch(token) {
                    observe(NavigationEventKind.NavigationError, errorCode.name)
                    timeout?.cancel()
                    mutableState.update { it.copy(querying = false, destination = null, confirmedAtElapsedMs = null,
                        message = "내비게이션 응답 오류: ${errorCode.name}") }
                    failCourse("경로 요청을 완료하지 못했어요 (${errorCode.name}).")
                }
            }
            listener = events
            timeout = scope.launch {
                delay(10_000)
                if (token == generation && state.value.connecting) failed("서비스 연결 응답이 없어요. 다시 연결해 주세요.")
            }
            client.initialize(onConnected = {
                dispatch(token) {
                    timeout?.cancel()
                    try {
                        client.addListener(events)
                        Log.d("ThriveNavi", "connected")
                        mutableState.update { it.copy(connected = true, connecting = false, message = "기본 내비게이션에 연결했어요.") }
                        queryDestination()
                    } catch (error: Exception) { failed("이벤트 등록 실패: ${error.javaClass.simpleName}") }
                }
            }, onFailed = { error -> dispatch(token) { failed("서비스 연결 실패: ${error.javaClass.simpleName}") } })
        } catch (error: Exception) { failed("SDK 초기화 실패: ${error.javaClass.simpleName}") }
    }

    private fun dispatch(token: Int, block: () -> Unit) {
        scope.launch { if (token == generation) block() }
    }

    fun queryDestination() {
        if (!state.value.connected) return
        if (state.value.querying) { refreshAgain = true; return }
        timeout?.cancel()
        mutableState.update { it.copy(querying = true, destination = null, routeMode = null, estimate = null, waypoints = null,
            confirmedAtElapsedMs = null, routeConfirmedAtMs = null, turns = emptyList(), message = "현재 경로를 확인하고 있어요.") }
        val token = generation
        timeout = scope.launch {
            delay(8_000)
            if (token == generation && state.value.querying) {
                mutableState.update { it.copy(querying = false, destination = null, message = "경로 응답을 받지 못했어요. 다시 확인해 주세요.") }
                failCourse("경로를 확인하지 못했어요. 다시 계산해 주세요.")
            }
        }
        try {
            helper?.getRouteStateInfo()
            helper?.getDestinationInfo()
            helper?.getCurrentLocationInfo()
            helper?.getRecentDestinationInfo()
            helper?.getWaypointInfo()
            helper?.getTBTInfo()
        } catch (error: Exception) {
            timeout?.cancel()
            mutableState.update { it.copy(querying = false, destination = null, message = "경로 조회 실패: ${error.javaClass.simpleName}") }
            failCourse("경로를 확인하지 못했어요. 다시 시도해 주세요.")
        }
    }

    private fun finishQueryIfReady() {
        if (state.value.confirmedAtElapsedMs == null || state.value.routeConfirmedAtMs == null) return
        timeout?.cancel()
        mutableState.update { it.copy(querying = false) }
        if (refreshAgain) { refreshAgain = false; queryDestination() } else evaluateCourse()
    }

    fun requestPractice(plan: NativePracticePlan) {
        if (!plan.valid || state.value.course?.phase == CoursePhase.Calculating) return
        if (!state.value.connected || state.value.querying) {
            mutableState.update { it.copy(course = NativeCourseState(plan, now(), phase = CoursePhase.Failed,
                message = "현재 경로를 확인 중이에요. 잠시 후 다시 계산해 주세요.")) }
            return
        }
        if (appContext.checkSelfPermission(NAVI_ROUTE_SEARCH_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            mutableState.update { it.copy(course = NativeCourseState(plan, now(), phase = CoursePhase.Failed, message = "경로 변경 권한을 허용해 주세요.")) }
            return
        }
        val started = now()
        Log.d("ThriveNavi", "practice_request option=${plan.option} roundTrip=${plan.roundTrip}")
        mutableState.update { it.copy(course = NativeCourseState(plan, started)) }
        courseTimeout?.cancel()
        courseTimeout = scope.launch {
            delay(30_000)
            if (state.value.course?.requestedAtMs == started && state.value.course?.phase == CoursePhase.Calculating)
                failCourse("경로 계산 응답이 없어요. 기본 내비에서 확인하고 다시 시도해 주세요.")
        }
        try {
            val target = plan.destination
            val route = RouteInfo(latitude = target.latitude, longitude = target.longitude, poiName = target.name,
                address = target.address, poiId = target.poiId, poiSubId = target.poiSubId,
                routeOption = RouteOption.valueOf(plan.option), poiProvider = target.poiProvider)
            if (plan.roundTrip) {
                val stop = plan.stop
                helper?.requestRouteWithWaypoints(RouteWithWaypointsRequest(route, listOf(RequestWaypointInfo(
                    latitude = stop.latitude, longitude = stop.longitude, poiName = stop.name, waypointIndex = WaypointIndex.FIRST,
                    address = stop.address, poiId = stop.poiId, poiSubId = stop.poiSubId, poiProvider = stop.poiProvider))))
            } else helper?.requestRoute(route)
        } catch (error: Exception) { failCourse("경로를 요청하지 못했어요 (${error.javaClass.simpleName}).") }
    }

    private fun routingCompleted() {
        Log.d("ThriveNavi", "routing_completed")
        observe(NavigationEventKind.RouteCalculated)
        mutableState.update { it.copy(course = it.course?.copy(routingCompleted = true)) }
        queryDestination()
    }

    private fun evaluateCourse() {
        val snapshot = state.value
        val course = snapshot.course ?: return
        if (course.phase == CoursePhase.Failed || !course.routingCompleted || snapshot.querying) return
        val destination = snapshot.freshDestination(now()) ?: return
        if (!destination.samePlace(course.plan.destination)) return
        if (snapshot.routeOption != course.plan.option) {
            failCourse("기본 내비가 선택한 경로 옵션을 적용하지 않았어요. 다른 옵션으로 다시 계산해 주세요.")
            return
        }
        if (course.plan.roundTrip) {
            val waypoints = snapshot.waypoints ?: return
            if (waypoints.none { it.samePlace(course.plan.stop) }) {
                if (waypoints.isNotEmpty()) failCourse(if (waypoints.any { it.latitude == 0.0 && it.longitude == 0.0 && (it.poiId.isBlank() || it.poiId == "0") })
                    "내비가 경유지 위치를 제공하지 않아 왕복 경로를 확인할 수 없어요. 목적지까지로 변경하거나 다른 장소로 다시 계산해 주세요."
                    else "내비의 경유지가 선택한 장소와 달라요. 경로를 다시 계산해 주세요.")
                return
            }
        }
        val estimate = snapshot.estimate ?: return
        courseTimeout?.cancel()
        val withinBudget = estimate.fits(course.plan.maxMinutes)
        mutableState.update { it.copy(course = course.copy(phase = if (withinBudget) CoursePhase.Ready else CoursePhase.Failed,
            estimate = estimate, message = if (withinBudget) "기본 내비에서 계산한 경로를 확인했어요."
                else "예상 ${estimate.minutes}분으로 선택한 최대 ${course.plan.maxMinutes}분을 넘어요. 시간이나 목적지를 조정해 주세요.")) }
    }

    private fun failCourse(message: String) {
        courseTimeout?.cancel()
        mutableState.update { it.copy(course = it.course?.copy(phase = CoursePhase.Failed, message = message)) }
    }

    fun openNavigation() {
        try {
            val intent = appContext.packageManager.getLaunchIntentForPackage("ai.umos.maps.android.navigation.app")
            if (intent == null) mutableState.update { it.copy(message = "기본 내비 앱을 열 수 없어요. 차량의 내비 메뉴에서 목적지를 설정해 주세요.") }
            else appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: Exception) {
            mutableState.update { it.copy(message = "기본 내비 앱을 열지 못했어요 (${error.javaClass.simpleName}).") }
        }
    }

    private fun observe(kind: NavigationEventKind, detail: String = "", turnType: String? = null) {
        onObservation(NavigationObservation(kind, now(), System.currentTimeMillis(), detail.take(200), turnType))
    }

    private fun now() = SystemClock.elapsedRealtime()
    private fun failed(message: String) { release(); mutableState.value = NavigationState(message = message) }
    fun release() {
        generation++
        timeout?.cancel(); courseTimeout?.cancel(); refreshAgain = false
        val client = helper; val events = listener
        helper = null; listener = null
        if (client != null) {
            try { if (events != null) client.removeListener(events) }
            catch (error: Exception) { Log.w("ThriveNavi", "Listener cleanup failed: ${error.javaClass.simpleName}") }
            try { client.release() }
            catch (error: Exception) { Log.w("ThriveNavi", "SDK cleanup failed: ${error.javaClass.simpleName}") }
        }
    }
}
