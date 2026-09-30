package com.yt8492.asmrplayer.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yt8492.asmrplayer.data.local.AppDatabase
import com.yt8492.asmrplayer.data.local.LibraryDocumentEntity
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryFolderRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var source: FakeDocumentSource
    private lateinit var repository: LibraryFolderRepository
    private val tree = Uri.parse("content://test.documents/tree/root")
    private val rootPath = DocumentPath(tree.toString(), "root")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        source = FakeDocumentSource()
        repository = LibraryFolderRepository(context, database, source)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun 音声権限があってもファイル一覧は手動追加フォルダだけを表示する() = runBlocking {
        repository.addFolder(tree)
        val explorer = FileExplorerRepositoryImpl(contextWithoutMediaStoreAccess(), repository)

        val root = explorer.getContent("")
        assertEquals(listOf(rootPath.encode()), root.directories.map { it.path })
        assertTrue(root.tracks.isEmpty())
        assertTrue(root.images.isEmpty())

        val folder = explorer.getContent(root.directories.single().path)
        assertEquals("voice.wav", folder.tracks.single().title)
        assertEquals("cover.png", folder.images.single().title)
        val child = explorer.getContent(folder.directories.single().path)
        assertEquals("extra.wav", child.tracks.single().title)
    }

    @Test
    fun フォルダ未登録時や従来パスからの表示でも自動検出一覧を返さない() = runBlocking {
        val explorer = FileExplorerRepositoryImpl(contextWithoutMediaStoreAccess(), repository)
        for (path in listOf("", "Music/")) {
            val content = explorer.getContent(path)
            assertEquals("", content.currentPath)
            assertTrue(content.directories.isEmpty())
            assertTrue(content.tracks.isEmpty())
            assertTrue(content.images.isEmpty())
        }
    }

    @Test
    fun 音声権限なしで選択フォルダの音声と画像と子フォルダを取得できる() = runBlocking {
        repository.addFolder(tree)
        val content = repository.getContent(rootPath)
        assertEquals("作品", content.directoryTitle)
        assertEquals("", content.parentPath)
        assertEquals("voice.wav", content.tracks.single().title)
        assertTrue(content.tracks.single().id > DOCUMENT_TRACK_ID_BASE)
        assertEquals("cover.png", content.images.single().title)
        assertEquals("content", content.tracks.single().uri.scheme)
        val childPath = DocumentPath.parse(content.directories.single().path)!!
        val child = repository.getContent(childPath)
        assertEquals(rootPath.encode(), child.parentPath)
        assertEquals("extra.wav", child.tracks.single().title)
        assertEquals(2, repository.getFolders().single().audioCount)
    }

    @Test
    fun 再読み込みで追加削除を反映して既存トラックのIDを保持する() = runBlocking {
        repository.addFolder(tree)
        val old = repository.getContent(rootPath).tracks.single()
        source.children["root"] = source.children.getValue("root") + document("new", "new.wav", "audio/wav")
        repository.reloadFolder(tree.toString())
        val tracks = repository.getContent(rootPath).tracks
        assertEquals(old.id, tracks.single { it.title == old.title }.id)
        assertEquals(2, tracks.size)
        source.children["root"] = source.children.getValue("root").filterNot { it.documentId == "audio" }
        repository.reloadFolder(tree.toString())
        assertTrue(repository.getTracks(listOf(old.id)).isEmpty())
        assertEquals("new.wav", repository.getContent(rootPath).tracks.single().title)
    }

    @Test
    fun 途中の読み込み失敗では前回の一覧と曲数を維持する() = runBlocking {
        repository.addFolder(tree)
        val old = repository.getContent(rootPath)
        source.children["root"] = source.children.getValue("root").filterNot { it.documentId == "audio" }
        source.failParent = "nested"
        assertTrue(runCatching { repository.reloadFolder(tree.toString()) }.exceptionOrNull() is IOException)
        assertEquals(old.tracks, repository.getContent(rootPath).tracks)
        assertEquals(2, repository.getFolders().single().audioCount)
        assertNotNull(repository.getFolders().single().error)
    }

    @Test
    fun 許可取り消しや登録解除後は曲を返さず再追加すると同じIDを使う() = runBlocking {
        repository.addFolder(tree)
        val trackId = repository.getContent(rootPath).tracks.single().id
        source.grants.clear()
        assertTrue(repository.getTracks(listOf(trackId)).isEmpty())
        assertTrue(runCatching { repository.getContent(rootPath) }.exceptionOrNull() is SecurityException)
        repository.removeFolder(tree.toString())
        assertTrue(repository.getFolders().isEmpty())
        repository.addFolder(tree)
        assertEquals(trackId, repository.getContent(rootPath).tracks.single().id)
        assertEquals(trackId, repository.getTracks(listOf(trackId)).single().id)
    }

    @Test
    fun フォルダ情報だけ復元された場合は再許可待ちを表示し同じフォルダでIDを維持して復旧する() = runBlocking {
        repository.addFolder(tree)
        val before = repository.getContent(rootPath)
        // バックアップされたDBは残り、OS側の許可だけ失われた状態を再現する。
        source.grants.clear()
        val restored = LibraryFolderRepository(ApplicationProvider.getApplicationContext(), database, source)
        val explorer = FileExplorerRepositoryImpl(contextWithoutMediaStoreAccess(), restored)
        val pending = restored.getFolders().single()
        assertFalse(pending.hasPermission)
        assertNull(pending.error)
        val root = explorer.getContent(rootPath.encode())
        assertEquals("", root.currentPath)
        assertFalse(root.directories.single().hasPermission)
        assertFalse(explorer.scanDirectory(rootPath.encode()))
        assertTrue(restored.getTracks(before.tracks.map { it.id }).isEmpty())

        restored.restoreFolderAccess(tree.toString(), tree)

        assertTrue(restored.getFolders().single().hasPermission)
        assertTrue(explorer.getContent("").directories.single().hasPermission)
        val after = explorer.getContent(rootPath.encode())
        assertEquals(before.tracks, after.tracks)
        assertEquals(before.images, after.images)
        assertEquals(before.tracks, restored.getTracks(before.tracks.map { it.id }))
        assertEquals(1, restored.getFolders().size)
    }

    @Test
    fun 再許可で別のフォルダを選んでも登録と曲の参照を書き換えない() = runBlocking {
        repository.addFolder(tree)
        val before = repository.getContent(rootPath)
        source.grants.clear()
        val other = Uri.parse("content://test.documents/tree/other")
        val error = runCatching { repository.restoreFolderAccess(tree.toString(), other) }.exceptionOrNull()
        assertTrue(error is DifferentFolderSelectedException)
        assertTrue(source.grants.isEmpty())
        assertEquals(listOf(tree.toString()), repository.getFolders().map { it.uri })
        assertFalse(repository.getFolders().single().hasPermission)
        repository.restoreFolderAccess(tree.toString(), tree)
        assertEquals(before.tracks, repository.getContent(rootPath).tracks)
    }

    @Test
    fun 再許可待ちの再読み込みではフォルダ情報にエラーを保存しない() = runBlocking {
        repository.addFolder(tree)
        val saved = database.libraryFolderDao().getFolders().single()
        source.grants.clear()
        val error = runCatching { repository.reloadFolder(tree.toString()) }.exceptionOrNull()
        assertTrue(error is SecurityException)
        assertEquals(saved, database.libraryFolderDao().getFolders().single())
        assertNull(repository.getFolders().single().error)
    }

    @Test
    fun 同名の別フォルダにある曲は異なるIDを持つ() = runBlocking {
        repository.addFolder(tree)
        val other = Uri.parse("content://other.documents/tree/root")
        repository.addFolder(other)
        val first = repository.getContent(rootPath).tracks.single()
        val second = repository.getContent(DocumentPath(other.toString(), "root")).tracks.single()
        assertNotEquals(first.id, second.id)
        assertEquals(2, repository.getTracks(listOf(first.id, second.id)).size)
    }

    @Test
    fun フォルダと文書IDの特殊文字を含むパスを復元できる() {
        val path = DocumentPath("content://test/tree/primary%3A音声%2F作品", "primary:音声/作品 #1/画像%表紙")
        assertEquals(path, DocumentPath.parse(normalizeDirectoryPath(path.encode())))
        assertNull(DocumentPath.parse("Music/ASMR/"))
    }

    @Test
    fun PDFとtxtだけのフォルダを一覧と件数に含める() = runBlocking {
        source.children["root"] = listOf(
            document("pdf", "01.PDF", "application/octet-stream"),
            document("text", "02.txt", "text/plain"),
            document("zip", "03.zip", "application/zip"),
        )
        repository.addFolder(tree)
        val content = repository.getContent(rootPath)
        assertEquals(listOf("01.PDF", "02.txt"), content.documents.map { it.name })
        assertTrue(content.tracks.isEmpty())
        assertTrue(content.images.isEmpty())
        assertEquals(2, repository.getFolders().single().documentCount)
        assertEquals(2, repository.rootDirectories().single().trackCount)
        val id = content.documents.first().id
        source.children["root"] = source.children.getValue("root").filterNot { it.documentId == "text" }
        repository.reloadFolder(tree.toString())
        assertEquals(id, repository.getContent(rootPath).documents.single().id)
        assertEquals(1, repository.getFolders().single().documentCount)
    }

    @Test
    fun 子フォルダの文書も数え読み込み失敗時には前回の一覧を維持する() = runBlocking {
        source.children["nested"] = listOf(document("text", "説明.txt", "text/plain"))
        repository.addFolder(tree)
        val content = repository.getContent(rootPath)
        val child = repository.getContent(DocumentPath.parse(content.directories.single().path)!!)
        assertEquals("説明.txt", child.documents.single().name)
        assertEquals(1, repository.getFolders().single().documentCount)
        source.failParent = "nested"
        assertTrue(runCatching { repository.reloadFolder(tree.toString()) }.isFailure)
        assertEquals(1, repository.getFolders().single().documentCount)
        assertEquals(content.tracks, repository.getContent(rootPath).tracks)
    }

    private fun contextWithoutMediaStoreAccess(): Context = object : ContextWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
    ) {
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int = PackageManager.PERMISSION_GRANTED

        override fun getContentResolver(): ContentResolver {
            throw AssertionError("ファイル一覧でMediaStoreを読み取らない")
        }
    }

    private inner class FakeDocumentSource : FolderDocumentSource {
        val grants = mutableSetOf<String>()
        var failParent: String? = null
        val children = mutableMapOf(
            "root" to listOf(document("audio", "voice.wav", "audio/wav"), document("image", "cover.png", "image/png"),
                document("nested", "特典", Document.MIME_TYPE_DIR)),
            "nested" to listOf(document("extra", "extra.wav", "audio/wav")),
        )

        override fun hasPermission(uri: String) = uri in grants
        override fun persistPermission(uri: Uri) { grants.add(uri.toString()) }
        override fun releasePermission(uri: String) { grants.remove(uri) }
        override fun readAudioMetadata(item: LibraryDocumentEntity) = item

        override suspend fun queryDocuments(uri: Uri, treeUri: String, parentId: String?): List<LibraryDocumentEntity> {
            if (parentId != null && parentId == failParent) throw IOException("読み込み失敗")
            val result = if (parentId == null) {
                listOf(document(DocumentsContract.getDocumentId(uri), "作品", Document.MIME_TYPE_DIR))
            } else children[parentId].orEmpty()
            return result.map { it.copy(treeUri = treeUri, parentId = parentId) }
        }
    }

    private fun document(id: String, name: String, mime: String) = LibraryDocumentEntity(
        treeUri = tree.toString(), documentId = id, parentId = null, name = name,
        mimeType = mime, size = 100, modifiedAt = 1,
    )
}
