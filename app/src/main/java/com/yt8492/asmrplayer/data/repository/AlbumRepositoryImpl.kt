package com.yt8492.asmrplayer.data.repository

import android.content.Context
import android.provider.MediaStore
import com.yt8492.asmrplayer.data.model.Album
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class AlbumRepositoryImpl(
    private val context: Context,
) : AlbumRepository {
    override suspend fun getAlbumById(albumId: Long): Album {
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
            MediaStore.Audio.Albums.ALBUM_ART,
        )
        val selection = "${MediaStore.Audio.Albums._ID}=?"
        val selectionArgs = arrayOf(albumId.toString())
        val contentResolver = context.contentResolver
        val album = contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val trackCountColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
            if (!cursor.moveToNext()) {
                error("album not found")
            }
            val id = cursor.getLong(idColumn)
            val title = cursor.getString(albumColumn).orEmpty()
            val artist = normalizeArtistName(cursor.getString(artistColumn))
            val trackCount = cursor.getInt(trackCountColumn)
            val albumArtUri = runCatching {
                val albumArtColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART)
                val albumArt = cursor.getString(albumArtColumn)
                albumArt?.toUri()
            }.getOrNull()
            Album(
                id = id,
                title = title,
                artist = artist,
                trackCount = trackCount,
                albumArtUri = albumArtUri,
            )
        } ?: error("album not found")
        return album
    }

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
            val albumArtColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(albumColumn).orEmpty()
                val artist = normalizeArtistName(cursor.getString(artistColumn))
                val trackCount = cursor.getInt(trackCountColumn)
                val albumArtUri = runCatching {
                    val albumArt = cursor.getString(albumArtColumn)
                    albumArt?.toUri()
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
