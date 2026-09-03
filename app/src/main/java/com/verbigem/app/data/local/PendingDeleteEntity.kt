package com.verbigem.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lightweight queue of deletions that still need to be pushed to Firestore as tombstones.
 *
 * When a history row is deleted locally (even while offline) we physically remove it from
 * [HistoryEntity] but keep its `syncId` here so the next sync can tell other devices
 * "this row was deleted" (a tombstone). Without this, an offline delete would vanish from the
 * local list and the normal sync (which iterates over *existing* local rows) would never emit
 * the tombstone, so the row would re-appear on other devices.
 *
 * Each entry is just `syncId` + `updatedAt` (+ the owning `collection`) — intentionally
 * tiny, so this table never bloats the local DB the way keeping soft-deleted full rows would.
 * `collection` says which Firestore subcollection the tombstone belongs to
 * ("history" for Translator, "ocr_history" for OCR) so the sync can route it correctly.
 */
@Entity(tableName = "pending_deletes")
data class PendingDeleteEntity(
    @PrimaryKey val syncId: String,
    val collection: String = "history",
    val updatedAt: Long = System.currentTimeMillis()
)
