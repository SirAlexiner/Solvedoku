package com.example.sudokuocr.cv

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import com.example.sudokuocr.data.settings.CvParams
import kotlin.math.abs
import androidx.core.graphics.createBitmap

data class DetectionResult(
    val cells: List<Mat>,
    val warpedBitmap: Bitmap,
    val corners: MatOfPoint2f,
    val cameraFrameWidth: Int,
    val cameraFrameHeight: Int
)

/** Each stage the debug view can show. */
enum class CvStage(val label: String) {
    RAW("Raw"),
    GRAYSCALE("Grayscale"),
    EDGES("Canny Edges"),
    CONTOURS("Contours"),
    QUAD("Quad / Corners"),
    WARPED("Warped Board")
}

data class DebugResult(
    val stageBitmap: Bitmap,          // visual for the requested stage
    val detection: DetectionResult? // null if no board found
)

object SudokuDetector {

    private const val WARP_SIZE = 450.0
    private val DEFAULT_PARAMS = CvParams()

    // ── Public API ────────────────────────────────────────────────────────────

    /** Production path — uses persistent params, no debug overhead. */
    fun detect(frame: Mat, params: CvParams = DEFAULT_PARAMS): DetectionResult? {
        if (frame.empty()) return null
        val (gray, edges) = preprocess(frame, params)
        val result = findAndWarp(frame, gray, edges, params)
        gray.release(); edges.release()
        return result
    }

    /**
     * Debug path — returns a [DebugResult] with a [Bitmap] of the requested [stage]
     * plus the [DetectionResult] if a board was found. Caller is responsible for nothing;
     * all intermediate Mats are released internally.
     */
    fun detectDebug(frame: Mat, params: CvParams, stage: CvStage): DebugResult {
        if (frame.empty()) return DebugResult(frame.toBitmap(), null)
        val gray = Mat()
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY)
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(blurred, edges, params.cannyLow, params.cannyHigh)
        blurred.release()

