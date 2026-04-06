package com.example.sudokuocr.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("sudoku_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_SOLUTION_COLOR       = intPreferencesKey("solution_color_argb")
        val KEY_GIVEN_DISPLAY        = stringPreferencesKey("given_display")
        val KEY_GIVEN_COLOR          = intPreferencesKey("given_color_argb")
        val KEY_SAVE_TO_GALLERY      = booleanPreferencesKey("save_to_gallery")
        val KEY_SAVE_STYLE           = stringPreferencesKey("save_style")
        val KEY_APP_THEME            = stringPreferencesKey("app_theme")
        val KEY_SPLASH_LAST_SHOWN_MS = longPreferencesKey("splash_last_shown_ms")
        val KEY_ONBOARDING_SHOWN     = booleanPreferencesKey("onboarding_shown")

        // SharedPreferences key — synchronous read for MainActivity before installSplashScreen()
        const val PREFS_NAME      = "sudoku_ui_prefs"
        const val PREFS_KEY_THEME = "app_theme_sync"

        // CV param keys
        val KEY_DEV_MODE        = booleanPreferencesKey("dev_mode")
        val KEY_CV_CANNY_LOW    = floatPreferencesKey("cv_canny_low")
        val KEY_CV_CANNY_HIGH   = floatPreferencesKey("cv_canny_high")
        val KEY_CV_MIN_AREA     = floatPreferencesKey("cv_min_area_ratio")
        val KEY_CV_POLY_EPSILON = floatPreferencesKey("cv_poly_epsilon")
        val KEY_CV_HOUGH_THRESH = intPreferencesKey("cv_hough_threshold")
        val KEY_CV_HOUGH_LINES  = intPreferencesKey("cv_hough_min_lines")
    }

    // Written whenever the theme changes so MainActivity can read it synchronously
    // before installSplashScreen() without blocking on DataStore.
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
            } ?: AppTheme.SYSTEM,
            devModeEnabled = prefs[KEY_DEV_MODE] == true,
            cvParams = CvParams(
                cannyLow          = (prefs[KEY_CV_CANNY_LOW]    ?: 50f).toDouble(),
                cannyHigh         = (prefs[KEY_CV_CANNY_HIGH]   ?: 100f).toDouble(),
                minAreaRatio      = (prefs[KEY_CV_MIN_AREA]     ?: 0.05f).toDouble(),
                polyEpsilonFactor = (prefs[KEY_CV_POLY_EPSILON] ?: 0.02f).toDouble(),
                houghThreshold    = prefs[KEY_CV_HOUGH_THRESH]  ?: 80,
                minHoughLines     = prefs[KEY_CV_HOUGH_LINES]   ?: 8
            )
        )
    }

    val splashLastShownMs: Flow<Long> = context.dataStore.data.map { it[KEY_SPLASH_LAST_SHOWN_MS] ?: 0L }
    val onboardingShown: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDING_SHOWN] == true }

    suspend fun updateSolutionColor(argb: Int)      { context.dataStore.edit { it[KEY_SOLUTION_COLOR]  = argb        } }
    suspend fun updateGivenDisplay(mode: GivenDisplay) { context.dataStore.edit { it[KEY_GIVEN_DISPLAY]  = mode.name   } }
    suspend fun updateGivenColor(argb: Int)         { context.dataStore.edit { it[KEY_GIVEN_COLOR]     = argb        } }
    suspend fun updateSaveToGallery(enabled: Boolean)  { context.dataStore.edit { it[KEY_SAVE_TO_GALLERY] = enabled   } }
    suspend fun updateSaveStyle(style: SaveStyle)   { context.dataStore.edit { it[KEY_SAVE_STYLE]      = style.name  } }
    suspend fun updateAppTheme(theme: AppTheme) {
        // Write to SharedPreferences first (sync) so the next cold start reads the
        // correct theme before DataStore is ready.
        prefs.edit().putString(PREFS_KEY_THEME, theme.name).apply()
        context.dataStore.edit { it[KEY_APP_THEME] = theme.name }
    }

    suspend fun updateSplashLastShown(ms: Long)     { context.dataStore.edit { it[KEY_SPLASH_LAST_SHOWN_MS] = ms         } }
    suspend fun updateOnboardingShown(shown: Boolean) { context.dataStore.edit { it[KEY_ONBOARDING_SHOWN]  = shown       } }
    suspend fun updateDevMode(enabled: Boolean)        { context.dataStore.edit { it[KEY_DEV_MODE]            = enabled     } }
    suspend fun updateCvParams(p: CvParams) {
        context.dataStore.edit {
            it[KEY_CV_CANNY_LOW]    = p.cannyLow.toFloat()
            it[KEY_CV_CANNY_HIGH]   = p.cannyHigh.toFloat()
            it[KEY_CV_MIN_AREA]     = p.minAreaRatio.toFloat()
            it[KEY_CV_POLY_EPSILON] = p.polyEpsilonFactor.toFloat()
            it[KEY_CV_HOUGH_THRESH] = p.houghThreshold
            it[KEY_CV_HOUGH_LINES]  = p.minHoughLines
        }
    }
}