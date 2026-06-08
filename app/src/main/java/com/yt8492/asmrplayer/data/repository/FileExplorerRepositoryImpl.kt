package com.yt8492.asmrplayer.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yt8492.asmrplayer.data.model.AudioDirectory
import com.yt8492.asmrplayer.data.model.FileExplorerContent
import com.yt8492.asmrplayer.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileExplorerRepositoryImpl(
    private val context: Context,
) : FileExplorerRepository {
    override suspend fun getContent(directoryPath: String): FileExplorerContent = withContext(Dispatchers.IO) {
        val currentPath = normalizeDirectoryPath(directoryPath)
        val directoriesByPath = linkedMapOf<String, AudioDirectoryAccumulator>()
        val tracks = mutableListOf<Track>()
        queryAudioFiles { item ->
            if (item.directoryPath == currentPath) {
                tracks.add(item.track)
                return@queryAudioFiles
            }

            val child = directChildDirectory(currentPath, item.directoryPath) ?: return@queryAudioFiles
            val accumulator = directoriesByPath.getOrPut(child.path) {
                AudioDirectoryAccumulator(path = child.path, name = child.name)
            }
            accumulator.trackCount += 1
        }

        FileExplorerContent(
            currentPath = currentPath,
            directories = directoriesByPath.values
                .map { AudioDirectory(path = it.path, name = it.name, trackCount = it.trackCount) }
                .sortedBy { it.name.lowercase() },
            tracks = tracks,
        )
    }

    private fun queryAudioFiles(onItem: (AudioFileItem) -> Unit) {
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC}!=0"
        val sortOrder = "${MediaStore.Audio.Media.DISPLAY_NAME} COLLATE NOCASE ASC"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumTitleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackNumberColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val title = cursor.getString(titleColumn).orEmpty()
                    .ifEmpty { cursor.getString(displayNameColumn).orEmpty() }
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
                val directoryPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    normalizeDirectoryPath(cursor.getString(pathColumn).orEmpty())
                } else {
                    normalizeLegacyDirectoryPath(cursor.getString(pathColumn).orEmpty())
                }
                onItem(
                    AudioFileItem(
                        directoryPath = directoryPath,
                        track = Track(
                            id = id,
                            title = title,
                            artist = cursor.getString(artistColumn).orEmpty(),
                            albumId = albumId,
                            albumTitle = cursor.getString(albumTitleColumn).orEmpty(),
                            albumArtUri = albumArtUri(albumId),
                            durationMs = cursor.getLong(durationColumn),
                            trackNumber = cursor.getInt(trackNumberColumn),
                            uri = contentUri,
                        ),
                    ),
                )
            }
        }
    }

    private fun directChildDirectory(currentPath: String, candidatePath: String): ChildDirectory? {
        if (!candidatePath.startsWith(currentPath)) return null
        val remainingPath = candidatePath.removePrefix(currentPath).trim('/')
        if (remainingPath.isEmpty()) return null
        val childName = remainingPath.substringBefore('/')
        val childPath = normalizeDirectoryPath(currentPath + childName)
        return ChildDirectory(path = childPath, name = childName)
    }

    private fun normalizeLegacyDirectoryPath(dataPath: String): String {
        val parentPath = File(dataPath).parent.orEmpty()
        val storagePath = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        val relativePath = parentPath.removePrefix(storagePath).trim('/')
        return normalizeDirectoryPath(relativePath)
    }

    private fun albumArtUri(albumId: Long): Uri {
        return ContentUris.withAppendedId(ALBUM_ART_CONTENT_URI, albumId)
    }

    private data class AudioFileItem(
        val directoryPath: String,
        val track: Track,
    )

    private data class ChildDirectory(
        val path: String,
        val name: String,
    )

    private data class AudioDirectoryAccumulator(
        val path: String,
        val name: String,
        var trackCount: Int = 0,
    )

    companion object {
        private val ALBUM_ART_CONTENT_URI = Uri.parse("content://media/external/audio/albumart")
    }
}

fun normalizeDirectoryPath(path: String): String {
    val trimmedPath = path.trim().trim('/')
    return if (trimmedPath.isEmpty()) {
        ""
    } else {
        "$trimmedPath/"
    }
}
