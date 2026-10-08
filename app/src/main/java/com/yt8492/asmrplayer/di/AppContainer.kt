package com.yt8492.asmrplayer.di

import android.content.Context
import com.yt8492.asmrplayer.ASMRPlayerApplication
import com.yt8492.asmrplayer.data.datasource.document.AndroidArtworkPermissionSource
import com.yt8492.asmrplayer.data.datasource.document.AndroidFolderDocumentSource
import com.yt8492.asmrplayer.data.datasource.preferences.LibrarySettingsDataSource
import com.yt8492.asmrplayer.data.local.database.AppDatabase
import com.yt8492.asmrplayer.data.repository.ArtworkRepository
import com.yt8492.asmrplayer.data.repository.FileExplorerRepository
import com.yt8492.asmrplayer.data.repository.LibraryFolderRepository
import com.yt8492.asmrplayer.data.repository.LibrarySettingsRepository
import com.yt8492.asmrplayer.data.repository.PlaylistRepository
import com.yt8492.asmrplayer.data.repository.TrackLoopRepository
import com.yt8492.asmrplayer.data.repository.TrackRepository
import com.yt8492.asmrplayer.data.repository.impl.ArtworkRepositoryImpl
import com.yt8492.asmrplayer.data.repository.impl.FileExplorerRepositoryImpl
import com.yt8492.asmrplayer.data.repository.impl.LibraryFolderRepositoryImpl
import com.yt8492.asmrplayer.data.repository.impl.LibrarySettingsRepositoryImpl
import com.yt8492.asmrplayer.data.repository.impl.PlaylistRepositoryImpl
import com.yt8492.asmrplayer.data.repository.impl.QueueArtworkRepositoryImpl
import com.yt8492.asmrplayer.data.repository.impl.TrackArtworkRepositoryImpl
import com.yt8492.asmrplayer.data.repository.impl.TrackLoopRepositoryImpl
import com.yt8492.asmrplayer.data.repository.impl.TrackRepositoryImpl
import com.yt8492.asmrplayer.domain.ResolvePlaylistTracks

/** DBとRepositoryの生成・共有範囲をApplicationに集約する。 */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    val libraryFolders: LibraryFolderRepository by lazy {
        LibraryFolderRepositoryImpl(database.libraryFolderDao(), AndroidFolderDocumentSource(applicationContext))
    }
    val librarySettings: LibrarySettingsRepository by lazy {
        LibrarySettingsRepositoryImpl(LibrarySettingsDataSource(applicationContext))
    }
    val tracks: TrackRepository by lazy { TrackRepositoryImpl(libraryFolders) }
    val playlistTracks by lazy { ResolvePlaylistTracks(tracks) }
    val files: FileExplorerRepository by lazy { FileExplorerRepositoryImpl(libraryFolders) }
    val playlists: PlaylistRepository by lazy { PlaylistRepositoryImpl(database.playlistDao()) }
    val trackLoops: TrackLoopRepository by lazy { TrackLoopRepositoryImpl(database.trackLoopDao()) }
    val artwork: ArtworkRepository by lazy {
        ArtworkRepositoryImpl(
            TrackArtworkRepositoryImpl(database.trackArtworkDao()),
            QueueArtworkRepositoryImpl(database.queueArtworkDao()),
            AndroidArtworkPermissionSource(applicationContext.contentResolver),
        )
    }
}

fun Context.appContainer(): AppContainer = (applicationContext as ASMRPlayerApplication).container
