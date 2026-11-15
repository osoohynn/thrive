package com.thrive.pleos.driving.invitation

import com.thrive.pleos.driving.navigation.*
import java.security.MessageDigest
import kotlin.math.roundToLong

internal const val CAR_DRIVE_PERMISSION = "android.car.permission.CAR_POWERTRAIN"
internal const val INVITATION_EXTRA = "practice_invitation"
internal const val INVITATION_NOTIFICATION_ID = 1002
internal const val INVITATION_LIFETIME_MS = 120_000L
internal const val INVITATION_DAY_MS = 86_400_000L

internal data class ParkState(val gear: String? = null, val receivedAtMs: Long? = null,
    val querying: Boolean = false, val message: String = "차량 상태를 아직 확인하지 않았어요.") {
    fun freshPark(nowMs: Long): Boolean = gear == "GEAR_PARK" && receivedAtMs?.let { nowMs - it in 0..5_000 } == true
}

internal fun invitationKey(place: LiveDestination): String {
    val identity = if (place.poiId.isNotBlank() && place.poiId != "0") "poi:${place.poiProvider}:${place.poiId}"
        else "geo:${(place.latitude * 10_000).roundToLong()}:${(place.longitude * 10_000).roundToLong()}"
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

internal data class InvitationSeen(val key: String, val atEpochMs: Long)
internal fun parseInvitationHistory(entries: Set<String>): List<InvitationSeen> = entries.mapNotNull {
    val split = it.indexOf(':')
    if (split < 1) return@mapNotNull null
    val at = it.substring(0, split).toLongOrNull()?.takeIf { time -> time >= 0 } ?: return@mapNotNull null
    val key = it.substring(split + 1).takeIf { key -> key.length == 64 && key.all { c -> c in '0'..'9' || c in 'a'..'f' } } ?: return@mapNotNull null
    InvitationSeen(key, at)
}

internal fun invitationWasSeen(key: String, history: Set<String>, nowEpochMs: Long): Boolean {
    val entries = parseInvitationHistory(history)
    return entries.any { nowEpochMs - it.atEpochMs < 60_000 } ||
        entries.any { it.key == key && nowEpochMs - it.atEpochMs <= INVITATION_DAY_MS }
}

internal fun invitationCandidate(navigation: NavigationState, park: ParkState, enabled: Boolean,
    activeSession: Boolean, appVisible: Boolean, notificationsAllowed: Boolean,
    history: Set<String>, nowMs: Long, nowEpochMs: Long): LiveDestination? {
    if (!enabled || activeSession || appVisible || !notificationsAllowed || !park.freshPark(nowMs)) return null
    if (navigation.routeMode != "ROUTE_GUIDING" || navigation.course?.phase == CoursePhase.Calculating) return null
    val destination = navigation.freshDestination(nowMs) ?: return null
    return destination.takeUnless { invitationWasSeen(invitationKey(it), history, nowEpochMs) }
}

internal data class PracticeInvitation(val token: String, val destination: LiveDestination, val createdAtMs: Long) {
    fun validAt(nowMs: Long): Boolean = nowMs - createdAtMs in 0 until INVITATION_LIFETIME_MS
}
