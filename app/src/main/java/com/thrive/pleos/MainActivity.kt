package com.thrive.pleos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.thrive.pleos.ui.theme.*
import com.thrive.pleos.ui.driving.DrivingApp

private data class RestOption(val title: Int, val duration: Int, val description: Int, val mark: String)
private val restOptions = listOf(
    RestOption(R.string.breathe, R.string.three_minutes, R.string.breathe_description, "01"),
    RestOption(R.string.stretch, R.string.five_minutes, R.string.stretch_description, "02"),
    RestOption(R.string.unwind, R.string.ten_minutes, R.string.unwind_description, "03"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { DrivingApp() }
    }
}

@Composable
private fun ThriveApp() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var resting by rememberSaveable { mutableStateOf(false) }
    val option = restOptions[selected]

    ThriveTheme {
        ThriveBackdrop {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val wide = maxWidth >= ThriveSize.WideBreakpoint
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(if (wide) ThriveSpace.Xxxl else ThriveSpace.Xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl),
                ) {
                    HomeHeader()
                    Column(
                        Modifier.widthIn(max = ThriveSize.ContentMaxWidth).fillMaxWidth()
                            .padding(vertical = if (wide) ThriveSpace.Lg else ThriveSpace.Sm),
                        verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm),
                    ) {
                        Text(
                            stringResource(R.string.home_title),
                            style = if (wide) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                            color = ThriveColors.Text,
                        )
                        Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge, color = ThriveColors.TextSecondary)
                    }
                    Column(
                        Modifier.widthIn(max = ThriveSize.ContentMaxWidth).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl),
                    ) {
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Xl)) {
                                RestCard(option, resting, { resting = !resting }, Modifier.weight(1.7f))
                                VehicleCard(Modifier.weight(1f))
                            }
                        } else {
                            RestCard(option, resting, { resting = !resting })
                            VehicleCard()
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg),
                                verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xs),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.activity_heading), style = MaterialTheme.typography.titleMedium, color = ThriveColors.Text)
                                Text(stringResource(R.string.activity_hint), style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                            }
                            if (wide) {
                                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
                                    restOptions.forEachIndexed { index, item ->
                                        ActivityCard(item, selected == index, {
                                            selected = index
                                            resting = false
                                        }, Modifier.weight(1f))
                                    }
                                }
                            } else {
                                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
                                    restOptions.forEachIndexed { index, item ->
                                        ActivityCard(item, selected == index, {
                                            selected = index
                                            resting = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    FlowRow(
        Modifier.widthIn(max = ThriveSize.ContentMaxWidth).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Xl),
        verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text("thrive", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = ThriveColors.Text)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
            Text(stringResource(R.string.demo_data), color = ThriveColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Surface(
                color = ThriveColors.Glass,
                shape = ThriveShape.Pill,
                border = BorderStroke(ThriveSize.Border, ThriveColors.Border),
            ) {
                Text(
                    stringResource(R.string.parked),
                    Modifier.padding(horizontal = ThriveSpace.Lg, vertical = ThriveSpace.Md),
                    color = ThriveColors.Accent,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun RestCard(option: RestOption, resting: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    ThriveGlassPanel(modifier.fillMaxWidth().heightIn(min = ThriveSize.CardMinHeight)) {
        Column(Modifier.padding(ThriveSpace.Xxl), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
            Text(stringResource(if (resting) R.string.resting_label else R.string.recommended_label), color = ThriveColors.Accent, style = MaterialTheme.typography.labelMedium)
            Text(stringResource(option.title), color = ThriveColors.Text, style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(if (resting) R.string.resting_description else option.description),
                color = ThriveColors.TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                minLines = 2,
            )
            Spacer(Modifier.height(ThriveSpace.Xs))
            var focused by remember { mutableStateOf(false) }
            val restState = stringResource(if (resting) R.string.resting_label else R.string.recommended_label)
            Button(
                onClick = onToggle,
                modifier = Modifier.heightIn(min = ThriveSize.TouchTarget)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { stateDescription = restState },
                shape = ThriveShape.Pill,
                border = if (focused) BorderStroke(ThriveSize.FocusBorder, ThriveColors.Accent) else null,
                colors = ButtonDefaults.buttonColors(containerColor = ThriveColors.Text, contentColor = ThriveColors.Background),
                contentPadding = PaddingValues(horizontal = ThriveSpace.Xxl, vertical = ThriveSpace.Md),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Sm),
                    verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(if (resting) R.string.finish_rest else R.string.start_rest), style = MaterialTheme.typography.labelLarge)
                    Text("· ${stringResource(option.duration)}", color = ThriveColors.Brand, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun VehicleCard(modifier: Modifier = Modifier) {
    ThriveGlassPanel(modifier.fillMaxWidth().heightIn(min = ThriveSize.CardMinHeight)) {
        Column(Modifier.padding(ThriveSpace.Xxl), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
            Text(stringResource(R.string.vehicle_heading), style = MaterialTheme.typography.titleSmall, color = ThriveColors.TextSecondary)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm),
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Sm)) {
                    Text("78", style = MaterialTheme.typography.displayLarge)
                    Text("%", Modifier.padding(bottom = ThriveSpace.Sm), style = MaterialTheme.typography.headlineSmall, color = ThriveColors.TextSecondary)
                }
                Text(stringResource(R.string.battery_label), Modifier.padding(bottom = ThriveSpace.Sm), style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
            }
            // Static mock values; no vehicle connection or charging calculation.
            Box(Modifier.fillMaxWidth().height(ThriveSize.ProgressHeight).progressSemantics(0.78f)
                .background(ThriveColors.Border, ThriveShape.Pill)) {
                Box(Modifier.fillMaxWidth(0.78f).fillMaxHeight().background(ThriveColors.Accent, ThriveShape.Pill))
            }
            VehicleMetric(stringResource(R.string.range_label), "324 km")
            VehicleMetric(stringResource(R.string.cabin_label), "22°")
        }
    }
}

@Composable
private fun VehicleMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ActivityCard(option: RestOption, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onSelect,
        selected = selected,
        modifier = modifier.fillMaxWidth().heightIn(min = ThriveSize.ActivityMinHeight)
            .onFocusChanged { focused = it.isFocused },
        shape = ThriveShape.Panel,
        color = if (selected) ThriveColors.GlassSelected else ThriveColors.Glass,
        contentColor = ThriveColors.Text,
        border = BorderStroke(
            if (focused) ThriveSize.FocusBorder else ThriveSize.Border,
            if (focused) ThriveColors.Focus else if (selected) ThriveColors.BorderStrong else ThriveColors.Border,
        ),
    ) {
        Row(Modifier.padding(ThriveSpace.Xl), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
            Text(option.mark, color = if (selected) ThriveColors.Accent else ThriveColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xs)) {
                Text(stringResource(option.title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(option.duration), color = ThriveColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Text(if (selected) "●" else "↗", color = if (selected) ThriveColors.Accent else ThriveColors.TextSecondary, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(name = "차량 가로 화면", widthDp = 1100, heightDp = 700)
@Preview(name = "멀티 윈도우", widthDp = 480, heightDp = 800)
@Composable
private fun ThrivePreview() {
    DrivingApp()
}
