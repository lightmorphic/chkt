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
        if (SetupCheck.allEssentialGranted(context)) "lists" else "setup"
    }

    CompositionLocalProvider(LocalRepository provides repository) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable("lists") {
                ListsScreen(
                    onOpenList = { navController.navigate("reminders/$it") },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenStats = { navController.navigate("stats") },
                )
            }
            composable(
                "reminders/{listId}",
                arguments = listOf(navArgument("listId") { type = NavType.StringType }),
            ) { entry ->
                val listId = entry.arguments?.getString("listId") ?: return@composable
                RemindersScreen(
                    listId = listId,
                    onBack = { navController.popBackStack() },
                    onEdit = { reminderId ->
                        if (reminderId == null) navController.navigate("new/$listId")
                        else navController.navigate("edit/$listId?reminderId=$reminderId")
                    },
                )
            }
            composable(
                "new/{listId}",
                arguments = listOf(navArgument("listId") { type = NavType.StringType }),
            ) { entry ->
                val listId = entry.arguments?.getString("listId") ?: return@composable
                // One screen with every field, per Charlie: no step-by-step.
                EditReminderScreen(
                    listId = listId,
                    reminderId = null,
                    onDone = { navController.popBackStack() },
                )
            }
            composable("setup") {
                SetupScreen(onDone = {
                    navController.navigate("lists") { popUpTo("setup") { inclusive = true } }
                })
            }
            composable(
                "edit/{listId}?reminderId={reminderId}",
                arguments = listOf(
                    navArgument("listId") { type = NavType.StringType },
                    navArgument("reminderId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val listId = entry.arguments?.getString("listId") ?: return@composable
                val reminderId = entry.arguments?.getString("reminderId").orEmpty().ifBlank { null }
                EditReminderScreen(
                    listId = listId,
                    reminderId = reminderId,
                    onDone = { navController.popBackStack() },
                )
            }
            composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable("stats") { StatsScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
