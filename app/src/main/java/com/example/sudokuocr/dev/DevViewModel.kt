package com.example.sudokuocr.dev

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sudokuocr.cv.CvStage
import com.example.sudokuocr.cv.SudokuDetector
import com.example.sudokuocr.data.settings.CvParams
import com.example.sudokuocr.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.opencv.core.Mat

class DevViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)

    val settings = repo.settingsFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        com.example.sudokuocr.data.settings.AppSettings()
    )

    private val _stage = MutableStateFlow(CvStage.RAW)
    val stage = _stage.asStateFlow()

    private val _debugBitmap = MutableStateFlow<Bitmap?>(null)
    val debugBitmap = _debugBitmap.asStateFlow()

    fun setStage(s: CvStage) { _stage.value = s }

    /** Called from the analyzeFrame loop with each rotated BGR Mat. */
    fun processFrame(rotated: Mat) {
        val params = settings.value.cvParams
        val result = SudokuDetector.detectDebug(rotated, params, _stage.value)
        _debugBitmap.value = result.stageBitmap
    }

    fun updateCvParams(p: CvParams) {
        viewModelScope.launch { repo.updateCvParams(p) }
    }
}