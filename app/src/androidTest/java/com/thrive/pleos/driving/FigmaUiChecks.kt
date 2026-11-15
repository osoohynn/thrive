package com.thrive.pleos.driving

import com.thrive.pleos.driving.curriculum.*
import com.thrive.pleos.driving.storage.*
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.MotionEvent
import android.view.InputDevice
import android.graphics.Rect
import androidx.lifecycle.ViewModelProvider
import com.thrive.pleos.ui.driving.DrivingViewModel
import kotlinx.coroutines.withTimeout
import android.view.accessibility.AccessibilityNodeInfo
import com.thrive.pleos.MainActivity
import java.io.File
import com.thrive.pleos.driving.storage.DriverPreferencesStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

/** Runs only in -PthriveVerification=figma; retains the main and SDK test apps' data. */
internal class FigmaUiChecks(private val runner: Instrumentation) {
    private val packageName = runner.targetContext.packageName
    init {
        val info = runner.uiAutomation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        runner.uiAutomation.serviceInfo = info
    }
    private val output by lazy { File(runner.targetContext.filesDir, "figma-ui").apply { mkdirs() } }
    private val checks = mutableListOf<String>()
    private var screenshotWindow: android.view.Window? = null

    private fun find(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.packageName?.toString() == packageName &&
            (node.text?.toString() == label || node.contentDescription?.toString() == label)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), label)?.let { return it }
        return null
    }

    private fun expect(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8_000
        var attempt = 0
        var forward = true
        while (SystemClock.uptimeMillis() < deadline) {
            if (android.os.Build.VERSION.SDK_INT >= 33) runner.uiAutomation.clearCache()
            val roots = runner.uiAutomation.windows.mapNotNull { it.root } + listOfNotNull(runner.uiAutomation.rootInActiveWindow)
            roots.forEach { root -> find(root, label)?.let { return it } }
            if (++attempt > 3) {
                fun scroll(node: AccessibilityNodeInfo?): Boolean {
                    if (node == null) return false
                    if (node.packageName?.toString() == packageName && node.isScrollable &&
                        node.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true
                    for (i in 0 until node.childCount) if (scroll(node.getChild(i))) return true
                    return false
                }
                if (!roots.any { scroll(it) }) forward = !forward
            }
            SystemClock.sleep(150)
        }
        capture("failure")
        error("Not found: $label")
    }

    private fun click(label: String, pauseMs: Long = 250) {
        var node: AccessibilityNodeInfo? = expect(label)
        while (node != null && !node.isClickable) {
            if (node.isSelected) {
                checks += label
                return
            }
            node = node.parent
        }
        node?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "Not clickable: $label" }
        runner.waitForIdleSync()
        SystemClock.sleep(pauseMs)
        checks += label
    }

    private fun capture(name: String) {
        // Pleos may publish the new accessibility tree before its rendered frame.
        runner.waitForIdleSync()
        SystemClock.sleep(1_000)
        val bitmap = checkNotNull(if (android.os.Build.VERSION.SDK_INT >= 34 && screenshotWindow != null)
            runner.uiAutomation.takeScreenshot(screenshotWindow!!) else runner.uiAutomation.takeScreenshot())
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    fun runCurriculum(): String {
        check(packageName.endsWith(".verification.figma"))
        val prefs = DriverPreferencesStore(runner.targetContext)
        val activity = runner.startActivitySync(Intent(runner.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        screenshotWindow = activity.window
        val model = ViewModelProvider(activity as MainActivity)[DrivingViewModel::class.java]
        runBlocking { withTimeout(12_000) { model.local.first { it.ready } } }
        check(model.local.value.active == null) { "Finish the isolated test app's active session first" }
        click("내 운전")
        click("연습 단계 변경")
        expect("연습 단계 선택")
        click("초보자")
        capture("curriculum-stage")
        click("이 단계로 시작하기")
        expect("연습 항목 선택")
        click("기초 주행 및 핸들링")
        click("차선 유지 및 직진·곡선 주행")
        click("유턴 구역 진입 및 핸들 감기·풀기 타이밍 연습하기")
        capture("curriculum-browser")
        click("15분")
        click("연습 코스 찾기")
        expect("조건에 맞는 코스 없음")
        click("항목 다시 고르기")
        click("30분")
        click("연습 코스 찾기")
        click("유턴 구역이 있는 한적한 길")
        capture("curriculum-courses")
        click("이 코스로 준비하기")
        fun disabled(label: String): Boolean {
            var node: AccessibilityNodeInfo? = expect(label)
            while (node != null) { if (!node.isEnabled) return true; node = node.parent }
            return false
        }
        check(disabled("함께 출발하기")) { "Learner road practice must require a companion" }
        click("면허 2년 이상 동승자가 함께 탔어요")
        check(!disabled("함께 출발하기"))
        capture("curriculum-brief")
        click("함께 출발하기")
        expect("시연 기록 종료하기")
        val active = runBlocking { withTimeout(8_000) { model.local.first { it.active?.curriculumItemId == "b213" }.active!! } }
        check(active.curriculumCourseId == "uturn-quiet" && active.source == "DEMO")
        capture("curriculum-driving")
        click("시연 기록 종료하기")
        expect("연습 리포트")
        click("여유 있었어요")
        capture("curriculum-report")
        click("이 항목으로 준비하기")
        expect("어린이 보호구역 및 규정 속도 표지판 인식하며 주행하기")
        check("b213" in completedTopics(model.local.value.records))
        click("장롱면허")
        click("출퇴근/정기 방문 목적지 사전 답사")
        click("주말에 실제 출근 경로(회사 방향) 미리 가보기 (코스 비교 활용)")
        click("목적지 선택하기")
        expect("장소 이름 검색")
        capture("curriculum-destination")
        click("성수 오피스")
        expect("성수 오피스 · 변경 적은 코스")
        click("항목 다시 고르기")
        click("주차 마스터 및 자립 운전")
        click("주차장 진입 및 공간 감각")
        click("가장 까다로운 평행 주차 및 T자(후방) 주차 공식 적용해 탈출하기")
        click("연습 장소 찾기")
        expect("서울숲 공영주차장")
        click("서울숲 공영주차장")
        capture("curriculum-parking-places")
        click("이 장소로 준비하기")
        expect("도착 후 주차 연습 포인트")
        check(!disabled("함께 출발하기"))
        capture("curriculum-parking-brief")
        click("이전 화면")
        click("항목 다시 고르기")
        click("야간 및 악천후 주행 심화")
        click("비 오는 날(와이퍼 단계별 조작 및 수막현상 주의) 주행 적응하기")
        expect("조건부 연습 · 시간대·날씨 확인")
        check(disabled("연습 코스 찾기"))
        expect("비 오는 날 알림 받기")
        capture("curriculum-conditional")
        check(runBlocking { prefs.preferences.first().curriculumTrack } == CurriculumTrack.Returning.name)
        check("b213" in completedTopics(model.local.value.records))
        return "PASS: curriculum; learner companion gate; 15-minute empty state; saved item/course; report completion and next item; returning destination and parking branches; unknown weather gate; persisted track; ${checks.size} actions."
    }

    fun runDashboard(): String {
        check(packageName.endsWith(".verification.figma"))
        val activity = runner.startActivitySync(Intent(runner.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        screenshotWindow = activity.window
        expect("오늘의 연습")
        val model = ViewModelProvider(activity as MainActivity)[DrivingViewModel::class.java]
        val data = runBlocking { withTimeout(12_000) { model.local.first { it.ready } } }
        expect("${data.records.count { it.status == "Completed" }}회")
        expect(data.preferences.experience)
        expect("기록 살펴보기")
        expect("내 운전 살펴보기")
        capture("dashboard-wide")
        click("기록 살펴보기")
        var selected: AccessibilityNodeInfo? = expect("주행 기록")
        while (selected != null && !selected.isSelected) selected = selected.parent
        check(selected != null) { "History dashboard action did not select the history tab" }
        click("홈")
        click("내 운전 살펴보기")
        expect("운전 경험")
        click("홈")
        click("연습 코스 찾기")
        if (data.preferences.curriculumTrack == null) expect("연습 단계 선택") else expect("연습 항목 선택")
        click("홈")
        click("목적지 찾기")
        expect("장소 이름 검색")
        click("홈")
        val content = activity.findViewById<View>(android.R.id.content)
        val originalWidth = content.layoutParams.width
        try {
            for (width in listOf(840, 600)) {
                runner.runOnMainSync { content.layoutParams = content.layoutParams.apply { this.width = width } }
                capture("dashboard-home-${width}dp")
                var recordAction: AccessibilityNodeInfo? = expect("기록 살펴보기")
                while (recordAction != null && !recordAction.isClickable) recordAction = recordAction.parent
                recordAction?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                capture("dashboard-cards-${width}dp")
                click("내 운전 살펴보기")
                expect("운전 경험")
                click("홈")
            }
        } finally {
            runner.runOnMainSync { content.layoutParams = content.layoutParams.apply { width = originalWidth } }
        }
        capture("dashboard-final")
        return "PASS: home dashboard; live record count and profile; ${checks.size} navigation actions; wide, 840dp and 600dp content layouts."
    }

    fun runNavigation(): String {
        check(packageName.endsWith(".verification.figma"))
        val activity = runner.startActivitySync(Intent(runner.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        expect("출발 준비하기")
        fun selected(label: String) {
            var node: AccessibilityNodeInfo? = expect(label)
            while (node != null && !node.isSelected && !node.isChecked) node = node.parent
            check(node?.isSelected == true || node?.isChecked == true) { "Navigation selection did not change: $label; class=${node?.className}; description=${node?.contentDescription}" }
        }
        selected("홈")
        click("내 운전")
        expect("운전 경험")
        selected("내 운전")
        capture("nav-profile")
        click("연습 목표")
        expect("최대 연습 시간")
        selected("연습 목표")
        click("주행 기록")
        selected("주행 기록")
        capture("nav-history")
        repeat(2) {
            listOf("홈", "내 운전", "연습 목표", "주행 기록").forEach { label ->
                click(label, 60)
                selected(label)
            }
        }
        val content = activity.findViewById<View>(android.R.id.content)
        val originalWidth = content.layoutParams.width
        try {
            runner.runOnMainSync { content.layoutParams = content.layoutParams.apply { width = 600 } }
            click("내 운전")
            expect("운전 경험")
            selected("내 운전")
            capture("nav-horizontal-600dp")
            click("홈")
            selected("홈")
        } finally {
            runner.runOnMainSync { content.layoutParams = content.layoutParams.apply { width = originalWidth } }
        }
        click("홈")
        capture("nav-home")
        return "PASS: connected navigation; ${checks.size} tab actions; selected state and page content; rapid reversal; vertical and 600dp horizontal layouts."
    }

    fun runMotion(): String {
        check(packageName.endsWith(".verification.figma"))
        val activity = runner.startActivitySync(Intent(runner.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        expect("홈")
        val model = ViewModelProvider(activity as MainActivity)[DrivingViewModel::class.java]
        runBlocking { withTimeout(12_000) { model.parkingState.first { it.freshPark(SystemClock.elapsedRealtime()) } } }
        capture("motion-home")
        click("내 운전")
        val store = DriverPreferencesStore(runner.targetContext)
        runBlocking { store.setReducedMotion(false) }
        click("가끔 운전해요")
        expect("저장 완료")
        capture("motion-profile-saved")

        // Dispatch a genuine pointer hold through the Activity's decor; accessibility clicks
        // deliberately do not produce press-scale feedback.
        val rect = Rect()
        expect("완료").getBoundsInScreen(rect)
        val origin = IntArray(2)
        val down = SystemClock.uptimeMillis()
        fun pointer(action: Int) {
            runner.runOnMainSync {
                activity.window.decorView.getLocationOnScreen(origin)
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    rect.centerX().toFloat() - origin[0], rect.centerY().toFloat() - origin[1], 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                activity.window.decorView.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        pointer(MotionEvent.ACTION_DOWN)
        SystemClock.sleep(100)
        val held = checkNotNull(runner.uiAutomation.takeScreenshot())
        File(output, "motion-button-held.png").outputStream().use { held.compress(Bitmap.CompressFormat.PNG, 100, it) }
        held.recycle()
        pointer(MotionEvent.ACTION_CANCEL)

        repeat(4) { click("화면 움직임 줄이기", 40) }
        check(!runBlocking { store.preferences.first() }.reducedMotion)
        click("화면 움직임 줄이기")
        check(runBlocking { store.preferences.first() }.reducedMotion)
        capture("motion-reduced")
        click("화면 움직임 줄이기")
        click("홈")
        click("주행 기록")
        click("이 기록 삭제")
        capture("motion-dialog")
        click("취소")
        expect("리포트 보기")
        // Re-open during normal use; closing must restore focus and permit the next action.
        click("완료 기록 전체 삭제")
        click("취소")
        click("내 운전")
        capture("motion-after-dialog")
        return "PASS: PARK received; real saved feedback; pointer hold; repeated/reduced-motion preference persisted; animated dialog cancellation restores navigation."
    }

    fun run(): String {
        check(packageName.endsWith(".verification.figma")) { "Use -PthriveVerification=figma" }
        val activity = runner.startActivitySync(Intent(runner.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        expect("홈")
        capture("01-home")
        click("주행 기록")
        capture("02-history-initial")
        click("연습 목표")
        click("15분")
        click("연습 코스 찾기")
        expect("조건에 맞는 예시 코스가 없어요")
        capture("03-no-course")
        click("연습 조건 바꾸기")
        click("30분")
        capture("04-practice")
        click("연습 코스 찾기")
        click("시간 짧은 코스")
        capture("05-direct-route")
        click("변경 적은 코스")
        click("이 코스로 준비하기")
        expect("출발 준비")
        capture("06-brief")
        click("시연 시작")
        expect("300 m 앞")
        capture("07-driving")
        click("시연 기록 종료하기")
        expect("주행 리포트")
        click("보통이에요")
        capture("08-report")
        click("이 제안으로 준비하기")
        expect("이전 연습")
        capture("09-next-practice")
        click("홈")
        click("목적지 찾기")
        var field: AccessibilityNodeInfo? = expect("장소 이름 검색")
        while (field != null && !field.isEditable) field = field.parent
        if (field == null) {
            fun editable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.isEditable) return node
                for (i in 0 until node.childCount) editable(node.getChild(i))?.let { return it }
                return null
            }
            field = editable(runner.uiAutomation.rootInActiveWindow)
        }
        val searchField = checkNotNull(field)
        fun query(value: String) {
            check(searchField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }))
            runner.waitForIdleSync()
        }
        query("zzzz")
        expect("일치하는 예시 장소가 없어요")
        capture("10-no-destination")
        query("서울숲")
        click("서울숲 공영주차장")
        expect("코스 선택")
        capture("11-destination-route")
        click("홈")
        click("지도 연동 체험")
        expect("실제 내비게이션 연결 확인")
        expect("성수 오피스")
        capture("12-map-demo")
        click("이 경로로 출발")
        expect("시연 경로 사용")
        capture("13-invitation")
        click("건너뛰기")
        click("이 경로로 출발")
        click("경로 선택")
        expect("출발 준비")
        click("홈")
        click("출발 준비하기")
        expect("실제 내비게이션 연결 확인")
        capture("14-navigation")
        click("내 운전")
        click("가끔 운전해요")
        click("차선 변경")
        capture("15-profile")
        click("완료")
        expect("홈")
        click("내 운전")
        expect("가끔 운전해요")
        val saved = runBlocking { DriverPreferencesStore(runner.targetContext).preferences.first() }
        check(saved.experience == "가끔 운전해요") { "Experience selection was not saved" }
        capture("16-profile-saved")
        click("주행 기록")
        expect("리포트 보기")
        capture("17-history")
        click("이 기록 삭제")
        expect("저장한 기록을 삭제할까요?")
        capture("18-delete-one")
        click("취소")
        click("완료 기록 전체 삭제")
        expect("저장한 기록을 삭제할까요?")
        capture("19-delete-all")
        click("취소")
        click("리포트 보기")
        expect("주행 리포트")
        capture("20-saved-report")
        val content = activity.findViewById<View>(android.R.id.content)
        val originalWidth = content.layoutParams.width
        try {
            for (width in listOf(840, 600)) {
                runner.runOnMainSync {
                    content.layoutParams = content.layoutParams.apply { this.width = width }
                }
                runner.waitForIdleSync()
                click("홈")
                expect("홈")
                capture("21-home-${width}dp")
                click("내 운전")
                expect("내 운전")
                capture("22-profile-${width}dp")
                click("연습 목표")
                expect("연습 목표")
                capture("23-practice-${width}dp")
            }
        } finally {
            runner.runOnMainSync { content.layoutParams = content.layoutParams.apply { width = originalWidth } }
        }
        click("홈")
        capture("24-home-stable")
        return "PASS: ${checks.size} UI actions; 27 screenshots; empty course/search, routes, brief, drive/report, next practice, invitation, saved profile and deletion cancellation. Files: $output"
    }
}
