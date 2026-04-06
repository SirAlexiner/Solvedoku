package com.example.sudokuocr.dev

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sudokuocr.cv.CvStage
import com.example.sudokuocr.data.settings.CvParams
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@Composable
fun DevScreen(vm: DevViewModel = viewModel()) {
    val settings    by vm.settings.collectAsState()
    val stage       by vm.stage.collectAsState()
    val debugBitmap by vm.debugBitmap.collectAsState()
    val params       = settings.cvParams
    val stages       = CvStage.entries
    val stageIndex   = stages.indexOf(stage)

    // Full-screen box — camera is the base, everything overlaid on top
    Box(Modifier.fillMaxSize()) {

        // ── Base layer: live camera preview ───────────────────────────────
        CameraPreviewView(vm = vm)

        // ── Processed debug bitmap (covers preview for non-RAW stages) ────
        if (stage != CvStage.RAW) {
            debugBitmap?.let { bmp ->
                val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                Image(
                    bitmap             = imageBitmap,
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Fit
                )
            }
        }

        // ── Stage label badge — top start ─────────────────────────────────
        Surface(
            color    = Color.Black.copy(alpha = 0.65f),
            shape    = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            Text(
                text     = stage.label,
                color    = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // ── Controls panel — overlaid at the bottom ───────────────────────
        Surface(
            color           = Color.Black.copy(alpha = 0.72f),
            shape           = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier        = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.48f)  // takes up ~bottom half
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {

                // Stage slider
                DevSectionLabel("Pipeline stage")
                Slider(
                    value         = stageIndex.toFloat(),
                    onValueChange = { vm.setStage(stages[it.roundToInt()]) },
                    valueRange    = 0f..(stages.size - 1).toFloat(),
                    steps         = stages.size - 2,
                    colors        = SliderDefaults.colors(
                        thumbColor       = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text  = stages.mapIndexed { i, s ->
                        if (i == stageIndex) "[${s.label}]" else s.label
                    }.joinToString("  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(Modifier.height(4.dp))

                DevSectionLabel("Canny thresholds")
                DevSlider(
                    label    = "Low: ${params.cannyLow.roundToInt()}",
                    value    = params.cannyLow.toFloat(),
                    range    = 1f..150f,
                    onChange = { vm.updateCvParams(params.copy(cannyLow = it.toDouble())) }
                )
                DevSlider(
                    label    = "High: ${params.cannyHigh.roundToInt()}",
                    value    = params.cannyHigh.toFloat(),
                    range    = 10f..300f,
                    onChange = { vm.updateCvParams(params.copy(cannyHigh = it.toDouble())) }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                DevSectionLabel("Contour detection")
                DevSlider(
                    label    = "Min area: ${"%.3f".format(params.minAreaRatio)}",
                    value    = params.minAreaRatio.toFloat(),
                    range    = 0.01f..0.3f,
                    onChange = { vm.updateCvParams(params.copy(minAreaRatio = it.toDouble())) }
                )
                DevSlider(
                    label    = "Poly ε factor: ${"%.3f".format(params.polyEpsilonFactor)}",
                    value    = params.polyEpsilonFactor.toFloat(),
                    range    = 0.005f..0.08f,
                    onChange = { vm.updateCvParams(params.copy(polyEpsilonFactor = it.toDouble())) }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                DevSectionLabel("Hough line validation")
                DevSlider(
                    label    = "Accumulator threshold: ${params.houghThreshold}",
                    value    = params.houghThreshold.toFloat(),
                    range    = 20f..200f,
                    onChange = { vm.updateCvParams(params.copy(houghThreshold = it.roundToInt())) }
                )
                DevSlider(
                    label    = "Min merged lines: ${params.minHoughLines}",
                    value    = params.minHoughLines.toFloat(),
                    range    = 2f..20f,
                    steps    = 17,
                    onChange = { vm.updateCvParams(params.copy(minHoughLines = it.roundToInt())) }
                )

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick  = { vm.updateCvParams(CvParams()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Reset to defaults")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Camera preview — fills the full screen ────────────────────────────────────

@Composable
private fun CameraPreviewView(vm: DevViewModel) {
    val lifecycleOwner   = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview  = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build().also { ia ->
                        ia.setAnalyzer(analysisExecutor) { proxy ->
                            analyzeDevFrame(proxy, vm)
                        }
                    }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                    )
                } catch (e: Exception) { e.printStackTrace() }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ── Frame analysis ────────────────────────────────────────────────────────────

private fun analyzeDevFrame(imageProxy: ImageProxy, vm: DevViewModel) {
    try {
        val w = imageProxy.width; val h = imageProxy.height
        val yPlane     = imageProxy.planes[0]
        val uPlane     = imageProxy.planes[1]
        val vPlane     = imageProxy.planes[2]
        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixStride = uPlane.pixelStride
        val nv21       = ByteArray(w * h * 3 / 2)
        val yBuf       = yPlane.buffer

        for (row in 0 until h)
            for (col in 0 until w)
                nv21[row * w + col] = yBuf[row * yRowStride + col]

        val uBuf = uPlane.buffer; val vBuf = vPlane.buffer; var uvOffset = w * h
        for (row in 0 until h / 2) for (col in 0 until w / 2) {
            val pos = row * uvRowStride + col * uvPixStride
            vBuf.position(pos); nv21[uvOffset++] = vBuf.get()
            uBuf.position(pos); nv21[uvOffset++] = uBuf.get()
        }

        val yuv     = Mat(h + h / 2, w, CvType.CV_8UC1).also { it.put(0, 0, nv21) }
        val bgr     = Mat()
        val rotated = Mat()
        Imgproc.cvtColor(yuv, bgr, Imgproc.COLOR_YUV2BGR_NV21)
        if (!bgr.empty()) {
            org.opencv.core.Core.rotate(bgr, rotated, org.opencv.core.Core.ROTATE_90_CLOCKWISE)
            vm.processFrame(rotated)
        }
        yuv.release(); bgr.release(); rotated.release()
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        imageProxy.close()
    }
}

// ── Shared UI helpers ─────────────────────────────────────────────────────────

@Composable
private fun DevSectionLabel(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun DevSlider(
    label:    String,
    value:    Float,
    range:    ClosedFloatingPointRange<Float>,
    steps:    Int    = 0,
    onChange: (Float) -> Unit
) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White)
    Slider(
        value         = value,
        onValueChange = onChange,
        valueRange    = range,
        steps         = steps,
        colors        = SliderDefaults.colors(
            thumbColor       = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary
        )
    )
}