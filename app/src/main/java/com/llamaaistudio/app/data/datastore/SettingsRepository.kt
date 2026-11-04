package com.llamaaistudio.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.llamaaistudio.app.engine.LlamaNative
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "studio_settings")

enum class PerformanceProfile { BATTERY_SAVER, BALANCED, MAX_PERFORMANCE }

data class StudioSettings(
    val threads: Int,
    val contextLength: Int,
    val profile: PerformanceProfile,
    val useMlock: Boolean,
)

/**
 * Persists user-tunable inference/performance settings across app restarts (Jetpack DataStore).
 */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val THREADS = intPreferencesKey("threads")
        val CONTEXT_LENGTH = intPreferencesKey("context_length")
        val PROFILE = stringPreferencesKey("perf_profile")
        val USE_MLOCK = booleanPreferencesKey("use_mlock")
    }

    val settings: Flow<StudioSettings> = context.settingsDataStore.data.map { prefs ->
        StudioSettings(
            threads = prefs[Keys.THREADS] ?: LlamaNative.nativeThreadCountRecommendation(),
            contextLength = prefs[Keys.CONTEXT_LENGTH] ?: 4096,
            profile = prefs[Keys.PROFILE]?.let { runCatching { PerformanceProfile.valueOf(it) }.getOrNull() }
                ?: PerformanceProfile.BALANCED,
            useMlock = prefs[Keys.USE_MLOCK] ?: false,
        )
    }

    suspend fun setThreads(threads: Int) {
        context.settingsDataStore.edit { it[Keys.THREADS] = threads }
    }

    suspend fun setContextLength(length: Int) {
        context.settingsDataStore.edit { it[Keys.CONTEXT_LENGTH] = length }
    }

    suspend fun setProfile(profile: PerformanceProfile) {
        context.settingsDataStore.edit { it[Keys.PROFILE] = profile.name }
        when (profile) {
            PerformanceProfile.BATTERY_SAVER -> {
                setThreads((LlamaNative.nativeThreadCountRecommendation() / 2).coerceAtLeast(2))
                setContextLength(2048)
            }
            PerformanceProfile.BALANCED -> {
                setThreads(LlamaNative.nativeThreadCountRecommendation())
                setContextLength(4096)
            }
            PerformanceProfile.MAX_PERFORMANCE -> {
                setThreads(LlamaNative.nativeThreadCountRecommendation() + 1)
                setContextLength(8192)
            }
        }
    }

    suspend fun setUseMlock(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.USE_MLOCK] = enabled }
    }
}
