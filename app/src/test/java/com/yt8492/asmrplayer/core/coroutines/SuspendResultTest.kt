package com.yt8492.asmrplayer.core.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SuspendResultTest {
    @Test fun cancellationIsRethrownWithoutRunningFailureHandler() = runTest {
        val cancellation = CancellationException("画面を離れた")
        var handled = false
        try {
            runSuspendCatching<Unit> { throw cancellation }.onFailure { handled = true }
            fail("キャンセルはResultに包まない")
        } catch (actual: CancellationException) { assertSame(cancellation, actual) }
        assertFalse(handled)
    }
    @Test fun ordinaryFailureCanBeRenderedAsUiState() = runTest {
        val error = IllegalStateException("読込失敗")
        assertSame(error, runSuspendCatching<Unit> { throw error }.exceptionOrNull())
    }
}
