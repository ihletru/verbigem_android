package com.verbigem.app.data.repository

import com.verbigem.app.data.local.HistoryDao
import com.verbigem.app.data.local.HistoryEntity
import com.verbigem.app.data.model.TranslationHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepository(private val historyDao: HistoryDao) {

    val allHistory: Flow<List<TranslationHistory>> = historyDao.getAllHistory().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun addHistory(sourceText: String, translatedText: String, sourceLang: String, targetLang: String) {
        historyDao.insert(
            HistoryEntity(
                sourceText = sourceText,
                translatedText = translatedText,
                sourceLang = sourceLang,
                targetLang = targetLang
            )
        )
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }
}
