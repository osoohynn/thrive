package com.thrive.pleos.ui.driving

internal data class PracticeRequest(
    val goal: PracticeGoal,
    val maxMinutes: Int,
    val minimizeLaneChanges: Boolean,
)

internal data class PracticeRecommendation(val route: DemoRoute, val reason: String)

/** Example catalog only; these attributes have not been verified against roads or traffic. */
internal fun recommendPractice(
    request: PracticeRequest,
    candidates: List<DemoRoute> = DemoRoute.entries,
): List<PracticeRecommendation> {
    if (request.maxMinutes <= 0 || request.goal == PracticeGoal.Daily) return emptyList()
    val eligible = candidates.distinct().filter { route ->
        route.minutes <= request.maxMinutes &&
            (!request.minimizeLaneChanges || route.laneChanges <= 1) &&
            (request.goal != PracticeGoal.Lanes || route.laneChanges >= 2)
    }
    val ordered = if (request.goal == PracticeGoal.Turns) {
        eligible.sortedWith(compareBy<DemoRoute> { it.laneChanges }.thenBy { it.minutes })
    } else eligible.sortedWith(compareBy<DemoRoute> { it.minutes }.thenBy { it.laneChanges })
    return ordered.map { route ->
        val goalReason = when (request.goal) {
            PracticeGoal.Turns -> "회전·교차로에 집중하도록 차선 변경이 적은 순서로 제안해요."
            PracticeGoal.Lanes -> "변경 구간이 2회 이상인 예시로 준비 과정을 살펴봐요."
            PracticeGoal.Daily -> error("Daily uses destination selection")
        }
        PracticeRecommendation(route, "최대 ${request.maxMinutes}분 안의 ${route.minutes}분 코스예요. $goalReason" +
            if (request.minimizeLaneChanges) " 차선 변경 1회 이하 조건을 적용했어요." else "")
    }
}

internal fun practiceTrip(request: PracticeRequest, route: DemoRoute): DemoTrip? =
    recommendPractice(request).firstOrNull { it.route == route }?.let {
        DemoTrip(DriveMode.Practice, request.goal, route, "출발지", route.minutes)
    }
