package com.example.data.repositoryimpl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserSettings
import com.example.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.hikariDataStore: DataStore<Preferences> by preferencesDataStore(name = "hikari_settings")

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_AUTOPLAY_NEXT = booleanPreferencesKey("autoplayNext")
        val KEY_RESUME_PROMPT = booleanPreferencesKey("resumePrompt")
        val KEY_ENABLED_SOURCES = stringSetPreferencesKey("enabledSources")
        val KEY_PREFERRED_QUALITY = stringPreferencesKey("preferredQuality")
        val KEY_RECENT_SEARCHES = stringPreferencesKey("recentSearches")
        private const val RECENT_DELIMITER = "\u001F"
        private const val MAX_RECENT_SEARCHES = 10
    }

    override val settingsFlow: Flow<UserSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            val themeMode = when (prefs[KEY_THEME]?.uppercase()) {
                "LIGHT" -> ThemeMode.LIGHT
                "SYSTEM" -> ThemeMode.SYSTEM
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.DARK
            }
            val autoplay = prefs[KEY_AUTOPLAY_NEXT] ?: true
            val resumeAuto = prefs[KEY_RESUME_PROMPT] ?: true
            val enabledSources = prefs[KEY_ENABLED_SOURCES] ?: setOf("openfilms")
            val preferredQuality = prefs[KEY_PREFERRED_QUALITY]

            UserSettings(
                themeMode = themeMode,
                autoplayNext = autoplay,
                resumeAutomatically = resumeAuto,
                enabledSources = if (enabledSources.isEmpty()) setOf("openfilms") else enabledSources,
                preferredQuality = preferredQuality
            )
        }

    override val recentSearchesFlow: Flow<List<String>> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            val raw = prefs[KEY_RECENT_SEARCHES].orEmpty()
            if (raw.isBlank()) {
                emptyList()
            } else {
                raw.split(RECENT_DELIMITER).filter { it.isNotBlank() }
            }
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME] = mode.name
        }
    }

    override suspend fun setAutoplayNext(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTOPLAY_NEXT] = enabled
        }
    }

    override suspend fun setResumeAutomatically(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_RESUME_PROMPT] = enabled
        }
    }

    override suspend fun toggleSourceEnabled(
        sourceId: String,
        allAvailableSourceIds: Set<String>
    ): Boolean {
        var success = true
        dataStore.edit { prefs ->
            val current = (prefs[KEY_ENABLED_SOURCES] ?: allAvailableSourceIds)
                .intersect(allAvailableSourceIds)
                .ifEmpty { allAvailableSourceIds }

            if (sourceId in current) {
                if (current.size <= 1) {
                    // Enforce at least one source remains enabled per §6.5-6
                    success = false
                } else {
                    prefs[KEY_ENABLED_SOURCES] = current - sourceId
                }
            } else {
                prefs[KEY_ENABLED_SOURCES] = current + sourceId
            }
        }
        return success
    }

    override suspend fun setPreferredQuality(qualityLabel: String?) {
        dataStore.edit { prefs ->
            if (qualityLabel.isNullOrBlank()) {
                prefs.remove(KEY_PREFERRED_QUALITY)
            } else {
                prefs[KEY_PREFERRED_QUALITY] = qualityLabel
            }
        }
    }

    override suspend fun addRecentSearch(query: String) {
        val cleaned = query.trim()
        if (cleaned.length < 2) return
        dataStore.edit { prefs ->
            val existing = prefs[KEY_RECENT_SEARCHES]
                ?.split(RECENT_DELIMITER)
                ?.filter { it.isNotBlank() && !it.equals(cleaned, ignoreCase = true) }
                .orEmpty()
            val updated = (listOf(cleaned) + existing).take(MAX_RECENT_SEARCHES)
            prefs[KEY_RECENT_SEARCHES] = updated.joinToString(RECENT_DELIMITER)
        }
    }

    override suspend fun removeRecentSearch(query: String) {
        dataStore.edit { prefs ->
            val existing = prefs[KEY_RECENT_SEARCHES]
                ?.split(RECENT_DELIMITER)
                ?.filter { it.isNotBlank() && !it.equals(query, ignoreCase = true) }
                .orEmpty()
            prefs[KEY_RECENT_SEARCHES] = existing.joinToString(RECENT_DELIMITER)
        }
    }

    override suspend fun clearRecentSearches() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_RECENT_SEARCHES)
        }
    }
}
