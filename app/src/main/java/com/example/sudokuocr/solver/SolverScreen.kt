package com.example.sudokuocr.solver

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sudokuocr.cv.SudokuDetector
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Executors
import androidx.core.graphics.createBitmap

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun SolverScreen(vm: SolverViewModel = viewModel(factory = SolverViewModel.Factory)) {
    val context  = LocalContext.current
    val state    by vm.state.collectAsState()
    val arBitmap by vm.arBitmap.collectAsState()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCameraPermission = it }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.processGalleryImage(it) } }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!hasCameraPermission) {
            PermissionDeniedContent(
                modifier  = Modifier.align(Alignment.Center),
                onRequest = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        } else {
            SolverReadyContent(
                vm            = vm,
                state         = state,
                arBitmap      = arBitmap,
                onGalleryPick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        }
    }
}

// ── Ready content (camera permission granted) ─────────────────────────────────
//
// Extracts the permission-granted branch into its own composable so that all
// gesture state and downstream if-checks sit at nesting depth 0 rather than
// depth 2 (inside Box > else). This is what reduces SolverScreen's complexity.

@Composable
private fun BoxScope.SolverReadyContent(
    vm:            SolverViewModel,
    state:         SolverState,
    arBitmap:      Bitmap?,
    onGalleryPick: () -> Unit
) {
    val cameraFrozen   by vm.cameraFrozen.collectAsState()
    val torchOn        by vm.torchOn.collectAsState()
    val frozenFrame    by vm.frozenFrame.collectAsState()
    val galleryBitmap  by vm.galleryBitmap.collectAsState()
    val boardVisible   by vm.boardVisible.collectAsState()

    // Transient press-to-hide: overlay visible while finger is lifted, hidden while held down
    var overlayHeld by remember { mutableStateOf(false) }
    val overlayVisible = !overlayHeld

    // Transient gallery-saved badge: shows for 2 seconds after a successful save
    var showSavedBadge by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.gallerySavedEvent.collect {
            showSavedBadge = true
            kotlinx.coroutines.delay(2_000)
            showSavedBadge = false
        }
    }

    // Push gallery image into frozenFrame via ViewModel when it arrives
    LaunchedEffect(galleryBitmap) { vm.applyGalleryFrame(galleryBitmap) }

    CameraContent(
        vm           = vm,
        state        = state,
        overlay      = OverlayState(arBitmap, overlayVisible, frozenFrame, boardVisible),
        onHoldStart  = { if (state is SolverState.Solved) overlayHeld = true },
        onHoldEnd    = { overlayHeld = false },
        onDoubleTap  = { vm.toggleFreeze(state) }
    )
    SolverOverlays(state = state, modifier = Modifier.fillMaxSize())
    SolverActionButtons(
        state         = state,
        onGalleryPick = onGalleryPick,
        onReset       = { vm.reset() },
        modifier      = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
    )
    SolverBadges(
        state          = state,
        cameraFrozen   = cameraFrozen,
        showSavedBadge = showSavedBadge
    )
    TorchButton(
        torchOn  = torchOn,
        onClick  = { vm.toggleTorch() },
        modifier = Modifier
            .align(Alignment.TopEnd)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .padding(end = 12.dp, top = 12.dp)
    )
}

// ── Solver badges ────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.SolverBadges(
    state:          SolverState,
    cameraFrozen:   Boolean,
    showSavedBadge: Boolean
) {
    val solved = state as? SolverState.Solved

    // Freeze badge — bottom-start, above action buttons
    if (solved != null && cameraFrozen && !solved.isGallery) {
        GestureBadge(
            text     = "Frozen  •  double-tap to resume",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 96.dp)
        )
    }

    // Gallery-saved badge — center screen, auto-dismisses after 2 s
    if (showSavedBadge) {
        GestureBadge(
            text     = "✓  Saved to gallery",
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// ── Permission denied ─────────────────────────────────────────────────────────

@Composable
private fun PermissionDeniedContent(modifier: Modifier, onRequest: () -> Unit) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Camera permission required", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRequest) { Text("Grant Permission") }
    }
}

// ── Overlay state (parameter object for CameraContent) ───────────────────────

/**
 * Groups the three overlay-display booleans so [CameraContent] stays within the
 * 7-parameter limit while keeping all related values together.
 */
private data class OverlayState(
    val arBitmap:       Bitmap?,
    val overlayVisible: Boolean,
    val frozenFrame:    Bitmap?,  // non-null while camera is frozen; covers live preview
    val boardVisible:   Boolean   // false when board leaves frame; hides overlay without reset
)

// ── Camera preview + AR overlay ───────────────────────────────────────────────

