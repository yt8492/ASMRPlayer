package com.yt8492.asmrplayer.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.yt8492.asmrplayer.data.model.FileExplorerContent
import com.yt8492.asmrplayer.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FileExplorerRepositoryImpl internal constructor(
    private val context: Context,
    private val folderRepository: LibraryFolderRepository = LibraryFolderRepository(context),
) : FileExplorerRepository {
    override suspend fun getContent(directoryPath: String): FileExplorerContent = withContext(Dispatchers.IO) {
        DocumentPath.parse(directoryPath)?.let { return@withContext folderRepository.getContent(it) }
        FileExplorerContent(
            currentPath = "",
            directories = folderRepository.rootDirectories(),
            tracks = emptyList(),
            images = emptyList(),
        )
    }

    // 自動検出で作成された既存の再生キューに限り、従来のパスから音声を取得する。
    internal suspend fun getMediaStoreTracksInDirectory(directoryPath: String): List<Track> = withContext(Dispatchers.IO) {
        val currentPath = normalizeDirectoryPath(directoryPath)
        buildList {
            queryAudioFiles { item ->
                if (item.directoryPath == currentPath) add(item.track)
            }
        }
    }

    override suspend fun scanDirectory(directoryPath: String): Boolean = withContext(Dispatchers.IO) {
        DocumentPath.parse(directoryPath)?.let {
            folderRepository.reloadFolder(it.treeUri)
            return@withContext true
        }
        val directories = resolveDirectoriesToScan(
            storageRoots = getSharedStorageRoots(),
            directoryPath = directoryPath,
        )
        if (directories.isEmpty()) return@withContext false

        withTimeoutOrNull(MEDIA_SCAN_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val remainingCallbacks = AtomicInteger(directories.size)
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        directories.map { it.absolutePath }.toTypedArray(),
                        null,
                    ) { _, _ ->
                        if (remainingCallbacks.decrementAndGet() == 0 && continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                } catch (throwable: Throwable) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(throwable)
                    }
                }
            }
        } != null
    }

    private fun getSharedStorageRoots(): List<File> {
        val volumeRoots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(StorageManager::class.java)
                .storageVolumes
                .filter { volume ->
                    volume.state == Environment.MEDIA_MOUNTED ||
                        volume.state == Environment.MEDIA_MOUNTED_READ_ONLY
                }
                .mapNotNull { it.directory }
        } else {
            context.getExternalFilesDirs(null)
                .mapNotNull { externalFilesDir ->
                    storageRootFromExternalFilesDir(
                        externalFilesDir = externalFilesDir,
                        packageName = context.packageName,
                    )
                }
        }

        @Suppress("DEPRECATION")
        val primaryStorageRoot = Environment.getExternalStorageDirectory()
        return (volumeRoots + primaryStorageRoot)
            .mapNotNull { root -> runCatching { root.canonicalFile }.getOrNull() }
            .distinctBy { it.absolutePath }
    }

    private fun queryAudioFiles(onItem: (AudioFileItem) -> Unit) {
        if (!context.hasAudioReadPermission()) return
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()
        val selection: String? = null
        val sortOrder = "${MediaStore.Audio.Media.DISPLAY_NAME} COLLATE NOCASE ASC"
        queryMediaStore(
            uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection = projection,
            selection = selection,
            sortOrder = sortOrder,
        ) { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumTitleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
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
                            artist = normalizeArtistName(cursor.getString(artistColumn)),
                            albumId = albumId,
                            albumTitle = cursor.getString(albumTitleColumn).orEmpty(),
                            albumArtUri = albumArtUri(albumId),
                            durationMs = cursor.getLong(durationColumn),
                            fileSizeBytes = cursor.getNullableLong(sizeColumn),
                            trackNumber = cursor.getInt(trackNumberColumn),
                            uri = contentUri,
                        ),
                    ),
                )
            }
        }
    }

    private fun queryMediaStore(
        uri: Uri,
        projection: Array<String>,
        selection: String? = null,
        sortOrder: String? = null,
        onCursor: (Cursor) -> Unit,
    ) {
        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                null,
                sortOrder,
            )?.use(onCursor)
        } catch (_: SecurityException) {
            // 音声権限が取り消された場合も、選択済みフォルダは利用できる。
        }
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

    private fun Cursor.getNullableLong(columnIndex: Int): Long? {
        return if (isNull(columnIndex)) null else getLong(columnIndex)
    }

    private data class AudioFileItem(
        val directoryPath: String,
        val track: Track,
    )

    companion object {
        private const val MEDIA_SCAN_TIMEOUT_MILLIS = 30_000L
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

internal fun resolveDirectoryToScan(storageRoot: File, directoryPath: String): File? {
    val normalizedPath = normalizeDirectoryPath(directoryPath)
    if (normalizedPath.isEmpty()) return null

    val canonicalRoot = storageRoot.canonicalFile
    val canonicalDirectory = File(canonicalRoot, normalizedPath).canonicalFile
    val rootPrefix = canonicalRoot.absolutePath.trimEnd(File.separatorChar) + File.separator
    return canonicalDirectory.takeIf { directory ->
        directory.absolutePath.startsWith(rootPrefix)
    }
}

internal fun resolveDirectoriesToScan(
    storageRoots: List<File>,
    directoryPath: String,
): List<File> {
    return storageRoots
        .mapNotNull { storageRoot -> resolveDirectoryToScan(storageRoot, directoryPath) }
        .distinctBy { it.absolutePath }
}

internal fun storageRootFromExternalFilesDir(
    externalFilesDir: File,
    packageName: String,
): File? {
    val packageDirectory = externalFilesDir.parentFile ?: return null
    val dataDirectory = packageDirectory.parentFile ?: return null
    val androidDirectory = dataDirectory.parentFile ?: return null
    if (
        externalFilesDir.name != "files" ||
        packageDirectory.name != packageName ||
        dataDirectory.name != "data" ||
        androidDirectory.name != "Android"
    ) {
        return null
    }
    return androidDirectory.parentFile
}
