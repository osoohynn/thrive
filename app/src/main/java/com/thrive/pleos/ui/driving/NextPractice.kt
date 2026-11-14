package com.thrive.pleos.ui.driving

import com.thrive.pleos.driving.storage.SavedDrive
import com.thrive.pleos.driving.storage.SessionStatus

internal val practiceTimeOptions = listOf(15, 30, 45)
internal val drivingFeelings = listOf("여유 있었어요", "보통이에요", "부담됐어요")

internal sealed interface NextPracticePlan {
    val reason: String

    data class Practice(
        val request: PracticeRequest,
        val preferredRoute: DemoRoute?,
        override val reason: String,
    ) : NextPracticePlan

    data class Destination(
        val name: String,
        val native: Boolean,
        override val reason: String,
    ) : NextPracticePlan
}

/** Product policy based on the user's response, never a sensor-based skill assessment. */
internal fun nextPracticePlan(record: SavedDrive): NextPracticePlan? {
    if (record.status != SessionStatus.Completed.name || record.activeSlot != null) return null
    if (record.feeling !in drivingFeelings || record.source !in setOf("SDK", "DEMO")) return null
    val response = "‘${record.feeling}’라는 응답을 기준으로"
    if (record.source == "SDK" || record.mode == DriveMode.Destination.name) {
        if (record.destination.isBlank()) return null
        return NextPracticePlan.Destination(record.destination, record.source == "SDK",
            "$response 생활 경로를 다시 살펴봐요. 이전 목적지는 참고하고, 출발할 경로는 다시 확인해요.")
    }
    if (record.mode != DriveMode.Practice.name) return null
    val goal = PracticeGoal.entries.firstOrNull { it.name == record.goal } ?: return null
    if (goal == PracticeGoal.Daily) return null
    val previousRoute = DemoRoute.entries.firstOrNull { it.name == record.route } ?: return null
    if (record.plannedMinutes <= 0) return null
    val previousBudget = practiceTimeOptions.firstOrNull { it >= record.plannedMinutes } ?: practiceTimeOptions.last()
    val burdened = record.feeling == "부담됐어요"
    val request = PracticeRequest(goal, if (burdened) minOf(previousBudget, 30) else previousBudget,
        minimizeLaneChanges = burdened && goal == PracticeGoal.Turns)
    val candidates = recommendPractice(request)
    val preferred = if (burdened) candidates.firstOrNull()?.route
        else candidates.firstOrNull { it.route == previousRoute }?.route ?: candidates.firstOrNull()?.route
    val reason = if (burdened) "$response 한 가지 목표를 유지하고 조건을 다시 정해봐요."
        else "$response 같은 목표를 반복할 수 있게 준비했어요."
    return NextPracticePlan.Practice(request, preferred, reason)
}
