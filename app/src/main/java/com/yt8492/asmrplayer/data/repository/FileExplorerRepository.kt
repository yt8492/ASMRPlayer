package com.yt8492.asmrplayer.data.repository

import com.yt8492.asmrplayer.data.model.FileExplorerContent

interface FileExplorerRepository {
    suspend fun getContent(directoryPath: String): FileExplorerContent

    /**
     * 接続中の各共有ストレージにある指定相対パスをMediaStoreへベストエフォートで再スキャンする。
     *
     * 公開APIはファイルのスキャンのみを保証しているため、ディレクトリの再帰処理は端末実装に依存する。
     */
    suspend fun scanDirectory(directoryPath: String): Boolean
}
