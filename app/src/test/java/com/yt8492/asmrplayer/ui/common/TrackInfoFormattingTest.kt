package com.yt8492.asmrplayer.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackInfoFormattingTest {
    @Test
    fun formatTrackDuration_0秒を分秒で表示する() {
        assertEquals("0:00", formatTrackDuration(0L))
    }

    @Test
    fun formatTrackDuration_1分未満を分秒で表示する() {
        assertEquals("0:59", formatTrackDuration(59_999L))
    }

    @Test
    fun formatTrackDuration_1時間未満を分秒で表示する() {
        assertEquals("3:30", formatTrackDuration(210_000L))
    }

    @Test
    fun formatTrackDuration_1時間以上を時分秒で表示する() {
        assertEquals("1:02:03", formatTrackDuration(3_723_000L))
    }
}
