package com.yt8492.asmrplayer.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormattingTest {
    @Test fun listAndPlayerRetainMinuteFormatForLongTracks() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("1:01", formatDuration(61_999))
        assertEquals("60:01", formatDuration(3_601_000))
    }
}
