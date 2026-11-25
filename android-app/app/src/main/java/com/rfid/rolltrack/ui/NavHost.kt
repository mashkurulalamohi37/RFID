package com.rfid.rolltrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.ui.delivery.DeliveryScreen
import com.rfid.rolltrack.ui.diagnostics.DiagnosticsScreen
import com.rfid.rolltrack.ui.home.HomeScreen
import com.rfid.rolltrack.ui.issue.TagIssueScreen
import com.rfid.rolltrack.ui.loading.LoadingScreen
import com.rfid.rolltrack.ui.login.LoginScreen
import com.rfid.rolltrack.ui.production.ProductionScreen
import com.rfid.rolltrack.ui.settings.SettingsScreen
import com.rfid.rolltrack.ui.trace.TraceScreen
import com.rfid.rolltrack.ui.unloading.UnloadingScreen

@Composable
fun RollTrackNavHost(
    container: AppContainer
) {
    val navController = rememberNavController()
    val startDestination = if (container.settings.session.value.isLoggedIn) "home" else "login"

    // Signed out from anywhere (Log out, or leaving the app): go to the sign-in page and clear every screen behind it.
    LaunchedEffect(container.settings) {
        container.settings.session.collect { session ->
            val current = navController.currentDestination?.route
            if (!session.isLoggedIn && current != null && current != "login") {
                navController.navigate("login") {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") {
            LoginScreen(
                container = container,
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                container = container,
                onOpen = { route -> navController.navigate(route) },
                onLoggedOut = {
                    // The sign-out watcher above opens the sign-in page.
                }
            )
        }

        composable("issue") {
            TagIssueScreen(
                container = container,
                onBack = { navController.popBackStack() }
            )
        }

        composable("production") {
            ProductionScreen(
                container = container,
                onBack = { navController.popBackStack() }
            )
        }

        composable("loading") {
            LoadingScreen(
                container = container,
                onBack = { navController.popBackStack() }
            )
        }

        composable("unloading") {
            UnloadingScreen(
                container = container,
                onBack = { navController.popBackStack() }
            )
        }

        composable("delivery") {
            DeliveryScreen(
                container = container,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "trace?tag={tag}",
            arguments = listOf(navArgument("tag") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val tag = backStackEntry.arguments?.getString("tag")
            TraceScreen(
                container = container,
                initialTag = tag,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                container = container,
                onOpenDiagnostics = { navController.navigate("diagnostics") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("diagnostics") {
            DiagnosticsScreen(
                container = container,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
