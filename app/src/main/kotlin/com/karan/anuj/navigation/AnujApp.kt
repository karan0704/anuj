package com.karan.anuj.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.karan.anuj.R
import com.karan.anuj.core.domain.task.TaskId
import com.karan.anuj.core.ui.components.AnujScaffold
import com.karan.anuj.feature.task.detail.TaskDetailScreen
import com.karan.anuj.feature.task.detail.TaskDetailViewModel
import com.karan.anuj.feature.task.inbox.InboxScreen
import com.karan.anuj.feature.task.search.SearchScreen
import com.karan.anuj.feature.task.search.TrashScreen
import com.karan.anuj.feature.task.tree.TaskTreeScreen
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
    TASKS("tasks", R.string.nav_tasks, Icons.AutoMirrored.Filled.List),
    INBOX("inbox", R.string.nav_inbox, Icons.Filled.MailOutline),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}

/** Screens that open on top of a tab and are left with the back arrow. */
private object Routes {
    const val SEARCH = "search"
    const val TRASH = "trash"
    private const val TASK = "task"
    const val TASK_PATTERN = "$TASK/{${TaskDetailViewModel.TASK_ID_ARG}}"

    fun task(id: TaskId) = "$TASK/${id.value}"
}

/**
 * The unlocked, set-up app: a bottom tab bar with one screen per tab.
 *
 * @param onForeground called each time the app comes into view, so work
 * that depends on the date (carrying unfinished tasks over) can run
 */
@Composable
fun AnujApp(
    lockAvailable: Boolean,
    onAppLockToggled: (Boolean) -> Unit,
    onForeground: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    /** One host for the whole app, so an "Undo" message looks and sits the same on every screen. */
    val snackbar = remember { SnackbarHostState() }
    val openTask: (TaskId) -> Unit = { navController.navigate(Routes.task(it)) }

    LifecycleStartEffect(Unit) {
        onForeground()
        onStopOrDispose {}
    }

    /**
     * The bottom bar belongs to the tabs. On a pushed screen (one task,
     * search, trash) it is hidden so the back arrow is the single way out.
     * Until the first destination is known the bar is shown, so it does not
     * pop in a frame late.
     *
     * Before phase 1 there were only tabs and the bar was always drawn:
     *
     *     AnujScaffold(
     *         bottomBar = {
     *             NavigationBar { ... }
     *         },
     *     ) {
     */
    val onTab = currentDestination == null ||
        TopLevelDestination.entries.any { tab -> currentDestination.hierarchy.any { it.route == tab.route } }

    AnujScaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = { navController.switchTab(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
        ) {
            /**
             * Home now shows today's tasks under the clock. Before phase 1:
             *
             *     composable(TopLevelDestination.HOME.route) { HomeScreen() }
             */
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(snackbar = snackbar, onOpenTask = openTask)
            }
            composable(TopLevelDestination.TASKS.route) {
                TaskTreeScreen(
                    snackbar = snackbar,
                    onOpenTask = openTask,
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenTrash = { navController.navigate(Routes.TRASH) },
                )
            }
            composable(TopLevelDestination.INBOX.route) {
                InboxScreen(snackbar = snackbar, onOpenTask = openTask)
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    lockAvailable = lockAvailable,
                    onAppLockToggled = onAppLockToggled,
                )
            }

            composable(
                route = Routes.TASK_PATTERN,
                arguments = listOf(navArgument(TaskDetailViewModel.TASK_ID_ARG) { type = NavType.StringType }),
            ) {
                TaskDetailScreen(snackbar = snackbar, onBack = navController::popBackStack, onOpenTask = openTask)
            }
            composable(Routes.SEARCH) {
                SearchScreen(snackbar = snackbar, onBack = navController::popBackStack, onOpenTask = openTask)
            }
            composable(Routes.TRASH) {
                TrashScreen(onBack = navController::popBackStack)
            }
        }
    }
}

/**
 * Switching tabs keeps one copy of each tab and restores where the user was
 * in it, so the back button leaves the app from Home instead of walking
 * back through every tab tapped.
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
