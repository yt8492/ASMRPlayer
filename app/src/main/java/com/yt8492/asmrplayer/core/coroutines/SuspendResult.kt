package com.yt8492.asmrplayer.core.coroutines

import kotlinx.coroutines.CancellationException

/** キャンセルは操作失敗に変換せず、呼び出し元のJobへ伝える。 */
internal suspend inline fun <T> runSuspendCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
