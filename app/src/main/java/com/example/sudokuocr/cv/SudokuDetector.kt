package com.example.sudokuocr.cv

import android.graphics.Bitmap
import com.example.sudokuocr.cv.SudokuDetector.MIN_HOUGH_LINES
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import androidx.core.graphics.createBitmap

data class DetectionResult(
    val cells: List<Mat>,           // 81 cell Mats, row-major, top-left → bottom-right
    val warpedBitmap: Bitmap,       // deskewed 450×450 board (for history thumbnail)
    val corners: MatOfPoint2f,      // 4 corners in camera-frame space, order: TL, TR, BR, BL
    val cameraFrameWidth: Int,
    val cameraFrameHeight: Int
)

object SudokuDetector {

    private const val WARP_SIZE   = 450.0
    private const val MIN_AREA_RATIO = 0.05   // board must be ≥ 5% of frame
    private const val MIN_HOUGH_LINES = 8     // relaxed from C++'s 16 — handles partial views

    fun detect(frame: Mat): DetectionResult? {
        if (frame.empty()) return null

        // ── 1. Preprocess: grayscale → small blur → Canny (mirrors C++) ──────
        val gray = Mat()
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY)

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(blurred, edges, 50.0, 100.0)
        blurred.release()

        // ── 2. Find all contours (RETR_LIST catches inner boards too) ─────────
        val contours  = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edges, contours, hierarchy,
            Imgproc.RETR_LIST,
            Imgproc.CHAIN_APPROX_TC89_KCOS
        )
        hierarchy.release()

        val frameArea = frame.rows().toDouble() * frame.cols().toDouble()
        val minArea   = frameArea * MIN_AREA_RATIO

        // ── 3. Collect all convex quadrilaterals above minimum area ───────────
        val quads = contours.mapNotNull { approxConvexQuad(it, minArea) }

        if (quads.isEmpty()) {
            gray.release(); edges.release()
            return null
        }

        // ── 4. Two-candidate strategy (mirrors C++) ───────────────────────────
        //    Sort by area descending so quads[0] is the largest
        val sorted = quads.sortedByDescending { Imgproc.contourArea(it) }
        val largest = sorted[0]

        // Largest inner quad: a quad that fits entirely inside the largest one
        val bestInner = sorted.drop(1).filter { inner ->
            isInsideQuad(largest, inner)
        }.maxByOrNull { Imgproc.contourArea(it) }

        val candidate = bestInner
            ?.takeIf { isLikelySudoku(it, edges) }
            ?: largest.takeIf { isLikelySudoku(it, edges) }

        edges.release()
        if (candidate == null) return null

        // ── 5. Order corners TL, TR, BR, BL ──────────────────────────────────
        val ordered = sortCorners(candidate) ?: return null

        // ── 6. Perspective warp to 450×450 ───────────────────────────────────
        val dst = MatOfPoint2f(
            Point(0.0,          0.0),
            Point(WARP_SIZE-1,  0.0),
            Point(WARP_SIZE-1,  WARP_SIZE-1),
            Point(0.0,          WARP_SIZE-1)
        )
        val transform = Imgproc.getPerspectiveTransform(ordered, dst)

        val warpedGray = Mat()
        Imgproc.warpPerspective(gray, warpedGray, transform, Size(WARP_SIZE, WARP_SIZE))
        gray.release()

        val warpedColor = Mat()
        Imgproc.warpPerspective(frame, warpedColor, transform, Size(WARP_SIZE, WARP_SIZE))
        transform.release()

        // ── 7. Extract 81 cells (4px inset removes grid lines) ───────────────
        val cellSize = (WARP_SIZE / 9.0).toInt()
        val inset    = 4
        val cells    = (0 until 9).flatMap { row ->
            (0 until 9).map { col ->
                val x = col * cellSize + inset
                val y = row * cellSize + inset
                val w = cellSize - inset * 2
                val h = cellSize - inset * 2
                warpedGray.submat(y, y + h, x, x + w).clone()
            }
        }

        // ── 8. Build thumbnail bitmap ─────────────────────────────────────────
        val bmp = createBitmap(warpedColor.cols(), warpedColor.rows())
        Utils.matToBitmap(warpedColor, bmp)
        warpedGray.release(); warpedColor.release()

        return DetectionResult(cells, bmp, ordered, frame.cols(), frame.rows())
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Approximates [contour] as a convex quadrilateral.
     * Returns the 4-point MatOfPoint2f, or null if not a convex quad or too small.
     */
    private fun approxConvexQuad(contour: MatOfPoint, minArea: Double): MatOfPoint2f? {
        if (Imgproc.contourArea(contour) < minArea) return null
        val c2f  = MatOfPoint2f(*contour.toArray())
        val peri = Imgproc.arcLength(c2f, true)
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
        if (approx.rows() != 4) return null
        // Convexity check — mirrors C++ isContourConvex
        val hull = MatOfInt()
        val mp   = MatOfPoint(*approx.toArray())
        Imgproc.convexHull(mp, hull)
        if (hull.rows() != 4) return null
        return approx
    }

    /**
     * Returns true if every corner of [inner] lies within [outer].
     */
    private fun isInsideQuad(outer: MatOfPoint2f, inner: MatOfPoint2f): Boolean =
        inner.toArray().all { pt ->
            Imgproc.pointPolygonTest(outer, pt, false) >= 0.0
        }

    /**
     * Validates a quadrilateral candidate by checking for Hough grid lines inside it.
     * Applies a mask, runs HoughLines, separates/merges horizontal+vertical lines,
     * and requires at least [MIN_HOUGH_LINES] total merged lines.
     *
     * This is less strict than the C++ >16 threshold to handle partial views.
     */
    private fun isLikelySudoku(quad: MatOfPoint2f, edges: Mat): Boolean {
        val sorted = sortCorners(quad) ?: return false

        // Mask the edge image to only look inside the candidate quad
        val mask = Mat.zeros(edges.size(), CvType.CV_8U)
        val quadPoly = MatOfPoint(*sorted.toArray().map { it }.toTypedArray())
        Imgproc.fillConvexPoly(mask, quadPoly, Scalar(255.0))
        val maskedEdges = Mat()
        Core.bitwise_and(edges, edges, maskedEdges, mask)
        mask.release(); quadPoly.release()

        // Hough line detection
        val lines = Mat()
        Imgproc.HoughLines(maskedEdges, lines, 1.0, Math.PI / 180.0, 80)
        maskedEdges.release()

        if (lines.empty()) return false

        val angleTol = 0.261799f   // ±15°
        val horizRhos = mutableListOf<Float>()
        val vertRhos  = mutableListOf<Float>()

        for (i in 0 until lines.rows()) {
            var rho   = lines.get(i, 0)[0].toFloat()
            var theta = lines.get(i, 0)[1].toFloat()
            if (theta < 0) { theta += Math.PI.toFloat(); rho = -rho }

            when {
                abs(theta - 0f)                         < angleTol -> horizRhos.add(rho)
                abs(theta - Math.PI.toFloat())           < angleTol -> horizRhos.add(rho)
                abs(theta - (Math.PI / 2).toFloat())     < angleTol -> vertRhos.add(rho)
            }
        }
        lines.release()

        val totalMerged = mergeRhos(horizRhos).size + mergeRhos(vertRhos).size
        return totalMerged >= MIN_HOUGH_LINES
    }

    /** Merges rho values that are within 10px of each other — mirrors C++ mergeLines. */
    private fun mergeRhos(rhos: List<Float>): List<Float> {
        if (rhos.isEmpty()) return emptyList()
        val sorted  = rhos.sorted()
        val merged  = mutableListOf(sorted[0])
        for (r in sorted.drop(1)) {
            if (abs(r - merged.last()) > 10f) merged.add(r) else merged[merged.lastIndex] = (merged.last() + r) / 2f
        }
        return merged
    }

    /**
     * Orders four corner points: top-left, top-right, bottom-right, bottom-left.
     * Uses sum (x+y) and difference (x−y) — same as the C++ sortCornerPoints approach.
     */
    private fun sortCorners(pts: MatOfPoint2f): MatOfPoint2f? {
        val points = pts.toArray()
        if (points.size != 4) return null

        val center = Point(
            points.sumOf { it.x } / 4.0,
            points.sumOf { it.y } / 4.0
        )

        var tl: Point? = null; var tr: Point? = null
        var bl: Point? = null; var br: Point? = null

        for (pt in points) {
            when {
                pt.x < center.x && pt.y < center.y -> tl = pt
                pt.x > center.x && pt.y < center.y -> tr = pt
                pt.x < center.x && pt.y > center.y -> bl = pt
                else                                -> br = pt
            }
        }

        if (tl == null || tr == null || br == null || bl == null) return null
        return MatOfPoint2f(tl, tr, br, bl)
    }
}