package com.thrive.pleos.ui.driving

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import com.thrive.pleos.R
import com.thrive.pleos.ui.theme.*

@Composable
internal fun PageTitle(title: String, description: String = "", status: String? = null, badge: String? = null) {
    val onBack = LocalPageBack.current
    var backFocused by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= ThriveSize.WideBreakpoint * LocalDensity.current.fontScale
        Row(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Surface(
                onClick = onBack, shape = ThriveShape.Control, color = ThriveColors.Glass,
                border = if (backFocused) BorderStroke(ThriveSize.FocusBorder, ThriveColors.Focus) else null,
                modifier = Modifier.size(ThriveSize.TouchTarget).onFocusChanged { backFocused = it.isFocused }
                    .semantics { contentDescription = "이전 화면" },
            ) { Box(contentAlignment = Alignment.Center) { Image(painterResource(R.drawable.icon_back), null, Modifier.size(32.dp)) } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xs)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    if (badge != null) StatusBadge(badge)
                }
                if (description.isNotEmpty()) Text(description, style = MaterialTheme.typography.bodyLarge, color = ThriveColors.TextSecondary)
                if (!wide && status != null) Text(status, style = MaterialTheme.typography.labelSmall, color = ThriveColors.Brand)
            }
            if (wide && status != null) Surface(color = ThriveColors.Glass, shape = ThriveShape.Pill,
                border = BorderStroke(ThriveSize.FocusBorder, ThriveColors.Brand)) {
                Text(status, Modifier.padding(horizontal = ThriveSpace.Xxl, vertical = ThriveSpace.Md),
                    style = MaterialTheme.typography.labelMedium, color = ThriveColors.Brand)
            }
        }
    }
    LocalPracticeStep.current?.let { PracticeSteps(it) }
    LocalPageNotices.current()
}

@Composable
internal fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interactions = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        interactionSource = interactions,
        enabled = enabled,
        modifier = modifier.pressFeedback(interactions).heightIn(min = ThriveSize.TouchTarget).onFocusChanged { focused = it.isFocused },
        shape = ThriveShape.Control,
        border = if (focused) BorderStroke(ThriveSize.FocusBorder, MaterialTheme.colorScheme.secondary) else null,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
        contentPadding = PaddingValues(horizontal = ThriveSpace.Panel, vertical = ThriveSpace.Md),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
internal fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        interactionSource = interactions,
        modifier = modifier.pressFeedback(interactions).heightIn(min = ThriveSize.TouchTarget).onFocusChanged { focused = it.isFocused },
        shape = ThriveShape.Control,
        border = BorderStroke(if (focused) ThriveSize.FocusBorder else ThriveSize.Border,
            if (focused) ThriveColors.Focus else ThriveColors.Border),
        colors = ButtonDefaults.buttonColors(containerColor = ThriveColors.GlassSubtle, contentColor = ThriveColors.Text),
        contentPadding = PaddingValues(horizontal = ThriveSpace.Panel, vertical = ThriveSpace.Md),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
