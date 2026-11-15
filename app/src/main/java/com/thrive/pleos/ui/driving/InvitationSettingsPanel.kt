package com.thrive.pleos.ui.driving

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.thrive.pleos.driving.invitation.InvitationState

@Composable
internal fun InvitationSettingsPanel(state: InvitationState, onEnabled: (Boolean) -> Unit, onReconnect: () -> Unit) {
    ContentPanel("내비에서 연습 이어가기") {
        Text("기본 내비에서 목적지를 정하면 주차(P) 상태에서 연습을 제안해요.", style = MaterialTheme.typography.bodyLarge)
        Text("쓰라이브를 실행한 뒤 연결이 유지되는 동안 동작해요. 같은 목적지는 24시간 동안 반복 제안하지 않아요.", style = MaterialTheme.typography.bodyMedium)
        Text("알림을 눌러 목적지를 확인하고 직접 동의해야 기록이 시작돼요.", style = MaterialTheme.typography.bodyMedium)
        Text(state.message, style = MaterialTheme.typography.bodyMedium)
        state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (state.enabled) {
            SecondaryAction("참여 제안 알림 끄기", { onEnabled(false) })
            SecondaryAction("알림 연결 다시 확인", onReconnect)
        } else PrimaryAction("참여 제안 알림 켜기", { onEnabled(true) })
    }
}
