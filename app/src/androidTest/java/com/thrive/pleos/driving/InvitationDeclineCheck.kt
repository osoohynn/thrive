package com.thrive.pleos.driving

import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import com.thrive.pleos.MainActivity
import com.thrive.pleos.driving.invitation.*
import com.thrive.pleos.driving.navigation.*
import com.thrive.pleos.driving.storage.*
import com.thrive.pleos.ui.driving.PracticeGoal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Uses a temporary navigation coordinate on the emulator, then restores the original destination. */
internal suspend fun checkInvitationDecline(test: Instrumentation): String {
    val context = test.targetContext
    check(context.packageName.endsWith(".verification")) { "Use isolated verification package" }
    val dao = DriveDatabase.get(context).drives()
    val settings = DriverPreferencesStore(context)
    val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    val activity = test.startActivitySync(launch)
    val invitations = withContext(Dispatchers.Main) { PracticeInvitations.get(context) }
    val navigation = invitations.navigation
    val manager = context.getSystemService(NotificationManager::class.java)
    check(settings.preferences.first().invitations) { "Enable participation invitations first" }
    check(dao.active() == null) { "Preserve existing active record" }
    val original = withTimeout(10_000) { navigation.state.first { it.freshDestination(SystemClock.elapsedRealtime()) != null } }.destination!!
    val history = settings.preferences.first().invitationHistory
    while (parseInvitationHistory(history).any { System.currentTimeMillis() - it.atEpochMs < 60_000 }) delay(250)
    val target = (2..34).map { offset -> original.copy(name = "연동 검증 지점", latitude = original.latitude + offset * 0.001,
        longitude = original.longitude + 0.002, poiId = "", poiProvider = "", poiSubId = "0") }
        .first { !invitationWasSeen(invitationKey(it), history, System.currentTimeMillis()) }
    try {
        withContext(Dispatchers.Main) {
            navigation.requestPractice(NativePracticePlan(PracticeGoal.Daily, 120, target, option = "RECOMMENDED"))
        }
        withTimeout(35_000) { navigation.state.first { state ->
            state.freshDestination(SystemClock.elapsedRealtime())?.let { !it.samePlace(original) } == true
        } }
        test.runOnMainSync { activity.finish() }
        test.waitForIdleSync()
        val notification = withTimeout(15_000) {
            var found = manager.activeNotifications.firstOrNull { it.id == INVITATION_NOTIFICATION_ID }
            while (found == null) {
                delay(250)
                found = manager.activeNotifications.firstOrNull { it.id == INVITATION_NOTIFICATION_ID }
            }
            found
        }
        check(dao.active() == null) { "Invitation started a record" }
        val action = checkNotNull(notification.notification.actions.firstOrNull { it.title.toString() == "나중에" })
        action.actionIntent.send()
        delay(500)
        check(manager.activeNotifications.none { it.id == INVITATION_NOTIFICATION_ID }) { "Decline did not remove notification" }
        withContext(Dispatchers.Main) { navigation.queryDestination() }
        delay(4_000)
        check(manager.activeNotifications.none { it.id == INVITATION_NOTIFICATION_ID }) { "Same destination was offered again" }
        check(dao.active() == null) { "Decline started a record" }
        val shown = settings.preferences.first().invitationHistory
        check(parseInvitationHistory(shown).isNotEmpty()) { "Suppression was not persisted" }
        return "PASS: real navigation destination change generated a system invitation; Later action removed it; re-query did not re-offer; no record created."
    } finally {
        withContext(Dispatchers.Main) {
            navigation.requestPractice(NativePracticePlan(PracticeGoal.Daily, 120, original, option = "RECOMMENDED"))
        }
        withTimeout(35_000) { navigation.state.first { state ->
            state.freshDestination(SystemClock.elapsedRealtime())?.samePlace(original) == true
        } }
    }
}
