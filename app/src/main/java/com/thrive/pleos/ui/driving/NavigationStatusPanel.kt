package com.thrive.pleos.ui.driving

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
        if (!state.connecting && !state.connected) PrimaryAction("목적지 불러오기", onConnect, enabled = !state.connecting)
        if (state.connected && !state.querying) SecondaryAction("현재 목적지 다시 조회", onQuery)
        if (state.connected) SecondaryAction("다시 연결", onConnect)
        Text(if (state.routeMode in listOf("ROUTE_GUIDING", "SIMULATION_GUIDING")) "기본 내비에서 안내 중이에요." else "안내 중인 경로를 확인해 주세요.", style = MaterialTheme.typography.bodyMedium)
        state.freshDestination(SystemClock.elapsedRealtime())?.let { destination ->
            PrimaryAction("이 목적지로 기록 시작", { confirmation = destination })
        }
    }
    confirmation?.let { destination ->
        ThriveDialog(onDismiss = { confirmation = null },
            title = "${destination.name}까지 기록할까요?",
            text = "목적지·이동 위치·내비 안내 사건을 이 기기에 저장해요. 위치를 받지 못한 구간은 미측정으로 남아요. 다른 앱을 사용해도 기록을 이어가며 시스템 강제 종료 시에는 중단돼요. 저장한 기록은 나중에 삭제할 수 있어요.",
            confirmLabel = "동의하고 기록 시작", onConfirm = { confirmation = null; onStart(destination) })
    }
}
