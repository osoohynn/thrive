package com.thrive.pleos.ui.driving

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.thrive.pleos.driving.voice.VoiceState
import com.thrive.pleos.ui.theme.*

internal val PracticeGoal.voiceCue: String get() = when (this) {
    PracticeGoal.Turns -> "회전하기 전에는 신호와 주변을 확인하고, 이동할 방향을 미리 살펴보세요."
    PracticeGoal.Lanes -> "차선을 바꾸기 전에는 주변 차량과 거울을 확인하고, 충분한 여유를 두고 준비하세요."
    PracticeGoal.Daily -> "익숙하지 않은 길에서는 기본 내비게이션의 안내를 먼저 확인하고, 여유 있게 이동하세요."
}

@Composable
internal fun VoiceGuidancePanel(
    goal: PracticeGoal,
    enabled: Boolean,
    state: VoiceState,
    onEnabled: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {
    ContentPanel("음성 학습 안내") {
        Text(goal.voiceCue, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
            Switch(checked = enabled, onCheckedChange = onEnabled,
                modifier = Modifier.sizeIn(minWidth = ThriveSize.TouchTarget, minHeight = ThriveSize.TouchTarget)
                    .semantics { contentDescription = "음성 학습 안내" })
            Text(if (enabled) "안내 켜짐" else "안내 꺼짐", style = MaterialTheme.typography.labelLarge)
        }
        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
        if (state.busy) SecondaryAction("안내 중단", onStop)
        else if (enabled) SecondaryAction("목표 안내 듣기", onPlay)
        Text("버튼을 누를 때만 재생해요. 주변 상황을 감지한 길 안내는 아니에요. 다른 화면으로 이동하면 중단해요.",
            style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    }
}
