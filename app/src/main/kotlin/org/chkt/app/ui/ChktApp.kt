package org.chkt.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.chkt.app.data.Repository

val LocalRepository = staticCompositionLocalOf<Repository> {
    error("Repository not provided")
}

@Composable
fun ChktApp() {
    val context = LocalContext.current
    val repository = remember { Repository(context.applicationContext) }
    val navController = rememberNavController()
    // First run (or missing permissions): walk through setup before anything else.
    val startDestination = remember {
        if (SetupCheck.allEssentialGranted(context)) "home" else "setup"
    }

    CompositionLocalProvider(LocalRepository provides repository) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable("home") {
                HomeScreen(
                    onEdit = { reminderId ->
                        if (reminderId == null) navController.navigate("new")
                        else navController.navigate("edit?reminderId=$reminderId")
                    },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenStats = { navController.navigate("stats") },
                )
            }
            composable("new") {
                EditReminderScreen(
                    reminderId = null,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                "edit?reminderId={reminderId}",
                arguments = listOf(
                    navArgument("reminderId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val reminderId = entry.arguments?.getString("reminderId").orEmpty().ifBlank { null }
                EditReminderScreen(
                    reminderId = reminderId,
                    onDone = { navController.popBackStack() },
                )
            }
            composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable("stats") { StatsScreen(onBack = { navController.popBackStack() }) }
            composable("setup") {
                SetupScreen(onDone = {
                    navController.navigate("home") { popUpTo("setup") { inclusive = true } }
                })
            }
        }
    }
}
