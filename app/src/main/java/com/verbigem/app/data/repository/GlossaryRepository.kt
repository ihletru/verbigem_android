package com.verbigem.app.data.repository

import android.content.Context
import android.util.Log
import com.verbigem.app.data.local.AppDatabase
import com.verbigem.app.data.model.GlossaryEntry
import com.verbigem.app.data.model.LangCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Local termbase: "when translating EN -> PL, render `board` as `rada nadzorcza`".
 *
 * WHY AN OBJECT AND NOT A CLASS
 * The engine is constructed as `HyMt2NativeEngine(application)` in five different
 * ViewModels, none of which knows about each other. Threading a glossary instance
 * through all of them (and through every `translateSegmented` overload) would touch
 * a dozen signatures for no benefit. The cache is process-wide by nature — there is
 * one termbase and one app process — so it is a singleton, built lazily from the
 * application Context the first time a translation runs.
 *
 * WHY THE CACHE AT ALL
 * `translateSegmented` calls `translate()` once per ~400-char segment. Hitting Room
 * per segment would put a disk read inside the decode loop. Instead the whole table
 * (a handful of rows in practice) is read once and kept in memory; every read after
 * that is a map lookup.
 *
 * NOT synced to Firestore, deliberately: this is per-device, offline, and free.
 */
object GlossaryRepository {

    private const val TAG = "GlossaryRepository"

    private val loadMutex = Mutex()

    @Volatile
    private var byPair: Map<String, List<GlossaryEntry>> = emptyMap()

    @Volatile
    private var loaded = false

    private fun key(from: LangCode, to: LangCode): String = "${from.code}>${to.code}"

    /**
     * Reads the table into memory on first use. Safe to call on every translation:
     * after the first one it is a volatile read.
     */
    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return@withLock
            val rows = withContext(Dispatchers.IO) {
                try {
                    AppDatabase.getInstance(context.applicationContext).glossaryDao().all()
                } catch (e: Exception) {
                    // A broken migration must never take translation down with it.
                    // No glossary is the pre-feature behaviour.
                    Log.e(TAG, "Failed to load glossary; continuing without it", e)
                    emptyList()
                }
            }
            byPair = rows.map { it.toDomain() }.groupBy { key(it.sourceLang, it.targetLang) }
            loaded = true
        }
    }

    /** Terms for this pair, or empty. Never blocks — see [ensureLoaded]. */
    fun forPair(from: LangCode, to: LangCode): List<GlossaryEntry> =
        byPair[key(from, to)].orEmpty()

    // ---------------------------------------------------------------- writes

    private suspend fun dao(context: Context) =
        AppDatabase.getInstance(context.applicationContext).glossaryDao()

    /** Inserts or updates (unique index on the triple -> REPLACE updates). */
    suspend fun save(context: Context, entry: GlossaryEntry) {
        if (!entry.isUsable) return
        withContext(Dispatchers.IO) {
            dao(context).upsert(
                entry.copy(
                    sourceTerm = entry.sourceTerm.trim(),
                    targetTerm = entry.targetTerm.trim(),
                    createdAt = entry.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
                ).toEntity()
            )
        }
        reload(context)
    }

    suspend fun delete(context: Context, id: Long) {
        withContext(Dispatchers.IO) { dao(context).deleteById(id) }
        reload(context)
    }

    suspend fun clearPair(context: Context, from: LangCode, to: LangCode) {
        withContext(Dispatchers.IO) { dao(context).clearPair(from.code, to.code) }
        reload(context)
    }

    /** Forces a re-read of the table into the cache. */
    suspend fun reload(context: Context) {
        withContext(Dispatchers.IO) {
            val rows = try {
                dao(context).all()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reload glossary", e)
                emptyList()
            }
            byPair = rows.map { it.toDomain() }.groupBy { key(it.sourceLang, it.targetLang) }
        }
        loaded = true
    }
}
