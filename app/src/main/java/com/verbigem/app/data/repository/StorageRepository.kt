package com.verbigem.app.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await

/**
 * Faza 5 — załączniki czatu w Firebase Storage.
 *
 * Ścieżka to `chat_attachments/{chatId}/{msgId}` (msgId = `clientMsgId` nadawcy,
 * dzięki temu upload jest idempotentny i pasuje do dokumentu wiadomości). Zapis
 * jest dopuszczalny TYLKO dla członków czatu — to egzekwuje `storage.rules`
 * (członkostwo czytane z `chats/{chatId}.members`).
 */
class StorageRepository(private val context: Context) {

    private val storage = FirebaseStorage.getInstance()

    /**
     * Wgrywa załącznik i zwraca jego download URL.
     *
     * Używa `putFile(Uri)` (a nie `putBytes`), żeby nie ładować dużego zdjęcia
     * w całości do pamięci — Storage strumieniuje z content URI.
     *
     * @param mime deklarowany Content-Type — reguły Storage dopuszczają
     *            wyłącznie obrazy i dźwięk (typy image oraz audio).
     */
    suspend fun uploadAttachment(
        chatId: String,
        msgId: String,
        uri: Uri,
        mime: String
    ): String {
        val ref = storage.reference.child("chat_attachments/$chatId/$msgId")
        val metadata = StorageMetadata.Builder().setContentType(mime).build()
        ref.putFile(uri, metadata).await()
        return ref.downloadUrl.await().toString()
    }
}
