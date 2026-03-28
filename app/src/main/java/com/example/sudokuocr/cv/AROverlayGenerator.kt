package com.example.sudokuocr.cv

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import com.example.sudokuocr.data.settings.AppSettings
import com.example.sudokuocr.data.settings.GivenDisplay
import androidx.core.graphics.createBitmap

/**
 * Two-phase AR overlay:
 *
 * Phase 1 — generateSolutionMat() [once after solving]:
 *   Draws grid + digits onto a transparent 450×450 BGRA Mat in warp space,
 *   using the user's chosen colors from settings.
 *
 * Phase 2 — projectOntoFrame() [every camera frame]:
 *   Inverse-warps the stored Mat using fresh corners for AR tracking.
 *
 * flatToBitmap() — produces a clean black-on-white printout of the solution grid,
 *   always using black digits on a white background regardless of color settings.
 *
 * Channel note: OpenCV draws BGRA. Utils.matToBitmap expects RGBA.
 *   => cvtColor(COLOR_BGRA2RGBA) before matToBitmap in all paths.
 */
object AROverlayGenerator {

    private const val TAG       = "AROverlay"
    private const val WARP_SIZE = 450.0
    private const val CELL_SIZE = (WARP_SIZE / 9).toInt()  // 50px per cell

    // ── Phase 1: generate once ────────────────────────────────────────────────

    fun generateSolutionMat(
        given:    IntArray,
        solution: IntArray,
        settings: AppSettings
    ): Mat {
        Log.d(TAG, "generateSolutionMat: given=${settings.givenDisplay}")
        return generatePerspectiveMat(given, solution, settings)
    }

    /** Transparent background BGRA mat — will be inverse-warped each frame. */
    private fun generatePerspectiveMat(
        given: IntArray, solution: IntArray, settings: AppSettings
    ): Mat {
        val mat = Mat(
            WARP_SIZE.toInt(), WARP_SIZE.toInt(),
            CvType.CV_8UC4,
            Scalar(0.0, 0.0, 0.0, 0.0)  // fully transparent
        )
        val solutionBgra = argbToBgra(settings.solutionColorArgb)
        drawGrid(mat, solutionBgra)
        drawDigits(mat, given, solution, settings, solutionBgra)
        return mat
    }

    /**
     * Clean black-on-white solution grid — always black digits on white background,
     * regardless of color settings. Used for gallery saves and the flat save format.
     */
    fun generateFlatMat(solution: IntArray): Mat {
        val black = Scalar(0.0, 0.0, 0.0, 255.0)
        val mat = Mat(
            WARP_SIZE.toInt(), WARP_SIZE.toInt(),
            CvType.CV_8UC4,
            Scalar(255.0, 255.0, 255.0, 255.0)  // white opaque background
        )
        drawGrid(mat, black)
        drawFlatDigits(mat, solution, black)
        return mat
    }

    // ── Phase 2: project onto camera frame ───────────────────────────────────

    /**
     * Inverse-warps [solutionMat] using the fresh corners in [detection] and
     * returns a camera-frame-sized ARGB_8888 Bitmap ready to composite over the preview.
     */
    fun projectOntoFrame(solutionMat: Mat, detection: DetectionResult): Bitmap {
        check(!solutionMat.empty()) { "solutionMat is empty" }
        return projectPerspective(solutionMat, detection)
    }

    fun flatToBitmap(solutionMat: Mat): Bitmap {
        check(!solutionMat.empty()) { "solutionMat is empty for flatToBitmap" }
        val rgbaMat = Mat()
        Imgproc.cvtColor(solutionMat, rgbaMat, Imgproc.COLOR_BGRA2RGBA)
        val bitmap = createBitmap(WARP_SIZE.toInt(), WARP_SIZE.toInt())
        Utils.matToBitmap(rgbaMat, bitmap)
        rgbaMat.release()
        Log.v(TAG, "flatToBitmap: generated ${WARP_SIZE.toInt()}x${WARP_SIZE.toInt()}")
        return bitmap
    }

