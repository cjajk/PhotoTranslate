package com.cjajk.phototranslate.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cjajk.phototranslate.ui.favorites.FavoritesScreen
import com.cjajk.phototranslate.ui.home.HomeScreen
import com.cjajk.phototranslate.ui.result.ResultScreen
import com.cjajk.phototranslate.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val RESULT = "result?uri={uri}"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"

    fun result(uri: String): String = "result?uri=${Uri.encode(uri)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoTranslateApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { AppBottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(onImageSelected = { uri ->
                    navController.navigate(Routes.result(uri.toString()))
                })
            }
            composable(
                route = Routes.RESULT,
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("uri")?.let(Uri::parse)
                if (uri != null) {
                    ResultScreen(uri = uri, onBack = { navController.popBackStack() })
                }
            }
            composable(Routes.FAVORITES) { FavoritesScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val items: List<Triple<String, String, ImageVector>> = listOf(
        Triple(Routes.HOME, "拍照", Icons.Filled.Home),
        Triple(Routes.FAVORITES, "收藏", Icons.Filled.Favorite),
        Triple(Routes.SETTINGS, "设置", Icons.Filled.Settings)
    )
    NavigationBar {
        items.forEach { (route, label, icon) ->
            val selected = currentRoute?.startsWith(route) == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}