        // Find quads for contour/quad stages
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edges,
            contours,
            hierarchy,
            Imgproc.RETR_LIST,
            Imgproc.CHAIN_APPROX_TC89_KCOS
        )
        hierarchy.release()

        val frameArea = frame.rows().toDouble() * frame.cols().toDouble()
        val minArea = frameArea * params.minAreaRatio
        val quads = contours.mapNotNull { approxConvexQuad(it, minArea, params) }
        val sorted = quads.sortedByDescending { Imgproc.contourArea(it) }

        // Find best candidate
        val largest = sorted.firstOrNull()
        val bestInner =
            sorted.drop(1).filter { inner -> largest != null && isInsideQuad(largest, inner) }
                .maxByOrNull { Imgproc.contourArea(it) }
        val candidate = bestInner?.takeIf { isLikelySudoku(it, edges, params) }
            ?: largest?.takeIf { isLikelySudoku(it, edges, params) }

        return when (stage) {
            CvStage.RAW -> DebugResult(frame.toBitmap(), null).also { gray.release() }
            CvStage.GRAYSCALE -> DebugResult(
                gray.grayToBitmap(),
                null
            ).also { gray.release() }

            CvStage.EDGES -> DebugResult(
                edges.grayToBitmap(),
                null
            ).also { gray.release(); edges.release() }

            CvStage.CONTOURS -> {
                val vis = frame.clone()
                // Draw all contours grey, valid quads white
                Imgproc.drawContours(vis, contours, -1, Scalar(80.0, 80.0, 80.0), 1)
                val quadMops = quads.map { mof ->
                    MatOfPoint(*mof.toArray().map { Point(it.x, it.y) }.toTypedArray())
                }
                Imgproc.drawContours(vis, quadMops, -1, Scalar(255.0, 255.0, 255.0), 2)
                val bmp = vis.toBitmap()
                vis.release(); gray.release(); edges.release()
                DebugResult(bmp, null)
            }

            CvStage.QUAD -> {
                val vis = frame.clone()
                if (candidate != null) {
                    val ordered = sortCorners(candidate)
                    if (ordered != null) {
                        val pts = ordered.toArray()
                        // Draw the quad outline in green
                        for (i in pts.indices) {
                            Imgproc.line(vis, pts[i], pts[(i + 1) % 4], Scalar(0.0, 255.0, 0.0), 3)
                        }
                        // Draw corners as orange circles
                        pts.forEach { pt ->
                            Imgproc.circle(vis, pt, 10, Scalar(0.0, 140.0, 255.0), -1)
                        }
                    }
                }
                val bmp = vis.toBitmap()
                vis.release(); gray.release(); edges.release()
                DebugResult(bmp, null)
            }

            CvStage.WARPED -> {
                // WARPED stage — also produce the full detection result
                val detection =
                    if (candidate != null) warpCandidate(frame, gray, candidate) else null

                val stageBmp = detection?.warpedBitmap  // already a bitmap
                    ?: frame.toBitmap()        // fallback: raw frame

                gray.release(); edges.release()
                DebugResult(stageBmp, detection)
            }
        }
    }

    // ── Shared pipeline ───────────────────────────────────────────────────────

    private fun preprocess(frame: Mat, params: CvParams): Pair<Mat, Mat> {
        val gray = Mat()
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY)
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(blurred, edges, params.cannyLow, params.cannyHigh)
        blurred.release()
        return Pair(gray, edges)
    }

    private fun findAndWarp(frame: Mat, gray: Mat, edges: Mat, params: CvParams): DetectionResult? {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edges,
            contours,
            hierarchy,
            Imgproc.RETR_LIST,
            Imgproc.CHAIN_APPROX_TC89_KCOS
        )
        hierarchy.release()

        val frameArea = frame.rows().toDouble() * frame.cols().toDouble()
        val quads =
            contours.mapNotNull { approxConvexQuad(it, frameArea * params.minAreaRatio, params) }
        val sorted = quads.sortedByDescending { Imgproc.contourArea(it) }
        val largest = sorted.firstOrNull() ?: return null

        val bestInner = sorted.drop(1)
            .filter { isInsideQuad(largest, it) }
            .maxByOrNull { Imgproc.contourArea(it) }

        val candidate = bestInner?.takeIf { isLikelySudoku(it, edges, params) }
            ?: largest.takeIf { isLikelySudoku(it, edges, params) }
            ?: return null

        return warpCandidate(frame, gray, candidate)
    }

    private fun warpCandidate(frame: Mat, gray: Mat, candidate: MatOfPoint2f): DetectionResult? {
        val ordered = sortCorners(candidate) ?: return null

        val dst = MatOfPoint2f(
            Point(0.0, 0.0), Point(WARP_SIZE - 1, 0.0),
            Point(WARP_SIZE - 1, WARP_SIZE - 1), Point(0.0, WARP_SIZE - 1)
        )
        val transform = Imgproc.getPerspectiveTransform(ordered, dst)
        val warpedGray = Mat()
        Imgproc.warpPerspective(gray, warpedGray, transform, Size(WARP_SIZE, WARP_SIZE))
        val warpedColor = Mat()
        Imgproc.warpPerspective(frame, warpedColor, transform, Size(WARP_SIZE, WARP_SIZE))
        transform.release()

        val cellSize = (WARP_SIZE / 9.0).toInt()
        val inset = 4
        val cells = (0 until 9).flatMap { row ->
            (0 until 9).map { col ->
                val x = col * cellSize + inset
                val y = row * cellSize + inset
                warpedGray.submat(y, y + cellSize - inset * 2, x, x + cellSize - inset * 2).clone()
            }
        }

        val bmp = createBitmap(warpedColor.cols(), warpedColor.rows())
        Utils.matToBitmap(warpedColor, bmp)
        warpedGray.release(); warpedColor.release()

        return DetectionResult(cells, bmp, ordered, frame.cols(), frame.rows())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun approxConvexQuad(
        contour: MatOfPoint,
        minArea: Double,
        params: CvParams
    ): MatOfPoint2f? {
        if (Imgproc.contourArea(contour) < minArea) return null
        val c2f = MatOfPoint2f(*contour.toArray())
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(
            c2f,
            approx,
            params.polyEpsilonFactor * Imgproc.arcLength(c2f, true),
            true
        )
        if (approx.rows() != 4) return null
        val hull = MatOfInt()
        Imgproc.convexHull(MatOfPoint(*approx.toArray()), hull)
        if (hull.rows() != 4) return null
        return approx
    }

    private fun isInsideQuad(outer: MatOfPoint2f, inner: MatOfPoint2f): Boolean =
        inner.toArray().all { Imgproc.pointPolygonTest(outer, it, false) >= 0.0 }

    private fun isLikelySudoku(quad: MatOfPoint2f, edges: Mat, params: CvParams): Boolean {
        val sorted = sortCorners(quad) ?: return false
        val mask = Mat.zeros(edges.size(), CvType.CV_8U)
        Imgproc.fillConvexPoly(mask, MatOfPoint(*sorted.toArray()), Scalar(255.0))
        val masked = Mat()
        Core.bitwise_and(edges, edges, masked, mask)
        mask.release()

        val lines = Mat()
        Imgproc.HoughLines(masked, lines, 1.0, Math.PI / 180.0, params.houghThreshold)
        masked.release()
        if (lines.empty()) return false

        val angleTol = 0.261799f
        val hRhos = mutableListOf<Float>()
        val vRhos = mutableListOf<Float>()
        for (i in 0 until lines.rows()) {
            var rho = lines[i, 0][0].toFloat()
            var theta = lines[i, 0][1].toFloat()
            if (theta < 0) {
                theta += Math.PI.toFloat(); rho = -rho
            }
            when {
                abs(theta) < angleTol || abs(theta - Math.PI.toFloat()) < angleTol -> hRhos.add(rho)
                abs(theta - (Math.PI / 2).toFloat()) < angleTol -> vRhos.add(rho)
            }
        }
        lines.release()
        return mergeRhos(hRhos).size + mergeRhos(vRhos).size >= params.minHoughLines
    }

    private fun mergeRhos(rhos: List<Float>): List<Float> {
        if (rhos.isEmpty()) return emptyList()
        val s = rhos.sorted()
        val m = mutableListOf(s[0])
        for (r in s.drop(1)) {
            if (abs(r - m.last()) > 10f) m.add(r) else m[m.lastIndex] = (m.last() + r) / 2f
        }
        return m
    }

    private fun sortCorners(pts: MatOfPoint2f): MatOfPoint2f? {
        val points = pts.toArray(); if (points.size != 4) return null
        val cx = points.sumOf { it.x } / 4.0
        val cy = points.sumOf { it.y } / 4.0
        var tl: Point? = null
        var tr: Point? = null
        var bl: Point? = null
        var br: Point? = null
        for (pt in points) when {
            pt.x < cx && pt.y < cy -> tl = pt
            pt.x > cx && pt.y < cy -> tr = pt
            pt.x < cx && pt.y > cy -> bl = pt
            else -> br = pt
        }
        if (tl == null || tr == null || br == null || bl == null) return null
        return MatOfPoint2f(tl, tr, br, bl)
    }

    // ── Bitmap helpers ────────────────────────────────────────────────────────

    private fun Mat.toBitmap(): Bitmap {
        val rgba = Mat()
        when (channels()) {
            1 -> Imgproc.cvtColor(this, rgba, Imgproc.COLOR_GRAY2RGBA)
            3 -> Imgproc.cvtColor(this, rgba, Imgproc.COLOR_BGR2RGBA)
            else -> Imgproc.cvtColor(this, rgba, Imgproc.COLOR_BGRA2RGBA)
        }
        val bmp = createBitmap(cols(), rows())
        Utils.matToBitmap(rgba, bmp)
        rgba.release()
        return bmp
    }

    private fun Mat.grayToBitmap(): Bitmap {
        val rgba = Mat()
        Imgproc.cvtColor(this, rgba, Imgproc.COLOR_GRAY2RGBA)
        val bmp = createBitmap(cols(), rows())
        Utils.matToBitmap(rgba, bmp)
        rgba.release()
        return bmp
    }
}