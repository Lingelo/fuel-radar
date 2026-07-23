package fr.fuelradar.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import fr.fuelradar.data.ServiceLocator
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import fr.fuelradar.R
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.fuelradar.ui.detail.StationDetailScreen
import fr.fuelradar.ui.favorites.FavoritesScreen
import fr.fuelradar.ui.map.MapScreen
import fr.fuelradar.ui.settings.SettingsScreen
import fr.fuelradar.ui.stations.StationsScreen
import fr.fuelradar.ui.trends.TrendsScreen

private enum class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    Map("map", R.string.tab_map, Icons.Filled.Map),
    Stations("stations", R.string.tab_stations, Icons.AutoMirrored.Filled.List),
    Favorites("favorites", R.string.tab_favorites, Icons.Filled.Favorite),
    Trends("trends", R.string.tab_trends, Icons.AutoMirrored.Filled.ShowChart),
    Settings("settings", R.string.tab_settings, Icons.Filled.Settings),
}

@Composable
fun AppNav() {
    val navController = rememberNavController()

    // Honor the startup screen chosen in Settings (map is the graph default).
    LaunchedEffect(Unit) {
        val tab = ServiceLocator.settings.settings.first().startupTab
        if (tab != Tab.Map.route && Tab.entries.any { it.route == tab }) {
            navController.navigate(tab) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                Tab.entries.forEach { tab ->
                    val selected =
                        currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    val label = stringResource(tab.labelRes)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Map.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Tab.Map.route) {
                MapScreen(
                    onOpenStation = { id -> navController.navigate("details/$id") },
                    onOpenRoute = { navController.navigate("route") },
                )
            }
            composable("route") {
                fr.fuelradar.ui.route.RouteScreen(
                    onBack = { navController.popBackStack() },
                    onOpenStation = { id -> navController.navigate("details/$id") },
                )
            }
            composable(Tab.Stations.route) {
                StationsScreen(
                    onOpenStation = { id -> navController.navigate("details/$id") },
                    onOpenMap = {
                        navController.navigate(Tab.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Tab.Favorites.route) {
                FavoritesScreen(
                    onOpenStation = { id -> navController.navigate("details/$id") },
                    onOpenMap = {
                        navController.navigate(Tab.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Tab.Trends.route) { TrendsScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable(
                route = "details/{stationId}",
                arguments = listOf(navArgument("stationId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("stationId") ?: return@composable
                StationDetailScreen(stationId = id, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun Placeholder(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = label, style = MaterialTheme.typography.headlineMedium)
    }
}
