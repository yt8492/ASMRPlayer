package com.yt8492.asmrplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        TrackLoopEntity::class,
        TrackArtworkEntity::class,
        QueueArtworkEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun trackLoopDao(): TrackLoopDao
    abstract fun trackArtworkDao(): TrackArtworkDao
    abstract fun queueArtworkDao(): QueueArtworkDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `track_loops` (
                        `trackId` INTEGER NOT NULL,
                        `startMs` INTEGER NOT NULL,
                        `endMs` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`trackId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `track_artworks` (
                        `trackId` INTEGER NOT NULL,
                        `imageUri` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`trackId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `queue_artworks` (
                        `queueType` TEXT NOT NULL,
                        `queueId` INTEGER NOT NULL,
                        `imageUri` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`queueType`, `queueId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `queue_artworks_new` (
                        `queueType` TEXT NOT NULL,
                        `queueKey` TEXT NOT NULL,
                        `imageUri` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`queueType`, `queueKey`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `queue_artworks_new` (`queueType`, `queueKey`, `imageUri`, `updatedAt`)
                    SELECT `queueType`, CAST(`queueId` AS TEXT), `imageUri`, `updatedAt`
                    FROM `queue_artworks`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `queue_artworks`")
                db.execSQL("ALTER TABLE `queue_artworks_new` RENAME TO `queue_artworks`")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_tracks_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playlistId` INTEGER NOT NULL,
                        `trackId` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `playlist_tracks_new` (`playlistId`, `trackId`, `position`, `addedAt`)
                    SELECT `playlistId`, `trackId`, `position`, `addedAt`
                    FROM `playlist_tracks`
                    ORDER BY `playlistId`, `position`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `playlist_tracks`")
                db.execSQL("ALTER TABLE `playlist_tracks_new` RENAME TO `playlist_tracks`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId_position` " +
                        "ON `playlist_tracks` (`playlistId`, `position`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId_trackId` " +
                        "ON `playlist_tracks` (`playlistId`, `trackId`)",
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "asmr_player.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
