package dev.bitplugg.matesound.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.data.local.AppSettings
import dev.bitplugg.matesound.ui.screens.ArtistScreen
import dev.bitplugg.matesound.ui.screens.HomeScreen
import dev.bitplugg.matesound.ui.screens.LibraryScreen
import dev.bitplugg.matesound.ui.screens.PlaylistScreen
import dev.bitplugg.matesound.ui.screens.PlayerScreen
import dev.bitplugg.matesound.ui.screens.SearchScreen
import dev.bitplugg.matesound.ui.screens.SettingsScreen
import dev.bitplugg.matesound.ui.theme.MateSoundTheme

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", R.string.nav_home, Icons.Rounded.Home),
    Tab("search", R.string.nav_search, Icons.Rounded.Search),
    Tab("library", R.string.nav_library, Icons.Rounded.LibraryMusic),
    Tab("settings", R.string.nav_settings, Icons.Rounded.Settings),
)

@Composable
fun MateSoundRoot() {
    val container = LocalAppContainer.current
    val settings by container.prefs.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val updateState by container.updates.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var updateDismissed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        container.updates.check()
    }

    MateSoundTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        var playerOpen by rememberSaveable { mutableStateOf(false) }

        val signInLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { }

        val startSignIn: () -> Unit = {
            signInLauncher.launch(Intent(context, AuthWebViewActivity::class.java))
        }

        BackHandler(enabled = playerOpen) { playerOpen = false }

        Box(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    Column {
                        if (playerState.mediaId != null) {
                            MiniPlayer(
                                state = playerState,
                                isLiked = false,
                                onToggle = { container.player.toggle() },
                                onNext = { container.player.next() },
                                onOpen = { playerOpen = true },
                            )
                        }
                        NavigationBar {
                            TABS.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(stringResource(tab.label)) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.padding(padding),
                ) {
                    composable("home") {
                        HomeScreen(onOpenPlayer = { playerOpen = true })
                    }
                    composable("search") {
                        SearchScreen(
                            onOpenArtist = { id -> navController.navigate("artist/$id") },
                            onOpenPlaylist = { id -> navController.navigate("playlist/$id") },
                        )
                    }
                    composable("library") {
                        LibraryScreen(
                            onOpenPlaylist = { id -> navController.navigate("playlist/$id") },
                            onSignIn = startSignIn,
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onSignIn = startSignIn)
                    }
                    composable(
                        route = "artist/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) {
                        ArtistScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "playlist/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) {
                        PlaylistScreen(onBack = { navController.popBackStack() })
                    }
                }
            }

            AnimatedVisibility(
                visible = playerOpen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                PlayerScreen(onClose = { playerOpen = false })
            }
        }

        if (updateState.info != null && !updateDismissed) {
            UpdateDialog(
                state = updateState,
                onDownload = { container.updates.download() },
                onInstall = { container.updates.install() },
                onOpenRelease = { container.updates.openReleasePage() },
                onDismiss = { updateDismissed = true },
            )
        }
    }
}
