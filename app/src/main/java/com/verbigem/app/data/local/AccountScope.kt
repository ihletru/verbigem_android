package com.verbigem.app.data.local

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Which account the on-device database belongs to.
 *
 * The app can hold several signed-in accounts on one device (Milosz tests with a
 * few), and everything local used to be shared: signing in as B showed A's
 * translation history, OCR history and chat caches. Firestore was always
 * per-account (`users/{uid}/…`) — only the on-device copy was not.
 *
 * The fix is one database file per account (`verbigem_db_<uid>`), so isolation
 * comes for free for every table rather than one column at a time. This object
 * holds the current uid; [AppDatabase.getInstance] reads it when handing out an
 * instance, and builds (or reuses) the matching file.
 *
 * `null` means "nobody signed in" and maps to the `anon` file, used only by
 * screens reachable before login. Because ViewModels are torn down when the user
 * signs out (`AppNavigation` navigates to Login with `popUpTo(0)`), a later
 * sign-in always builds its repositories against the new file.
 */
object AccountScope {

    private const val TAG = "AccountScope"
    private const val LEGACY_DB = "verbigem_db"
    private const val DB_PREFIX = "verbigem_db_"

    /**
     * Suffix used while nobody is signed in. Screens reachable before login (phone
     * gate, legal pages) still need a database, and it must not be one of the real
     * accounts' files.
     */
    const val ANON = "anon"

    /** SQLite sidecar files that must move together with the main one. */
    private val SUFFIXES = listOf("", "-shm", "-wal")

    @Volatile
    private var appContext: Context? = null

    @Volatile
    var uid: String? = null
        private set

    /**
     * Remember the application context once, at startup.
     *
     * `AuthRepository` has no Context (it is built with a no-arg constructor and
     * only talks to Firebase), so it cannot pass one to [bind] when the user signs
     * out. Installing it here keeps the call sites argument-free.
     */
    fun install(context: Context) {
        appContext = context.applicationContext
    }

    /** Database name for the account that is currently signed in. */
    fun key(): String = uid ?: ANON

    /**
     * Point the scope at [newUid] (null = signed out).
     *
     * Idempotent: calling it with the value it already holds does nothing, which
     * matters because it is called from both an auth-state listener and the
     * navigation entry point on every start.
     */
    fun bind(newUid: String?) {
        val previous = uid
        if (previous == newUid) return
        uid = newUid
        // The pre-v1.0.69 database was a single shared file. The first account to
        // sign in after the upgrade adopts it, so a locally-only table that
        // Firestore cannot restore (glossary, external contacts, TTS key) is not
        // silently lost.
        if (newUid != null) adoptLegacyDatabase(newUid)
        Log.i(TAG, "account scope: ${previous ?: ANON} -> ${newUid ?: ANON}")
    }

    private fun adoptLegacyDatabase(newUid: String) {
        val context = appContext ?: return
        try {
            val legacy = context.getDatabasePath(LEGACY_DB)
            val target = context.getDatabasePath("$DB_PREFIX$newUid")
            if (!legacy.exists() || target.exists()) return
            target.parentFile?.mkdirs()
            var moved = 0
            for (suffix in SUFFIXES) {
                val from = File(legacy.path + suffix)
                if (from.exists() && from.renameTo(File(target.path + suffix))) moved++
            }
            Log.i(TAG, "legacy database adopted by $newUid ($moved file(s))")
        } catch (e: Exception) {
            Log.e(TAG, "legacy database adoption failed", e)
        }
    }
}
