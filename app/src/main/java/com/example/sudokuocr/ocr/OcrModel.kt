package com.example.sudokuocr.ocr

import android.content.Context
import android.util.Log
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File

/**
 * Wraps sudoku_ocr.ptl — place the file in app/src/main/assets/
 *
 * Preprocessing matches train.ipynb exactly:
 *   Grayscale → Resize 28×28 → float32/255 → Normalize(mean=0.1307, std=0.3081)
 *
 * Output: 10 logits → argmax → digit 0–9
 *   Class 0 = empty cell  →  returns 0 to the solver
 *   Classes 1–9 = digits  →  returned as-is
 *
 * If your data/custom/ folders were named "1"–"9" (no "0" folder),
 * set CLASS_INDEX_SHIFTED = true — run test_ptl.py to confirm which to use.
 */
class OcrModel(context: Context) {

    companion object {
        private const val TAG        = "OcrModel"
        private const val ASSET_NAME = "sudoku_ocr.ptl"
        private const val INPUT_SIZE = 28

        // MNIST normalization — must match transforms.Normalize((0.1307,), (0.3081,))
        private const val NORM_MEAN = 0.1307f
        private const val NORM_STD  = 0.3081f

        // Set to true if your custom dataset had folders named "1"–"9" with no "0" folder.
        // Run test_ptl.py — it will tell you the correct value here.
        private const val CLASS_INDEX_SHIFTED = false
    }

    private val module: Module = LiteModuleLoader.load(assetFilePath(context))

    /**
     * Predict digit in a single cell Mat.
     * @param cellMat Grayscale or BGR Mat of one sudoku cell (any size).
     * @return Predicted digit 1–9, or 0 if empty.
     */
    @Suppress("unused")
    fun predict(cellMat: Mat): Int {
        val input = Tensor.fromBlob(
            preprocessCell(cellMat),
            longArrayOf(1L, 1L, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        )
        return try {
            val logits = module.forward(IValue.from(input)).toTensor().dataAsFloatArray
            mapClassIndex(logits.indices.maxByOrNull { logits[it] } ?: 0)
        } catch (e: Exception) {
            Log.e(TAG, "predict failed: ${e.message}")
            0
        }
    }

    /**
     * Batch-predict all 81 cells in one forward pass — much faster than 81 separate calls.
     * @param cells 81 cell Mats ordered row-major (top-left → bottom-right).
     * @return IntArray(81) of predicted digits, 0 = empty.
     */
    fun predictAll(cells: List<Mat>): IntArray {
        require(cells.size == 81) { "Expected 81 cells, got ${cells.size}" }

        val batchData = FloatArray(81 * INPUT_SIZE * INPUT_SIZE)
        cells.forEachIndexed { i, mat ->
            preprocessCell(mat).copyInto(batchData, i * INPUT_SIZE * INPUT_SIZE)
        }

        val input = Tensor.fromBlob(
            batchData,
            longArrayOf(81L, 1L, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        )

        return try {
            val logits = module.forward(IValue.from(input)).toTensor().dataAsFloatArray
            IntArray(81) { i ->
                val offset = i * 10
                mapClassIndex((0 until 10).maxByOrNull { logits[offset + it] } ?: 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "predictAll failed: ${e.message}")
            IntArray(81) { 0 }
        }
    }

    // ── Preprocessing ──────────────────────────────────────────────────────────

    private fun preprocessCell(cellMat: Mat): FloatArray {
        // 1. Ensure grayscale
        val gray = Mat()
        when (cellMat.channels()) {
            1    -> cellMat.copyTo(gray)
            3    -> Imgproc.cvtColor(cellMat, gray, Imgproc.COLOR_BGR2GRAY)
            4    -> Imgproc.cvtColor(cellMat, gray, Imgproc.COLOR_BGRA2GRAY)
            else -> cellMat.copyTo(gray)
        }

        // 2. Resize to 28×28
        val resized = Mat()
        Imgproc.resize(gray, resized, Size(INPUT_SIZE.toDouble(), INPUT_SIZE.toDouble()))

        // 3. Convert to float32 in [0, 1]
        val float32 = Mat()
        resized.convertTo(float32, CvType.CV_32F, 1.0 / 255.0)

        // 4. Read pixels into FloatArray
        val pixels = FloatArray(INPUT_SIZE * INPUT_SIZE)
        float32[0, 0, pixels]

        // 5. Normalize: (x − mean) / std  — matches transforms.Normalize((0.1307,),(0.3081,))
        for (i in pixels.indices) {
            pixels[i] = (pixels[i] - NORM_MEAN) / NORM_STD
        }

        gray.release(); resized.release(); float32.release()
        return pixels
    }

    private fun mapClassIndex(idx: Int): Int =
        if (CLASS_INDEX_SHIFTED) {
            if (idx in 0..8) idx + 1 else 0
        } else {
            idx
        }

    // ── Asset helper ───────────────────────────────────────────────────────────

    private fun assetFilePath(context: Context): String {
        val file = File(context.filesDir, ASSET_NAME)
        if (!file.exists()) {
            context.assets.open(ASSET_NAME).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            Log.d(TAG, "Copied $ASSET_NAME → ${file.absolutePath}")
        }
        return file.absolutePath
    }
}