    private fun projectPerspective(solutionMat: Mat, detection: DetectionResult): Bitmap {
        val warpSize1 = WARP_SIZE - 1
        val srcPoints = MatOfPoint2f(
            Point(0.0,       0.0      ),
            Point(warpSize1, 0.0      ),
            Point(warpSize1, warpSize1),
            Point(0.0,       warpSize1)
        )

        val inverseTransform = Imgproc.getPerspectiveTransform(srcPoints, detection.corners)
        srcPoints.release()
        check(!inverseTransform.empty()) { "getPerspectiveTransform returned empty matrix" }

        val cameraW = detection.cameraFrameWidth
        val cameraH = detection.cameraFrameHeight
        val warpedMat = Mat(cameraH, cameraW, CvType.CV_8UC4, Scalar(0.0, 0.0, 0.0, 0.0))

        Imgproc.warpPerspective(
            solutionMat, warpedMat, inverseTransform,
            Size(cameraW.toDouble(), cameraH.toDouble()),
            Imgproc.INTER_LINEAR,
            Core.BORDER_TRANSPARENT
        )

        val rgbaMat = Mat()
        Imgproc.cvtColor(warpedMat, rgbaMat, Imgproc.COLOR_BGRA2RGBA)

        val bitmap = createBitmap(cameraW, cameraH)
        Utils.matToBitmap(rgbaMat, bitmap)

        warpedMat.release()
        rgbaMat.release()
        inverseTransform.release()

        Log.v(TAG, "projectOntoFrame: ${cameraW}x${cameraH}")
        return bitmap
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    private fun drawGrid(mat: Mat, color: Scalar) {
        for (i in 0..9) {
            val pos       = (i * CELL_SIZE).toDouble()
            val thickness = if (i % 3 == 0) 3 else 1
            // Horizontal line
            Imgproc.line(
                mat,
                Point(0.0,             pos),
                Point(WARP_SIZE,       pos),
                color, thickness
            )
            // Vertical line
            Imgproc.line(
                mat,
                Point(pos, 0.0),
                Point(pos, WARP_SIZE),
                color, thickness
            )
        }
    }

    private fun drawDigits(
        mat:           Mat,
        given:         IntArray,
        solution:      IntArray,
        settings:      AppSettings,
        solutionColor: Scalar
    ) {
        val fontFace  = Imgproc.FONT_HERSHEY_SIMPLEX
        val fontScale = 1.5
        val thickness = 3

        for (index in 0..80) {
            val digit   = solution[index]
            if (digit == 0) continue

            val isGiven = given[index] != 0
            if (isGiven && settings.givenDisplay == GivenDisplay.HIDE) continue

            val row = index / 9
            val col = index % 9
            val cx  = (col * CELL_SIZE + CELL_SIZE / 2 - 14).toDouble()
            val cy  = (row * CELL_SIZE + CELL_SIZE / 2 + 14).toDouble()

            val color = when {
                !isGiven                                  -> solutionColor
                settings.givenDisplay == GivenDisplay.SAME   -> solutionColor
                settings.givenDisplay == GivenDisplay.CUSTOM -> argbToBgra(settings.givenColorArgb)
                else                                      -> solutionColor  // HIDE already filtered above
            }

            Imgproc.putText(mat, digit.toString(), Point(cx, cy), fontFace, fontScale, color, thickness)
        }
    }

    /**
     * Draws all solution digits in [color] with no settings dependency.
     * Used by [generateFlatMat] where the color is always hardcoded black.
     */
    private fun drawFlatDigits(mat: Mat, solution: IntArray, color: Scalar) {
        val fontFace  = Imgproc.FONT_HERSHEY_SIMPLEX
        val fontScale = 1.5
        val thickness = 3

        for (index in 0..80) {
            val digit = solution[index]
            if (digit == 0) continue
            val row = index / 9
            val col = index % 9
            val cx  = (col * CELL_SIZE + CELL_SIZE / 2 - 14).toDouble()
            val cy  = (row * CELL_SIZE + CELL_SIZE / 2 + 14).toDouble()
            Imgproc.putText(mat, digit.toString(), Point(cx, cy), fontFace, fontScale, color, thickness)
        }
    }

    // ── Color helpers ─────────────────────────────────────────────────────────

    /**
     * Convert Android ARGB int to OpenCV BGRA Scalar.
     * Android ARGB: bits 31-24=A, 23-16=R, 15-8=G, 7-0=B
     * OpenCV Scalar for BGRA channels: (B, G, R, A)
     */
    private fun argbToBgra(argb: Int): Scalar {
        val a = ((argb shr 24) and 0xFF).toDouble()
        val r = ((argb shr 16) and 0xFF).toDouble()
        val g = ((argb shr  8) and 0xFF).toDouble()
        val b = ( argb         and 0xFF).toDouble()
        return Scalar(b, g, r, a)
    }
}