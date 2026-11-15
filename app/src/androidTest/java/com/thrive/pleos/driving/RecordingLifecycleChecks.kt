package com.thrive.pleos.driving

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import com.thrive.pleos.MainActivity
import com.thrive.pleos.driving.recording.DriveRecordingService
import com.thrive.pleos.driving.recording.DriveRecorder
import com.thrive.pleos.driving.storage.DriveDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

/** Device-only integration check. Requires an existing SDK verification record and location grants. */
class RecordingLifecycleChecks : Instrumentation() {
    private var scenario: String? = null
    private var renderWidth = 1100
    private var renderFontScale = 1f
    override fun onCreate(arguments: Bundle?) { scenario = arguments?.getString("scenario"); renderWidth = arguments?.getString("width")?.toIntOrNull() ?: 1100; renderFontScale = arguments?.getString("fontScale")?.toFloatOrNull() ?: 1f; super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        try {
            if (scenario == "curriculum_ui") {
                result.putString("stream", FigmaUiChecks(this).runCurriculum() + "\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (scenario == "dashboard_ui") {
                result.putString("stream", FigmaUiChecks(this).runDashboard() + "\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (scenario == "nav_ui") {
                result.putString("stream", FigmaUiChecks(this).runNavigation() + "\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (scenario == "motion_ui") {
                result.putString("stream", FigmaUiChecks(this).runMotion() + "\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (scenario == "figma_ui") {
                result.putString("stream", FigmaUiChecks(this).run() + "\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (scenario == "mvp_stability") {
                result.putString("stream", runBlocking { checkStability(this@RecordingLifecycleChecks) } + "\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (scenario == "ui_render") {
                result.putString("stream", runBlocking { checkUiRender(this@RecordingLifecycleChecks, renderWidth, renderFontScale) } + "\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (scenario in setOf("mvp_events", "mvp_roundtrip", "mvp_storage")) {
                result.putString("stream", runBlocking { when (scenario) {
                    "mvp_events" -> checkMvpEvents(this@RecordingLifecycleChecks)
                    "mvp_roundtrip" -> checkRoundTrip(this@RecordingLifecycleChecks)
                    else -> checkEventStorageLimits(this@RecordingLifecycleChecks)
                } } + "\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (scenario == "invitation_decline") {
                result.putString("stream", runBlocking { checkInvitationDecline(this@RecordingLifecycleChecks) } + "\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            runBlocking {
                check(targetContext.packageName.endsWith(".verification")) { "Use the isolated verification package" }
                val app = targetContext.applicationContext as Application
                val destroyed = AtomicInteger()
                val callbacks = object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityDestroyed(activity: Activity) { if (activity is MainActivity) destroyed.incrementAndGet() }
                    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                    override fun onActivityStarted(activity: Activity) = Unit
                    override fun onActivityResumed(activity: Activity) = Unit
                    override fun onActivityPaused(activity: Activity) = Unit
                    override fun onActivityStopped(activity: Activity) = Unit
                    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
                }
                app.registerActivityLifecycleCallbacks(callbacks)
                val intent = Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                val recorder = DriveRecorder.get(app)
                try {
                    val activity = startActivitySync(intent)
                    recorder.awaitReady()
                    val before = recorder.resume()
                    delay(2_000)
                    val dao = DriveDatabase.get(app).drives()
                    val started = checkNotNull(dao.active())
                    destroyed.set(0)
                    runOnMainSync { activity.finish() }
                    waitForIdleSync()
                    check(destroyed.get() > 0) { "Activity did not finish" }
                    delay(7_000)
                    val background = checkNotNull(dao.active())
                    check(background.id == before.id && background.status == "Recording") { "Session changed or interrupted" }
                    check(background.elapsedMs >= started.elapsedMs + 5_000) { "Background timer stopped" }
                    check(background.acceptedFixes > started.acceptedFixes) { "SDK location samples stopped" }
                    startActivitySync(intent)
                    val reopened = recorder.resume()
                    check(reopened.id == before.id && reopened.gapCount == before.gapCount) { "Recreation caused a duplicate or gap" }
                    check(recorder.finish("stale-notification-session") == null) { "Stale stop action completed the current drive" }
                    check(dao.active()?.id == before.id)
                    runOnMainSync { targetContext.stopService(Intent(targetContext, DriveRecordingService::class.java)) }
                    delay(2_000)
                    val interrupted = checkNotNull(dao.active())
                    check(interrupted.id == before.id && interrupted.status == "Interrupted") { "Service loss was not persisted" }
                    val recovered = recorder.resume()
                    check(recovered.id == before.id && recovered.status == "Recording" && recovered.gapCount == reopened.gapCount + 1)
                    result.putString("stream", "PASS: Activity destroyed; same SDK session kept time and samples; new Activity resumed without gap; stale stop ignored; service loss interrupted and resumed same record.\n" +
                        "elapsed=${started.elapsedMs}->${background.elapsedMs}; fixes=${started.acceptedFixes}->${background.acceptedFixes}\n")
                } finally {
                    app.unregisterActivityLifecycleCallbacks(callbacks)
                    // The runner may end this process; reopen the verification app to resume or finish its record.
                }
            }
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "FAIL: ${error.stackTraceToString()}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
