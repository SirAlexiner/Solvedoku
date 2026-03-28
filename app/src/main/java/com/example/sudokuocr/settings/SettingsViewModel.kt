package com.example.sudokuocr.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sudokuocr.data.settings.AppSettings
import com.example.sudokuocr.data.settings.GivenDisplay
import com.example.sudokuocr.data.settings.AppTheme
import com.example.sudokuocr.data.settings.SaveStyle
import com.example.sudokuocr.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)

    val settings = repo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )

    fun setSolutionColor(argb: Int)        { viewModelScope.launch { repo.updateSolutionColor(argb)    } }
    fun setGivenDisplay(g: GivenDisplay)   { viewModelScope.launch { repo.updateGivenDisplay(g)        } }
    fun setGivenColor(argb: Int)           { viewModelScope.launch { repo.updateGivenColor(argb)       } }
    fun setSaveToGallery(v: Boolean)       { viewModelScope.launch { repo.updateSaveToGallery(v)       } }
    fun setSaveStyle(s: SaveStyle)         { viewModelScope.launch { repo.updateSaveStyle(s)           } }
    fun setAppTheme(t: AppTheme)           { viewModelScope.launch { repo.updateAppTheme(t)            } }
}