package com.example.sudokuocr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.sudokuocr.data.settings.AppTheme

// ── Colour schemes ─────────────────────────────────────────────────────────────
//
// Dark: charcoal backgrounds with amber/orange accent — matches the scanner icon.
// Light: clean white surfaces with dark text.

private val DarkColorScheme = darkColorScheme(
    primary          = SudokuAmber,
    onPrimary        = SudokuDarkSurface,
    primaryContainer = SudokuAmberDim,
    secondary        = SudokuOrange,
    background       = SudokuDarkBackground,
    surface          = SudokuDarkSurface,
    onBackground     = SudokuDarkOnBackground,
    onSurface        = SudokuDarkOnBackground
)

private val LightColorScheme = lightColorScheme(
    primary          = SudokuOrangeDark,
    onPrimary        = SudokuWhite,
    primaryContainer = SudokuAmberLight,
    secondary        = SudokuAmber,
    background       = SudokuWhite,
    surface          = SudokuLightSurface,
    onBackground     = SudokuBlack,
    onSurface        = SudokuBlack
)

@Composable
fun SudokuOCRTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content:  @Composable () -> Unit
) {
    val useDark = when (appTheme) {
        AppTheme.DARK   -> true
        AppTheme.LIGHT  -> false
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (useDark) DarkColorScheme else LightColorScheme,
        typography  = Typography,
        content     = content
    )
}