package com.karan.anuj.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.karan.anuj.R
import com.karan.anuj.core.ui.components.AnujScaffold
import com.karan.anuj.ui.home.HomeScreen
import com.karan.anuj.ui.settings.SettingsScreen

/**
 * A tab in the bottom bar. A later phase adds its tab by adding an entry
 * here and a matching `composable` in the NavHost below.
 */
private enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}

/** The unlocked, set-up app: a bottom tab bar with one screen per tab. */
@Composable
fun AnujApp(
    lockAvailable: Boolean,
    onAppLockToggled: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    AnujScaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            /**
                             * Switching tabs keeps one copy of each tab and
                             * restores where the user was in it, so the back
                             * button leaves the app from Home instead of
                             * walking back through every tab tapped.
                             */
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
        ) {
            composable(TopLevelDestination.HOME.route) { HomeScreen() }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    lockAvailable = lockAvailable,
                    onAppLockToggled = onAppLockToggled,
                )
            }
        }
    }
}
