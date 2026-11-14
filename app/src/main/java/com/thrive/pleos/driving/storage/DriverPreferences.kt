package com.thrive.pleos.driving.storage

import android.content.Context
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
)

internal class DriverPreferencesStore(context: Context) {
    private val store = context.applicationContext.driverDataStore
    private val experience = stringPreferencesKey("experience")
    private val concerns = stringSetPreferencesKey("concerns")
    private val voice = booleanPreferencesKey("voice")
    val preferences = store.data.map { values ->
        DriverPreferences(
            values[experience] ?: DriverPreferences().experience,
            values[concerns] ?: DriverPreferences().concerns,
            values[voice] ?: true,
        )
    }

    suspend fun setExperience(value: String) { store.edit { it[experience] = value } }
    suspend fun setVoice(enabled: Boolean) { store.edit { it[voice] = enabled } }
    suspend fun toggleConcern(value: String) {
        store.edit { values ->
            val current = values[concerns] ?: DriverPreferences().concerns
            values[concerns] = if (value in current) current - value else current + value
        }
    }
}
