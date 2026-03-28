package com.example.sudokuocr.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("sudoku_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_SOLUTION_COLOR  = intPreferencesKey("solution_color_argb")
        val KEY_GIVEN_DISPLAY   = stringPreferencesKey("given_display")
        val KEY_GIVEN_COLOR     = intPreferencesKey("given_color_argb")
        val KEY_SAVE_TO_GALLERY = booleanPreferencesKey("save_to_gallery")
        val KEY_SAVE_STYLE           = stringPreferencesKey("save_style")
        val KEY_APP_THEME            = stringPreferencesKey("app_theme")
        val KEY_SPLASH_LAST_SHOWN_MS = longPreferencesKey("splash_last_shown_ms")
        val KEY_ONBOARDING_SHOWN     = booleanPreferencesKey("onboarding_shown")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            solutionColorArgb = prefs[KEY_SOLUTION_COLOR]
                ?: android.graphics.Color.rgb(76, 175, 80),
            givenDisplay = GivenDisplay.entries.firstOrNull {
                it.name == prefs[KEY_GIVEN_DISPLAY]
            } ?: GivenDisplay.SAME,
            givenColorArgb = prefs[KEY_GIVEN_COLOR]
                ?: android.graphics.Color.rgb(76, 175, 80),
            saveToGallery = prefs[KEY_SAVE_TO_GALLERY] == true,
            saveStyle = SaveStyle.entries.firstOrNull {
                it.name == prefs[KEY_SAVE_STYLE]
            } ?: SaveStyle.AR_COMPOSITE,
            appTheme = AppTheme.entries.firstOrNull {
                it.name == prefs[KEY_APP_THEME]
            } ?: AppTheme.SYSTEM
        )
    }

    val splashLastShownMs: Flow<Long> = context.dataStore.data.map { it[KEY_SPLASH_LAST_SHOWN_MS] ?: 0L }
    val onboardingShown: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDING_SHOWN] == true }

    suspend fun updateSolutionColor(argb: Int)      { context.dataStore.edit { it[KEY_SOLUTION_COLOR]  = argb        } }
    suspend fun updateGivenDisplay(mode: GivenDisplay) { context.dataStore.edit { it[KEY_GIVEN_DISPLAY]  = mode.name   } }
    suspend fun updateGivenColor(argb: Int)         { context.dataStore.edit { it[KEY_GIVEN_COLOR]     = argb        } }
    suspend fun updateSaveToGallery(enabled: Boolean)  { context.dataStore.edit { it[KEY_SAVE_TO_GALLERY] = enabled   } }
    suspend fun updateSaveStyle(style: SaveStyle)   { context.dataStore.edit { it[KEY_SAVE_STYLE]      = style.name  } }
    suspend fun updateAppTheme(theme: AppTheme)     { context.dataStore.edit { it[KEY_APP_THEME]           = theme.name  } }
    suspend fun updateSplashLastShown(ms: Long)     { context.dataStore.edit { it[KEY_SPLASH_LAST_SHOWN_MS] = ms         } }
    suspend fun updateOnboardingShown(shown: Boolean) { context.dataStore.edit { it[KEY_ONBOARDING_SHOWN]  = shown       } }
}