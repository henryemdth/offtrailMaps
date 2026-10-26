package com.offtrail.maps

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.offtrail.maps.model.AppRepository
import com.offtrail.maps.ui.OfftrailTheme
import com.offtrail.maps.ui.screens.MapDetailsScreen
import com.offtrail.maps.ui.screens.MapListScreen
import com.offtrail.maps.ui.screens.MapStoreScreen
import com.offtrail.maps.ui.screens.MapViewScreen
import com.offtrail.maps.ui.screens.SettingsScreen
import com.offtrail.maps.ui.screens.TrackStatsScreen
import com.offtrail.maps.ui.screens.UpgradeScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        )
        setContent {
            val data by AppRepository.data.collectAsState()
            OfftrailTheme(themeMode = data.themeMode) {
                Root()
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun Root() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val tabs = listOf(
        Tab("mapList", "Maps") { Icon(Icons.Filled.Map, null) },
        Tab("mapStore", "Store") { Icon(Icons.Filled.ShoppingCart, null) },
        Tab("settings", "Settings") { Icon(Icons.Filled.Settings, null) },
    )

    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.navigate(tab.route) { popUpTo("mapList"); launchSingleTop = true } },
                            icon = tab.icon,
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "mapList",
            modifier = Modifier.padding(padding),
        ) {
            composable("mapList") { MapListScreen(nav) }
            composable("map/{mapId}") { entry -> MapViewScreen(nav, entry.arguments?.getString("mapId")) }
            composable("mapDetails/{mapId}") { entry -> MapDetailsScreen(nav, entry.arguments?.getString("mapId")) }
            composable("mapStore") { MapStoreScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("upgrade") { UpgradeScreen(nav) }
            composable("trackStats") { TrackStatsScreen(nav) }
        }
    }
}
