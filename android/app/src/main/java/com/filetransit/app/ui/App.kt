package com.filetransit.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.filetransit.app.DeepLinkEvent
import com.filetransit.app.Graph
import com.filetransit.app.ui.screens.CollectionShareScreen
import com.filetransit.app.ui.screens.FilesScreen
import com.filetransit.app.ui.screens.FolderShareScreen
import com.filetransit.app.ui.screens.LoginScreen
import com.filetransit.app.ui.screens.LogsScreen
import com.filetransit.app.ui.screens.PreviewScreen
import com.filetransit.app.ui.screens.PublicFileShareScreen
import com.filetransit.app.ui.screens.SettingsScreen
import com.filetransit.app.ui.screens.SharesScreen
import com.filetransit.app.ui.screens.TextShareScreen
import com.filetransit.app.ui.screens.TextsScreen

enum class MainTab(val route: String, val label: String, val icon: ImageVector) {
    FILES(Routes.FILES, "文件", Icons.Outlined.Folder),
    TEXTS(Routes.TEXTS, "文本", Icons.Outlined.Description),
    SHARES(Routes.SHARES, "分享", Icons.Outlined.Share),
    LOGS(Routes.LOGS, "日志", Icons.Outlined.ListAlt),
    SETTINGS(Routes.SETTINGS, "设置", Icons.Outlined.Settings),
}

@Composable
fun App(deepLink: DeepLinkEvent?) {
    val nav = rememberNavController()
    val token by Graph.session.token.collectAsState()
    val start = remember { if (token != null) Routes.FILES else Routes.LOGIN }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    nav.navigate(Routes.FILES) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                    if (ShareInbox.text.value != null) {
                        nav.navigate(Routes.TEXTS) { launchSingleTop = true }
                    }
                },
            )
        }
        composable(Routes.FILES) {
            MainScaffold(nav, MainTab.FILES) { padding -> FilesScreen(nav, padding) }
        }
        composable(Routes.TEXTS) {
            MainScaffold(nav, MainTab.TEXTS) { padding -> TextsScreen(nav, padding) }
        }
        composable(Routes.SHARES) {
            MainScaffold(nav, MainTab.SHARES) { padding -> SharesScreen(nav, padding) }
        }
        composable(Routes.LOGS) {
            MainScaffold(nav, MainTab.LOGS) { padding -> LogsScreen(padding) }
        }
        composable(Routes.SETTINGS) {
            MainScaffold(nav, MainTab.SETTINGS) { padding -> SettingsScreen(nav, padding) }
        }

        composable(
            Routes.SHARE,
            arguments = listOf(navArgument("code") { type = NavType.StringType }),
        ) { entry ->
            PublicFileShareScreen(nav, entry.arguments?.getString("code").orEmpty())
        }
        composable(
            Routes.COLLECTION_SHARE,
            arguments = listOf(navArgument("code") { type = NavType.StringType }),
        ) { entry ->
            CollectionShareScreen(nav, entry.arguments?.getString("code").orEmpty())
        }
        composable(
            Routes.FOLDER_SHARE,
            arguments = listOf(navArgument("code") { type = NavType.StringType }),
        ) { entry ->
            FolderShareScreen(nav, entry.arguments?.getString("code").orEmpty())
        }
        composable(
            Routes.TEXT_SHARE,
            arguments = listOf(navArgument("code") { type = NavType.StringType }),
        ) { entry ->
            TextShareScreen(nav, entry.arguments?.getString("code").orEmpty())
        }
        composable(Routes.PREVIEW) { PreviewScreen(nav) }
    }

    LaunchedEffect(deepLink) {
        val event = deepLink ?: return@LaunchedEffect
        val requiresAuth = event.route in setOf(Routes.TEXTS, Routes.SHARES, Routes.LOGS, Routes.SETTINGS)
        val loggedIn = Graph.session.token.value != null
        if (requiresAuth && !loggedIn) {
            nav.navigate(Routes.LOGIN) { launchSingleTop = true }
        } else {
            nav.navigate(event.route) { launchSingleTop = true }
        }
    }

    LaunchedEffect(Unit) {
        Graph.authEvents.collect {
            nav.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}

@Composable
fun MainScaffold(
    nav: NavHostController,
    current: MainTab,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == current,
                        onClick = {
                            if (tab == current) return@NavigationBarItem
                            nav.navigate(tab.route) {
                                popUpTo(Routes.FILES) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        content = content,
    )
}
