package com.wallifyhd.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wallifyhd.app.ui.detail.DetailScreen
import com.wallifyhd.app.ui.favorites.FavoritesScreen
import com.wallifyhd.app.ui.home.HomeScreen

private const val HomeRoute = "home"
private const val FavoritesRoute = "favorites"
private const val DetailRoute = "detail/{wallpaperId}"

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun WallifyApp(
    viewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val topLevelDestinations = listOf(
        TopLevelDestination(
            route = HomeRoute,
            label = "Discover",
            icon = Icons.Rounded.Home
        ),
        TopLevelDestination(
            route = FavoritesRoute,
            label = "Favorites",
            icon = Icons.Rounded.Favorite
        )
    )

    val showBottomBar = currentDestination?.route != DetailRoute

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(text = destination.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(HomeRoute) {
                HomeScreen(
                    viewModelFactory = viewModelFactory,
                    onWallpaperSelected = { wallpaper ->
                        navController.navigate("detail/${wallpaper.id}")
                    }
                )
            }

            composable(FavoritesRoute) {
                FavoritesScreen(
                    viewModelFactory = viewModelFactory,
                    onWallpaperSelected = { wallpaper ->
                        navController.navigate("detail/${wallpaper.id}")
                    }
                )
            }

            composable(DetailRoute) { entry ->
                val wallpaperId = entry.arguments?.getString("wallpaperId").orEmpty()
                DetailScreen(
                    wallpaperId = wallpaperId,
                    viewModelFactory = viewModelFactory,
                    onBackClick = { navController.navigateUp() }
                )
            }
        }
    }
}
