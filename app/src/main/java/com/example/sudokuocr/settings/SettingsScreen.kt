package com.example.sudokuocr.settings

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sudokuocr.data.settings.AppTheme
import com.example.sudokuocr.data.settings.GivenDisplay
import com.example.sudokuocr.data.settings.SaveStyle
import kotlinx.coroutines.delay
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── App theme — header doubles as the dev-mode unlock ──────────
            DevModeThemeHeader(
                devModeEnabled = settings.devModeEnabled,
                onToggleDev    = { vm.setDevMode(it) }
            )
            AppTheme.entries.forEach { theme ->
                RadioRow(
                    label    = theme.label,
                    selected = settings.appTheme == theme,
                    onClick  = { vm.setAppTheme(theme) }
                )
            }

            Divider()

            // ── Solution color ─────────────────────────────────────────────
            SectionHeader("Solution color")
            ColorPickerRow(
                argb     = settings.solutionColorArgb,
                onChange = { vm.setSolutionColor(it) }
            )

            Divider()

            // ── Given hints ────────────────────────────────────────────────
            GivenHintsSection(settings = settings, vm = vm)

            Divider()

            // ── Save to gallery ────────────────────────────────────────────
            GallerySection(settings = settings, vm = vm)
        }
    }
}

// ── Dev mode unlock header ────────────────────────────────────────────────────
//
// Tap "Theme" header 15 times (within 2 s between taps) to toggle dev mode.
// Shows a small badge for 2 s after toggling.

@Composable
private fun DevModeThemeHeader(devModeEnabled: Boolean, onToggleDev: (Boolean) -> Unit) {
    val context  = LocalContext.current
    var toast: Toast? = null
    val currentDev by rememberUpdatedState(devModeEnabled)
    var tapCount by remember { mutableIntStateOf(0) }
    var hintCount by remember { mutableIntStateOf(5) }
    var lastTap  by remember { mutableLongStateOf(0L) }
    var showBadge by remember { mutableStateOf(false) }

    LaunchedEffect(showBadge) {
        if (showBadge) { delay(2_000); showBadge = false }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures {
                    val now = System.currentTimeMillis()
                    if (now - lastTap > 2_000) {
                        tapCount = 0
                        hintCount = 5
                    }
                    lastTap = now
                    tapCount++
                    when {
                        tapCount in 10 until 15 -> {
                            toast?.cancel()
                            toast = Toast.makeText(context, "$hintCount more taps to toggle developer mode", Toast.LENGTH_SHORT)
                            toast?.show()
                            hintCount--
                        }
                        tapCount >= 15 -> {
                            toast?.cancel()
                            toast = Toast.makeText(context, "$hintCount more taps to toggle developer mode", Toast.LENGTH_SHORT)
                            toast?.show()
                            onToggleDev(!currentDev)
                            showBadge = true
                            tapCount  = 0
                            hintCount = 5
                        }
                    }
                }
            }
    ) {
        SectionHeader("Theme")
        if (showBadge) {
            DevModeBadge(
                enabled  = devModeEnabled,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
            )
        }
    }
}

