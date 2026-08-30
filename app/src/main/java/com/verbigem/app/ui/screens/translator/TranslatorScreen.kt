package com.verbigem.app.ui.screens.translator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.verbigem.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.model.TranslationHistory
import com.verbigem.app.ui.components.AdBannerView
import com.verbigem.app.ui.components.EnginePicker
import com.verbigem.app.ui.components.FlagIcon
import com.verbigem.app.ui.components.LangSelect
import com.verbigem.app.ui.components.ModelDownloadDialog
import com.verbigem.app.ui.theme.VerbigemTheme

@Composable
fun TranslatorScreen(
    viewModel: TranslatorViewModel,
    onNavigateToOcr: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val sourceLang by viewModel.sourceLang.collectAsState()
    val targetLang by viewModel.targetLang.collectAsState()
    val engineChoice by viewModel.engineChoice.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val primaryResult by viewModel.primaryResult.collectAsState()
    val secondaryResult by viewModel.secondaryResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isSpeakingPro by viewModel.isSpeakingPro.collectAsState()
    val isPro by viewModel.isPro.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val historyList by viewModel.historyList.collectAsState()
    val showDownloadDialog by viewModel.showDownloadDialog.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AdBannerView(isPro = isPro)
        }

        item {
            Text(
                text = stringResource(R.string.app_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = VerbigemTheme.colors.ink
            )
        }

        // Karta główna tłumacza
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                // Wybór języków i zamiana
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        LangSelect(
                            selectedLang = sourceLang,
                            onLangSelected = { viewModel.setSourceLang(it) }
                        )
                    }

                    IconButton(onClick = { viewModel.swapLanguages() }) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = stringResource(R.string.swap_direction),
                            tint = VerbigemTheme.colors.accent
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        LangSelect(
                            selectedLang = targetLang,
                            onLangSelected = { viewModel.setTargetLang(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Wybór silnika
                EnginePicker(
                    selectedEngine = engineChoice,
                    onEngineSelected = { viewModel.setEngine(it) },
                    isPro = isPro
                )

                // Przycisk skrótu OCR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onNavigateToOcr,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = VerbigemTheme.colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.ocr_shortcut),
                            color = VerbigemTheme.colors.accent,
                            fontSize = 13.sp
                        )
                    }
                }

                // Pole wprowadzania tekstu
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { viewModel.onInputChanged(it) },
                    placeholder = { Text(stringResource(R.string.translate_hint), color = VerbigemTheme.colors.muted) },
                    trailingIcon = {
                        if (inputText.isNotBlank()) {
                            IconButton(onClick = {
                                viewModel.onInputChanged("")
                                focusRequester.requestFocus()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = VerbigemTheme.colors.danger
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .height(110.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VerbigemTheme.colors.accent,
                        unfocusedBorderColor = VerbigemTheme.colors.border,
                        focusedTextColor = VerbigemTheme.colors.ink,
                        unfocusedTextColor = VerbigemTheme.colors.ink
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Przycisk Tłumacz
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.translate()
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text(stringResource(R.string.translate_button), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                // Wyniki tłumaczenia
                if (primaryResult.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ResultCard(
                        text = primaryResult,
                        isSpeaking = isSpeaking,
                        isSpeakingPro = isSpeakingPro,
                        isPro = isPro,
                        onSpeak = { viewModel.speak(primaryResult, targetLang) },
                        onSpeakPro = { viewModel.speakPro(primaryResult, targetLang) },
                        onCopy = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("translation", primaryResult))
                            Toast.makeText(context, context.getString(R.string.copied_clipboard), Toast.LENGTH_SHORT).show()
                        },
                        onShare = {
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, primaryResult)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, context.getString(R.string.action_share))
                            shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(shareIntent)
                        },
                        onClear = { viewModel.clearResult() }
                    )
                }

                if (secondaryResult.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ResultCard(
                        text = secondaryResult,
                        label = stringResource(R.string.accurate_label),
                        isSpeaking = isSpeaking,
                        isSpeakingPro = isSpeakingPro,
                        isPro = isPro,
                        onSpeak = { viewModel.speak(secondaryResult, targetLang) },
                        onSpeakPro = { viewModel.speakPro(secondaryResult, targetLang) },
                        onCopy = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("translation", secondaryResult))
                            Toast.makeText(context, context.getString(R.string.copied_clipboard), Toast.LENGTH_SHORT).show()
                        },
                        onShare = {
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, secondaryResult)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, context.getString(R.string.action_share))
                            shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(shareIntent)
                        },
                        onClear = { viewModel.clearResult() }
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = VerbigemTheme.colors.danger,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Historia ostatnich tłumaczeń
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.recent_translations),
                    color = VerbigemTheme.colors.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (historyList.isEmpty()) {
                    Text(stringResource(R.string.no_history), color = VerbigemTheme.colors.muted, fontSize = 13.sp)
                }
            }
        }

        items(historyList.take(8)) { item ->
            HistoryCard(
                item = item,
                isPro = isPro,
                isSpeaking = isSpeaking,
                isSpeakingPro = isSpeakingPro,
                onCopy = {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("translation", item.translatedText))
                    Toast.makeText(context, context.getString(R.string.copied_clipboard), Toast.LENGTH_SHORT).show()
                },
                onShare = {
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, item.translatedText)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, context.getString(R.string.action_share))
                    shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(shareIntent)
                },
                onRead = { viewModel.speak(item.translatedText, LangCode.fromCode(item.targetLang)) },
                onReadPro = { viewModel.speakPro(item.translatedText, LangCode.fromCode(item.targetLang)) },
                onDelete = { viewModel.deleteHistory(item) }
            )
        }
    }

    if (showDownloadDialog) {
        ModelDownloadDialog(
            downloadState = downloadState,
            onStartDownload = { viewModel.startModelDownload(isAccurate = engineChoice == com.verbigem.app.data.model.EngineChoice.LOCAL_ACCURATE) },
            onDismiss = { viewModel.setShowDownloadDialog(false) }
        )
    }
}

