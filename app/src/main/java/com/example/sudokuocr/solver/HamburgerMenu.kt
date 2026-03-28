package com.example.sudokuocr.solver

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

private enum class DrawerPage { MENU, ABOUT, CONTACT }

/**
 * Floating hamburger button shown in the top-left corner at all times.
 * Tapping it opens the [HamburgerMenu] overlay.
 */
@Composable
fun HamburgerButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick      = onClick,
        modifier     = modifier,
        shape        = RoundedCornerShape(12.dp),
        color        = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Icon(
            imageVector         = Icons.Filled.Menu,
            contentDescription  = "Open menu",
            tint                = MaterialTheme.colorScheme.onSurface,
            modifier            = Modifier.padding(10.dp).size(24.dp)
        )
    }
}

/**
 * Slide-in overlay drawer from the left.
 * Clicking outside the drawer (the scrim) closes it.
 */
@Composable
fun HamburgerMenu(open: Boolean, onDismiss: () -> Unit, onShowOnboarding: () -> Unit) {
    var page by remember(open) { mutableStateOf(DrawerPage.MENU) }

    // Scrim — fades in behind the drawer; tap to dismiss
    AnimatedVisibility(
        visible = open,
        enter   = fadeIn(),
        exit    = fadeOut()
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss)
        )
    }

    // Drawer panel — slides from left
    AnimatedVisibility(
        visible = open,
        enter   = slideInHorizontally { -it },
        exit    = slideOutHorizontally { -it }
    ) {
        Surface(
            modifier        = Modifier
                .fillMaxHeight()
                .width(300.dp),
            shape           = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            tonalElevation  = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                DrawerHeader(
                    page      = page,
                    onBack    = { page = DrawerPage.MENU },
                    onClose   = onDismiss
                )
                HorizontalDivider()
                when (page) {
                    DrawerPage.MENU    -> DrawerMenuContent(onNavigate = { page = it }, onShowOnboarding = { onDismiss(); onShowOnboarding() })
                    DrawerPage.ABOUT   -> AboutContent()
                    DrawerPage.CONTACT -> ContactContent()
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(page: DrawerPage, onBack: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (page != DrawerPage.MENU) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Text(
            text       = when (page) {
                DrawerPage.MENU    -> "Solvedoku"
                DrawerPage.ABOUT   -> "About"
                DrawerPage.CONTACT -> "Contact"
            },
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier
                .weight(1f)
                .padding(start = if (page == DrawerPage.MENU) 8.dp else 0.dp)
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close menu")
        }
    }
}

@Composable
private fun DrawerMenuContent(onNavigate: (DrawerPage) -> Unit, onShowOnboarding: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        DrawerItem(icon = Icons.Filled.Info,         label = "About")      { onNavigate(DrawerPage.ABOUT) }
        DrawerItem(icon = Icons.Filled.Email,        label = "Contact")    { onNavigate(DrawerPage.CONTACT) }
        DrawerItem(icon = Icons.AutoMirrored.Filled.HelpOutline,  label = "How to use") { onShowOnboarding() }
    }
}

@Composable
private fun DrawerItem(
    icon:    ImageVector,
    label:   String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

// ── About ─────────────────────────────────────────────────────────────────────

@Composable
private fun AboutContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Solvedoku", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider()

        Text(
            "An AI-powered Sudoku solver that detects and overlays solutions " +
                    "directly on the puzzle in real time using your camera.",
            style = MaterialTheme.typography.bodyMedium
        )

        SectionLabel("Built with")
        BulletItem("OpenCV 4.9 — computer vision pipeline")
        BulletItem("PyTorch Mobile — digit recognition model")
        BulletItem("Jetpack Compose — UI")
        BulletItem("CameraX — camera pipeline")

        SectionLabel("Solver")
        BulletItem("Backtracking with forward checking")
        BulletItem("MRV + MCV heuristics")
        BulletItem("Singleton arc-consistency propagation")
    }
}

// ── Contact ───────────────────────────────────────────────────────────────────

@Composable
private fun ContactContent() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Get in touch", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Found a bug or have a feature request? Reach out!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        ContactButton(
            icon    = Icons.Filled.Email,
            label   = "Send an email",
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO,
                    "mailto:contact@example.com?subject=Solvedoku Feedback".toUri())
                context.startActivity(intent)
            }
        )

        ContactButton(
            icon    = Icons.Filled.Code,
            label   = "View on GitHub",
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, "https://github.com/".toUri())
                context.startActivity(intent)
            }
        )

        ContactButton(
            icon    = Icons.Filled.BugReport,
            label   = "Report a bug",
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW,
                    "https://github.com/issues/new".toUri())
                context.startActivity(intent)
            }
        )
    }
}

@Composable
private fun ContactButton(
    icon:    ImageVector,
    label:   String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelLarge,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun BulletItem(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}