@Composable
private fun CameraContent(
    vm:           SolverViewModel,
    state:        SolverState,
    overlay:      OverlayState,
    onHoldStart:  () -> Unit,
    onHoldEnd:    () -> Unit,
    onDoubleTap:  () -> Unit
) {
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    val gestureModifier = Modifier
        .holdGesture(onHoldStart, onHoldEnd)
        .pointerInput(state) { detectTapGestures(onDoubleTap = { onDoubleTap() }) }

    CameraPreview(vm = vm, analysisExecutor = analysisExecutor)

    if (state is SolverState.Scanning) ScannerOverlay()

    overlay.frozenFrame?.let { frozen ->
        val fb = remember(frozen) { frozen.asImageBitmap() }
        androidx.compose.foundation.Image(
            bitmap             = fb,
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )
    }

    Box(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
        val solved = state as? SolverState.Solved
        val showOverlay = solved != null
                && overlay.overlayVisible
                && overlay.arBitmap != null
                && (overlay.boardVisible || solved.isGallery)
        if (showOverlay) ArOverlayImage(bitmap = overlay.arBitmap)
        if (solved != null) SolveTimeBadge(solveTimeMs = solved.solveTimeMs)
    }
}

/**
 * Fires [onStart] on the first press-down and [onEnd] when all fingers lift.
 * Runs independently of tap recognition so both can coexist on the same surface.
 */
private fun Modifier.holdGesture(onStart: () -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitPointerEvent()
                if (down.changes.any { it.pressed }) {
                    onStart()
                    do { val up = awaitPointerEvent() } while (up.changes.any { it.pressed })
                    onEnd()
                }
            }
        }
    }

/** Binds the camera lifecycle and wires the image analyser. */
@Composable
private fun CameraPreview(vm: SolverViewModel, analysisExecutor: java.util.concurrent.ExecutorService) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val future      = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                bindCamera(future.get(), lifecycleOwner, previewView, vm, analysisExecutor)
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun bindCamera(
    provider:        ProcessCameraProvider,
    lifecycleOwner:  androidx.lifecycle.LifecycleOwner,
    previewView:     PreviewView,
    vm:              SolverViewModel,
    executor:        java.util.concurrent.ExecutorService
) {
    val preview  = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
    val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .build().also { it.setAnalyzer(executor) { proxy -> analyzeFrame(proxy, vm) } }
    try {
        provider.unbindAll()
        val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        vm.onCameraReady(camera)
    } catch (e: Exception) { e.printStackTrace() }
}

@Composable
private fun BoxScope.ArOverlayImage(bitmap: Bitmap) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    androidx.compose.foundation.Image(
        bitmap             = imageBitmap,
        contentDescription = null,
        modifier           = Modifier.fillMaxSize(),
        contentScale       = ContentScale.Crop
    )
}

// ── State overlays ────────────────────────────────────────────────────────────

@Composable
private fun SolverOverlays(state: SolverState, modifier: Modifier) {
    if (state is SolverState.Processing) ProcessingOverlay(modifier)
    if (state is SolverState.Error) ErrorBanner(message = state.message)
}

@Composable
private fun ProcessingOverlay(modifier: Modifier) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape    = RoundedCornerShape(8.dp),
            color    = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text     = message,
                modifier = Modifier.padding(12.dp),
                color    = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun SolveTimeBadge(solveTimeMs: Long) {
    Box(
        modifier = Modifier.fillMaxSize().padding(top = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.55f)
        ) {
            Text(
                text     = "Solved in ${solveTimeMs}ms",
                color    = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun GestureBadge(text: String, modifier: Modifier) {
    Surface(
        shape    = RoundedCornerShape(20.dp),
        color    = Color.Black.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Text(
            text     = text,
            color    = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}


// ── Torch button ──────────────────────────────────────────────────────────────

@Composable
private fun TorchButton(torchOn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val icon = if (torchOn) Icons.Filled.FlashOff else Icons.Filled.FlashOn
    Surface(
        onClick   = onClick,
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        color     = if (torchOn)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        else
            Color.Black.copy(alpha = 0.55f),
        tonalElevation  = 4.dp,
        shadowElevation = 4.dp
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = if (torchOn) "Turn off flashlight" else "Turn on flashlight",
            tint               = Color.White,
            modifier           = Modifier.padding(10.dp).size(24.dp)
        )
    }
}

// ── Action buttons ────────────────────────────────────────────────────────────

@Composable
private fun SolverActionButtons(
    state:         SolverState,
    onGalleryPick: () -> Unit,
    onReset:       () -> Unit,
    modifier:      Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (state is SolverState.Scanning || state is SolverState.Error) {
            FloatingActionButton(
                onClick        = onGalleryPick,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Filled.Image, contentDescription = "Pick from gallery")
            }
        }
        if (state is SolverState.Solved || state is SolverState.Error) {
            FloatingActionButton(
                onClick        = onReset,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Scan again")
            }
        }
    }
}

// ── Scanner guide overlay ─────────────────────────────────────────────────────

@Composable
private fun ScannerOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = size.minDimension * 0.80f
        val left = (size.width  - side) / 2f
        val top  = (size.height - side) / 2f

        drawRect(color = Color.Black.copy(alpha = 0.45f))
        drawRect(
            color     = Color.Transparent,
            topLeft   = Offset(left, top),
            size      = Size(side, side),
            blendMode = BlendMode.Clear
        )
        drawRect(
            color   = Color.White.copy(alpha = 0.8f),
            topLeft = Offset(left, top),
            size    = Size(side, side),
            style   = Stroke(width = 2.dp.toPx())
        )

        val corner = 24.dp.toPx()
        val w = 3.dp.toPx()
        val c = Color.White
        drawLine(c, Offset(left,             top + corner    ), Offset(left,          top             ), w)
        drawLine(c, Offset(left,             top             ), Offset(left + corner,  top             ), w)
        drawLine(c, Offset(left+side-corner, top             ), Offset(left + side,   top             ), w)
        drawLine(c, Offset(left + side,      top             ), Offset(left + side,   top + corner    ), w)
        drawLine(c, Offset(left,             top+side-corner ), Offset(left,          top + side      ), w)
        drawLine(c, Offset(left,             top + side      ), Offset(left + corner,  top + side      ), w)
        drawLine(c, Offset(left+side-corner, top+side        ), Offset(left + side,   top + side      ), w)
        drawLine(c, Offset(left + side,      top + side      ), Offset(left + side,   top+side-corner ), w)
    }
}

