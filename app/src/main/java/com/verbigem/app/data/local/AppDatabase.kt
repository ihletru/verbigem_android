package com.verbigem.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HistoryEntity::class, TtsConfigEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun ttsConfigDao(): TtsConfigDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // v1 -> v2: keep existing translation_history rows (syncId/updatedAt default to
        // empty/now) and add the tts_config table for the paid "Read Pro" feature.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE translation_history ADD COLUMN syncId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE translation_history ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tts_config (" +
                        "id INTEGER PRIMARY KEY NOT NULL, " +
                        "apiKey TEXT NOT NULL DEFAULT '', " +
                        "defaultModelId TEXT NOT NULL DEFAULT 'google/gemini-3.1-flash-tts-preview', " +
                        "chineseModelId TEXT NOT NULL DEFAULT 'fish-audio/s2.1-pro', " +
                        "defaultVoice TEXT NOT NULL DEFAULT 'default', " +
                        "chineseVoice TEXT NOT NULL DEFAULT 'default', " +
                        "updatedAt INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "verbigem_db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
        }
    }
}