internal fun ChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    mark: String = "",
) {
    val interactions = remember { MutableInteractionSource() }
    val mode = LocalMotionMode.current
    val selection = animateFloatAsState(if (selected) 1f else 0f,
        tween(mode.duration(ThriveMotion.Selection), easing = ThriveMotion.EaseOut), label = "card selection")
    val selectedAlpha = { if (mode == MotionMode.Off) { if (selected) 1f else 0f } else selection.value }
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onSelect, selected = selected, interactionSource = interactions,
        modifier = modifier.pressFeedback(interactions).fillMaxWidth().heightIn(min = ThriveSize.ActivityMinHeight).onFocusChanged { focused = it.isFocused },
        shape = ThriveShape.Panel,
        color = ThriveColors.ReadingSurface,
        contentColor = ThriveColors.Text,
        border = BorderStroke(
            if (selected) ThriveSize.SelectedBorder else if (focused) ThriveSize.FocusBorder else ThriveSize.Border,
            if (focused) ThriveColors.Focus else if (selected) ThriveColors.BorderStrong else ThriveColors.Border,
        ),
    ) {
        Box {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = selectedAlpha() }
                .background(ThriveColors.ReadingSurfaceSelected, ThriveShape.Panel))
            Row(Modifier.padding(ThriveSpace.Panel), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm)) {
                    if (mark.isNotEmpty()) Text(mark, style = MaterialTheme.typography.labelSmall, color = ThriveColors.Brand)
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (description.isNotEmpty()) Text(description, style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                }
                Surface(Modifier.size(40.dp), shape = ThriveShape.Pill,
                    color = Color.Transparent,
                    border = BorderStroke(ThriveSize.FocusBorder, if (selected) ThriveColors.Brand else ThriveColors.ControlBorder)) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.matchParentSize().graphicsLayer { alpha = selectedAlpha() }.background(ThriveColors.Brand, ThriveShape.Pill))
                        Text("✓", Modifier.clearAndSetSemantics {}.graphicsLayer {
                            alpha = selectedAlpha()
                            scaleX = if (mode == MotionMode.Full) .9f + .1f * selectedAlpha() else 1f
                            scaleY = scaleX
                        }, style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ContentPanel(title: String, modifier: Modifier = Modifier, badge: String? = null, shadowElevation: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    ThriveGlassPanel(modifier.fillMaxWidth(), shadowElevation = shadowElevation) {
        Column(Modifier.padding(ThriveSpace.Panel), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
            if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleMedium)
            if (badge != null) StatusBadge(badge)
            content()
        }
    }
}

@Composable
internal fun ResponsivePair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit, firstWeight: Float = 1f, secondWeight: Float = 1f, equalHeight: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= ThriveSize.WideBreakpoint * LocalDensity.current.fontScale) {
            Row(if (equalHeight) Modifier.height(IntrinsicSize.Min) else Modifier, horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl)) {
                first(Modifier.weight(firstWeight).then(if (equalHeight) Modifier.fillMaxHeight() else Modifier))
                second(Modifier.weight(secondWeight).then(if (equalHeight) Modifier.fillMaxHeight() else Modifier))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun SelectionChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    FilterChip(
        selected = selected,
        onClick = onSelect,
        interactionSource = interactions,
        modifier = Modifier.pressFeedback(interactions).heightIn(min = ThriveSize.TouchTarget).onFocusChanged { focused = it.isFocused },
        shape = ThriveShape.Control,
        label = { Text(label, Modifier.padding(horizontal = ThriveSpace.Lg, vertical = ThriveSpace.Sm), style = MaterialTheme.typography.labelLarge) },
        border = BorderStroke(
            if (focused || selected) ThriveSize.FocusBorder else ThriveSize.Border,
            if (focused) MaterialTheme.colorScheme.primary else if (selected) MaterialTheme.colorScheme.secondary else ThriveColors.Border,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ThriveColors.Glass,
            labelColor = ThriveColors.Text,
            selectedContainerColor = ThriveColors.GlassSelected,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = MaterialTheme.colorScheme.secondary,
        ),
    )
}

internal val LocalPageBack = staticCompositionLocalOf<(() -> Unit)?> { null }
internal val LocalPageNotices = staticCompositionLocalOf<@Composable () -> Unit> { {} }

@Composable
internal fun DrivingScaffold(
    active: DemoScreen,
    driving: Boolean,
    scrollState: ScrollState,
    onNavigate: (DemoScreen) -> Unit,
    onBack: (() -> Unit)?,
    motion: MotionMode = MotionMode.Off,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dialogs = remember { ThriveDialogState() }
    CompositionLocalProvider(LocalMotionMode provides motion, LocalThriveDialogs provides dialogs) {
        ThriveTheme {
            Surface(color = ThriveColors.Background) {
                ThriveBackdrop {
                    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                        val rail = maxWidth >= ThriveSize.WideBreakpoint * LocalDensity.current.fontScale
                        val drivingInset = if (maxWidth >= 1200.dp) 110.dp else ThriveSpace.Xxl
                        Row(Modifier.widthIn(max = 1700.dp).fillMaxSize()
                            .padding(start = if (driving) drivingInset else if (rail) ThriveSize.PageStart else ThriveSpace.Xxl,
                                end = if (driving) drivingInset else if (rail) ThriveSize.PageEnd else ThriveSpace.Xxl),
                            horizontalArrangement = Arrangement.spacedBy(if (rail && !driving) ThriveSize.RailContentGap else 0.dp)) {
                            if (rail && !driving) Box(Modifier.padding(top = ThriveSize.PageTop)) { NavRail(active, onNavigate) }
                            Column(Modifier.weight(1f).verticalScroll(scrollState)
                                .padding(top = ThriveSize.PageTop, bottom = ThriveSpace.Section),
                                verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl)) {
                                if (!rail && !driving) NavRail(active, onNavigate, horizontal = true)
                                CompositionLocalProvider(LocalPageBack provides onBack) { content() }
                            }
                        }
                    }
                }
            }
            ThriveDialogHost(dialogs)
        }
    }
}

