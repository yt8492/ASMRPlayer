package com.yt8492.asmrplayer.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DirectoryScanPathTest {
    private val storageRoot = File("/storage/emulated/0")

    @Test
    fun 相対パスを共有ストレージ配下のディレクトリへ変換する() {
        val directory = resolveDirectoryToScan(storageRoot, "Music/ASMR/")

        assertEquals(File("/storage/emulated/0/Music/ASMR"), directory)
    }

    @Test
    fun ルート階層はスキャン対象にしない() {
        assertNull(resolveDirectoryToScan(storageRoot, ""))
    }

    @Test
    fun 共有ストレージ外へ抜けるパスは拒否する() {
        assertNull(resolveDirectoryToScan(storageRoot, "../Android/"))
        assertNull(resolveDirectoryToScan(storageRoot, "Music/../../../data/"))
    }

    @Test
    fun 同じ相対パスを接続中の全共有ストレージへ展開する() {
        val directories = resolveDirectoriesToScan(
            storageRoots = listOf(
                File("/storage/emulated/0"),
                File("/storage/ABCD-1234"),
            ),
            directoryPath = "Music/ASMR/",
        )

        assertEquals(
            listOf(
                File("/storage/emulated/0/Music/ASMR"),
                File("/storage/ABCD-1234/Music/ASMR"),
            ),
            directories,
        )
    }

    @Test
    fun 不正な相対パスはどの共有ストレージにも展開しない() {
        val directories = resolveDirectoriesToScan(
            storageRoots = listOf(
                File("/storage/emulated/0"),
                File("/storage/ABCD-1234"),
            ),
            directoryPath = "../Android/",
        )

        assertTrue(directories.isEmpty())
    }

    @Test
    fun 外部ファイルディレクトリから共有ストレージのルートを取得する() {
        val root = storageRootFromExternalFilesDir(
            externalFilesDir = File("/storage/ABCD-1234/Android/data/com.yt8492.asmrplayer/files"),
            packageName = "com.yt8492.asmrplayer",
        )

        assertEquals(File("/storage/ABCD-1234"), root)
    }

    @Test
    fun 想定外の外部ファイルディレクトリは共有ストレージとして扱わない() {
        assertNull(
            storageRootFromExternalFilesDir(
                externalFilesDir = File("/storage/ABCD-1234/app/files"),
                packageName = "com.yt8492.asmrplayer",
            ),
        )
        assertNull(
            storageRootFromExternalFilesDir(
                externalFilesDir = File("/storage/ABCD-1234/Android/data/other.package/files"),
                packageName = "com.yt8492.asmrplayer",
            ),
        )
        assertNull(
            storageRootFromExternalFilesDir(
                externalFilesDir = File("/storage/ABCD-1234/Android/data/com.yt8492.asmrplayer/cache"),
                packageName = "com.yt8492.asmrplayer",
            ),
        )
    }
}
