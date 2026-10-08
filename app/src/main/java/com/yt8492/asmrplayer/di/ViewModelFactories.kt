package com.yt8492.asmrplayer.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yt8492.asmrplayer.playback.model.PlaybackQueue
import com.yt8492.asmrplayer.ui.fileexplorer.FileExplorerViewModel
import com.yt8492.asmrplayer.ui.player.PlayerViewModel
import com.yt8492.asmrplayer.ui.playlist.PlaylistDetailViewModel
import com.yt8492.asmrplayer.ui.playlist.PlaylistListViewModel
import com.yt8492.asmrplayer.ui.settings.SettingsViewModel

private inline fun <reified VM : ViewModel> factory(crossinline create: () -> VM) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(VM::class.java)) { "未対応のViewModel: ${modelClass.name}" }
        return modelClass.cast(create())!!
    }
}

fun AppContainer.fileExplorerFactory(): ViewModelProvider.Factory = factory { FileExplorerViewModel(files, playlists, tracks) }
fun AppContainer.playlistListFactory(): ViewModelProvider.Factory = factory { PlaylistListViewModel(playlists) }
fun AppContainer.playlistDetailFactory(playlistId: Long): ViewModelProvider.Factory = factory { PlaylistDetailViewModel(playlistId, playlists, playlistTracks) }
fun AppContainer.settingsFactory(): ViewModelProvider.Factory = factory { SettingsViewModel(libraryFolders) }
fun AppContainer.playerFactory(
    queue: PlaybackQueue, startTrackId: Long, startPlaylistTrackId: Long?, startIndexHint: Int?,
): ViewModelProvider.Factory = factory {
    PlayerViewModel(queue, startTrackId, startPlaylistTrackId, startIndexHint, tracks, playlistTracks, playlists, trackLoops, artwork)
}
