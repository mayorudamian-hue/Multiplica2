package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.game.GameMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "multidrop_prefs")

data class UserPreferences(
    val gameMode: GameMode = GameMode.ARCADE,
    val selectedTable: Int = 0, // 0 = Clásico (Todas), 2..10 = tabla específica
    val includePrimes: Boolean = true,
    val showFactorPills: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val bestScore: Int = 0,
    val hasCompletedTutorial: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val GAME_MODE = stringPreferencesKey("game_mode")
        val SELECTED_TABLE = intPreferencesKey("selected_table")
        val INCLUDE_PRIMES = booleanPreferencesKey("include_primes")
        val SHOW_FACTOR_PILLS = booleanPreferencesKey("show_factor_pills")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val BEST_SCORE = intPreferencesKey("best_score")
        val TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val mode = prefs[Keys.GAME_MODE]?.let { runCatching { GameMode.valueOf(it) }.getOrNull() }
            ?: GameMode.ARCADE

        UserPreferences(
            gameMode = mode,
            selectedTable = prefs[Keys.SELECTED_TABLE] ?: 0,
            includePrimes = prefs[Keys.INCLUDE_PRIMES] ?: true,
            showFactorPills = prefs[Keys.SHOW_FACTOR_PILLS] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            bestScore = prefs[Keys.BEST_SCORE] ?: 0,
            hasCompletedTutorial = prefs[Keys.TUTORIAL_COMPLETED] ?: false
        )
    }

    suspend fun updateGameMode(mode: GameMode) {
        context.dataStore.edit { it[Keys.GAME_MODE] = mode.name }
    }

    suspend fun updateSelectedTable(table: Int) {
        context.dataStore.edit { it[Keys.SELECTED_TABLE] = table }
    }

    suspend fun updateIncludePrimes(enabled: Boolean) {
        context.dataStore.edit { it[Keys.INCLUDE_PRIMES] = enabled }
    }

    suspend fun updateShowFactorPills(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_FACTOR_PILLS] = enabled }
    }

    suspend fun updateHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun updateSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun updateTutorialCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.TUTORIAL_COMPLETED] = completed }
    }

    suspend fun updateBestScoreIfHigher(score: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.BEST_SCORE] ?: 0
            if (score > current) {
                prefs[Keys.BEST_SCORE] = score
            }
        }
    }
}
