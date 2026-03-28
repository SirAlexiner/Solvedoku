package com.example.sudokuocr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sudokuocr.data.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val SPLASH_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000L
        /** How long the AVD plays — keep splash on screen at least this long. */
        private const val AVD_DURATION_MS     = 1_000L
    }

    private val repo = SettingsRepository(application)

    val settings = repo.settingsFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        com.example.sudokuocr.data.settings.AppSettings()
    )

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    /** True when the splash animation should play; read by MainActivity. */
    var needsSplash: Boolean = false
        private set

    /** True after onboarding has never been shown; drives OnboardingOverlay. */
    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding = _showOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            val lastShown = repo.splashLastShownMs.first()
            val onboarded = repo.onboardingShown.first()
            val now       = System.currentTimeMillis()

            needsSplash = lastShown == 0L || (now - lastShown > SPLASH_THRESHOLD_MS)

            if (needsSplash) {
                repo.updateSplashLastShown(now)
                // Hold isReady until the AVD has had time to finish,
                // so the exit listener fires after the animation is complete.
                delay(AVD_DURATION_MS)
            }

            _showOnboarding.value = !onboarded
            _isReady.value = true
        }
    }

    fun onOnboardingFinished() {
        viewModelScope.launch {
            repo.updateOnboardingShown(true)
            _showOnboarding.value = false
        }
    }
}