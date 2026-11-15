package com.thrive.pleos.driving

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
    private val output by lazy { File(runner.targetContext.filesDir, "figma-ui").apply { mkdirs() } }
    private val checks = mutableListOf<String>()

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
        while (SystemClock.uptimeMillis() < deadline) {
            if (android.os.Build.VERSION.SDK_INT >= 33) runner.uiAutomation.clearCache()
            val root = runner.uiAutomation.rootInActiveWindow
            find(root, label)?.let { return it }
            if (++attempt > 3) {
                fun scroll(node: AccessibilityNodeInfo?): Boolean {
                    if (node == null) return false
                    if (node.packageName?.toString() == packageName && node.isScrollable &&
                        node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                    for (i in 0 until node.childCount) if (scroll(node.getChild(i))) return true
                    return false
                }
                scroll(root)
            }
            SystemClock.sleep(150)
        }
        capture("failure")
        error("Not found: $label")
    }

    private fun click(label: String, pauseMs: Long = 250) {
        var node: AccessibilityNodeInfo? = expect(label)
        while (node != null && !node.isClickable) {
            if (label in setOf("홈", "연습 목표", "주행 기록", "내 운전") && node.isSelected) {
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
        val bitmap = checkNotNull(runner.uiAutomation.takeScreenshot())
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    fun runNavigation(): String {
        check(packageName.endsWith(".verification.figma"))
        val activity = runner.startActivitySync(Intent(runner.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        expect("실제 목적지 기록")
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
        click("실제 목적지 기록")
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
