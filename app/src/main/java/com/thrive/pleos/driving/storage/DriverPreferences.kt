package com.thrive.pleos.driving.storage

import android.content.Context
import com.thrive.pleos.driving.invitation.INVITATION_DAY_MS
import com.thrive.pleos.driving.invitation.parseInvitationHistory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.driverDataStore by preferencesDataStore(name = "driver_preferences")

internal data class DriverPreferences(
    val experience: String = "쉬었다 다시 시작해요",
    val concerns: Set<String> = setOf("차선 변경", "낯선 경로"),
    val voice: Boolean = true,
    val invitations: Boolean = false,
    val invitationHistory: Set<String> = emptySet(),
    val reducedMotion: Boolean = false,
    val curriculumTrack: String? = null,
)

internal class DriverPreferencesStore(context: Context) {
    private val store = context.applicationContext.driverDataStore
    private val experience = stringPreferencesKey("experience")
    private val concerns = stringSetPreferencesKey("concerns")
    private val voice = booleanPreferencesKey("voice")
    private val curriculumTrack = stringPreferencesKey("curriculum_track")
    private val reducedMotion = booleanPreferencesKey("reduced_motion")
    private val invitations = booleanPreferencesKey("practice_invitations")
    private val invitationHistory = stringSetPreferencesKey("invitation_history")
    val preferences = store.data.map { values ->
        DriverPreferences(
            values[experience] ?: DriverPreferences().experience,
            values[concerns] ?: DriverPreferences().concerns,
            values[voice] ?: true,
            values[invitations] ?: false,
            values[invitationHistory] ?: emptySet(),
            values[reducedMotion] ?: false,
            values[curriculumTrack],
        )
    }

    suspend fun setInvitations(enabled: Boolean) { store.edit { it[invitations] = enabled } }
    suspend fun markInvitationShown(key: String, atEpochMs: Long) {
        store.edit { values ->
            val recent = parseInvitationHistory(values[invitationHistory] ?: emptySet())
                .filter { atEpochMs - it.atEpochMs <= INVITATION_DAY_MS }
                .filterNot { it.key == key }.sortedByDescending { it.atEpochMs }.take(31)
            values[invitationHistory] = (recent.map { "${it.atEpochMs}:${it.key}" } + "$atEpochMs:$key").toSet()
        }
    }

    suspend fun setCurriculumTrack(value: String) { store.edit { it[curriculumTrack] = value } }
    suspend fun setExperience(value: String) { store.edit { it[experience] = value } }
    suspend fun setReducedMotion(enabled: Boolean) { store.edit { it[reducedMotion] = enabled } }
    suspend fun setVoice(enabled: Boolean) { store.edit { it[voice] = enabled } }
    suspend fun toggleConcern(value: String) {
        store.edit { values ->
            val current = values[concerns] ?: DriverPreferences().concerns
            values[concerns] = if (value in current) current - value else current + value
        }
    }
}
