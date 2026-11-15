package com.thrive.pleos.ui.driving

import com.thrive.pleos.driving.curriculum.*
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.thrive.pleos.driving.navigation.*
import android.content.pm.PackageManager
import com.thrive.pleos.driving.location.FUSED_LOCATION_PERMISSION
import com.thrive.pleos.driving.location.hasRecordingLocationPermission
import android.Manifest
import android.os.Build
import com.thrive.pleos.MainActivity
import com.thrive.pleos.driving.invitation.CAR_DRIVE_PERMISSION
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import kotlinx.coroutines.delay
import android.os.SystemClock
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalInspectionMode
import com.thrive.pleos.driving.storage.SessionStatus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.thrive.pleos.ui.theme.*

@Composable
fun DrivingApp() {
    if (LocalInspectionMode.current) {
        DrivingScaffold(DemoScreen.Home, false, rememberScrollState(), {}, null) {
            DrivingHome(NavigationState(), onNavigate = {})
        }
        return
    }
    val model: DrivingViewModel = viewModel()
    val context = LocalContext.current
    // Deliberately transient: activity recreation requires the user to confirm a new action.
    var pendingReminderId by remember { mutableStateOf<String?>(null) }
    val reminderPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = pendingReminderId; pendingReminderId = null
        if (granted && id != null) model.setPracticeReminder(id, true)
        else if (!granted) model.reminderPermissionDenied()
    }
    fun requestReminder(id: String) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingReminderId = id; reminderPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else model.setPracticeReminder(id, true)
    }
    var pendingLocationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val action = pendingLocationAction
        pendingLocationAction = null
        if (context.hasRecordingLocationPermission()) {
            model.clearLocationPermissionNotice()
            action?.invoke()
        } else model.locationPermissionDenied()
    }
    fun withLocationPermission(action: () -> Unit) {
        if (context.hasRecordingLocationPermission()) {
            model.clearLocationPermissionNotice()
            action()
        } else if (pendingLocationAction == null) {
            pendingLocationAction = action
            locationPermission.launch(buildList {
                add(FUSED_LOCATION_PERMISSION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }.toTypedArray())
        }
    }
    var pendingRouteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val routePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingRouteAction; pendingRouteAction = null
        if (granted) action?.invoke() else model.navigationPermissionDenied()
    }
    fun withRoutePermission(action: () -> Unit) {
        if (context.checkSelfPermission(NAVI_ROUTE_SEARCH_PERMISSION) == PackageManager.PERMISSION_GRANTED) action()
        else if (pendingRouteAction == null) { pendingRouteAction = action; routePermission.launch(NAVI_ROUTE_SEARCH_PERMISSION) }
    }
    var notificationAllowed by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationAllowed = granted
        if (granted) model.refreshRecordingNotification()
    }
    val invitations by model.invitationState.collectAsState()
    val invitationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val allowed = context.checkSelfPermission(CAR_DRIVE_PERMISSION) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        if (allowed) model.setInvitations(true) else model.invitationPermissionDenied()
    }
    fun setInvitationEnabled(enabled: Boolean) {
        if (!enabled) model.setInvitations(false)
        else invitationPermission.launch(buildList {
            add(CAR_DRIVE_PERMISSION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray())
    }
    val local by model.local.collectAsState()
    val navigation by model.navigationState.collectAsState()
    val speech by model.voiceState.collectAsState()
    val coaching by model.coachingState.collectAsState()
    val timeline by model.timeline.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val parking by model.parkingState.collectAsState()
    val parked by produceState(false, parking) {
        value = parking.freshPark(SystemClock.elapsedRealtime())
        if (value) {
            delay((parking.receivedAtMs!! + 5_001 - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            value = false
        }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { model.observeMotionParking() }
    }
    val systemMotion = rememberSystemMotionEnabled()
    val keyboard = LocalInputModeManager.current.inputMode != InputMode.Touch
    var backwards by remember { mutableStateOf(false) }
    var animatePage by remember { mutableStateOf(false) }
    val flowScreens = listOf(DemoScreen.Practice, DemoScreen.Destination, DemoScreen.Routes, DemoScreen.Brief, DemoScreen.NativePractice)
    var backStack by rememberSaveable { mutableStateOf(listOf(DemoScreen.Home.name)) }
    var curriculumTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var curriculumMinutes by rememberSaveable { mutableIntStateOf(30) }
    var changeCurriculumStage by rememberSaveable { mutableStateOf(false) }
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
    var preferReturn by rememberSaveable { mutableStateOf(false) }

    val screen = DemoScreen.valueOf(backStack.last())
    LaunchedEffect(screen, local.active?.id, reportId) {
        model.observeTimeline(when (screen) {
            DemoScreen.Driving -> local.active?.id
            DemoScreen.Report -> reportId
            else -> null
        })
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.stopGuidance()
            if (event == Lifecycle.Event.ON_START) {
                model.refreshNavigation()
                notificationAllowed = Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (notificationAllowed) model.refreshRecordingNotification()
            }
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
        backwards = false; animatePage = target in flowScreens
        if (target != screen) backStack = backStack + target.name
        invitation = false
    }
    fun returnHome() { animatePage = false; backStack = listOf(DemoScreen.Home.name); invitation = false; reviewOriginId = null }
    fun navigateBack() {
        if (invitation) invitation = false
        else if (backStack.size > 1) {
            backwards = true
            backStack = backStack.dropLast(1)
            animatePage = DemoScreen.valueOf(backStack.last()) in flowScreens
        }
    }
    BackHandler(backStack.size > 1 || invitation) { navigateBack() }
    val activity = context as MainActivity
    val requestedTopic by activity.curriculumTopic.collectAsState()
    LaunchedEffect(requestedTopic, local.ready) {
        val topic = practiceTopic(requestedTopic)
        if (local.ready && topic != null) {
            model.setCurriculumTrack(topicTrack(topic)) {
                curriculumTopicId = topic.id; changeCurriculumStage = false
                backStack = listOf(DemoScreen.Home.name, DemoScreen.Practice.name)
                activity.clearCurriculumTopic()
            }
        }
    }
    val invitationToken by activity.invitationToken.collectAsState()
    LaunchedEffect(invitationToken) {
        invitationToken?.let { token ->
            model.openInvitation(token) { mode = DriveMode.Destination; navigate(DemoScreen.Navigation) }
            activity.clearInvitationToken(token)
        }
    }

    val notices: @Composable () -> Unit = {
        if (local.locationPermissionNotice != null || local.error != null ||
            (local.busy && screen !in listOf(DemoScreen.Profile, DemoScreen.Report, DemoScreen.Brief)) ||
            (local.active != null && screen != DemoScreen.Driving) ||
            (local.active?.isSdk == true && local.active?.status == SessionStatus.Recording.name && !notificationAllowed)) {
                    Column(Modifier.widthIn(max = ThriveSize.ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
                        if (local.locationPermissionNotice != null) ContentPanel("⚠ 위치 사용 권한") {
                            Text(local.locationPermissionNotice.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                            SecondaryAction("앱 권한 설정", {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            })
                        }
                        if (local.error != null) ContentPanel("⚠ 저장 상태 확인이 필요해요") {
                            Text(local.error.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                            SecondaryAction("다시 읽기", model::reload)
                        }
                        if (local.busy && screen !in listOf(DemoScreen.Profile, DemoScreen.Report, DemoScreen.Brief)) Text("저장 중…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                        val active = local.active
                        if (active?.isSdk == true && active.status == SessionStatus.Recording.name && !notificationAllowed && Build.VERSION.SDK_INT >= 33) {
                            ContentPanel("기록 알림을 켤 수 있어요") {
                                Text("알림을 허용하면 다른 앱에서도 기록 상태를 확인하고 종료할 수 있어요. 허용하지 않아도 기록은 계속돼요.", style = MaterialTheme.typography.bodyMedium)
                                SecondaryAction("기록 알림 허용", { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) })
                            }
                        }
                        if (active != null && screen != DemoScreen.Driving) ContentPanel(
                            if (active.status == SessionStatus.Interrupted.name) "중단된 기록이 있어요" else "기록 중인 주행이 있어요",
                        ) {
                            Text(if (active.status == SessionStatus.Interrupted.name) "저장된 기록 ${active.elapsedMs / 1000}초 · 앱이 꺼져 있던 시간은 포함하지 않아요." else if (active.isSdk) "기록 시간 ${local.elapsedMs / 1000}초 · 다른 앱을 사용해도 기록을 이어가요." else "시연 기록 ${local.elapsedMs / 1000}초 · 실제 주행 데이터와 구분해요.", style = MaterialTheme.typography.bodyMedium)
                            PrimaryAction("이어서 기록하기", {
                                if (active.isSdk) withLocationPermission { model.resume { navigate(DemoScreen.Driving) } }
                                else model.resume { navigate(DemoScreen.Driving) }
                            })
                            SecondaryAction("기록 종료하기", { model.finish { reportId = it; navigate(DemoScreen.Report) } })
                        }
                    }
        }
    }
    CompositionLocalProvider(LocalPageNotices provides notices,
        LocalPracticeStep provides (when {
            screen == DemoScreen.Practice -> null
            backStack.contains(DemoScreen.Practice.name) && screen == DemoScreen.Destination -> 0
            (mode == DriveMode.Practice || backStack.contains(DemoScreen.Practice.name)) && screen == DemoScreen.Routes -> 1
            (mode == DriveMode.Practice || backStack.contains(DemoScreen.Practice.name)) && screen == DemoScreen.Brief -> 2
            else -> null
        }),
        LocalSaveFeedback provides local.saved, LocalSaving provides local.savingKey) {
    DrivingScaffold(
        active = DemoScreen.valueOf(activeTab), driving = screen == DemoScreen.Driving,
        scrollState = scrollState,
        motion = motionMode(parked, screen == DemoScreen.Driving || local.active?.status == SessionStatus.Recording.name,
            keyboard, systemMotion, local.preferences.reducedMotion),
        onNavigate = { tab ->
            reviewOriginId = null; animatePage = false; changeCurriculumStage = false
            if (tab == DemoScreen.Home) returnHome()
            else { backStack = listOf(DemoScreen.Home.name, tab.name); invitation = false }
        },
        onBack = if (screen == DemoScreen.Home) null else ({ navigateBack() }),
    ) {
                    if (!local.ready) {
                        PageTitle("홈")
                        ContentPanel("") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
                                CircularProgressIndicator()
                                Text("설정과 기록을 읽고 있어요…", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    } else key(screen) {
                      PageMotion(screen, backwards, enabled = animatePage) {
                        Column(Modifier.widthIn(max = ThriveSize.ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl)) {
                            when (screen) {
                                DemoScreen.Home -> { DrivingHome(navigation, preferences = local.preferences, records = local.records,
                                    onNavigate = { target ->
                                    if (target in listOf(DemoScreen.Navigation, DemoScreen.Brief, DemoScreen.DemoNavigation, DemoScreen.Destination)) mode = DriveMode.Destination
                                    if (target == DemoScreen.Brief || target == DemoScreen.DemoNavigation) {
                                        destination = demoDestinations.first()
                                        route = DemoRoute.Gentle
                                        reviewOriginId = null
                                    }
                                    if (target == DemoScreen.Practice) { changeCurriculumStage = false; curriculumTopicId = null }
                                    navigate(target)
                                })
                                }
                                DemoScreen.Practice -> CurriculumFlow(local, model, curriculumTopicId, curriculumMinutes, changeCurriculumStage,
                                    parked, scrollState, ::requestReminder, { navigate(DemoScreen.Driving) }, {
                                        mode = DriveMode.Practice; navigate(DemoScreen.NativePractice)
                                    })
                                DemoScreen.NativePractice -> {
                                    val origin = local.records.firstOrNull { it.id == reviewOriginId }
                                    val preferred = origin?.practiceStopName?.let { name ->
                                        val lat = origin.practiceStopLatitude; val lon = origin.practiceStopLongitude
                                        if (lat != null && lon != null) LiveDestination(name, lat, lon) else null
                                    }
                                    NativePracticeScreen(practiceRequest, navigation, nativePracticeCandidates(navigation, local.records),
                                        preferred, preferReturn, model::connectNavigation, model::queryDestination, model::openNavigation,
                                        { duration = it }, { minimizeLaneChanges = false },
                                        { plan -> withRoutePermission { model.requestNativePractice(plan) } },
                                        { plan -> withLocationPermission { model.startNativePractice(plan) { navigate(DemoScreen.Driving) } } })
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
                                    PageTitle("내비 연결")
                                    local.records.firstOrNull { it.id == reviewOriginId && it.isSdk }?.let { origin ->
                                        ContentPanel("지난 기록의 목적지") {
                                            Text(origin.destination, style = MaterialTheme.typography.titleSmall)
                                            Text("이전 기록을 참고해 기본 내비의 현재 목적지를 다시 확인해 주세요.", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    NavigationStatusPanel(navigation, model::connectNavigation, model::queryDestination) { selected ->
                                        withLocationPermission { model.startLive(selected) { navigate(DemoScreen.Driving) } }
                                    }
                                    NativeRoutePreview(navigation)
                                    SecondaryAction("기본 내비 열기", model::openNavigation)
                                    InvitationSettingsPanel(invitations, ::setInvitationEnabled, model::reconnectInvitations)
                                }
                                DemoScreen.DemoNavigation -> {
                                    NavigationDemo(trip, invitation, { invitation = true }, {
                                        mode = DriveMode.Destination; navigate(DemoScreen.Brief)
                                    }, { invitation = false }, connection = {
                                        NavigationStatusPanel(navigation, model::connectNavigation, model::queryDestination) { selected ->
                                            withLocationPermission { model.startLive(selected) { navigate(DemoScreen.Driving) } }
                                        }
                                    })
                                }
                                DemoScreen.Driving -> {
                                    val active = local.active
                                    if (active == null) {
                                        PageTitle("기록 종료")
                                        ContentPanel("진행 중인 기록이 없어요") { PrimaryAction("홈으로", { returnHome() }) }
                                    } else if (active.status == SessionStatus.Interrupted.name) ContentPanel("기록이 중단됐어요") {
                                        Text("저장된 기록 ${active.elapsedMs / 1000}초 · 중단된 시간은 포함하지 않아요.", style = MaterialTheme.typography.bodyLarge)
                                        PrimaryAction("이어서 기록하기", {
                                            if (active.isSdk) withLocationPermission { model.resume {} }
                                            else model.resume {}
                                        })
                                        SecondaryAction("기록 종료하기", { model.finish { reportId = it; navigate(DemoScreen.Report) } })
                                    } else if (practiceTopic(active.curriculumItemId) != null) CurriculumDriving(active, local.elapsedMs,
                                        { model.finish { reportId = it; navigate(DemoScreen.Report) } }, model, parked)
                                    else if (active.isSdk) LiveDriveScreen(active, local.elapsedMs,
                                        onReconnect = { withLocationPermission { model.reconnectLocation() } },
                                        onFinish = { model.finish { reportId = it; navigate(DemoScreen.Report) } },
                                        coaching = coaching, onCoaching = model::setVoice,
                                    ) else DrivingScreen(active.toDemoTrip(), onArrive = {
                                        model.finish { reportId = it; navigate(DemoScreen.Report) }
                                    }, elapsedMs = local.elapsedMs) {
                                        VoiceGuidancePanel(active.toDemoTrip().goal, voice, speech, model::setVoice,
                                            model::playDemoGuidance, model::stopGuidance, compact = true)
                                    }
                                }
                                DemoScreen.Report -> {
                                    val record = local.records.firstOrNull { it.id == reportId }
                                    if (record == null) {
                                        PageTitle("주행 리포트")
                                        ContentPanel("기록을 찾을 수 없어요") { SecondaryAction("기록 목록으로", { navigate(DemoScreen.History) }) }
                                    } else if (practiceTopic(record.curriculumItemId) != null) {
                                        fun prepareCurriculum(topic: PracticeTopic, minutes: Int) {
                                            model.setCurriculumTrack(topicTrack(topic)) {
                                                curriculumTopicId = topic.id; curriculumMinutes = minutes; changeCurriculumStage = false
                                                backStack = listOf(DemoScreen.Home.name, DemoScreen.Practice.name)
                                            }
                                        }
                                        CurriculumReport(record, local.records, local.busy, { model.setFeeling(record.id, it) }, ::prepareCurriculum, ::prepareCurriculum)
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
                                                is NextPracticePlan.NativePractice -> {
                                                    goal = plan.goal; duration = plan.maxMinutes; minimizeLaneChanges = false
                                                    preferReturn = plan.roundTrip; mode = DriveMode.Practice
                                                    navigate(DemoScreen.NativePractice)
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
                                    }, timeline = timeline, onRetryEvidence = model::reload)
                                }
                                DemoScreen.History -> HistoryScreen(local.records, { record ->
                                    reportId = record.id; navigate(DemoScreen.Report)
                                }, model::deleteRecord, model::deleteAllFinished)
                                DemoScreen.Profile -> ProfileScreen(local.preferences.experience, local.preferences.concerns.toList(),
                                    model::setExperience, model::toggleConcern, { model.finishProfileEdit { returnHome() } },
                                    reducedMotion = local.preferences.reducedMotion, onReducedMotion = model::setReducedMotion,
                                    curriculumTrack = local.preferences.curriculumTrack,
                                    onCurriculumStage = { changeCurriculumStage = true; navigate(DemoScreen.Practice) })
                            }
                        }
                      }
                    }
    }
}
}

@Preview(name = "운전 적응 · 가로", widthDp = 1700, heightDp = 1184)
@Preview(name = "운전 적응 · 분할", widthDp = 840, heightDp = 1184)
@Composable
private fun DrivingPreview() { DrivingApp() }
