package com.leadmilers.saathi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.leadmilers.saathi.ui.screen.*
import com.leadmilers.saathi.ui.theme.SaathiTheme

// ─── Route constants ────────────────────────────────────────────────────────

private object Routes {
    const val HOME     = "home"
    const val LOG      = "log"
    const val CAMERA   = "camera"
    const val VOICE    = "voice"
    const val REPORT   = "report"
    const val OFFICE   = "office"
    const val SETTINGS = "settings"
}

// ─── Bottom-nav items (6 tabs as requested) ─────────────────────────────────

private data class NavItem(val route: String, val icon: ImageVector, val label: String)

private val bottomNavItems = listOf(
    NavItem(Routes.HOME,   Icons.Default.Home,        "Home"),
    NavItem(Routes.LOG,    Icons.Default.EditNote,    "Log"),
    NavItem(Routes.CAMERA, Icons.Default.CameraAlt,   "Camera"),
    NavItem(Routes.VOICE,  Icons.Default.Mic,         "Voice"),
    NavItem(Routes.REPORT, Icons.Default.Description, "Report"),
    NavItem(Routes.OFFICE, Icons.Default.Laptop,      "Office"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaathiTheme {
                SaathiNavHost()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaathiNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDest = backStack?.destination

    // Show bottom bar only on the six main tabs, not on settings
    val showBottomBar = bottomNavItems.any { it.route == currentDest?.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (showBottomBar) {
                TopAppBar(
                    title = {
                        val label = bottomNavItems.firstOrNull { it.route == currentDest?.route }?.label ?: "Saathi"
                        Text(label)
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDest?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    // Pop back to start so back-press from any tab goes to Home
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(220)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(220)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(220)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(220)
                )
            }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onLogTodayClick = {
                        navController.navigate(Routes.LOG) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.LOG) {
                LogScreen()
            }
            composable(Routes.CAMERA) {
                CameraScreen()
            }
            composable(Routes.VOICE) {
                VoiceScreen()
            }
            composable(Routes.REPORT) {
                ReportScreen()
            }
            composable(Routes.OFFICE) {
                OfficeScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
