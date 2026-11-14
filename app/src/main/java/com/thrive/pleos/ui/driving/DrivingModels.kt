package com.thrive.pleos.ui.driving

internal enum class DemoScreen(val title: String) {
    Home("홈"), Navigation("지도 연동 체험"), Practice("연습 목표"), Destination("목적지"),
    Routes("코스 선택"), Brief("출발 준비"), Driving("주행 시연"), Report("주행 리포트"),
    History("주행 기록"), Profile("내 운전"),
}

internal enum class DriveMode { Practice, Destination }

internal enum class PracticeGoal(val title: String, val description: String, val mark: String) {
    Turns("회전·교차로", "신호를 살피고, 차근차근 회전해요.", "01"),
    Lanes("차선 변경 준비", "경로를 미리 보고 여유 있게 준비해요.", "02"),
    Daily("생활 경로 적응", "자주 가야 할 목적지까지 익혀봐요.", "03"),
}

internal enum class DemoRoute(val title: String, val minutes: Int, val kilometers: String, val laneChanges: Int) {
    Gentle("차근차근 가는 길", 24, "6.8", 1),
    Direct("조금 더 빠른 길", 19, "5.9", 3),
}

internal data class DemoTrip(
    val mode: DriveMode,
    val goal: PracticeGoal,
    val route: DemoRoute,
    val destination: String,
    val durationMinutes: Int,
) : java.io.Serializable {
    val title: String get() = if (mode == DriveMode.Practice) goal.title + " 연습" else destination
    val minutes: Int get() = if (mode == DriveMode.Practice) durationMinutes else route.minutes
    // Demonstration estimate only; never used as live route or vehicle data.
    val distance: String get() = if (mode == DriveMode.Practice) "약 ${durationMinutes / 5}.0" else route.kilometers
    val endLabel: String get() = if (mode == DriveMode.Practice) "출발지로 돌아오기" else destination
}

internal val demoDestinations = listOf("성수 오피스", "서울숲 공영주차장", "뚝섬역")

internal data class DemoRecord(val date: String, val trip: DemoTrip)

internal val exampleRecords = listOf(
    DemoRecord("11월 12일 · 예시", DemoTrip(DriveMode.Practice, PracticeGoal.Turns, DemoRoute.Gentle, "현재 위치", 30)),
    DemoRecord("11월 9일 · 예시", DemoTrip(DriveMode.Destination, PracticeGoal.Daily, DemoRoute.Direct, "서울숲 공영주차장", 15)),
)
