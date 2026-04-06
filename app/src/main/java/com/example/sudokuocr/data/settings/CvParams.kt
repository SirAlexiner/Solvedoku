package com.example.sudokuocr.data.settings

/**
 * All tunable parameters for the CV detection pipeline.
 * Defaults match the original hardcoded values in SudokuDetector.
 */
data class CvParams(
    val cannyLow:          Double = 50.0,   // Canny lower threshold
    val cannyHigh:         Double = 100.0,  // Canny upper threshold
    val minAreaRatio:      Double = 0.05,   // minimum contour area as fraction of frame
    val polyEpsilonFactor: Double = 0.02,   // approxPolyDP epsilon = factor × perimeter
    val houghThreshold:    Int    = 80,     // HoughLines accumulator threshold
    val minHoughLines:     Int    = 8       // merged lines required to accept a quad
)