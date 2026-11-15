package com.thrive.pleos.driving

import android.app.Instrumentation
import android.os.SystemClock
import com.thrive.pleos.driving.recording.DriveRecorder
import com.thrive.pleos.driving.storage.DriveDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Real SDK callbacks and Room writes with no Activity owner for fifteen minutes. */
internal suspend fun checkStability(test: Instrumentation): String {
    val activity = test.openActivity()
    val navigation = test.liveNavigation()
    val recorder = DriveRecorder.get(test.targetContext)
    val dao = DriveDatabase.get(test.targetContext).drives()
    recorder.awaitReady(); check(dao.active() == null)
    val session = recorder.start(proposed(navigation.state.value))
    test.runOnMainSync { activity.finish() }; test.waitForIdleSync()
    val began = SystemClock.elapsedRealtime()
    var elapsed = session.elapsedMs
    var fixes = session.acceptedFixes
    var checks = 0
    try {
        while (SystemClock.elapsedRealtime() - began < 900_000) {
            delay(30_000)
            val saved = checkNotNull(dao.active())
            check(saved.id == session.id && saved.status == "Recording")
            check(saved.elapsedMs > elapsed && saved.acceptedFixes > fixes)
            check(recorder.state.value.error == null) { recorder.state.value.error.orEmpty() }
            elapsed = saved.elapsedMs; fixes = saved.acceptedFixes; checks++
            android.util.Log.i("ThriveStability", "checkpoint=$checks elapsed=$elapsed fixes=$fixes gaps=${saved.gapCount}")
        }
        val beforeReconnect = checkNotNull(dao.active())
        recorder.reconnectLocation()
        withTimeout(12_000) { dao.observeAll().first { rows -> rows.any { it.id == session.id && it.acceptedFixes > beforeReconnect.acceptedFixes } } }
        val finished = checkNotNull(recorder.finish())
        check(finished.gapCount > beforeReconnect.gapCount)
        return "PASS: 15-minute Activity-free real SDK recording; $checks durable checkpoints grew time and location counts; explicit reconnect resumed samples and marked gap; same session finished. elapsed=${finished.elapsedMs} fixes=${finished.acceptedFixes} gaps=${finished.gapCount} distance=${finished.distanceMeters}"
    } finally { if (dao.active()?.id == session.id) recorder.finish() }
}
