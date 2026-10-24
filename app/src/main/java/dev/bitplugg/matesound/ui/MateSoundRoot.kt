package dev.bitplugg.matesound.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import dev.bitplugg.matesound.ui.screens.LocalPlaylistScreen
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
        var queueSheetOpen by rememberSaveable { mutableStateOf(false) }

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
                                onPrevious = { container.player.previous() },
                                onOpenQueue = { queueSheetOpen = true },
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
                            onOpenLocalPlaylist = { id -> navController.navigate("local/$id") },
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
                        ArtistScreen(
                            onBack = { navController.popBackStack() },
                            onOpenPlaylist = { id -> navController.navigate("playlist/$id") },
                        )
                    }
                    composable(
                        route = "playlist/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) {
                        PlaylistScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "local/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) {
                        LocalPlaylistScreen(
                            onBack = { navController.popBackStack() },
                            navController = navController,
                        )
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

        if (queueSheetOpen) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                onDismissRequest = { queueSheetOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.player_queue),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { container.player.clearQueue() }) {
                            Text("Очистить")
                        }
                    }
                    LazyColumn(Modifier.fillMaxWidth()) {
                        itemsIndexed(playerState.queue) { index, item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        container.player.playIndex(index)
                                        queueSheetOpen = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (index == playerState.currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(28.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (index == playerState.currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = item.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { container.player.removeQueueItem(index) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                                }
                            }
                        }
                    }
                }
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
