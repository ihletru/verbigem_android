package com.verbigem.app.data.local

import android.content.Context
import android.database.Cursor
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HistoryEntity::class,
        TtsConfigEntity::class,
        PendingDeleteEntity::class,
        OcrHistoryEntity::class,
        ChatTranslationEntity::class,
        ChatOutboxEntity::class,
        ChatReadEntity::class,
        ChatDeletedEntity::class,
        ChatHiddenEntity::class,
        ExternalContactEntity::class,
        ExternalOutboxEntity::class,
        GlossaryEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun ttsConfigDao(): TtsConfigDao
    abstract fun pendingDeleteDao(): PendingDeleteDao
    abstract fun ocrHistoryDao(): OcrHistoryDao
    abstract fun chatTranslationDao(): ChatTranslationDao
    abstract fun chatOutboxDao(): ChatOutboxDao
    abstract fun chatReadDao(): ChatReadDao
    abstract fun chatDeletedDao(): ChatDeletedDao
    abstract fun chatHiddenDao(): ChatHiddenDao
    abstract fun externalContactDao(): ExternalContactDao
    abstract fun externalOutboxDao(): ExternalOutboxDao
    abstract fun glossaryDao(): GlossaryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // Returns true if [column] already exists in [table].
        private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            var cursor: Cursor? = null
            return try {
                cursor = db.query("PRAGMA table_info($table)")
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIdx) == column) return true
                }
                false
            } finally {
                cursor?.close()
            }
        }

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

        // v2 -> v3: add the pending_deletes queue used to propagate local deletions to other
        // devices as Firestore tombstones ({syncId, deleted:true}). Physically deleted rows
        // leave no trace in translation_history, so this tiny table is the only record of them.
        // `collection` routes the tombstone to the right subcollection (history vs ocr_history).
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_deletes (" +
                        "syncId TEXT NOT NULL PRIMARY KEY, " +
                        "collection TEXT NOT NULL DEFAULT 'history', " +
                        "updatedAt INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        // v3 -> v4: add the OCR history table so OCR translations keep their own history,
        // separate from the (synced) Translator history. Includes syncId/updatedAt so it can
        // be synced to Firestore (the OcrHistoryEntity schema MUST match — Room validates this
        // at runtime and crashes on startup if a declared column is missing).
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ocr_history (" +
                        "id INTEGER PRIMARY KEY NOT NULL, " +
                        "syncId TEXT NOT NULL DEFAULT '', " +
                        "sourceText TEXT NOT NULL DEFAULT '', " +
                        "translatedText TEXT NOT NULL DEFAULT '', " +
                        "sourceLang TEXT NOT NULL DEFAULT 'en', " +
                        "targetLang TEXT NOT NULL DEFAULT 'pl', " +
                        "timestamp INTEGER NOT NULL DEFAULT 0, " +
                        "updatedAt INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        // v4 -> v5: repair databases that were created/upgraded by a BROKEN build where
        // MIGRATION_2_3 omitted the `collection` column and/or MIGRATION_3_4 omitted
        // `syncId`/`updatedAt` from ocr_history. Those CREATE TABLE IF NOT EXISTS calls did
        // nothing on an already-existing table, so existing user DBs (v3 from the broken
        // release, v4 that already had ocr_history without syncId) must be ALTERed here.
        // Each addition is guarded by a PRAGMA column check so re-running is safe.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "pending_deletes", "collection")) {
                    db.execSQL(
                        "ALTER TABLE pending_deletes ADD COLUMN collection TEXT NOT NULL DEFAULT 'history'"
                    )
                }
                if (!hasColumn(db, "ocr_history", "syncId")) {
                    db.execSQL(
                        "ALTER TABLE ocr_history ADD COLUMN syncId TEXT NOT NULL DEFAULT ''"
                    )
                }
                if (!hasColumn(db, "ocr_history", "updatedAt")) {
                    db.execSQL(
                        "ALTER TABLE ocr_history ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }
        }

        // v5 -> v6: the chat tables (phase 1).
        //   chat_translations      — cache of translations done on THIS device (decision D1),
        //                            keyed by (msgId, targetLang).
        //   chat_outbox            — outgoing messages waiting for the network. clientMsgId is
        //                            also the Firestore document id, so a retry is idempotent.
        //   chat_reads             — local per-conversation read watermark (inbox unread dot).
        //   chat_deleted_messages  — local tombstones for "delete for me".
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS chat_translations (" +
                        "msgId TEXT NOT NULL, " +
                        "targetLang TEXT NOT NULL, " +
                        "chatId TEXT NOT NULL DEFAULT '', " +
                        "translatedText TEXT NOT NULL DEFAULT '', " +
                        "updatedAt INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(msgId, targetLang))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS chat_outbox (" +
                        "clientMsgId TEXT NOT NULL PRIMARY KEY, " +
                        "chatId TEXT NOT NULL DEFAULT '', " +
                        "text TEXT NOT NULL DEFAULT '', " +
                        "sourceLang TEXT NOT NULL DEFAULT 'pl', " +
                        "createdAt INTEGER NOT NULL DEFAULT 0, " +
                        "status TEXT NOT NULL DEFAULT 'pending', " +
                        "attempts INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS chat_reads (" +
                        "chatId TEXT NOT NULL PRIMARY KEY, " +
                        "lastReadAt INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS chat_deleted_messages (" +
                        "msgId TEXT NOT NULL PRIMARY KEY, " +
                        "deletedAt INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        // v6 -> v7: hidden conversations ("delete conversation" from the contact card).
        // Firestore messages are append-only, so removing a conversation can only be a
        // local hide — this table is the record of it.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS chat_hidden (" +
                        "chatId TEXT NOT NULL PRIMARY KEY, " +
                        "hiddenAt INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        // v7 -> v8: contacts who do NOT have Verbigem, and what we sent them (3.6).
        //   external_contacts — an address-book person kept on the device, with the
        //                       language we translate to for them. Local-only: there is
        //                       no "other side" to sync with.
        //   external_outbox   — history of hand-offs to WhatsApp/SMS/e-mail/Telegram.
        //                       `status` is always 'handed_off' on purpose: once a
        //                       message leaves for another app we cannot know whether
        //                       it was sent, and a column that could only ever hold one
        //                       value states that in the schema instead of hiding it.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS external_contacts (" +
                        "phone TEXT NOT NULL, " +
                        "name TEXT NOT NULL DEFAULT '', " +
                        "e164 TEXT NOT NULL DEFAULT '', " +
                        "email TEXT NOT NULL DEFAULT '', " +
                        "lang TEXT NOT NULL DEFAULT '', " +
                        "createdAt INTEGER NOT NULL DEFAULT 0, " +
                        "lastUsedAt INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(phone))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS external_outbox (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "phone TEXT NOT NULL DEFAULT '', " +
                        "channel TEXT NOT NULL DEFAULT '', " +
                        "originalText TEXT NOT NULL DEFAULT '', " +
                        "translatedText TEXT NOT NULL DEFAULT '', " +
                        "lang TEXT NOT NULL DEFAULT '', " +
                        "createdAt INTEGER NOT NULL DEFAULT 0, " +
                        "status TEXT NOT NULL DEFAULT 'handed_off')"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_external_outbox_phone " +
                        "ON external_outbox (phone)"
                )
            }
        }

        // v8 -> v9: the user's termbase (glossary).
        //
        //   glossary — "when translating <sourceLang> -> <targetLang>, render
        //              <sourceTerm> as <targetTerm>". Injected into the Hy-MT2 prompt
        //              as Tencent's documented terminology block. It is the one quality
        //              lever that measures as working without shipping a bigger model,
        //              so it is local-first: no account, no sync, no server.
        //
        // (sourceLang, targetLang, sourceTerm) is UNIQUE so re-adding a term updates
        // it instead of creating a second, contradicting row. The UNIQUE index is what
        // makes ON CONFLICT REPLACE do that — a plain index would not.
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ⚠️ Room runs `onValidateSchema` AFTER every migration and throws
                // IllegalStateException ("Migration didn't properly handle …") on a
                // mismatch with the entity. Authoritative DDL to copy from:
                // app/build/generated/ksp/debug/java/.../AppDatabase_Impl.java
                // Room compares name / notNull / affinity / PK position, plus
                // defaultValue — but ONLY when the ENTITY declares one
                // (@ColumnInfo(defaultValue=…)). A Kotlin `= ""` is not a column
                // default, so the generated Column carries `null` and a `DEFAULT`
                // clause here is tolerated (see MIGRATION_7_8). Kept verbatim anyway.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS glossary (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sourceLang TEXT NOT NULL, " +
                        "targetLang TEXT NOT NULL, " +
                        "sourceTerm TEXT NOT NULL, " +
                        "targetTerm TEXT NOT NULL, " +
                        "caseSensitive INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_glossary_sourceLang_targetLang_sourceTerm " +
                        "ON glossary (sourceLang, targetLang, sourceTerm)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_glossary_sourceLang_targetLang " +
                        "ON glossary (sourceLang, targetLang)"
                )
            }
        }

        // v9 -> v10: the outbox stops being text-only (README §5.4).
        //
        // Until now a photo or a voice message was uploaded OUTSIDE the outbox, in a
        // bare try/catch: when it failed, the only trace was a line in logcat and the
        // message vanished from the user's thread. Queuing them gives them the same
        // fate as a text message — a red bubble and "retry" instead of silence.
        //
        // ⚠️ SQLite cannot ADD COLUMN as NOT NULL without a DEFAULT, hence the defaults
        // below. Room tolerates a DEFAULT clause it does not expect: it only compares
        // defaultValue when the ENTITY declares one via @ColumnInfo, and here the
        // Kotlin `= ""` / `= "text"` initialisers are NOT column defaults, so the
        // generated column carries `null` (see MIGRATION_7_8, same pattern shipped).
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // `hasColumn` guard: an interrupted v8->v9 run can leave a device that
                // already got part of this. ALTER TABLE has no IF NOT EXISTS.
                if (!hasColumn(db, "chat_outbox", "type")) {
                    db.execSQL(
                        "ALTER TABLE chat_outbox ADD COLUMN type TEXT NOT NULL DEFAULT 'text'"
                    )
                }
                if (!hasColumn(db, "chat_outbox", "attachmentUrl")) {
                    db.execSQL(
                        "ALTER TABLE chat_outbox ADD COLUMN attachmentUrl TEXT NOT NULL DEFAULT ''"
                    )
                }
                if (!hasColumn(db, "chat_outbox", "localUri")) {
                    db.execSQL(
                        "ALTER TABLE chat_outbox ADD COLUMN localUri TEXT NOT NULL DEFAULT ''"
                    )
                }
                if (!hasColumn(db, "chat_outbox", "transcript")) {
                    db.execSQL(
                        "ALTER TABLE chat_outbox ADD COLUMN transcript TEXT NOT NULL DEFAULT ''"
                    )
                }
                if (!hasColumn(db, "chat_outbox", "ocrText")) {
                    db.execSQL(
                        "ALTER TABLE chat_outbox ADD COLUMN ocrText TEXT NOT NULL DEFAULT ''"
                    )
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "verbigem_db"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10
                ).build().also { instance = it }
            }
        }
    }
}
