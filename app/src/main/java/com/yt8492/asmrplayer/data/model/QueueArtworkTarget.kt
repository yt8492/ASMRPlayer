package com.yt8492.asmrplayer.data.model

/** 永続化済みの種別文字列・キーと互換性を持つ画像の設定対象。 */
sealed interface QueueArtworkTarget {
    val type: String
    val key: String
    data class Playlist(val playlistId: Long) : QueueArtworkTarget {
        override val type = "playlist"
        override val key get() = playlistId.toString()
    }
    data class Folder(val directoryPath: String) : QueueArtworkTarget {
        override val type = "folder"
        override val key get() = directoryPath
    }
}
