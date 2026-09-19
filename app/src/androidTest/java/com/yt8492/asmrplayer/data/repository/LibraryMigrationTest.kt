package com.yt8492.asmrplayer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yt8492.asmrplayer.data.local.AppDatabase
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryMigrationTest {
    private fun AppDatabase.withOpenDatabase(block: (AppDatabase) -> Unit) {
        try { block(this) } finally { close() }
    }

    @Test
    fun バージョン6から更新してもプレイリストとリピート設定を保持する() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "library-migration-${UUID.randomUUID()}.db"
        try {
            // 既存の6テーブルは変更していないため、新規テーブルを除いてv6の構成を作る。
            Room.databaseBuilder(context, AppDatabase::class.java, name).build().withOpenDatabase { database ->
                val sql = database.openHelper.writableDatabase
                sql.execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, '既存リスト', 1, 1)")
                sql.execSQL("INSERT INTO playlist_tracks (id, playlistId, trackId, position, addedAt) VALUES (1, 1, 42, 0, 1)")
                sql.execSQL("INSERT INTO track_loops (trackId, startMs, endMs, updatedAt) VALUES (42, 1000, 3000, 1)")
                sql.execSQL("DROP TABLE library_documents")
                sql.execSQL("DROP TABLE library_folders")
                sql.execSQL("DROP TABLE room_master_table")
                sql.version = 6
            }
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_6_7).build().withOpenDatabase { database ->
                    val sql = database.openHelper.writableDatabase
                    assertEquals(7, sql.version)
                    sql.query("SELECT trackId FROM playlist_tracks WHERE playlistId = 1").use {
                        check(it.moveToFirst())
                        assertEquals(42L, it.getLong(0))
                    }
                    sql.query("SELECT startMs, endMs FROM track_loops WHERE trackId = 42").use {
                        check(it.moveToFirst())
                        assertEquals(1000L, it.getLong(0))
                        assertEquals(3000L, it.getLong(1))
                    }
                    sql.query("SELECT COUNT(*) FROM library_documents").use {
                        check(it.moveToFirst())
                        assertEquals(0, it.getInt(0))
                    }
                }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
