package com.yt8492.asmrplayer.data.model

data class BrowsableDirectory(
    val path: String,
    val name: String,
    val itemCount: Int,
    val hasPermission: Boolean = true,
)