// ── Image analysis ────────────────────────────────────────────────────────────

private fun analyzeFrame(imageProxy: ImageProxy, vm: SolverViewModel) {
    val currentState = vm.state.value
    if (currentState is SolverState.Processing || currentState is SolverState.Error) {
        imageProxy.close(); return
    }
    // Read directly from ViewModel — Compose state is captured at factory time and never updates.
    if (vm.isFrozen && currentState is SolverState.Solved) {
        imageProxy.close(); return
    }

    try {
        val w       = imageProxy.width
        val h       = imageProxy.height
        val nv21    = buildNv21(imageProxy, w, h)
        val yuv     = Mat(h + h / 2, w, CvType.CV_8UC1).also { it.put(0, 0, nv21) }
        val bgr     = Mat()
        val rotated = Mat()

        Imgproc.cvtColor(yuv, bgr, Imgproc.COLOR_YUV2BGR_NV21)
        if (bgr.empty()) { yuv.release(); bgr.release(); return }
        org.opencv.core.Core.rotate(bgr, rotated, org.opencv.core.Core.ROTATE_90_CLOCKWISE)

        maybeCaptureFrameBitmap(rotated, vm, currentState)

        val detected = SudokuDetector.detect(rotated, vm.settings.value.cvParams)
        if (detected != null) {
            when (vm.state.value) {
                is SolverState.Solved   -> vm.updateArOverlay(detected)
                is SolverState.Scanning -> vm.processDetection(detected)
                else                    -> Unit
            }
        } else if (vm.state.value is SolverState.Solved) {
            // Board left frame — hide overlay but keep solve in memory
            vm.clearBoardVisible()
        }

        yuv.release(); bgr.release(); rotated.release()
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        imageProxy.close()
    }
}

/** Assembles a correctly-strided NV21 byte array from the YUV_420_888 planes. */
private fun buildNv21(imageProxy: ImageProxy, w: Int, h: Int): ByteArray {
    val yPlane      = imageProxy.planes[0]
    val uPlane      = imageProxy.planes[1]
    val vPlane      = imageProxy.planes[2]
    val yRowStride  = yPlane.rowStride
    val uvRowStride = uPlane.rowStride
    val uvPixStride = uPlane.pixelStride
    val nv21        = ByteArray(w * h * 3 / 2)
    val yBuf        = yPlane.buffer

    for (row in 0 until h) {
        for (col in 0 until w) {
            nv21[row * w + col] = yBuf[row * yRowStride + col]
        }
    }

    val uBuf = uPlane.buffer
    val vBuf = vPlane.buffer
    var uvOffset = w * h
    for (row in 0 until h / 2) {
        for (col in 0 until w / 2) {
            val pos = row * uvRowStride + col * uvPixStride
            vBuf.position(pos); nv21[uvOffset++] = vBuf.get()
            uBuf.position(pos); nv21[uvOffset++] = uBuf.get()
        }
    }
    return nv21
}

/** Captures the current rotated BGR frame as a bitmap.
 *  Always runs in Scanning/Solved so the freeze snapshot is always available.
 *  The heavier RGBA conversion is only done when saveToGallery is enabled,
 *  since plain freeze just needs the bitmap reference. */
private fun maybeCaptureFrameBitmap(rotated: Mat, vm: SolverViewModel, state: SolverState) {
    if (state !is SolverState.Solved && state !is SolverState.Scanning) return
    try {
        val bmp     = createBitmap(rotated.cols(), rotated.rows())
        val rgbaMat = Mat()
        Imgproc.cvtColor(rotated, rgbaMat, Imgproc.COLOR_BGR2RGBA)
        Utils.matToBitmap(rgbaMat, bmp)
        rgbaMat.release()
        vm.latestCameraFrameBitmap = bmp
    } catch (_: Exception) {
        // Non-fatal — freeze and gallery save will log a warning if no frame is available
    }
}