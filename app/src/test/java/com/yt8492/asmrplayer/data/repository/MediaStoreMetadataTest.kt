package com.yt8492.asmrplayer.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreMetadataTest {
    @Test
    fun normalizeArtistName_unknownは空文字にする() {
        assertEquals("", normalizeArtistName("unknown"))
        assertEquals("", normalizeArtistName("Unknown"))
        assertEquals("", normalizeArtistName("<unknown>"))
    }

    @Test
    fun normalizeArtistName_前後の空白を取り除く() {
        assertEquals("アーティスト", normalizeArtistName(" アーティスト "))
    }
}
