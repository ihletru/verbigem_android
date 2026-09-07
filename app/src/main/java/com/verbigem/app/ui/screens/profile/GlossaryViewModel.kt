package com.verbigem.app.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.model.GlossaryEntry
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.repository.GlossaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Editing screen for the termbase. Deliberately thin: the repository is the
 * singleton that owns the cache, so this only holds the selected language pair
 * and re-reads after every write.
 *
 * Default pair is EN -> PL, which is also what the entity defaults to.
 */
class GlossaryViewModel(application: Application) : AndroidViewModel(application) {

    private val _sourceLang = MutableStateFlow(LangCode.EN)
    val sourceLang: StateFlow<LangCode> = _sourceLang

    private val _targetLang = MutableStateFlow(LangCode.PL)
    val targetLang: StateFlow<LangCode> = _targetLang

    /** Bumped after every write so the list re-reads. */
    private val _revision = MutableStateFlow(0)

    val entries: StateFlow<List<GlossaryEntry>> =
        combine(_sourceLang, _targetLang, _revision) { from, to, _ -> from to to }
            .flatMapLatest { (from, to) ->
                flow { emit(GlossaryRepository.listFor(getApplication(), from, to)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSourceLang(lang: LangCode) {
        _sourceLang.value = lang
    }

    fun setTargetLang(lang: LangCode) {
        _targetLang.value = lang
    }

    fun add(sourceTerm: String, targetTerm: String, caseSensitive: Boolean) {
        // Same language on both sides is meaningless — the pair select lets you
        // pick it, so refuse here rather than writing a row that can never be used.
        if (_sourceLang.value == _targetLang.value) return
        viewModelScope.launch {
            GlossaryRepository.save(
                getApplication(),
                GlossaryEntry(
                    sourceLang = _sourceLang.value,
                    targetLang = _targetLang.value,
                    sourceTerm = sourceTerm,
                    targetTerm = targetTerm,
                    caseSensitive = caseSensitive
                )
            )
            _revision.value++
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            GlossaryRepository.delete(getApplication(), id)
            _revision.value++
        }
    }
}
