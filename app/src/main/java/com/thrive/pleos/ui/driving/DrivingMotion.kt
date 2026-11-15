package com.thrive.pleos.ui.driving

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.thrive.pleos.ui.theme.*
import kotlinx.coroutines.delay

/** All custom motion is opt-in after a current PARK reading. */
internal enum class MotionMode { Off, Reduced, Full }
internal fun motionMode(parked: Boolean, driving: Boolean, keyboard: Boolean, systemEnabled: Boolean, reduced: Boolean): MotionMode = when {
    !systemEnabled || !parked || driving || keyboard -> MotionMode.Off
    reduced -> MotionMode.Reduced
    else -> MotionMode.Full
}
internal object ThriveMotion {
    val EaseOut = CubicBezierEasing(.23f, 1f, .32f, 1f)
    val EaseInOut = CubicBezierEasing(.77f, 0f, .175f, 1f)
    const val Press = 120
    const val Selection = 160
    const val Toggle = 180
    const val Navigation = 180
    const val Page = 180
    const val DialogEnter = 220
    const val DialogExit = 140
}
internal val LocalMotionMode = staticCompositionLocalOf { MotionMode.Off }
internal val LocalPracticeStep = staticCompositionLocalOf<Int?> { null }
internal val LocalSaveFeedback = staticCompositionLocalOf<SaveFeedback?> { null }
internal val LocalSaving = staticCompositionLocalOf<String?> { null }
internal fun MotionMode.duration(full: Int): Int = when (this) {
    MotionMode.Off -> 0
    MotionMode.Reduced -> 100
    MotionMode.Full -> full
}

@Composable
internal fun rememberSystemMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun enabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var result by remember(resolver) { mutableStateOf(enabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { result = enabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return result
}

@Composable
internal fun Modifier.pressFeedback(interactions: MutableInteractionSource): Modifier {
    val mode = LocalMotionMode.current
    val pressed by interactions.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) .985f else 1f,
        tween(mode.duration(ThriveMotion.Press), easing = ThriveMotion.EaseOut), label = "press")
    // Draw-only scaling leaves the native clickable region and focus bounds intact.
    return drawWithContent {
        scale(if (mode == MotionMode.Full) scale.value else 1f) { this@drawWithContent.drawContent() }
    }
}

@Composable
internal fun PageMotion(screen: DemoScreen, backwards: Boolean, enabled: Boolean = true, content: @Composable () -> Unit) {
    val mode = LocalMotionMode.current
    val progress = remember(screen) { Animatable(if (mode == MotionMode.Off || !enabled) 1f else 0f) }
    LaunchedEffect(screen, mode, enabled) {
        if (mode == MotionMode.Off || !enabled) progress.snapTo(1f)
        else progress.animateTo(1f, tween(mode.duration(ThriveMotion.Page), easing = ThriveMotion.EaseOut))
    }
    Box(Modifier.fillMaxWidth().graphicsLayer {
        alpha = if (mode == MotionMode.Off || !enabled) 1f else progress.value
        translationX = if (mode == MotionMode.Full && enabled) (if (backwards) -16.dp.toPx() else 16.dp.toPx()) * (1 - progress.value) else 0f
    }) { content() }
}

@Composable
internal fun SaveStatus(key: String, modifier: Modifier = Modifier) {
    val receipt = LocalSaveFeedback.current
    val saving = LocalSaving.current == key
    val mode = LocalMotionMode.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(receipt?.id, saving) {
        val remaining = if (receipt?.key == key) receipt.savedAtMs + 2_500 - SystemClock.elapsedRealtime() else 0
        visible = !saving && remaining > 0
        if (visible) { delay(remaining); visible = false }
    }
    val alpha = animateFloatAsState(if (visible) 1f else 0f,
        tween(mode.duration(ThriveMotion.Selection), easing = ThriveMotion.EaseOut), label = "save receipt")
    Box(modifier.sizeIn(minWidth = 32.dp, minHeight = 32.dp), contentAlignment = Alignment.Center) {
        if (saving) CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = "저장 중" }, strokeWidth = 2.dp)
        else if (receipt?.key == key && (visible || alpha.value > 0f)) Text("✓",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "저장 완료" }.graphicsLayer { this.alpha = if (mode == MotionMode.Off) { if (visible) 1f else 0f } else alpha.value },
            style = MaterialTheme.typography.labelSmall, color = ThriveColors.Brand)
    }
}

/** The host outlives page content, so closing never delays navigation or a save. */
internal data class DialogContent(val title: String, val text: String, val dismissLabel: String,
    val confirmLabel: String, val onDismiss: () -> Unit, val onConfirm: () -> Unit)
internal class ThriveDialogState {
    var content by mutableStateOf<DialogContent?>(null)
        private set
    var visible by mutableStateOf(false)
        private set
    private var owner: Any? = null
    fun show(token: Any, next: DialogContent) { owner = token; content = next; visible = true }
    fun update(token: Any, next: DialogContent) { if (owner === token) content = next }
    fun close(token: Any) { if (owner === token) { owner = null; visible = false } }
    fun dismiss() { if (visible) { visible = false; content?.onDismiss?.invoke() } }
    fun confirm() { if (visible) { visible = false; content?.onConfirm?.invoke() } }
}
internal val LocalThriveDialogs = staticCompositionLocalOf<ThriveDialogState> { error("ThriveDialog requires DrivingScaffold") }

@Composable
internal fun ThriveDialogHost(state: ThriveDialogState) {
    val mode = LocalMotionMode.current
    val content = state.content ?: return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(state.visible, mode) {
        val target = if (state.visible) 1f else 0f
        if (mode == MotionMode.Off) progress.snapTo(target)
        else progress.animateTo(target, tween(mode.duration(if (state.visible) ThriveMotion.DialogEnter else ThriveMotion.DialogExit), easing = ThriveMotion.EaseOut))
    }
    val mounted by remember { derivedStateOf { state.visible || progress.value > 0f } }
    if (mounted) {
        Dialog(onDismissRequest = state::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val view = LocalView.current
            SideEffect { (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f) }
            val shown = { if (mode == MotionMode.Off) { if (state.visible) 1f else 0f } else progress.value }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.matchParentSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = state::dismiss)) {
                    drawRect(ThriveColors.Scrim.copy(alpha = ThriveColors.Scrim.alpha * shown()))
                }
                Surface(Modifier.padding(ThriveSpace.Xxl).widthIn(max = 860.dp).fillMaxWidth()
                    .graphicsLayer {
                        alpha = shown()
                        scaleX = if (mode == MotionMode.Full) .97f + .03f * shown() else 1f
                        scaleY = scaleX
                    }.pointerInput(Unit) { detectTapGestures {} },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(ThriveSize.Border, ThriveColors.Border), shadowElevation = 24.dp) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(44.dp), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl)) {
                        Text(content.title, style = MaterialTheme.typography.headlineSmall)
                        Text(content.text, style = MaterialTheme.typography.bodyLarge, color = ThriveColors.TextSecondary)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
                            SecondaryAction(content.dismissLabel, state::dismiss)
                            PrimaryAction(content.confirmLabel, state::confirm, enabled = state.visible)
                        }
                    }
                }
            }
        }
    }
}
