package com.example.sudokuocr.data.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class AppTheme(val label: String) {
    SYSTEM("Follow system"),
    DARK("Dark"),
    LIGHT("Light")
}

// ── AppSettings.kt ────────────────────────────────────────────────────────────

data class AppSettings(
    val solutionColorArgb: Int     = Color(0xFF4CAF50).toArgb(),  // green default
    val givenDisplay: GivenDisplay = GivenDisplay.SAME,
    val givenColorArgb: Int        = Color(0xFF4CAF50).toArgb(),  // used when CUSTOM
    val saveToGallery: Boolean     = false,
    val saveStyle: SaveStyle       = SaveStyle.AR_COMPOSITE,
    val appTheme: AppTheme           = AppTheme.SYSTEM,
    val devModeEnabled: Boolean      = false,
    val cvParams: CvParams           = CvParams()
)

enum class GivenDisplay(val label: String) {
    SAME("Same as solution"),
    HIDE("Hide"),
    CUSTOM("Custom color")
}

/** Controls what is written to the gallery. */
enum class SaveStyle(val label: String) {
    AR_COMPOSITE("Camera + AR overlay"),
    CLEAN_FLAT("Clean flat B/W grid")
}