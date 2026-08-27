package com.verbigem.app.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.model.ChatMessage
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.ChatRepository
import com.verbigem.app.engine.HyMt2NativeEngine
import com.verbigem.app.engine.SpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()
    private val chatRepository = ChatRepository()
    private val hyMt2Engine = HyMt2NativeEngine(application)
    private val speechManager = SpeechManager(application)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _otherUid = MutableStateFlow<String?>(null)
    val otherUid: StateFlow<String?> = _otherUid.asStateFlow()

    val currentUid: String
        get() = authRepository.currentUser?.uid ?: ""

    fun selectContact(uid: String) {
        _otherUid.value = uid
        val current = currentUid
        if (current.isNotBlank() && uid.isNotBlank()) {
            val chatId = chatRepository.getChatId(current, uid)
            viewModelScope.launch {
                chatRepository.watchMessages(chatId).collect { msgList ->
                    _messages.value = msgList
                }
            }
        }
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        val other = _otherUid.value ?: return
        val current = currentUid
        if (text.isBlank() || current.isBlank() || _isSending.value) return

        _isSending.value = true
        _inputText.value = ""

        viewModelScope.launch {
            try {
                // Tłumaczenie przed wysłaniem
                val translated = hyMt2Engine.translate(text, LangCode.PL, LangCode.EN)
                val chatId = chatRepository.getChatId(current, other)

                chatRepository.sendMessage(
                    chatId = chatId,
                    authorId = current,
                    text = text,
                    sourceLang = "pl",
                    translatedText = translated
                )
            } finally {
                _isSending.value = false
            }
        }
    }

    fun speak(text: String, langCode: String) {
        speechManager.speak(text, LangCode.fromCode(langCode))
    }

    override fun onCleared() {
        super.onCleared()
        hyMt2Engine.release()
        speechManager.release()
    }
}
