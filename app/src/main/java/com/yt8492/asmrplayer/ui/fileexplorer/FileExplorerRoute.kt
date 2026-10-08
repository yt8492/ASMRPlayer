package com.yt8492.asmrplayer.ui.fileexplorer

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.di.appContainer
import com.yt8492.asmrplayer.di.fileExplorerFactory

@Composable
fun FileExplorerRoute(
    onTrackClick: (directoryPath: String, directoryTitle: String, tracks: List<Track>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    resetRequestKey: Int = 0,
    viewModel: FileExplorerViewModel = viewModel(
        factory = LocalContext.current.appContainer().fileExplorerFactory(),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val rootTitle = stringResource(id = R.string.file_explorer_title)
    LifecycleResumeEffect(Unit) {
        viewModel.loadContent()
        onPauseOrDispose { }
    }
    LaunchedEffect(resetRequestKey) {
        if (resetRequestKey > 0) {
            viewModel.resetToInitialState()
        }
    }

    BackHandler(enabled = uiState.currentPath.isNotEmpty()) {
        viewModel.openParentDirectory()
    }

    FileExplorerScreen(
        uiState = uiState,
        onOpenSettings = onOpenSettings,
        onRetry = viewModel::refreshContent,
        onRefresh = viewModel::refreshContent,
        onDirectoryClick = viewModel::openDirectory,
        onBack = viewModel::openParentDirectory,
        onTrackClick = { index ->
            val directoryTitle = uiState.directoryTitle ?: directoryDisplayTitle(uiState.currentPath, rootTitle)
            onTrackClick(uiState.currentPath, directoryTitle, uiState.tracks, index)
        },
        onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
        onCreatePlaylistAndAddTrack = viewModel::createPlaylistAndAddTrack,
        onCreatePlaylistFromDirectory = viewModel::createPlaylistFromDirectory,
        onAddDirectoryToPlaylist = viewModel::addDirectoryToPlaylist,
        onErrorShown = viewModel::consumeError,
        onPlaylistMessageShown = viewModel::consumePlaylistMessage,
        bottomBar = bottomBar,
        resetRequestKey = resetRequestKey,
        modifier = modifier,
    )
}
