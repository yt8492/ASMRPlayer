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
import com.yt8492.asmrplayer.data.datasource.document.FolderDocument
import com.yt8492.asmrplayer.data.datasource.document.FolderDocumentSource
import com.yt8492.asmrplayer.data.library.DOCUMENT_TRACK_ID_BASE
import com.yt8492.asmrplayer.data.library.DocumentPath
import com.yt8492.asmrplayer.data.library.toEntity
import com.yt8492.asmrplayer.data.local.database.AppDatabase
import com.yt8492.asmrplayer.data.model.documentKind
import com.yt8492.asmrplayer.data.repository.impl.FileExplorerRepositoryImpl
import com.yt8492.asmrplayer.data.repository.impl.LibraryFolderRepositoryImpl
import com.yt8492.asmrplayer.data.repository.impl.TrackRepositoryImpl
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
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
    private data class Query(val sql: String, val args: List<Any?>)
    private val queries = ConcurrentLinkedQueue<Query>()
    private fun librarySelects() = queries.filter {
        it.sql.trimStart().startsWith("SELECT", ignoreCase = true) && "library_" in it.sql
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCallback({ sql, args -> queries.add(Query(sql, args.toList())) }, Executor { it.run() })
            .build()
        source = FakeDocumentSource()
        repository = LibraryFolderRepositoryImpl(database.libraryFolderDao(), source)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun 音声権限なしでファイル一覧は手動追加フォルダだけを表示する() = runBlocking {
        repository.addFolder(tree)
        val explorer = FileExplorerRepositoryImpl(repository)

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
        val explorer = FileExplorerRepositoryImpl(repository)
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
        val content = repository.getContent(rootPath.encode())
        assertEquals("作品", content.directoryTitle)
        assertEquals("", content.parentPath)
        assertEquals("voice.wav", content.tracks.single().title)
        assertTrue(content.tracks.single().id > DOCUMENT_TRACK_ID_BASE)
        assertEquals("cover.png", content.images.single().title)
        assertEquals("content", content.tracks.single().uri.scheme)
        val childPath = DocumentPath.parse(content.directories.single().path)!!
        val child = repository.getContent(childPath.encode())
        assertEquals(rootPath.encode(), child.parentPath)
        assertEquals("extra.wav", child.tracks.single().title)
        assertEquals(2, repository.getFolders().single().audioCount)
    }

    @Test
    fun 再読み込みで追加削除を反映して既存トラックのIDを保持する() = runBlocking {
        repository.addFolder(tree)
        val old = repository.getContent(rootPath.encode()).tracks.single()
        source.children["root"] = source.children.getValue("root") + document("new", "new.wav", "audio/wav")
        repository.reloadFolder(tree.toString())
        val tracks = repository.getContent(rootPath.encode()).tracks
        assertEquals(old.id, tracks.single { it.title == old.title }.id)
        assertEquals(2, tracks.size)
        source.children["root"] = source.children.getValue("root").filterNot { it.documentId == "audio" }
        repository.reloadFolder(tree.toString())
        assertTrue(repository.getTracks(listOf(old.id)).isEmpty())
        assertEquals("new.wav", repository.getContent(rootPath.encode()).tracks.single().title)
    }

    @Test
    fun 途中の読み込み失敗では前回の一覧と曲数を維持する() = runBlocking {
        repository.addFolder(tree)
        val old = repository.getContent(rootPath.encode())
        source.children["root"] = source.children.getValue("root").filterNot { it.documentId == "audio" }
        source.failParent = "nested"
        assertTrue(runCatching { repository.reloadFolder(tree.toString()) }.exceptionOrNull() is IOException)
        assertEquals(old.tracks, repository.getContent(rootPath.encode()).tracks)
        assertEquals(2, repository.getFolders().single().audioCount)
        assertNotNull(repository.getFolders().single().error)
    }

    @Test
    fun 許可取り消しや登録解除後は曲を返さず再追加すると同じIDを使う() = runBlocking {
        repository.addFolder(tree)
        val trackId = repository.getContent(rootPath.encode()).tracks.single().id
        source.grants.clear()
        assertTrue(repository.getTracks(listOf(trackId)).isEmpty())
        assertTrue(runCatching { repository.getContent(rootPath.encode()) }.exceptionOrNull() is SecurityException)
        repository.removeFolder(tree.toString())
        assertTrue(repository.getFolders().isEmpty())
        repository.addFolder(tree)
        assertEquals(trackId, repository.getContent(rootPath.encode()).tracks.single().id)
        assertEquals(trackId, repository.getTracks(listOf(trackId)).single().id)
    }

    @Test
    fun フォルダ情報だけ復元された場合は再許可待ちを表示し同じフォルダでIDを維持して復旧する() = runBlocking {
        repository.addFolder(tree)
        val before = repository.getContent(rootPath.encode())
        // バックアップされたDBは残り、OS側の許可だけ失われた状態を再現する。
        source.grants.clear()
        val restored = LibraryFolderRepositoryImpl(database.libraryFolderDao(), source)
        val explorer = FileExplorerRepositoryImpl(restored)
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
        val before = repository.getContent(rootPath.encode())
        source.grants.clear()
        val other = Uri.parse("content://test.documents/tree/other")
        val error = runCatching { repository.restoreFolderAccess(tree.toString(), other) }.exceptionOrNull()
        assertTrue(error is DifferentFolderSelectedException)
        assertTrue(source.grants.isEmpty())
        assertEquals(listOf(tree.toString()), repository.getFolders().map { it.uri })
        assertFalse(repository.getFolders().single().hasPermission)
        repository.restoreFolderAccess(tree.toString(), tree)
        assertEquals(before.tracks, repository.getContent(rootPath.encode()).tracks)
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
        val first = repository.getContent(rootPath.encode()).tracks.single()
        val second = repository.getContent(DocumentPath(other.toString(), "root").encode()).tracks.single()
        assertNotEquals(first.id, second.id)
        assertEquals(2, repository.getTracks(listOf(first.id, second.id)).size)
    }

    @Test
    fun フォルダと文書IDの特殊文字を含むパスを復元できる() {
        val path = DocumentPath("content://test/tree/primary%3A音声%2F作品", "primary:音声/作品 #1/画像%表紙")
        assertEquals(path, DocumentPath.parse(path.encode()))
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
        val content = repository.getContent(rootPath.encode())
        assertEquals(listOf("01.PDF", "02.txt"), content.documents.map { it.name })
        assertTrue(content.tracks.isEmpty())
        assertTrue(content.images.isEmpty())
        assertEquals(2, repository.getFolders().single().documentCount)
        assertEquals(2, repository.rootDirectories().single().itemCount)
        val id = content.documents.first().id
        source.children["root"] = source.children.getValue("root").filterNot { it.documentId == "text" }
        repository.reloadFolder(tree.toString())
        assertEquals(id, repository.getContent(rootPath.encode()).documents.single().id)
        assertEquals(1, repository.getFolders().single().documentCount)
    }

    @Test
    fun 子フォルダの文書も数え読み込み失敗時には前回の一覧を維持する() = runBlocking {
        source.children["nested"] = listOf(document("text", "説明.txt", "text/plain"))
        repository.addFolder(tree)
        val content = repository.getContent(rootPath.encode())
        val child = repository.getContent(DocumentPath.parse(content.directories.single().path)!!.encode())
        assertEquals("説明.txt", child.documents.single().name)
        assertEquals(1, repository.getFolders().single().documentCount)
        source.failParent = "nested"
        assertTrue(runCatching { repository.reloadFolder(tree.toString()) }.isFailure)
        assertEquals(1, repository.getFolders().single().documentCount)
        assertEquals(content.tracks, repository.getContent(rootPath.encode()).tracks)
    }

    @Test
    fun 音声権限なしで登録済みの曲だけを指定順と重複を保って取得する() = runBlocking {
        repository.addFolder(tree)
        val first = repository.getContent(rootPath.encode()).tracks.single()
        val second = repository.getContent(DocumentPath(tree.toString(), "nested").encode()).tracks.single()
        val tracks = TrackRepositoryImpl(repository)
        // 過去の端末全体の曲IDが混ざっていても、同じ番号の登録曲に読み替えない。
        val oldId = first.id - DOCUMENT_TRACK_ID_BASE
        assertEquals(
            listOf(second, first, second),
            tracks.getTracks(listOf(second.id, oldId, first.id, second.id)),
        )
        assertEquals(listOf(first), tracks.getTracksInDirectory(rootPath.encode()))
        source.grants.clear()
        assertTrue(tracks.getTracks(listOf(first.id)).isEmpty())
    }

    @Test
    fun 従来のフォルダパスは読み込みもメディアスキャンもしない() = runBlocking {
        val context = contextWithoutMediaStoreAccess()
        val tracks = TrackRepositoryImpl(repository)
        val explorer = FileExplorerRepositoryImpl(repository)
        assertTrue(tracks.getTracksInDirectory("Music/").isEmpty())
        assertFalse(explorer.scanDirectory("Music/"))
        assertFalse(explorer.scanDirectory(""))
    }

    @Test
    fun 選択フォルダの再読み込みで追加された曲を取得できる() = runBlocking {
        repository.addFolder(tree)
        val explorer = FileExplorerRepositoryImpl(repository)
        source.children["root"] = source.children.getValue("root") + document("new", "new.wav", "audio/wav")
        assertTrue(explorer.scanDirectory(rootPath.encode()))
        assertEquals(2, explorer.getContent(rootPath.encode()).tracks.size)
        source.grants.clear()
        assertFalse(explorer.scanDirectory(rootPath.encode()))
    }

    @Test
    fun キャンセルされた走査は前回の一覧とエラー状態を書き換えない() = runBlocking {
        repository.addFolder(tree)
        val before = repository.getContent(rootPath.encode()).tracks
        source.cancelParent = "nested"
        try {
            repository.reloadFolder(tree.toString())
            throw AssertionError("キャンセルが伝播する必要がある")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(before, repository.getContent(rootPath.encode()).tracks)
        assertNull(repository.getFolders().single().error)
    }

    @Test
    fun 更新時刻とサイズが同じ音声はメタデータを再読込しない() = runBlocking {
        repository.addFolder(tree)
        assertEquals(2, source.metadataReads)
        repository.reloadFolder(tree.toString())
        assertEquals(2, source.metadataReads)
        source.children["root"] = source.children.getValue("root").map {
            if (it.documentId == "audio") it.copy(modifiedAt = 2) else it
        }
        repository.reloadFolder(tree.toString())
        assertEquals(3, source.metadataReads)
    }

    @Test
    fun 件数集計はMIME優先と拡張子補完を保ち非アクティブ文書を除外する() = runBlocking {
        repository.addFolder(tree)
        val fixtures = listOf(
            "application/pdf" to "説明.txt",
            "APPLICATION/PDF" to "拡張子なし",
            "text/plain" to "メモ.pdf",
            "TEXT/PLAIN" to "拡張子なし",
            "application/octet-stream" to "説明.PDF",
            "application/octet-stream" to "説明.TxT",
            "" to ".pdf",
            "" to "説明.txt",
            "" to "pdf",
            "image/jpeg" to "画像.pdf",
            "text/html" to "ページ.txt",
            Document.MIME_TYPE_DIR to "フォルダ.txt",
            "application/zip" to "圧縮.pdf",
        )
        val dao = database.libraryFolderDao()
        dao.saveDocuments(fixtures.mapIndexed { index, (mime, name) ->
            document("fixture-$index", name, mime).toEntity().copy(parentId = "root")
        } + document("inactive", "削除済み.txt", "text/plain").toEntity().copy(active = false))
        val expectedCount = fixtures.count { (mime, name) -> documentKind(mime, name) != null }
        val folder = repository.getFolders().single()
        assertEquals(expectedCount, folder.documentCount)
        assertEquals(expectedCount, repository.observeFolders().first().single().documentCount)
        assertEquals(expectedCount + folder.audioCount + folder.imageCount, repository.rootDirectories().single().itemCount)
    }

    @Test
    fun フォルダ一覧とルート件数は一度のクエリと権限取得で集計する() = runBlocking {
        repository.addFolder(tree)
        repository.addFolder(Uri.parse("content://other.documents/tree/root"))
        for (load in listOf<suspend () -> Unit>({ repository.getFolders(); Unit }, { repository.rootDirectories(); Unit })) {
            queries.clear()
            source.permissionReads = 0
            load()
            assertEquals(1, librarySelects().size)
            assertEquals(1, source.permissionReads)
        }
    }

    @Test
    fun 直下の子と件数だけを取得し別ツリーや非アクティブ文書を混ぜない() = runBlocking {
        source.children["root"] = source.children.getValue("root") + document("empty", "空", Document.MIME_TYPE_DIR)
        source.children["nested"] = source.children.getValue("nested") + listOf(
            document("pdf", "説明.pdf", "application/pdf"),
            document("deep", "さらに下", Document.MIME_TYPE_DIR),
        )
        source.children["deep"] = listOf(document("deep-audio", "深い曲.wav", "audio/wav"))
        repository.addFolder(tree)
        repository.addFolder(Uri.parse("content://other.documents/tree/root"))
        database.libraryFolderDao().saveDocuments(listOf(
            document("deleted", "削除済み.wav", "audio/wav").toEntity().copy(parentId = "nested", active = false),
        ))
        queries.clear()
        val content = repository.getContent(rootPath.encode())
        assertEquals(listOf("voice.wav"), content.tracks.map { it.title })
        assertEquals(3, content.directories.single { it.name == "特典" }.itemCount)
        assertEquals(0, content.directories.single { it.name == "空" }.itemCount)
        assertFalse(librarySelects().any {
            it.sql.trim().replace(Regex("\\s+"), " ") == "SELECT * FROM library_documents WHERE treeUri = ?"
        })
        assertEquals("作品", content.directoryTitle)
        assertEquals("", content.parentPath)
    }

    @Test
    fun 音声専用取得は画像や件数を取得せず従来の名前順を保つ() = runBlocking {
        source.children["root"] = listOf(
            document("z", "Zulu.wav", "audio/wav"),
            document("a", "Alpha.wav", "audio/wav"),
            document("unicode", "Áudio.wav", "audio/wav"),
            document("ja", "あ.wav", "audio/wav"),
            document("pdf", "説明.pdf", "application/pdf"),
            document("image", "表紙.png", "image/png"),
        )
        repository.addFolder(tree)
        queries.clear()
        val tracks = TrackRepositoryImpl(repository).getTracksInDirectory(rootPath.encode())
        assertEquals(listOf("Alpha.wav", "Zulu.wav", "Áudio.wav", "あ.wav"), tracks.map { it.title })
        val documentQueries = librarySelects().filter { "FROM library_documents" in it.sql }
        assertEquals(2, documentQueries.size)
        assertTrue(documentQueries.all { "EXISTS(" in it.sql || "mimeType GLOB 'audio/*'" in it.sql })
        assertTrue(runCatching { repository.getTracksInDirectory(DocumentPath(tree.toString(), "missing").encode()) }.exceptionOrNull() is IOException)
        source.grants.clear()
        assertTrue(runCatching { repository.getTracksInDirectory(rootPath.encode()) }.exceptionOrNull() is SecurityException)
    }

    @Test
    fun 大量の重複行は一度だけ問い合わせて行順と重複を復元する() = runBlocking {
        repository.addFolder(tree)
        val track = repository.getContent(rootPath.encode()).tracks.single()
        queries.clear()
        source.permissionReads = 0
        val tracks = TrackRepositoryImpl(repository).getTracks(List(1801) { track.id })
        assertEquals(List(1801) { track }, tracks)
        assertEquals(1, source.permissionReads)
        val lookups = librarySelects().filter { "id IN" in it.sql }
        assertEquals(1, lookups.size)
        assertEquals(1, lookups.single().args.size)
    }

    @Test
    fun 分割取得でも権限一覧を共有し次の取得では権限喪失を反映する() = runBlocking {
        repository.addFolder(tree)
        val dao = database.libraryFolderDao()
        dao.saveDocuments(List(1100) { index ->
            document("bulk-$index", "$index.wav", "audio/wav").toEntity().copy(parentId = "root")
        })
        val ids = dao.getDocuments(tree.toString()).filter { it.documentId.startsWith("bulk-") }
            .map { it.id + DOCUMENT_TRACK_ID_BASE }
        queries.clear()
        source.permissionReads = 0
        assertEquals(1100, repository.getTracks(ids).size)
        assertEquals(1, source.permissionReads)
        val lookups = librarySelects().filter { "id IN" in it.sql }
        assertEquals(listOf(900, 200), lookups.map { it.args.size })
        source.grants.clear()
        assertTrue(repository.getTracks(ids).isEmpty())
        assertEquals(2, source.permissionReads)
    }

    private fun contextWithoutMediaStoreAccess(): Context = object : ContextWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
    ) {
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int = PackageManager.PERMISSION_DENIED

        override fun getContentResolver(): ContentResolver {
            throw AssertionError("手動追加フォルダの取得でMediaStoreを読み取らない")
        }
    }

    private inner class FakeDocumentSource : FolderDocumentSource {
        val grants = mutableSetOf<String>()
        var failParent: String? = null
        var cancelParent: String? = null
        var metadataReads = 0
        var permissionReads = 0
        val children = mutableMapOf(
            "root" to listOf(document("audio", "voice.wav", "audio/wav"), document("image", "cover.png", "image/png"),
                document("nested", "特典", Document.MIME_TYPE_DIR)),
            "nested" to listOf(document("extra", "extra.wav", "audio/wav")),
        )

        override fun readableTreeUris(): Set<String> { permissionReads += 1; return grants.toSet() }
        override fun persistPermission(uri: Uri) { grants.add(uri.toString()) }
        override fun releasePermission(uri: String) { grants.remove(uri) }
        override fun readAudioMetadata(item: FolderDocument): FolderDocument { metadataReads += 1; return item }

        override suspend fun queryDocuments(uri: Uri, treeUri: String, parentId: String?): List<FolderDocument> {
            if (parentId != null && parentId == cancelParent) throw kotlinx.coroutines.CancellationException()
            if (parentId != null && parentId == failParent) throw IOException("読み込み失敗")
            val result = if (parentId == null) {
                listOf(document(DocumentsContract.getDocumentId(uri), "作品", Document.MIME_TYPE_DIR))
            } else children[parentId].orEmpty()
            return result.map { it.copy(treeUri = treeUri, parentId = parentId) }
        }
    }

    private fun document(id: String, name: String, mime: String) = FolderDocument(
        treeUri = tree.toString(), documentId = id, parentId = null, name = name,
        mimeType = mime, size = 100, modifiedAt = 1,
    )
}
