package com.yt8492.asmrplayer.ui.common

import java.util.concurrent.TimeUnit

/** プレイヤー・一覧の共通表記。1時間以上も分を繰り上げて表示する。 */
internal fun formatDuration(durationMs: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
