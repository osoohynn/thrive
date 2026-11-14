package com.thrive.pleos.ui.driving

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.thrive.pleos.ui.theme.*

@Composable
fun DrivingApp() {
    var backStack by rememberSaveable { mutableStateOf(listOf(DemoScreen.Home.name)) }
    var goal by rememberSaveable { mutableStateOf(PracticeGoal.Turns) }
    var mode by rememberSaveable { mutableStateOf(DriveMode.Destination) }
    var route by rememberSaveable { mutableStateOf(DemoRoute.Gentle) }
    var duration by rememberSaveable { mutableIntStateOf(30) }
    var destination by rememberSaveable { mutableStateOf("성수 오피스") }
    var query by rememberSaveable { mutableStateOf("") }
    var voice by rememberSaveable { mutableStateOf(true) }
    var invitation by rememberSaveable { mutableStateOf(false) }
    var feeling by rememberSaveable { mutableStateOf("") }
    var experience by rememberSaveable { mutableStateOf("쉬었다 다시 시작해요") }
    var concerns by rememberSaveable { mutableStateOf(listOf("차선 변경", "낯선 경로")) }
    var completedTrip by rememberSaveable { mutableStateOf<DemoTrip?>(null) }
    var reportTrip by rememberSaveable { mutableStateOf<DemoTrip?>(null) }
    var reportDate by rememberSaveable { mutableStateOf("오늘의 시연") }
    val screen = DemoScreen.valueOf(backStack.last())
    val tabs = listOf(DemoScreen.Home, DemoScreen.Practice, DemoScreen.History, DemoScreen.Profile)
    val activeTab = backStack.asReversed().first { name -> tabs.any { it.name == name } }
    val trip = DemoTrip(mode, goal, route, destination, duration)
    val scrollState = rememberScrollState()
    LaunchedEffect(screen) { scrollState.scrollTo(0) }

    fun navigate(target: DemoScreen) {
        if (target != screen) backStack = backStack + target.name
        invitation = false
    }
    fun returnHome() { backStack = listOf(DemoScreen.Home.name); invitation = false }
    fun navigateBack() {
        if (invitation) invitation = false
        else if (backStack.size > 1) backStack = backStack.dropLast(1)
    }
    BackHandler(backStack.size > 1 || invitation) { navigateBack() }

    ThriveTheme {
        Surface(color = ThriveColors.Background, contentColor = ThriveColors.Text) {
          ThriveBackdrop {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val wide = maxWidth >= ThriveSize.WideBreakpoint
                Column(
                    Modifier.fillMaxSize().verticalScroll(scrollState).padding(if (wide) ThriveSpace.Xxxl else ThriveSpace.Xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl),
                ) {
                    Column(Modifier.widthIn(max = ThriveSize.ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xl)) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md), itemVerticalAlignment = Alignment.CenterVertically) {
                            Text("thrive", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("운전 적응 · 디자인 시연", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.Accent)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Sm), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm)) {
                            tabs.forEach { tab ->
                                SelectionChip(tab.title, activeTab == tab.name) {
                                    if (tab == DemoScreen.Home) returnHome() else navigate(tab)
                                }
                            }
                            if (backStack.size > 1) TextButton(onClick = { navigateBack() }, modifier = Modifier.heightIn(min = ThriveSize.TouchTarget)) {
                                Text("← 이전", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    key(screen) {
                        Column(Modifier.widthIn(max = ThriveSize.ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl)) {
                            when (screen) {
                                DemoScreen.Home -> DrivingHome(trip) { target ->
                                    if (target == DemoScreen.Brief || target == DemoScreen.Navigation) mode = DriveMode.Destination
                                    navigate(target)
                                }
                                DemoScreen.Practice -> PracticeScreen(goal, duration, { goal = it }, { duration = it }) {
                                    if (goal == PracticeGoal.Daily) navigate(DemoScreen.Destination)
                                    else { mode = DriveMode.Practice; navigate(DemoScreen.Routes) }
                                }
                                DemoScreen.Destination -> DestinationScreen(query, { query = it }) {
                                    destination = it; mode = DriveMode.Destination; query = ""; navigate(DemoScreen.Routes)
                                }
                                DemoScreen.Routes -> RoutesScreen(trip, { route = it }) { navigate(DemoScreen.Brief) }
                                DemoScreen.Brief -> BriefScreen(trip, voice, { voice = it }) { navigate(DemoScreen.Driving) }
                                DemoScreen.Navigation -> NavigationDemo(trip, invitation, { invitation = true }, {
                                    mode = DriveMode.Destination; navigate(DemoScreen.Brief)
                                }, { invitation = false })
                                DemoScreen.Driving -> DrivingScreen(trip, voice, { voice = !voice }) {
                                    completedTrip = trip; reportTrip = trip; reportDate = "오늘의 시연"; feeling = ""; navigate(DemoScreen.Report)
                                }
                                DemoScreen.Report -> ReportScreen(reportTrip ?: trip, reportDate, feeling, { feeling = it }) {
                                    val reviewed = reportTrip ?: trip
                                    goal = reviewed.goal; route = reviewed.route; duration = reviewed.minutes; destination = reviewed.destination
                                    mode = DriveMode.Practice; navigate(DemoScreen.Practice)
                                }
                                DemoScreen.History -> HistoryScreen(completedTrip) { record, date ->
                                    reportTrip = record; reportDate = date; feeling = ""; navigate(DemoScreen.Report)
                                }
                                DemoScreen.Profile -> ProfileScreen(experience, concerns, { experience = it }, { label ->
                                    concerns = if (label in concerns) concerns - label else concerns + label
                                }, { returnHome() })
                            }
                        }
                    }
                    ThriveGlassPanel(Modifier.widthIn(max = ThriveSize.ContentMaxWidth).fillMaxWidth()) {
                        Text("지도·경로・기록은 모의 데이터입니다. 차량·내비게이션·음성 SDK는 아직 연결하지 않았습니다.",
                            Modifier.padding(ThriveSpace.Lg), style = MaterialTheme.typography.bodySmall, color = ThriveColors.TextSecondary)
                    }
                }
            }
          }
        }
    }
}

@Preview(name = "운전 적응 · 가로", widthDp = 1100, heightDp = 850)
@Preview(name = "운전 적응 · 분할", widthDp = 480, heightDp = 850)
@Composable
private fun DrivingPreview() { DrivingApp() }
