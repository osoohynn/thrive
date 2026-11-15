package com.thrive.pleos.driving.navigation

import com.thrive.pleos.ui.driving.PracticeGoal
import kotlin.math.abs

internal const val NAVI_ROUTE_SEARCH_PERMISSION = "pleos.car.permission.NAVI_ROUTE_SEARCH"

internal fun LiveDestination.samePlace(other: LiveDestination): Boolean {
    if (!valid || !other.valid) return false
    // The navigation app may return an entrance coordinate after calculating a POI route.
    if (poiId.isNotBlank() && poiId != "0" && poiId == other.poiId && poiProvider == other.poiProvider) return true
    return abs(latitude - other.latitude) <= 0.0003 && abs(longitude - other.longitude) <= 0.0003
}

internal data class NativePracticePlan(
    val goal: PracticeGoal,
    val maxMinutes: Int,
    val stop: LiveDestination,
    val returnPoint: LiveDestination? = null,
    val option: String = "COMFORTABLE",
) {
    val destination: LiveDestination get() = returnPoint ?: stop
    val roundTrip: Boolean get() = returnPoint != null
    val valid: Boolean get() = maxMinutes in 1..120 && stop.valid &&
        (returnPoint == null || (returnPoint.valid && !returnPoint.samePlace(stop))) &&
        option in setOf("COMFORTABLE", "RECOMMENDED")
}

internal data class NativeRouteEstimate(val seconds: Int, val meters: Int) {
    val valid: Boolean get() = seconds > 0 && meters > 0
    val minutes: Int get() = ((seconds.toLong() + 59) / 60).toInt()
    fun fits(maxMinutes: Int): Boolean = valid && seconds.toLong() <= maxMinutes.toLong() * 60
}

internal enum class CoursePhase { Calculating, Ready, Failed }
internal data class NativeCourseState(
    val plan: NativePracticePlan,
    val requestedAtMs: Long,
    val phase: CoursePhase = CoursePhase.Calculating,
    val message: String = "기본 내비에서 경로를 계산하고 있어요.",
    val estimate: NativeRouteEstimate? = null,
    val routingCompleted: Boolean = false,
)

internal data class NativeTurn(val type: String, val meters: Double?, val description: String)

internal data class NavigationState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val querying: Boolean = false,
    val message: String = "아직 연결하지 않았어요.",
    val destination: LiveDestination? = null,
    val routeMode: String? = null,
    val confirmedAtElapsedMs: Long? = null,
    val routeConfirmedAtMs: Long? = null,
    val routeOption: String? = null,
    val estimate: NativeRouteEstimate? = null,
    val currentPoint: LiveDestination? = null,
    val currentPointAtMs: Long? = null,
    val recentDestinations: List<LiveDestination> = emptyList(),
    val waypoints: List<LiveDestination>? = null,
    val turns: List<NativeTurn> = emptyList(),
    val turnsAtMs: Long? = null,
    val course: NativeCourseState? = null,
    val arrivalEvents: Int = 0,
    val destinationChanges: Int = 0,
) {
    val destinationName: String? get() = destination?.name
    fun freshDestination(nowElapsedMs: Long): LiveDestination? {
        val confirmed = confirmedAtElapsedMs ?: return null
        val routeConfirmed = routeConfirmedAtMs ?: return null
        if (!connected || querying || nowElapsedMs - confirmed !in 0..30_000 || nowElapsedMs - routeConfirmed !in 0..30_000) return null
        if (routeMode !in listOf("ROUTE_GUIDING", "SIMULATION_GUIDING")) return null
        return destination?.takeIf { it.valid }
    }
    fun freshOrigin(nowMs: Long): LiveDestination? {
        val at = currentPointAtMs ?: return null
        return currentPoint?.takeIf { connected && nowMs - at in 0..30_000 && it.valid }
    }
    fun confirmedCourse(plan: NativePracticePlan, nowMs: Long): NativeCourseState? {
        val item = course ?: return null
        val target = freshDestination(nowMs) ?: return null
        if (item.plan != plan || item.phase != CoursePhase.Ready || !item.routingCompleted || !plan.valid) return null
        if (estimate != item.estimate) return null
        if (!target.samePlace(plan.destination) || routeOption != plan.option) return null
        if (plan.roundTrip && waypoints?.any { it.samePlace(plan.stop) } != true) return null
        return item.takeIf { it.estimate?.fits(plan.maxMinutes) == true }
    }
}
