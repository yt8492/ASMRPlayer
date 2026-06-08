package com.yt8492.asmrplayer.navigation

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yt8492.asmrplayer.service.PlaybackService
import com.yt8492.asmrplayer.ui.album.AlbumListRoute
import com.yt8492.asmrplayer.ui.player.PlaybackQueue
import com.yt8492.asmrplayer.ui.player.PlayerRoute
import com.yt8492.asmrplayer.ui.playlist.PlaylistDetailRoute
import com.yt8492.asmrplayer.ui.playlist.PlaylistListRoute
import com.yt8492.asmrplayer.ui.track.TrackListRoute

@Composable
fun AppNavHost(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    navController: NavHostController = rememberNavController(),
    playbackDestination: PlaybackDestination? = null,
) {
    LaunchedEffect(playbackDestination?.requestId) {
        val destination = playbackDestination ?: return@LaunchedEffect
        when (destination.queueType) {
            PlaybackService.QUEUE_TYPE_PLAYLIST -> {
                val name = Uri.encode(destination.playlistName)
                navController.navigate("player/playlist/${destination.playlistId}/${destination.trackId}?name=$name") {
                    launchSingleTop = true
                }
            }

            else -> {
                val title = Uri.encode(destination.albumTitle)
                val art = Uri.encode(destination.albumArtUri?.toString() ?: "")
                navController.navigate("player/album/${destination.albumId}/${destination.trackId}?title=$title&art=$art") {
                    launchSingleTop = true
                }
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (currentRoute in listOf("album_list", "playlist_list")) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "album_list",
                        onClick = {
                            navController.navigate("album_list") {
                                launchSingleTop = true
                                popUpTo("album_list")
                            }
                        },
                        icon = { Icon(Icons.Filled.Album, contentDescription = null) },
                        label = { Text("アルバム") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == "playlist_list",
                        onClick = {
                            navController.navigate("playlist_list") {
                                launchSingleTop = true
                                popUpTo("album_list")
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                        label = { Text("プレイリスト") },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "album_list",
            modifier = androidx.compose.ui.Modifier.padding(innerPadding),
        ) {
            composable("album_list") {
                AlbumListRoute(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    onAlbumClick = { album ->
                        val title = Uri.encode(album.title)
                        val art = Uri.encode(album.albumArtUri?.toString() ?: "")
                        navController.navigate("track_list/${album.id}?title=$title&art=$art")
                    },
                )
            }
            composable("playlist_list") {
                PlaylistListRoute(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    onPlaylistClick = { playlist ->
                        val name = Uri.encode(playlist.name)
                        navController.navigate("playlist_detail/${playlist.id}?name=$name")
                    },
                )
            }
            composable(
                route = "track_list/{albumId}?title={title}&art={art}",
                arguments = listOf(
                    navArgument("albumId") { type = NavType.LongType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("art") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
                val albumTitle = backStackEntry.arguments?.getString("title").orEmpty()
                val albumArt = backStackEntry.arguments?.getString("art").orEmpty()
                val albumArtUri = albumArt.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
                TrackListRoute(
                    albumId = albumId,
                    onBack = { navController.popBackStack() },
                    onTrackClick = { tracks, index ->
                        val trackId = tracks.getOrNull(index)?.id ?: return@TrackListRoute
                        val title = Uri.encode(albumTitle)
                        val art = Uri.encode(albumArtUri?.toString() ?: "")
                        navController.navigate("player/album/$albumId/$trackId?title=$title&art=$art")
                    },
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                )
            }
            composable(
                route = "playlist_detail/{playlistId}?name={name}",
                arguments = listOf(
                    navArgument("playlistId") { type = NavType.LongType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
                val playlistName = backStackEntry.arguments?.getString("name").orEmpty()
                PlaylistDetailRoute(
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() },
                    onTrackClick = { tracks, index ->
                        val trackId = tracks.getOrNull(index)?.id ?: return@PlaylistDetailRoute
                        val name = Uri.encode(playlistName)
                        navController.navigate("player/playlist/$playlistId/$trackId?name=$name")
                    },
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                )
            }
            composable(
                route = "player/album/{albumId}/{trackId}?title={title}&art={art}",
                arguments = listOf(
                    navArgument("albumId") { type = NavType.LongType },
                    navArgument("trackId") { type = NavType.LongType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("art") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
                val trackId = backStackEntry.arguments?.getLong("trackId") ?: return@composable
                val albumTitle = backStackEntry.arguments?.getString("title").orEmpty()
                val albumArt = backStackEntry.arguments?.getString("art").orEmpty()
                val albumArtUri = albumArt.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
                PlayerRoute(
                    queue = PlaybackQueue.Album(
                        albumId = albumId,
                        albumTitle = albumTitle,
                        albumArtUri = albumArtUri,
                    ),
                    startTrackId = trackId,
                    onBack = { navController.popBackStack() },
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                )
            }
            composable(
                route = "player/playlist/{playlistId}/{trackId}?name={name}",
                arguments = listOf(
                    navArgument("playlistId") { type = NavType.LongType },
                    navArgument("trackId") { type = NavType.LongType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
                val trackId = backStackEntry.arguments?.getLong("trackId") ?: return@composable
                val playlistName = backStackEntry.arguments?.getString("name").orEmpty()
                PlayerRoute(
                    queue = PlaybackQueue.Playlist(
                        playlistId = playlistId,
                        playlistName = playlistName,
                    ),
                    startTrackId = trackId,
                    onBack = { navController.popBackStack() },
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                )
            }
        }
    }
}
