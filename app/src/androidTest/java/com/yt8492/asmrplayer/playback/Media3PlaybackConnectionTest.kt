package com.yt8492.asmrplayer.playback

import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Media3PlaybackConnectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var future: ListenableFuture<MediaController>
    private var connection: Media3PlaybackConnection? = null

    @Before fun setUp() = instrumentation.runOnMainSync {
        player = ExoPlayer.Builder(context).build()
        session = MediaSession.Builder(context, player).build()
        future = MediaController.Builder(context, session.token).buildAsync()
    }
    @After fun tearDown() = instrumentation.runOnMainSync {
        connection?.close()
        MediaController.releaseFuture(future)
        session.release()
        player.release()
    }

    @Test fun connectedControllerIsSharedAndCloseIsIdempotent() {
        future.get(10, TimeUnit.SECONDS)
        instrumentation.runOnMainSync {
            connection = Media3PlaybackConnection(future, ContextCompat.getMainExecutor(context))
        }
        // MainExecutorはmain上で呼んでもコールバックをpostする。
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertSame(future.get(), connection!!.state.value.controller)
            connection!!.close()
            connection!!.close()
            assertNull(connection!!.state.value.controller)
            assertFalse(connection!!.state.value.connectionFailed)
        }
    }
    @Test fun closingWhileCompletionCallbackIsQueuedCannotReattachController() {
        future.get(10, TimeUnit.SECONDS)
        val callbacks = mutableListOf<Runnable>()
        instrumentation.runOnMainSync {
            connection = Media3PlaybackConnection(future, Executor { callbacks.add(it) })
            assertEquals(1, callbacks.size)
            connection!!.close()
            callbacks.single().run()
            assertNull(connection!!.state.value.controller)
            assertFalse(connection!!.state.value.connectionFailed)
        }
    }
    @Test fun closingBeforeConnectionCancelsFutureWithoutPublishingConnectionError() {
        val pending = SettableFuture.create<MediaController>()
        instrumentation.runOnMainSync {
            val connecting = Media3PlaybackConnection(pending, ContextCompat.getMainExecutor(context))
            connecting.close()
            connecting.close()
            assertTrue(pending.isCancelled)
            assertFalse(connecting.state.value.connectionFailed)
            assertNull(connecting.state.value.controller)
        }
    }
}
