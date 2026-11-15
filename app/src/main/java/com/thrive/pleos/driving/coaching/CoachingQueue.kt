package com.thrive.pleos.driving.coaching

import com.thrive.pleos.driving.navigation.NavigationEventKind
import com.thrive.pleos.driving.navigation.NavigationObservation
import com.thrive.pleos.ui.driving.PracticeGoal
import com.thrive.pleos.ui.driving.voiceCue

internal data class CoachingCandidate(val key: String, val text: String, val reason: String, val createdAtMs: Long) {
    fun fresh(nowMs: Long): Boolean = nowMs - createdAtMs in 0..5_000
}
internal data class CoachingState(val candidate: CoachingCandidate? = null, val enabled: Boolean = true,
    val message: String = "새 내비 안내가 오면 선택 목표에 맞는 연습 포인트를 준비해요.")

/** Candidates only. Live TTS remains gated until navigation/call audio priority is verified. */
internal class CoachingQueue {
    private var enabled = true
    private var pending: CoachingCandidate? = null
    private val recent = linkedMapOf<String, Long>()
    fun setEnabled(value: Boolean) { enabled = value; if (!value) pending = null }
    fun clear() { pending = null }
    fun reset() { pending = null; recent.clear() }
    fun offer(event: NavigationObservation, goal: PracticeGoal, nowMs: Long) {
        if (!enabled || nowMs - event.receivedElapsedMs !in 0..5_000) return
        if (event.kind in setOf(NavigationEventKind.RouteCancelled, NavigationEventKind.DestinationChanged,
                NavigationEventKind.NavigationError, NavigationEventKind.RouteSearchStarted)) { clear(); return }
        val content = when (event.kind) {
            NavigationEventKind.Rerouted -> "경로가 다시 계산됐어요. 새 경로 안내는 기본 내비에서 확인해요."
            NavigationEventKind.DestinationArrived -> "내비에서 도착 신호를 받았어요. 주차한 뒤 기록 종료를 확인해요."
            NavigationEventKind.TbtReceived -> {
                val turn = event.turnType ?: return
                val turns = setOf("TURN_LEFT", "TURN_RIGHT", "TURN_SHARP_L", "TURN_SHARP_R", "TURN_UTURN", "TURN_EXIT_ROUNDABOUT")
                val lanes = setOf("TURN_KEEP_L", "TURN_KEEP_R", "TURN_ENTER_IC_L", "TURN_ENTER_IC_R", "TURN_EXIT_IC_L", "TURN_EXIT_IC_R", "TURN_DEVIDE_MERGE")
                val relevant = when (goal) {
                    PracticeGoal.Turns -> turn in turns || turn.startsWith("TURN_ENTER_ROUNDABOUT_")
                    PracticeGoal.Lanes -> turn in lanes
                    PracticeGoal.Daily -> turn != "TURN_UNKNOWN"
                }
                if (!relevant) return
                goal.voiceCue
            }
            else -> return
        }
        val key = "${event.kind}:${event.turnType}:${goal.name}"
        if (recent[key]?.let { nowMs - it in 0..29_999 } == true) return
        recent[key] = nowMs
        while (recent.size > 32) recent.remove(recent.keys.first())
        pending = CoachingCandidate(key, content, "${event.kind.title} · 선택 목표 ${goal.title}", event.receivedElapsedMs)
    }
    fun state(nowMs: Long): CoachingState {
        if (pending?.fresh(nowMs) == false) pending = null
        return CoachingState(pending, enabled, if (enabled) "새 내비 안내가 오면 선택 목표에 맞는 연습 포인트를 준비해요." else "학습 안내가 꺼져 있어요. 대기 중인 안내도 비웠어요.")
    }
}