@Composable
internal fun ThriveDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String = "취소",
) {
    val host = LocalThriveDialogs.current
    val owner = remember { Any() }
    val data = DialogContent(title, text, dismissLabel, confirmLabel, onDismiss, onConfirm)
    DisposableEffect(host, owner) {
        host.show(owner, data)
        onDispose { host.close(owner) }
    }
    SideEffect { host.update(owner, data) }
}

/** Choice cards share one responsive grid in goals, profiles, and history. */
@Composable
internal fun ChoiceGrid(items: List<@Composable (Modifier) -> Unit>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= ThriveSize.WideBreakpoint * LocalDensity.current.fontScale) 3 else 1
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
            items.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
                    row.forEach { item -> item(Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun ThriveToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    val mode = LocalMotionMode.current
    val offset = animateFloatAsState(if (checked) 40f else 0f,
        tween(mode.duration(ThriveMotion.Toggle), easing = ThriveMotion.EaseOut), label = "toggle thumb")
    Box(Modifier.size(96.dp, ThriveSize.TouchTarget)
        .semantics { contentDescription = label }
        .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange), contentAlignment = Alignment.Center) {
        Box(Modifier.size(96.dp, 56.dp).background(if (checked) ThriveColors.Brand else ThriveColors.ControlBorder, ThriveShape.Pill)
            .padding(8.dp)) {
            if (mode == MotionMode.Reduced) {
                listOf(0f, 40f).forEach { position ->
                    Surface(Modifier.size(40.dp).graphicsLayer {
                        translationX = position.dp.toPx()
                        alpha = if (position == 0f) 1f - offset.value / 40f else offset.value / 40f
                    }, color = Color.White, shape = ThriveShape.Pill, shadowElevation = 2.dp) {}
                }
            } else Surface(Modifier.size(40.dp).graphicsLayer {
                translationX = (if (mode == MotionMode.Full) offset.value else if (checked) 40f else 0f).dp.toPx()
            }, color = Color.White, shape = ThriveShape.Pill, shadowElevation = 2.dp) {}
        }
    }
}

