package com.yt8492.asmrplayer.data.repository

import com.yt8492.asmrplayer.data.model.Album

interface AlbumRepository {
    suspend fun getAlbumById(albumId: Long): Album
    suspend fun getAlbums(): List<Album>
}