@Composable
private fun DevModeBadge(enabled: Boolean, modifier: Modifier) {
    Surface(
        shape    = RoundedCornerShape(8.dp),
        color    = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
    ) {
        Text(
            text     = if (enabled) "Dev mode ON" else "Dev mode OFF",
            style    = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ── Given hints section ───────────────────────────────────────────────────────

@Composable
private fun GivenHintsSection(
    settings: com.example.sudokuocr.data.settings.AppSettings,
    vm:       SettingsViewModel
) {
    SectionHeader("Given hints")
    GivenDisplay.entries.forEach { mode ->
        RadioRow(
            label    = mode.label,
            selected = settings.givenDisplay == mode,
            onClick  = { vm.setGivenDisplay(mode) }
        )
    }
    if (settings.givenDisplay == GivenDisplay.CUSTOM) {
        Spacer(Modifier.height(4.dp))
        ColorPickerRow(
            argb     = settings.givenColorArgb,
            onChange = { vm.setGivenColor(it) }
        )
    }
}

// ── Gallery section ───────────────────────────────────────────────────────────

@Composable
private fun GallerySection(
    settings: com.example.sudokuocr.data.settings.AppSettings,
    vm:       SettingsViewModel
) {
    SectionHeader("Gallery")
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Save solution to gallery", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Auto-saves a snapshot when a solve completes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked         = settings.saveToGallery,
            onCheckedChange = { vm.setSaveToGallery(it) }
        )
    }

    if (settings.saveToGallery) {
        Spacer(Modifier.height(8.dp))
        SectionHeader("Save format")
        Text(
            "What to write to the gallery — independent of the overlay shown on screen.",
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SaveStyle.entries.forEach { style ->
            RadioRow(
                label    = style.label,
                selected = settings.saveStyle == style,
                onClick  = { vm.setSaveStyle(style) }
            )
        }
    }
}

// ── Color picker row: swatch + expandable wheel ───────────────────────────────

@Composable
private fun ColorPickerRow(argb: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val color = Color(argb)

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .pointerInput(Unit) { detectTapGestures { expanded = !expanded } }
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text  = "Tap swatch to ${if (expanded) "close" else "pick"} color",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            Surface(
                shape           = RoundedCornerShape(12.dp),
                tonalElevation  = 2.dp,
                modifier        = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RgbColorWheel(
                        currentColor  = color,
                        onColorChange = { onChange(it.toArgb()) },
                        modifier      = Modifier.size(240.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(argb, hsv)
                    Text("Brightness", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value         = hsv[2],
                        onValueChange = { v -> hsv[2] = v; onChange(android.graphics.Color.HSVToColor(hsv)) },
                        modifier      = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    )
                }
            }
        }
    }
}

// ── HSV color wheel ───────────────────────────────────────────────────────────

@Composable
private fun RgbColorWheel(
    currentColor:  Color,
    onColorChange: (Color) -> Unit,
    modifier:      Modifier = Modifier
) {
    val hsv = remember(currentColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(currentColor.toArgb(), it) }
    }
    var selectorPos by remember(currentColor) { mutableStateOf(hsvToWheelOffset(hsv[0], hsv[1])) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> pickFromWheel(offset, size.width.toFloat(), onColorChange) { selectorPos = it } },
                    onDrag      = { change, _ -> pickFromWheel(change.position, size.width.toFloat(), onColorChange) { selectorPos = it } }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> pickFromWheel(offset, size.width.toFloat(), onColorChange) { selectorPos = it } }
            }
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        for (angle in 0 until 360) {
            drawArc(
                brush       = Brush.radialGradient(
                    colors  = listOf(Color.White, Color.hsv(angle.toFloat(), 1f, 1f)),
                    center  = center,
                    radius  = radius
                ),
                startAngle  = angle.toFloat(),
                sweepAngle  = 1.5f,
                useCenter   = true
            )
        }

        val selX = center.x + selectorPos.x * radius
        val selY = center.y + selectorPos.y * radius
        drawCircle(Color.White, radius = 10f, center = Offset(selX, selY))
        drawCircle(Color.Black, radius = 10f, center = Offset(selX, selY), style = Stroke(2f))
    }
}

private fun hsvToWheelOffset(hue: Float, saturation: Float): Offset {
    val angleRad = Math.toRadians(hue.toDouble())
    return Offset((cos(angleRad) * saturation).toFloat(), (sin(angleRad) * saturation).toFloat())
}

private fun pickFromWheel(
    offset:        Offset,
    size:          Float,
    onColorChange: (Color) -> Unit,
    onSelectorPos: (Offset) -> Unit
) {
    val center     = Offset(size / 2f, size / 2f)
    val dx         = (offset.x - center.x) / (size / 2f)
    val dy         = (offset.y - center.y) / (size / 2f)
    val saturation = (dx * dx + dy * dy).pow(0.5f).coerceIn(0f, 1f)
    val hue        = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360).toFloat()
    onSelectorPos(Offset(dx.coerceIn(-1f, 1f), dy.coerceIn(-1f, 1f)))
    onColorChange(Color.hsv(hue, saturation, 1f))
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
}