@Composable
internal fun NavRail(active: DemoScreen, onNavigate: (DemoScreen) -> Unit, horizontal: Boolean = false) {
    val tabs = listOf(DemoScreen.Home to R.drawable.nav_home, DemoScreen.Practice to R.drawable.nav_practice,
        DemoScreen.History to R.drawable.nav_history, DemoScreen.Profile to R.drawable.nav_profile)
    val selectedIndex = tabs.indexOfFirst { it.first == active }.coerceAtLeast(0)
    val mode = LocalMotionMode.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val cellHeight = (ThriveSize.RailWidth - ThriveSpace.Sm * 2) * fontScale
    Box((if (horizontal) Modifier.fillMaxWidth() else Modifier.width(ThriveSize.RailWidth * fontScale))
        .clip(ThriveShape.Navigation).background(ThriveColors.NavigationSurface)) {
        BoxWithConstraints(Modifier.padding(ThriveSpace.Sm).selectableGroup()) {
            val gap = ThriveSpace.Sm
            val cellWidth = if (horizontal) (maxWidth - gap * (tabs.size - 1)) / tabs.size else maxWidth
            // Reset at an axis change; switching layouts should not move the highlight diagonally.
            key(horizontal) {
                val position = animateFloatAsState(selectedIndex.toFloat(),
                    tween(mode.duration(ThriveMotion.Navigation), easing = ThriveMotion.EaseInOut), label = "navigation selection")
                val indicator: @Composable (Float, () -> Float) -> Unit = { index, opacity ->
                    Box(Modifier.size(cellWidth, cellHeight).graphicsLayer {
                        translationX = if (horizontal) (cellWidth + gap).toPx() * index else 0f
                        translationY = if (horizontal) 0f else (cellHeight + gap).toPx() * index
                        alpha = opacity()
                    }.background(ThriveColors.NavigationSelection, ThriveShape.Panel))
                }
                if (mode == MotionMode.Reduced) {
                    tabs.indices.forEach { index ->
                        val alpha = animateFloatAsState(if (index == selectedIndex) 1f else 0f,
                            tween(mode.duration(ThriveMotion.Navigation)), label = "navigation fade")
                        indicator(index.toFloat()) { alpha.value }
                    }
                } else {
                    // Read motion in the draw layer; all hit targets stay fixed.
                    Box(Modifier.size(cellWidth, cellHeight).graphicsLayer {
                        val index = if (mode == MotionMode.Full) position.value else selectedIndex.toFloat()
                        translationX = if (horizontal) (cellWidth + gap).toPx() * index else 0f
                        translationY = if (horizontal) 0f else (cellHeight + gap).toPx() * index
                    }.background(ThriveColors.NavigationSelection, ThriveShape.Panel))
                }
                val items: @Composable () -> Unit = {
                    tabs.forEach { (screen, icon) ->
                        val selected = screen == active
                        val interactions = remember(screen) { MutableInteractionSource() }
                        var focused by remember(screen) { mutableStateOf(false) }
                        Box(Modifier.size(cellWidth, cellHeight).pressFeedback(interactions)
                            .onFocusChanged { focused = it.isFocused }
                            .then(if (focused) Modifier.border(ThriveSize.FocusBorder, ThriveColors.Focus, ThriveShape.Panel) else Modifier)
                            .selectable(selected = selected, interactionSource = interactions,
                                indication = null, role = Role.Tab, onClick = { onNavigate(screen) })
                            .semantics { contentDescription = screen.title }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Image(painterResource(icon), null, Modifier.size(if (horizontal) 36.dp else 44.dp),
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (selected) ThriveColors.Brand else Color.Black))
                                Text(screen.title, Modifier.padding(top = ThriveSpace.Sm).clearAndSetSemantics {},
                                    style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                                    color = Color.Black)
                            }
                        }
                    }
                }
                if (horizontal) Row(horizontalArrangement = Arrangement.spacedBy(gap)) { items() }
                else Column(verticalArrangement = Arrangement.spacedBy(gap)) { items() }
            }
        }
    }
}

@Composable
internal fun TripSummary(trip: DemoTrip) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm)) {
        Text("${trip.minutes}분", style = MaterialTheme.typography.headlineSmall)
        Text("${trip.distance} km", style = MaterialTheme.typography.headlineSmall, color = ThriveColors.TextSecondary)
    }
}

@Composable
internal fun StatusBadge(label: String) {
    val example = label.contains("시연") || label.contains("예시")
    Surface(shape = ThriveShape.Pill,
        color = if (example) ThriveColors.GlassSubtle else ThriveColors.GlassSelected,
        contentColor = if (example) ThriveColors.TextSecondary else ThriveColors.Brand) {
        Text(label, Modifier.padding(horizontal = ThriveSpace.Md, vertical = ThriveSpace.Xs), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun PracticeSteps(current: Int) {
    Row(Modifier.fillMaxWidth().semantics {
        contentDescription = "연습 ${current + 1}단계: ${listOf("목표 선택", "코스 확인", "출발 준비")[current]}"
    }, horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Sm)) {
        repeat(3) { index ->
            Box(Modifier.weight(1f).height(4.dp).background(
                if (index <= current) ThriveColors.Brand else ThriveColors.ControlBorder, ThriveShape.Pill))
        }
    }
}
