package com.leadmilers.saathi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.leadmilers.saathi.prefs.UserRole
import com.leadmilers.saathi.ui.screen.*
import com.leadmilers.saathi.ui.screen.companion.CompanionDashboardScreen
import com.leadmilers.saathi.ui.screen.companion.PairingScreen
import com.leadmilers.saathi.ui.screen.companion.SharingControlsScreen
import com.leadmilers.saathi.ui.screen.onboarding.OnboardingScreen
import com.leadmilers.saathi.ui.theme.SaathiTheme

// ─── Route constants ────────────────────────────────────────────────────────

private object Routes {
    // Primary (bottom nav)
    const val HOME     = "home"
    const val TRACK    = "track"
    const val INSIGHTS = "insights"
    const val SAATHI   = "saathi"
    // Secondary (navigated to from primary screens)
    const val LOG               = "log"
    const val CAMERA            = "camera"
    const val VOICE             = "voice"
    const val REPORT            = "report"
    const val OFFICE            = "office"
    const val SETTINGS          = "settings"
    const val MODULES           = "modules"
    const val PRIVACY           = "privacy"
    const val GENERAL_SYMPTOM   = "general_symptom"
    const val PARTNER           = "partner"
    const val HEART_RATE        = "heart_rate"
    // Onboarding + companion
    const val ONBOARDING         = "onboarding"
    const val COMPANION_DASHBOARD = "companion_dashboard"
    const val PAIRING            = "pairing"
    const val SHARING_CONTROLS   = "sharing_controls"
}

// ─── Bottom-nav items: 4 tabs ─────────────────────────────────────────────

private data class NavItem(
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String,
)

private val bottomNavItems = listOf(
    NavItem(Routes.HOME,     Icons.Outlined.Home,             Icons.Filled.Home,             "Home"),
    NavItem(Routes.TRACK,    Icons.Outlined.AddCircleOutline, Icons.Filled.AddCircleOutline, "Track"),
    NavItem(Routes.INSIGHTS, Icons.Outlined.BarChart,         Icons.Filled.BarChart,         "Insights"),
    NavItem(Routes.SAATHI,   Icons.Outlined.AutoAwesome,      Icons.Filled.AutoAwesome,      "Saathi"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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
    val context       = LocalContext.current
    val prefs         = remember { (context.applicationContext as SaathiApp).userPrefs }
    val navController = rememberNavController()
    val backStack     by navController.currentBackStackEntryAsState()
    val currentRoute  = backStack?.destination?.route

    // Determine start destination once (synchronous prefs read)
    val startDestination = remember {
        when {
            !prefs.onboardingComplete                -> Routes.ONBOARDING
            prefs.role == UserRole.COMPANION         -> Routes.COMPANION_DASHBOARD
            else                                     -> Routes.HOME
        }
    }

    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        modifier  = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (selected) item.selectedIcon else item.icon,
                                    contentDescription = item.label,
                                )
                            },
                            label    = { Text(item.label) },
                            selected = selected,
                            onClick  = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = MaterialTheme.colorScheme.primary,
                                selectedTextColor   = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = startDestination,
            modifier         = Modifier.padding(innerPadding),
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220))
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220))
            },
        ) {
            // ── Onboarding ───────────────────────────────────────────────────
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onComplete = {
                    val dest = if (prefs.role == UserRole.COMPANION) Routes.COMPANION_DASHBOARD else Routes.HOME
                    navController.navigate(dest) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                })
            }

            // ── Companion dashboard (COMPANION role) ─────────────────────────
            composable(Routes.COMPANION_DASHBOARD) {
                CompanionDashboardScreen(
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onSyncClick     = {},
                )
            }

            // ── Primary tabs (SELF role) ──────────────────────────────────────
            composable(Routes.HOME) {
                HomeScreen(
                    onLogTodayClick = { navController.navigate(Routes.TRACK) { launchSingleTop = true } },
                    onCameraClick   = { navController.navigate(Routes.CAMERA) },
                    onVoiceClick    = { navController.navigate(Routes.VOICE) },
                    onReportClick   = { navController.navigate(Routes.REPORT) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.TRACK) {
                TrackScreen(
                    onCameraClick      = { navController.navigate(Routes.CAMERA) },
                    onVoiceClick       = { navController.navigate(Routes.VOICE) },
                    onDetailedLogClick = { navController.navigate(Routes.LOG) },
                )
            }
            composable(Routes.INSIGHTS) {
                InsightsScreen()
            }
            composable(Routes.SAATHI) {
                SaathiCompanionScreen()
            }

            // ── Secondary screens ─────────────────────────────────────────────
            composable(Routes.LOG)    { LogScreen() }
            composable(Routes.CAMERA) { CameraScreen() }
            composable(Routes.VOICE)  { VoiceScreen() }
            composable(Routes.REPORT) { ReportScreen() }
            composable(Routes.OFFICE) { OfficeScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onPrivacyClick         = { navController.navigate(Routes.PRIVACY) },
                    onPairingClick         = { navController.navigate(Routes.PAIRING) },
                    onSharingControlsClick = { navController.navigate(Routes.SHARING_CONTROLS) },
                )
            }
            composable(Routes.PAIRING) {
                PairingScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SHARING_CONTROLS) {
                SharingControlsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.MODULES) {
                ModulesScreen(onModuleClick = { moduleId ->
                    when (moduleId) {
                        "general" -> navController.navigate(Routes.GENERAL_SYMPTOM)
                        "pcos"    -> navController.navigate(Routes.HOME)
                        else      -> {}
                    }
                })
            }
            composable(Routes.PRIVACY)         { PrivacyScreen() }
            composable(Routes.GENERAL_SYMPTOM) {
                GeneralSymptomScreen(onHeartRateClick = { navController.navigate(Routes.HEART_RATE) })
            }
            composable(Routes.PARTNER)         { PartnerScreen() }
            composable(Routes.HEART_RATE)      { HeartRateScreen() }
        }
    }
}
