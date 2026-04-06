package com.example.sudokuocr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class OnboardingStep(
    val icon:        ImageVector,
    val title:       String,
    val description: String
)

private val steps = listOf(
    OnboardingStep(
        icon        = Icons.Filled.CameraAlt,
        title       = "Point & detect",
        description = "Hold your camera over a Sudoku puzzle. Orange corner brackets will lock onto the grid automatically."
    ),
    OnboardingStep(
        icon        = Icons.Filled.FlashOn,
        title       = "Use the flashlight for better results",
        description = "Tap the flashlight button (top-right) to turn on the torch. Consistent lighting reduces shadows across the grid and significantly improves detection accuracy."
    ),
    OnboardingStep(
        icon        = Icons.Filled.Psychology,
        title       = "Auto-solve",
        description = "Once detected, Solvedoku solves the puzzle using a backtracking algorithm with MRV and MCV heuristics. This takes under a second for most puzzles."
    ),
    OnboardingStep(
        icon        = Icons.Filled.Visibility,
        title       = "AR overlay",
        description = "The solution is drawn directly onto the live camera feed, aligned to the puzzle in real time."
    ),
    OnboardingStep(
        icon        = Icons.Filled.TouchApp,
        title       = "Hold to peek",
        description = "Press and hold anywhere on the screen to temporarily hide the overlay and see the original puzzle. Release to show it again."
    ),
    OnboardingStep(
        icon        = Icons.Filled.PauseCircle,
        title       = "Freeze & inspect",
        description = "Double-tap to freeze the camera on a clear shot. The solution stays visible so you can study it at your own pace. Double-tap again to resume."
    ),
    OnboardingStep(
        icon        = Icons.Filled.Photo,
        title       = "Solve from gallery",
        description = "Tap the gallery button to pick a photo of a Sudoku from your library. The same detection and solve pipeline runs on your image."
    ),
    OnboardingStep(
        icon        = Icons.Filled.Settings,
        title       = "Customise",
        description = "In Settings you can change solution and hint colours, save solved images to your gallery, choose dark or light theme, and more."
    )
)

@Composable
fun OnboardingOverlay(onDismiss: () -> Unit) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier        = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape           = RoundedCornerShape(20.dp),
            tonalElevation  = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)) {
                    Text(
                        text       = "Welcome to Solvedoku",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = "Here's everything you need to know:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                LazyColumn(
                    modifier            = Modifier.weight(1f),
                    contentPadding      = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(steps) { step -> OnboardingStepCard(step) }
                }

                HorizontalDivider()

                Button(
                    onClick  = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text("Got it!", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepCard(step: OnboardingStep) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment     = Alignment.Top
    ) {
        Box(
            modifier         = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = step.icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier           = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text       = step.title,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = step.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}