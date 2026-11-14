package com.thrive.pleos.ui.driving

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.thrive.pleos.driving.storage.SavedDrive
import com.thrive.pleos.ui.theme.ThriveColors
import java.util.Locale

internal val SavedDrive.isSdk: Boolean get() = source == "SDK"
internal val SavedDrive.displayTitle: String get() = if (isSdk) destination else toDemoTrip().title
internal val SavedDrive.sourceLabel: String get() = if (!isSdk) "시연 데이터" else if (environment == "EMULATOR") "에뮬레이터 SDK 수신" else "SDK 수신 기록"
internal val SavedDrive.distanceLabel: String get() = distanceMeters?.let { String.format(Locale.getDefault(), "위치 기반 추정 %.2f km", it / 1000) } ?: "거리 미측정"

@Composable
internal fun LiveDriveScreen(record: SavedDrive, elapsedMs: Long, onReconnect: () -> Unit, onFinish: () -> Unit) {
    PageTitle(record.sourceLabel, record.destination, "기본 내비게이션의 경로 안내를 따라 이동해 주세요.")
    ContentPanel("주행을 기록하고 있어요") {
        Text("기록 시간 ${elapsedMs / 1000}초", style = MaterialTheme.typography.headlineSmall)
        Text(record.distanceLabel, style = MaterialTheme.typography.bodyLarge)
        Text(record.locationMessage, style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
        Text("위치 ${record.acceptedFixes}건 반영 · ${record.rejectedFixes}건 제외 · 공백 ${record.gapCount}회", style = MaterialTheme.typography.bodyMedium)
        SecondaryAction("위치 다시 연결", onReconnect)
    }
    Text("이 화면은 길 안내 지도나 운전 안전 판단을 제공하지 않아요. 앱을 종료하면 기록이 중단될 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    PrimaryAction("주행 기록 종료하기", onFinish)
}

@Composable
internal fun LiveReportSummary(record: SavedDrive) {
    ContentPanel("기록한 사실을 돌아봐요") {
        Text("기록 시간 ${record.elapsedMs / 1000}초", style = MaterialTheme.typography.headlineSmall)
        Text(record.distanceLabel, style = MaterialTheme.typography.bodyLarge)
        Text("반영한 위치 ${record.acceptedFixes}건 · 제외한 위치 ${record.rejectedFixes}건", style = MaterialTheme.typography.bodyMedium)
        Text("위치·기록 공백 ${record.gapCount}회", style = MaterialTheme.typography.bodyMedium)
        Text("마지막 위치 상태: ${record.locationMessage}", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
        Text("추정 거리는 수신한 구간만 계산해요. 차량 주행계 거리와 다를 수 있으며, 미수신·불연속 구간을 연결해 채우지 않아요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
        Text("회전·감속·차선 변경은 아직 분석하지 않았어요. 운전 점수나 잘못한 행동을 임의로 만들지 않아요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    }
}
