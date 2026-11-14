package com.thrive.pleos.ui.driving

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import com.thrive.pleos.ui.theme.*

@Composable
internal fun PageTitle(kicker: String, title: String, description: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= ThriveSize.WideBreakpoint * LocalDensity.current.fontScale
        Column(verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
            Text(kicker, style = MaterialTheme.typography.labelMedium, color = ThriveColors.Accent)
            Text(title, style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall)
            Text(description, style = MaterialTheme.typography.bodyLarge, color = ThriveColors.TextSecondary)
        }
    }
}

@Composable
internal fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ThriveSize.TouchTarget).onFocusChanged { focused = it.isFocused },
        shape = ThriveShape.Pill,
        border = if (focused) BorderStroke(ThriveSize.FocusBorder, ThriveColors.Accent) else null,
        colors = ButtonDefaults.buttonColors(containerColor = ThriveColors.Text, contentColor = ThriveColors.Background),
        contentPadding = PaddingValues(horizontal = ThriveSpace.Xxl, vertical = ThriveSpace.Md),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
internal fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ThriveSize.TouchTarget).onFocusChanged { focused = it.isFocused },
        shape = ThriveShape.Pill,
        border = BorderStroke(if (focused) ThriveSize.FocusBorder else ThriveSize.Border, if (focused) ThriveColors.Focus else ThriveColors.ControlBorder),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = ThriveColors.ReadingSurface, contentColor = ThriveColors.Text),
        contentPadding = PaddingValues(horizontal = ThriveSpace.Xxl, vertical = ThriveSpace.Md),
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
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onSelect, selected = selected,
        modifier = modifier.fillMaxWidth().heightIn(min = ThriveSize.ActivityMinHeight).onFocusChanged { focused = it.isFocused },
        shape = ThriveShape.Panel,
        color = if (selected) ThriveColors.ReadingSurfaceSelected else ThriveColors.ReadingSurface,
        contentColor = ThriveColors.Text,
        border = BorderStroke(
            if (focused) ThriveSize.FocusBorder else ThriveSize.Border,
            if (focused) ThriveColors.Focus else if (selected) ThriveColors.BorderStrong else ThriveColors.Border,
        ),
    ) {
        Row(Modifier.padding(ThriveSpace.Xxl), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg), verticalAlignment = Alignment.CenterVertically) {
            if (mark.isNotEmpty()) Text(mark, style = MaterialTheme.typography.labelMedium, color = ThriveColors.Accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
            }
            Text(if (selected) "✓" else "○", style = MaterialTheme.typography.titleSmall, color = if (selected) ThriveColors.Accent else ThriveColors.TextSecondary)
        }
    }
}

@Composable
internal fun ContentPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ThriveGlassPanel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(ThriveSpace.Xxl), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun ResponsivePair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= ThriveSize.WideBreakpoint * LocalDensity.current.fontScale) {
            Row(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl)) {
                first(Modifier.weight(1.3f))
                second(Modifier.weight(1f))
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
    var focused by remember { mutableStateOf(false) }
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = if (selected) ({ Text("✓", style = MaterialTheme.typography.labelMedium) }) else null,
        modifier = Modifier.heightIn(min = ThriveSize.TouchTarget).onFocusChanged { focused = it.isFocused },
        shape = ThriveShape.Pill,
        border = BorderStroke(
            if (focused || selected) ThriveSize.FocusBorder else ThriveSize.Border,
            if (focused) ThriveColors.Focus else if (selected) ThriveColors.Accent else ThriveColors.ControlBorder,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ThriveColors.ReadingSurface,
            labelColor = ThriveColors.TextSecondary,
            selectedContainerColor = ThriveColors.ReadingSurfaceSelected,
            selectedLabelColor = ThriveColors.Text,
            selectedLeadingIconColor = ThriveColors.Accent,
        ),
    )
}

@Composable
internal fun TripSummary(trip: DemoTrip) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm)) {
        Text("${trip.minutes}분", style = MaterialTheme.typography.headlineSmall)
        Text("${trip.distance} km", style = MaterialTheme.typography.headlineSmall, color = ThriveColors.TextSecondary)
    }
}
