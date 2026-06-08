package com.yt8492.asmrplayer.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.yt8492.asmrplayer.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrackRepositoryImpl(
    private val context: Context,
) : TrackRepository {
    override suspend fun getTracks(albumId: Long): List<Track> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
        )
        val selection = """
            ${MediaStore.Audio.Media.ALBUM_ID}=? AND
            ${MediaStore.Audio.Media.IS_MUSIC}!=0
        """
        val selectionArgs = arrayOf(albumId.toString())
        val sortOrder = "${MediaStore.Audio.Media.TRACK} ASC"
        val contentResolver = context.contentResolver
        val tracks = mutableListOf<Track>()
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumTitleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackNumberColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn).orEmpty()
                val artist = cursor.getString(artistColumn).orEmpty()
                val trackAlbumId = cursor.getLong(albumIdColumn)
                val albumTitle = cursor.getString(albumTitleColumn).orEmpty()
                val durationMs = cursor.getLong(durationColumn)
                val trackNumber = cursor.getInt(trackNumberColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
                tracks.add(
                    Track(
                        id = id,
                        title = title,
                        artist = artist,
                        albumId = trackAlbumId,
                        albumTitle = albumTitle,
                        albumArtUri = albumArtUri(trackAlbumId),
                        durationMs = durationMs,
                        trackNumber = trackNumber,
                        uri = contentUri,
                    ),
                )
            }
        }
        tracks
    }

    override suspend fun getTracks(trackIds: List<Long>): List<Track> = withContext(Dispatchers.IO) {
        if (trackIds.isEmpty()) {
            return@withContext emptyList()
        }
        val tracksById = mutableMapOf<Long, Track>()
        trackIds.chunked(MAX_SELECTION_ARGS).forEach { chunkedTrackIds ->
            queryTracks(chunkedTrackIds, tracksById)
        }
        trackIds.mapNotNull { tracksById[it] }
    }

    private fun queryTracks(trackIds: List<Long>, tracksById: MutableMap<Long, Track>) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
        )
        val placeholders = trackIds.joinToString(",") { "?" }
        val selection = """
            ${MediaStore.Audio.Media._ID} IN ($placeholders) AND
            ${MediaStore.Audio.Media.IS_MUSIC}!=0
        """
        val selectionArgs = trackIds.map { it.toString() }.toTypedArray()
        val contentResolver = context.contentResolver
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumTitleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackNumberColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
                tracksById[id] = Track(
                    id = id,
                    title = cursor.getString(titleColumn).orEmpty(),
                    artist = cursor.getString(artistColumn).orEmpty(),
                    albumId = cursor.getLong(albumIdColumn),
                    albumTitle = cursor.getString(albumTitleColumn).orEmpty(),
                    albumArtUri = albumArtUri(cursor.getLong(albumIdColumn)),
                    durationMs = cursor.getLong(durationColumn),
                    trackNumber = cursor.getInt(trackNumberColumn),
                    uri = contentUri,
                )
            }
        }
    }

    private fun albumArtUri(albumId: Long): Uri {
        return ContentUris.withAppendedId(ALBUM_ART_CONTENT_URI, albumId)
    }

    companion object {
        private const val MAX_SELECTION_ARGS = 900
        private val ALBUM_ART_CONTENT_URI = Uri.parse("content://media/external/audio/albumart")
    }
}
