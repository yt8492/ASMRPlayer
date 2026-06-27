package com.yt8492.asmrplayer.data.repository

internal fun normalizeArtistName(artist: String?): String {
    val trimmedArtist = artist.orEmpty().trim()
    return if (trimmedArtist.isUnknownArtistName()) "" else trimmedArtist
}

private fun String.isUnknownArtistName(): Boolean {
    return equals("unknown", ignoreCase = true) ||
        equals("<unknown>", ignoreCase = true)
}
