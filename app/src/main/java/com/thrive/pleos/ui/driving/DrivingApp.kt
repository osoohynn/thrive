package com.thrive.pleos.ui.driving

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.thrive.pleos.driving.location.FUSED_LOCATION_PERMISSION
import com.thrive.pleos.driving.location.hasFusedLocationPermission
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalInspectionMode
import com.thrive.pleos.driving.storage.SessionStatus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.thrive.pleos.ui.theme.*

@Composable
fun DrivingApp() {
    if (LocalInspectionMode.current) {
        ThriveTheme { Surface { Column(Modifier.padding(ThriveSpace.Xxl)) {
            DrivingHome(DemoTrip(DriveMode.Destination, PracticeGoal.Turns, DemoRoute.Gentle, "성수 오피스", 30)) {}
        } } }
        return
    }
    val model: DrivingViewModel = viewModel()
    val context = LocalContext.current
    // Deliberately transient: activity recreation requires the user to confirm a new action.
    var pendingLocationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingLocationAction
        pendingLocationAction = null
        if (granted) {
            model.clearLocationPermissionNotice()
            action?.invoke()
        } else model.locationPermissionDenied()
    }
    fun withLocationPermission(action: () -> Unit) {
        if (context.hasFusedLocationPermission()) {
            model.clearLocationPermissionNotice()
            action()
        } else if (pendingLocationAction == null) {
            pendingLocationAction = action
            locationPermission.launch(FUSED_LOCATION_PERMISSION)
        }
    }
    val local by model.local.collectAsState()
    val navigation by model.navigationState.collectAsState()
    val speech by model.voiceState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var backStack by rememberSaveable { mutableStateOf(listOf(DemoScreen.Home.name)) }
    var goal by rememberSaveable { mutableStateOf(PracticeGoal.Turns) }
    var mode by rememberSaveable { mutableStateOf(DriveMode.Destination) }
    var route by rememberSaveable { mutableStateOf(DemoRoute.Gentle) }
    var duration by rememberSaveable { mutableIntStateOf(30) }
    var minimizeLaneChanges by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf("성수 오피스") }
    var query by rememberSaveable { mutableStateOf("") }
    val voice = local.preferences.voice
    var invitation by rememberSaveable { mutableStateOf(false) }
    var reportId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewOriginId by rememberSaveable { mutableStateOf<String?>(null) }

    val screen = DemoScreen.valueOf(backStack.last())
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.stopGuidance()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); model.stopGuidance() }
    }
    DisposableEffect(screen, goal) { onDispose { model.stopGuidance() } }
    val tabs = listOf(DemoScreen.Home, DemoScreen.Practice, DemoScreen.History, DemoScreen.Profile)
    val activeTab = backStack.asReversed().first { name -> tabs.any { it.name == name } }
    val practiceRequest = PracticeRequest(goal, duration, minimizeLaneChanges)
    val recommendations = remember(practiceRequest) { recommendPractice(practiceRequest) }
    val selectedPractice = recommendations.firstOrNull { it.route == route } ?: recommendations.firstOrNull()
    val trip = if (mode == DriveMode.Practice && selectedPractice != null) {
        checkNotNull(practiceTrip(practiceRequest, selectedPractice.route))
    } else DemoTrip(mode, goal, route, destination, duration)
    val scrollState = rememberScrollState()
    LaunchedEffect(screen) { scrollState.scrollTo(0) }

    fun navigate(target: DemoScreen) {
        if (target != screen) backStack = backStack + target.name
        invitation = false
    }
    fun returnHome() { backStack = listOf(DemoScreen.Home.name); invitation = false; reviewOriginId = null }
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
                            Text("운전 적응 · 로컬 저장", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.Accent)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Sm), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm)) {
                            tabs.forEach { tab ->
                                SelectionChip(tab.title, activeTab == tab.name) {
                                    reviewOriginId = null
                                    if (tab == DemoScreen.Home) returnHome() else navigate(tab)
                                }
                            }
                            if (backStack.size > 1) TextButton(onClick = { navigateBack() }, modifier = Modifier.heightIn(min = ThriveSize.TouchTarget)) {
                                Text("← 이전", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Column(Modifier.widthIn(max = ThriveSize.ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
                        if (local.locationPermissionNotice != null) ContentPanel("위치 사용 권한") {
                            Text(local.locationPermissionNotice.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                            SecondaryAction("앱 권한 설정", {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            })
                        }
                        if (local.error != null) ContentPanel("저장 상태 확인이 필요해요") {
                            Text(local.error.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                            SecondaryAction("다시 읽기", model::reload)
                        }
                        if (local.busy) Text("저장 중…", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.Accent)
                        val active = local.active
                        if (active != null && screen != DemoScreen.Driving) ContentPanel(
                            if (active.status == SessionStatus.Interrupted.name) "중단된 기록이 있어요" else "기록 중인 주행이 있어요",
                        ) {
                            Text("저장된 기록 ${active.elapsedMs / 1000}초 · 앱이 꺼져 있던 시간은 포함하지 않아요.", style = MaterialTheme.typography.bodyMedium)
                            PrimaryAction("이어서 기록하기", {
                                if (active.isSdk) withLocationPermission { model.resume { navigate(DemoScreen.Driving) } }
                                else model.resume { navigate(DemoScreen.Driving) }
                            })
                            SecondaryAction("기록 종료하기", { model.finish { reportId = it; navigate(DemoScreen.Report) } })
                        }
                    }
                    if (!local.ready) {
                        Text("설정과 기록을 읽고 있어요…", style = MaterialTheme.typography.bodyLarge)
                    } else key(screen) {
                        Column(Modifier.widthIn(max = ThriveSize.ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl)) {
                            when (screen) {
                                DemoScreen.Home -> DrivingHome(trip) { target ->
                                    if (target == DemoScreen.Brief || target == DemoScreen.Navigation) mode = DriveMode.Destination
                                    navigate(target)
                                }
                                DemoScreen.Practice -> {
                                    local.records.firstOrNull { it.id == reviewOriginId }?.let { origin ->
                                        ContentPanel("이전 기록에서 이어지는 연습") {
                                            Text("${origin.feeling} · ${goal.title} · 최대 ${duration}분", style = MaterialTheme.typography.bodyLarge)
                                            Text("아래 조건을 바꾸면 원하는 연습으로 조정할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    PracticeScreen(
                                        goal, duration,
                                        { goal = it; route = DemoRoute.Gentle; reviewOriginId = null },
                                        { duration = it; reviewOriginId = null },
                                        minimizeLaneChanges, { minimizeLaneChanges = it; reviewOriginId = null },
                                    ) {
                                        if (goal == PracticeGoal.Daily) navigate(DemoScreen.Destination)
                                        else { mode = DriveMode.Practice; navigate(DemoScreen.Routes) }
                                    }
                                }
                                DemoScreen.Destination -> DestinationScreen(query, { query = it }) {
                                    destination = it; mode = DriveMode.Destination; query = ""; navigate(DemoScreen.Routes)
                                }
                                DemoScreen.Routes -> RoutesScreen(trip, recommendations, { route = it }, { navigate(DemoScreen.Practice) }) {
                                    if (mode != DriveMode.Practice || selectedPractice != null) navigate(DemoScreen.Brief)
                                }
                                DemoScreen.Brief -> {
                                    if (mode == DriveMode.Practice && selectedPractice == null) ContentPanel("연습 조건을 다시 확인해 주세요") {
                                        SecondaryAction("연습 조건 바꾸기", { navigate(DemoScreen.Practice) })
                                    } else BriefScreen(trip, onStart = {
                                        model.startDemo(trip, if (mode == DriveMode.Practice) practiceRequest else null) { navigate(DemoScreen.Driving) }
                                    }) {
                                        if (local.active?.isSdk == true) {
                                            Text("실제 주행 기록 중에는 음성 미리듣기를 제공하지 않아요.", style = MaterialTheme.typography.bodyMedium)
                                        } else VoiceGuidancePanel(trip.goal, voice, speech, model::setVoice,
                                            { model.previewGuidance(trip.goal) }, model::stopGuidance)
                                    }
                                }
                                DemoScreen.Navigation -> {
                                    local.records.firstOrNull { it.id == reviewOriginId && it.isSdk }?.let { origin ->
                                        ContentPanel("지난 기록의 목적지") {
                                            Text(origin.destination, style = MaterialTheme.typography.titleSmall)
                                            Text("이전 기록을 참고해 기본 내비의 현재 목적지를 다시 확인해 주세요.", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    NavigationStatusPanel(navigation, model::connectNavigation, model::queryDestination) { selected ->
                                        withLocationPermission { model.startLive(selected) { navigate(DemoScreen.Driving) } }
                                    }
                                    NavigationDemo(trip, invitation, { invitation = true }, {
                                        mode = DriveMode.Destination; navigate(DemoScreen.Brief)
                                    }, { invitation = false })
                                }
                                DemoScreen.Driving -> {
                                    val active = local.active
                                    if (active == null) ContentPanel("진행 중인 기록이 없어요") {
                                        PrimaryAction("홈으로", { returnHome() })
                                    } else if (active.isSdk) LiveDriveScreen(active, local.elapsedMs, { withLocationPermission { model.reconnectLocation() } }) {
                                        model.finish { reportId = it; navigate(DemoScreen.Report) }
                                    } else DrivingScreen(active.toDemoTrip(), onArrive = {
                                        model.finish { reportId = it; navigate(DemoScreen.Report) }
                                    }, elapsedMs = local.elapsedMs) {
                                        VoiceGuidancePanel(active.toDemoTrip().goal, voice, speech, model::setVoice,
                                            model::playDemoGuidance, model::stopGuidance)
                                    }
                                }
                                DemoScreen.Report -> {
                                    val record = local.records.firstOrNull { it.id == reportId }
                                    if (record == null) ContentPanel("기록을 찾을 수 없어요") {
                                        SecondaryAction("기록 목록으로", { navigate(DemoScreen.History) })
                                    } else ReportScreen(record, local.busy, { model.setFeeling(record.id, it) }, onSuggested = {
                                        model.prepareNextPractice(record.id) { plan ->
                                            reviewOriginId = record.id
                                            when (plan) {
                                                is NextPracticePlan.Practice -> {
                                                    goal = plan.request.goal; duration = plan.request.maxMinutes
                                                    minimizeLaneChanges = plan.request.minimizeLaneChanges
                                                    route = plan.preferredRoute ?: DemoRoute.Gentle
                                                    mode = DriveMode.Practice
                                                    navigate(DemoScreen.Practice)
                                                }
                                                is NextPracticePlan.Destination -> {
                                                    goal = PracticeGoal.Daily; query = ""; mode = DriveMode.Destination
                                                    if (plan.native) navigate(DemoScreen.Navigation)
                                                    else { destination = plan.name; mode = DriveMode.Destination; navigate(DemoScreen.Destination) }
                                                }
                                            }
                                        }
                                    }, onManual = {
                                        reviewOriginId = null; goal = PracticeGoal.Turns; duration = 30
                                        minimizeLaneChanges = false; route = DemoRoute.Gentle
                                        mode = DriveMode.Practice; navigate(DemoScreen.Practice)
                                    })
                                }
                                DemoScreen.History -> HistoryScreen(local.records, { record ->
                                    reportId = record.id; navigate(DemoScreen.Report)
                                }, model::deleteRecord, model::deleteAllFinished)
                                DemoScreen.Profile -> ProfileScreen(local.preferences.experience, local.preferences.concerns.toList(),
                                    model::setExperience, model::toggleConcern, { model.finishProfileEdit { returnHome() } })
                            }
                        }
                    }
                    ThriveGlassPanel(Modifier.widthIn(max = ThriveSize.ContentMaxWidth).fillMaxWidth()) {
                        Text("시연 코스와 SDK 수신 기록을 구분해 저장해요. 실제 목적지 기록은 지도 연동 화면에서 시작할 수 있어요.",
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
