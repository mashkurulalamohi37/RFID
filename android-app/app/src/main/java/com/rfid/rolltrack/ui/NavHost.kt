package com.rfid.rolltrack.ui

import androidx.compose.runtime.Composable
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
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
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
