package com.yt8492.asmrplayer.data.repository

import android.content.Context
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.yt8492.asmrplayer.data.model.Album
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlbumRepositoryImpl(
    private val context: Context,
) : AlbumRepository {
    override suspend fun getAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
            MediaStore.Audio.Albums.ALBUM_ART,
        )
        val sortOrder = "${MediaStore.Audio.Albums.ALBUM} COLLATE NOCASE ASC"
        val contentResolver = context.contentResolver
        val albums = mutableListOf<Album>()
        contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val trackCountColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(albumColumn).orEmpty()
                val artist = cursor.getString(artistColumn).orEmpty()
                val trackCount = cursor.getInt(trackCountColumn)
                val albumArtUri = runCatching {
                    val albumArt = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART))
                    if (albumArt != null) {
                        Uri.parse(albumArt)
                    } else {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            id,
                        )
                    }
                }.getOrNull()
                albums.add(
                    Album(
                        id = id,
                        title = title,
                        artist = artist,
                        trackCount = trackCount,
                        albumArtUri = albumArtUri,
                    ),
                )
            }
        }
        albums
    }
}
