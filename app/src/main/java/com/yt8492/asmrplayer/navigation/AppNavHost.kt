package com.yt8492.asmrplayer.navigation

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yt8492.asmrplayer.ui.album.AlbumListRoute
import com.yt8492.asmrplayer.ui.player.PlayerRoute
import com.yt8492.asmrplayer.ui.track.TrackListRoute

@Composable
fun AppNavHost(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = "album_list",
        modifier = modifier,
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
                    navController.navigate("player/$albumId/$trackId?title=$title&art=$art")
                },
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            )
        }
        composable(
            route = "player/{albumId}/{trackId}?title={title}&art={art}",
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
                albumId = albumId,
                albumTitle = albumTitle,
                albumArtUri = albumArtUri,
                startTrackId = trackId,
                onBack = { navController.popBackStack() },
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            )
        }
    }
}
