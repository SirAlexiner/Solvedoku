package com.example.sudokuocr.solver

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.sudokuocr.cv.AROverlayGenerator
import com.example.sudokuocr.cv.DetectionResult
import com.example.sudokuocr.cv.SudokuDetector
import com.example.sudokuocr.data.history.SolveRecord
import com.example.sudokuocr.data.history.SudokuDatabase
import com.example.sudokuocr.data.history.toGridString
import com.example.sudokuocr.data.settings.AppSettings
import com.example.sudokuocr.data.settings.SaveStyle
import com.example.sudokuocr.data.settings.SettingsRepository
import com.example.sudokuocr.ocr.OcrModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Executors
import android.graphics.Canvas as AndroidCanvas

// ── State ─────────────────────────────────────────────────────────────────────

sealed class SolverState {
    data object Scanning   : SolverState()
    data object Processing : SolverState()

    data class Solved(
        val given:       IntArray,
        val solution:    IntArray,
        val solveTimeMs: Long,
        val isGallery:   Boolean = false  // true = static image, no updateArOverlay loop
    ) : SolverState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Solved) return false
            return solveTimeMs == other.solveTimeMs &&
                    given.contentEquals(other.given) &&
                    solution.contentEquals(other.solution)
        }
        override fun hashCode(): Int {
            var result = given.contentHashCode()
            result = 31 * result + solution.contentHashCode()
            result = 31 * result + solveTimeMs.hashCode()
            return result
        }
    }

    data class Error(val message: String) : SolverState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class SolverViewModel(
    application:                   Application,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher:      CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SolverViewModel"
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.AndroidViewModelFactory() {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = extras[APPLICATION_KEY]!!
                return SolverViewModel(application) as T
            }
        }
    }

    private val ocrModel     = OcrModel(application)
    private val db           = SudokuDatabase.getInstance(application)
    private val settingsRepo = SettingsRepository(application)

    val settings = settingsRepo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )

    private val _state    = MutableStateFlow<SolverState>(SolverState.Scanning)
    val state = _state.asStateFlow()

    // AR bitmap is its own StateFlow — separate from SolverState.Solved so that
    // per-frame updates always emit (Solved.equals ignores bitmaps).
    private val _arBitmap     = MutableStateFlow<Bitmap?>(null)
    val arBitmap = _arBitmap.asStateFlow()

    // True only when the board is actively visible in the camera frame while Solved.
    // Drives overlay hide/show without resetting the solve.
    private val _boardVisible = MutableStateFlow(false)
    val boardVisible = _boardVisible.asStateFlow()

    // One-shot event — emits Unit when an image is successfully saved to the gallery
    private val _gallerySavedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val gallerySavedEvent = _gallerySavedEvent.asSharedFlow()

    /** Called by analyzeFrame when detection returns null in Solved state. */
    fun clearBoardVisible() { _boardVisible.value = false }

    // ── Persistent gesture state ───────────────────────────────────────────────
    // Kept in the ViewModel so state survives navigation to Settings / History and back.

    private val _cameraFrozen = MutableStateFlow(false)
    val cameraFrozen = _cameraFrozen.asStateFlow()

    private val _frozenFrame = MutableStateFlow<Bitmap?>(null)
    val frozenFrame = _frozenFrame.asStateFlow()

    fun toggleFreeze(state: SolverState) {
        val solved = state as? SolverState.Solved ?: return
        if (solved.isGallery) return
        val nowFrozen = !_cameraFrozen.value
        _cameraFrozen.value = nowFrozen
        isFrozen            = nowFrozen
        _frozenFrame.value  = if (nowFrozen) latestCameraFrameBitmap else null
    }

    fun applyGalleryFrame(bitmap: Bitmap?) {
        if (bitmap != null) _frozenFrame.value = bitmap
    }

    // The decoded gallery image — shown as the static background when solving from gallery
    private val _galleryBitmap = MutableStateFlow<Bitmap?>(null)
    val galleryBitmap = _galleryBitmap.asStateFlow()

    // The 450×450 warp-space solution Mat, generated once per solve.
    @Volatile private var solutionMat:   Mat?         = null
    @Volatile private var lastGiven:     IntArray?     = null
    @Volatile private var lastSolution:  IntArray?     = null
    @Volatile private var lastDetection: DetectionResult? = null

    // Single-thread executor — prevents concurrent warpPerspective on same Mat.
    private val arExecutor   = Executors.newSingleThreadExecutor()
    private val arDispatcher = arExecutor.asCoroutineDispatcher()
    private var arUpdateJob: Job? = null

    // Raw camera bitmap for gallery save composite (set each frame in Solved state).
    @Volatile var latestCameraFrameBitmap: Bitmap? = null

    // Read directly by analyzeFrame — avoids Compose state capture in AndroidView factory.
    @Volatile var isFrozen: Boolean = false

    // Torch (flashlight) toggle
    private val _torchOn = MutableStateFlow(false)
    val torchOn = _torchOn.asStateFlow()
    private var cameraControl: androidx.camera.core.CameraControl? = null

    fun onCameraReady(camera: androidx.camera.core.Camera) {
        cameraControl = camera.cameraControl
        // Apply current torch state immediately (handles rebind)
        camera.cameraControl.enableTorch(_torchOn.value)

        // Observe CameraInfo.torchState — CameraX or AE can silently reset the
        // torch to OFF. If our intent is ON, re-enable it automatically.
        camera.cameraInfo.torchState.observeForever { state ->
            val torchState = state ?: return@observeForever
            val isOff = torchState == androidx.camera.core.TorchState.OFF
            if (isOff && _torchOn.value) {
                camera.cameraControl.enableTorch(true)
            }
        }
    }

    fun toggleTorch() {
        val next = !_torchOn.value
        _torchOn.value = next
        cameraControl?.enableTorch(next)
    }

    init {
        // Regenerate solutionMat whenever settings change while puzzle is solved.
        viewModelScope.launch {
            settings.collect { newSettings ->
                val given     = lastGiven
                val solution  = lastSolution
                val detection = lastDetection
                if (given != null && solution != null &&
                    _state.value is SolverState.Solved) {

                    Log.d(TAG, "Settings changed while Solved — regenerating solutionMat")
                    withContext(arDispatcher) {
                        try {
                            solutionMat?.release()
                            val newMat = AROverlayGenerator.generateSolutionMat(given, solution, newSettings)
                            solutionMat = newMat
                            _arBitmap.value = detection?.let {
                                AROverlayGenerator.projectOntoFrame(newMat, it)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Settings regen failed", e)
                        }
                    }
                }
            }
        }
    }

    // ── Full solve (Scanning → Processing → Solved) ───────────────────────────

    fun processDetection(detection: DetectionResult, isGallery: Boolean = false) {
        if (_state.value is SolverState.Solved) return

        viewModelScope.launch {
            _state.value = SolverState.Processing

            val currentSettings = settings.value
            Log.d(TAG, "processDetection: given=${currentSettings.givenDisplay}")

            val (given, solved, elapsed) = withContext(defaultDispatcher) {
                val g       = ocrModel.predictAll(detection.cells)
                val startMs = System.currentTimeMillis()
                val s       = SudokuJni.solve(g)
                Triple(g, s, System.currentTimeMillis() - startMs)
            }

            if (solved == null) {
                _state.value = SolverState.Error("Could not solve — try repositioning")
                return@launch
            }

            lastGiven     = given
            lastSolution  = solved
            lastDetection = detection

            val firstBitmap = buildArBitmap(given, solved, detection, currentSettings)
            _arBitmap.value = firstBitmap

            maybeSaveToGallery(currentSettings, firstBitmap)

            withContext(ioDispatcher) {
                try {
                    db.historyDao().insert(
                        SolveRecord(
                            inputGrid   = given.toGridString(),
                            solvedGrid  = solved.toGridString(),
                            imagePath   = null,
                            solveTimeMs = elapsed
                        )
                    )
                    Log.d(TAG, "history: saved given=${given.toGridString().take(20)}...")
                } catch (e: Exception) {
                    Log.e(TAG, "history: insert failed", e)
                }
            }

            _state.value = SolverState.Solved(given, solved, elapsed, isGallery = isGallery)

            // CameraX can silently reset the torch when the analysis pipeline
            // processes its first frame. Re-assert the user's chosen state after solve.
            if (_torchOn.value) cameraControl?.enableTorch(true)
        }
    }

    /** Generates [solutionMat] and renders the first AR bitmap on [arDispatcher]. */
    private suspend fun buildArBitmap(
        given:     IntArray,
        solution:  IntArray,
        detection: DetectionResult,
        settings:  AppSettings
    ): Bitmap? = withContext(arDispatcher) {
        try {
            solutionMat?.release()
            val mat = AROverlayGenerator.generateSolutionMat(given, solution, settings)
            solutionMat = mat
            Log.d(TAG, "solutionMat generated: ${mat.size()}")
            AROverlayGenerator.projectOntoFrame(mat, detection)
        } catch (e: Exception) {
            Log.e(TAG, "AR generation failed", e)
            null
        }
    }

    /** Saves to the gallery according to [AppSettings.saveStyle], if gallery save is enabled. */
    private suspend fun maybeSaveToGallery(settings: AppSettings, arBitmap: Bitmap?) {
        if (!settings.saveToGallery) return
        withContext(ioDispatcher) {
            when (settings.saveStyle) {
                SaveStyle.CLEAN_FLAT -> {
                    val s = lastSolution ?: run { Log.w(TAG, "saveToGallery: no solution"); return@withContext }
                    val flatMat = AROverlayGenerator.generateFlatMat(s)
                    saveToGallery(AROverlayGenerator.flatToBitmap(flatMat))
                    flatMat.release()
                }
                SaveStyle.AR_COMPOSITE -> {
                    if (arBitmap == null) return@withContext
                    val cameraFrame = latestCameraFrameBitmap ?: run {
                        Log.w(TAG, "saveToGallery: no camera frame available"); return@withContext
                    }
                    saveCompositeToGallery(cameraFrame, arBitmap)
                }
            }
        }
    }

    // ── AR tracking: re-project every camera frame (Solved + live camera only) ─

    fun updateArOverlay(detection: DetectionResult) {
        // Release cell Mats immediately — OCR won't run in Solved state
        detection.cells.forEach { it.release() }

        val mat = solutionMat
        if (mat == null || mat.empty()) {
            Log.w(TAG, "updateArOverlay: solutionMat null/empty, skipping")
            return
        }
        val currentState = _state.value as? SolverState.Solved ?: return

        // Don't loop for gallery images — bitmap is already final
        if (currentState.isGallery) return

        lastDetection = detection
        _boardVisible.value = true

        arUpdateJob?.cancel()
        arUpdateJob = viewModelScope.launch {
            val newBitmap = withContext(arDispatcher) {
                try {
                    AROverlayGenerator.projectOntoFrame(mat, detection)
                } catch (e: Exception) {
                    Log.e(TAG, "updateArOverlay: projectOntoFrame failed", e); null
                }
            }
            if (newBitmap != null) {
                _arBitmap.value = newBitmap
            }
        }
    }

    // ── Gallery image ─────────────────────────────────────────────────────────

    fun processGalleryImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(ioDispatcher) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val src = android.graphics.ImageDecoder.createSource(
                            getApplication<Application>().contentResolver, uri
                        )
                        android.graphics.ImageDecoder.decodeBitmap(src) { dec, _, _ ->
                            dec.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(
                            getApplication<Application>().contentResolver, uri
                        )
                    }
                }

                _state.value = SolverState.Processing

                val result = withContext(defaultDispatcher) {
                    val rgba = Mat()
                    Utils.bitmapToMat(bitmap, rgba)          // bitmapToMat always gives RGBA
                    val bgr = Mat()
                    Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
                    rgba.release()
                    SudokuDetector.detect(bgr, settings.value.cvParams).also { bgr.release() }
                }

                if (result == null) {
                    _state.value = SolverState.Error("No sudoku grid found in image")
                    return@launch
                }

                // Show the gallery image as the static background
                _galleryBitmap.value = bitmap

                // isGallery = true → no updateArOverlay loop
                processDetection(result, isGallery = true)
            } catch (e: Exception) {
                _state.value = SolverState.Error("Failed to load image: ${e.message}")
            }
        }
    }

    // ── Gallery save ──────────────────────────────────────────────────────────

    /** Saves a single [bitmap] straight to the gallery (used for CLEAN_FLAT saves). */
    private fun saveToGallery(bitmap: Bitmap) {
        writeBitmapToGallery(bitmap)
    }

    /** Composites [arOverlay] onto [cameraFrame] and saves the result (used for AR_COMPOSITE saves). */
    private fun saveCompositeToGallery(cameraFrame: Bitmap, arOverlay: Bitmap) {
        val composite = createBitmap(cameraFrame.width, cameraFrame.height)
        val canvas = AndroidCanvas(composite)
        canvas.drawBitmap(cameraFrame, 0f, 0f, null)
        // Scale overlay to match camera frame if sizes differ (e.g. 450×450 vs 480×640)
        if (arOverlay.width == cameraFrame.width && arOverlay.height == cameraFrame.height) {
            canvas.drawBitmap(arOverlay, 0f, 0f, null)
        } else {
            canvas.drawBitmap(
                arOverlay, null,
                android.graphics.RectF(
                    0f, 0f, cameraFrame.width.toFloat(), cameraFrame.height.toFloat()
                ),
                null
            )
        }
        writeBitmapToGallery(composite)
    }

    /** Shared MediaStore write — used by both save paths. */
    private fun writeBitmapToGallery(bitmap: Bitmap) {
        try {
            val filename = "sudoku_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/SudokuOCR")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = getApplication<Application>().contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: run { Log.e(TAG, "writeBitmapToGallery: insert returned null"); return }

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Log.d(TAG, "writeBitmapToGallery: saved $filename")
            _gallerySavedEvent.tryEmit(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "writeBitmapToGallery: failed", e)
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun reset() {
        arUpdateJob?.cancel()
        arUpdateJob         = null
        solutionMat?.release()
        solutionMat         = null
        lastGiven           = null
        lastSolution        = null
        lastDetection       = null
        latestCameraFrameBitmap = null
        isFrozen            = false
        _arBitmap.value      = null
        _galleryBitmap.value = null
        _boardVisible.value   = false
        _cameraFrozen.value   = false
        isFrozen              = false
        _frozenFrame.value    = null
        _state.value          = SolverState.Scanning
    }

    override fun onCleared() {
        super.onCleared()
        arUpdateJob?.cancel()
        solutionMat?.release()
        solutionMat = null
        arDispatcher.close()
        arExecutor.shutdown()
    }
}