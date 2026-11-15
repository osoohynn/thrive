package com.thrive.pleos.driving

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Use the runner's accessibility connection rather than a competing uiautomator process. */
internal class UiTraversal(private val test: Instrumentation, private val tag: String) {
    private val output = File(test.targetContext.filesDir, "mvp-ui/$tag").apply { mkdirs() }
    private fun find(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.packageName?.toString() == test.targetContext.packageName && (node.text?.toString() == label || node.contentDescription?.toString() == label)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), label)?.let { return it }
        return null
    }
    private fun expect(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8_000
        var attempts = 0
        fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.packageName?.toString() == test.targetContext.packageName && node.isScrollable) return node
            for (i in 0 until node.childCount) scrollable(node.getChild(i))?.let { return it }
            return null
        }
        while (SystemClock.uptimeMillis() < deadline) {
            val root = test.uiAutomation.rootInActiveWindow
            find(root, label)?.let { return it }
            val action = if (attempts++ < 10) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            scrollable(root)?.performAction(action)
            SystemClock.sleep(250)
        }
        error("Not found in verification app: $label")
    }
    private fun click(label: String) {
        var node: AccessibilityNodeInfo? = expect(label)
        node?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        while (node != null && !node.isClickable) node = node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "Not clickable: $label" }
        test.waitForIdleSync(); SystemClock.sleep(300)
    }
    private fun capture(name: String, anchor: String) {
        val node = expect(anchor)
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        test.waitForIdleSync(); SystemClock.sleep(500)
        val items = JSONArray()
        fun visit(current: AccessibilityNodeInfo?) {
            if (current == null) return
            if (current.packageName?.toString() == test.targetContext.packageName) {
                val rect = Rect();current.getBoundsInScreen(rect)
                items.put(JSONObject().put("text", current.text?.toString()).put("description", current.contentDescription?.toString())
                    .put("bounds", rect.toShortString()).put("visible", current.isVisibleToUser).put("clickable", current.isClickable))
            }
            for (i in 0 until current.childCount) visit(current.getChild(i))
        }
        visit(test.uiAutomation.rootInActiveWindow)
        check(items.length() > 0)
        File(output, "$name.json").writeText(items.toString(2))
        val bitmap = checkNotNull(test.uiAutomation.takeScreenshot())
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    fun run(): String {
        capture("01-home", "홈")
        click("연습 목표");capture("02-practice", "연습 목표")
        click("실제 연습 경로 준비");capture("03-native-settings", "내비에서 경로 계산")
        click("주행 기록");click("리포트 보기");capture("04-event-report", "내비가 보낸 사건")
        click("부담됐어요");capture("05-next-practice", "다음 연습 제안")
        click("이 제안으로 준비하기");expect("내비에서 경로 계산")
        click("내 운전");capture("06-profile", "내 운전")
        return "PASS: $tag production Compose; home/goals/native route/journal/feeling/next practice/profile; captures=$output"
    }
}