@Composable
fun HistoryCard(
    item: TranslationHistory,
    isPro: Boolean,
    isSpeaking: Boolean,
    isSpeakingPro: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRead: () -> Unit,
    onReadPro: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VerbigemTheme.colors.surface)
            .border(0.5.dp, VerbigemTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Górny pasek: flagi języków + wszystkie ikony akcji.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FlagIcon(lang = LangCode.fromCode(item.sourceLang), size = 18.dp)
            Text("→", color = VerbigemTheme.colors.muted, fontSize = 13.sp)
            FlagIcon(lang = LangCode.fromCode(item.targetLang), size = 18.dp)
            Spacer(modifier = Modifier.weight(1f))
            // Kopiuj
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy), tint = VerbigemTheme.colors.accent)
            }
            // Udostępnij
            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share), tint = VerbigemTheme.colors.accent)
            }
            // Czytaj (darmowy TTS lokalny)
            IconButton(onClick = onRead, modifier = Modifier.size(32.dp)) {
                if (isSpeaking) {
                    CircularProgressIndicator(color = VerbigemTheme.colors.accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.action_read), tint = VerbigemTheme.colors.accent)
                }
            }
            // Czytaj Pro (płatne API — tylko dla Pro)
            if (isPro) {
                IconButton(onClick = onReadPro, modifier = Modifier.size(32.dp)) {
                    if (isSpeakingPro) {
                        CircularProgressIndicator(color = VerbigemTheme.colors.accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Star, contentDescription = stringResource(R.string.action_read_pro), tint = VerbigemTheme.colors.accent)
                    }
                }
            }
            // Skasuj z historii
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = VerbigemTheme.colors.danger)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tekst na całą szerokość karty (pod paskiem ikon).
        Text(text = item.sourceText, color = VerbigemTheme.colors.muted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = item.translatedText, color = VerbigemTheme.colors.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun ResultCard(
    text: String,
    label: String? = null,
    isSpeaking: Boolean = false,
    isSpeakingPro: Boolean = false,
    isPro: Boolean = false,
    onSpeak: () -> Unit,
    onSpeakPro: () -> Unit = {},
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VerbigemTheme.colors.accent.copy(alpha = 0.1f))
            .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Górny pasek z nazwą silnika (opcjonalnie) i ikonami akcji.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (label != null) {
                Text(label, color = VerbigemTheme.colors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy), tint = VerbigemTheme.colors.accent)
            }
            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share), tint = VerbigemTheme.colors.accent)
            }
            IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                if (isSpeaking) {
                    CircularProgressIndicator(color = VerbigemTheme.colors.accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.action_read), tint = VerbigemTheme.colors.accent)
                }
            }
            if (isPro) {
                IconButton(onClick = onSpeakPro, modifier = Modifier.size(32.dp)) {
                    if (isSpeakingPro) {
                        CircularProgressIndicator(color = VerbigemTheme.colors.accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Star, contentDescription = stringResource(R.string.action_read_pro), tint = VerbigemTheme.colors.accent)
                    }
                }
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_delete), tint = VerbigemTheme.colors.danger)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tekst na całą szerokość karty (pod paskiem ikon).
        Text(text = text, color = VerbigemTheme.colors.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
