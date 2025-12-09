package com.yt8492.asmrplayer.data.repository

import com.yt8492.asmrplayer.data.model.Album

interface AlbumRepository {
    suspend fun getAlbums(): List<Album>
}
