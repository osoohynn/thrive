package com.thrive.pleos.ui.driving

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import android.os.SystemClock
import com.thrive.pleos.driving.navigation.LiveDestination
import com.thrive.pleos.driving.navigation.NavigationState
import com.thrive.pleos.ui.theme.ThriveColors

@Composable
internal fun NavigationStatusPanel(state: NavigationState, onConnect: () -> Unit, onQuery: () -> Unit, onStart: (LiveDestination) -> Unit) {
    var confirmation by remember { mutableStateOf<LiveDestination?>(null) }
    ContentPanel("실제 내비게이션 연결 확인") {
        Text(state.message, style = MaterialTheme.typography.bodyLarge)
        state.destinationName?.let { Text("현재 목적지: $it", style = MaterialTheme.typography.titleMedium, color = ThriveColors.Accent) }
        Text("목적지 변경 이벤트 ${state.destinationChanges}회 수신 · 이 조회 결과는 아래 시연 경로와 별개예요.",
            style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
        if (!state.connecting && !state.connected) PrimaryAction("NaviHelper 연결하기", onConnect)
        if (state.connected && !state.querying) SecondaryAction("현재 목적지 다시 조회", onQuery)
        if (state.connected) SecondaryAction("서비스 다시 연결", onConnect)
        Text("경로 상태: ${state.routeMode ?: "확인 전"}", style = MaterialTheme.typography.bodyMedium)
        state.freshDestination(SystemClock.elapsedRealtime())?.let { destination ->
            PrimaryAction("이 목적지로 기록 시작", { confirmation = destination })
        }
    }
    confirmation?.let { destination ->
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text("${destination.name}까지 기록할까요?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("목적지와 이동 위치를 이 기기에 저장해요. 위치를 받지 못한 구간은 미측정으로 남아요. 앱을 종료하면 기록이 중단될 수 있어요. 저장한 기록은 나중에 삭제할 수 있어요.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { PrimaryAction("동의하고 기록 시작", { confirmation = null; onStart(destination) }) },
            dismissButton = { SecondaryAction("취소", { confirmation = null }) },
            containerColor = ThriveColors.Background, titleContentColor = ThriveColors.Text, textContentColor = ThriveColors.TextSecondary)
    }
}
