package com.example.sudokuocr

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.sudokuocr.dev.DevScreen
import com.example.sudokuocr.history.HistoryScreen
import com.example.sudokuocr.settings.SettingsScreen
import com.example.sudokuocr.solver.HamburgerButton
import com.example.sudokuocr.solver.HamburgerMenu
import com.example.sudokuocr.solver.SolverScreen
import com.example.sudokuocr.ui.theme.SudokuOCRTheme

sealed class Screen(val route: String, val label: String) {
    object Solver   : Screen("solver",   "Solver")
    object History  : Screen("history",  "History")
    object Settings  : Screen("settings",   "Settings")
    object Developer : Screen("developer",  "Developer")
}

@Composable
fun SudokuApp(mainVm: MainViewModel) {
    val settings       by mainVm.settings.collectAsState()
    val showOnboarding by mainVm.showOnboarding.collectAsState()

    SudokuOCRTheme(appTheme = settings.appTheme) {
        var drawerOpen by remember { mutableStateOf(false) }
        var showOnboardingOverride by remember { mutableStateOf(false) }

        // Hoisted so SudokuApp can observe the current route
        val devModeEnabled    = settings.devModeEnabled
        val navController     = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute      = navBackStackEntry?.destination?.route

        Box(Modifier.fillMaxSize()) {
            MainNavigation(navController, devModeEnabled)

            if (currentRoute == Screen.Solver.route) {
                HamburgerButton(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                        .padding(start = 12.dp, top = 12.dp),
                    onClick = { drawerOpen = true }
                )
            }

            HamburgerMenu(
                open             = drawerOpen,
                onDismiss        = { drawerOpen = false },
                onShowOnboarding = { drawerOpen = false; showOnboardingOverride = true }
            )

            if (showOnboarding || showOnboardingOverride) {
                OnboardingOverlay(onDismiss = {
                    showOnboardingOverride = false
                    mainVm.onOnboardingFinished()
                })
            }
        }
    }
}

@Composable
private fun MainNavigation(navController: NavHostController, devModeEnabled: Boolean) {
    val navBackStackEntry  by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val items = buildList {
        add(Screen.Solver); add(Screen.History); add(Screen.Settings)
        if (devModeEnabled) add(Screen.Developer)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            when (screen) {
                                Screen.Solver   -> Icon(Icons.Filled.CameraAlt, screen.label)
                                Screen.History  -> Icon(Icons.Filled.History,   screen.label)
                                Screen.Settings  -> Icon(Icons.Filled.Settings, screen.label)
                                Screen.Developer -> Icon(Icons.Filled.BugReport, screen.label)
                            }
                        },
                        label    = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick  = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Solver.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Solver.route)   { SolverScreen() }
            composable(Screen.History.route)  { HistoryScreen() }
            composable(Screen.Settings.route)   { SettingsScreen() }
            composable(Screen.Developer.route) { DevScreen() }
        }
    }
}