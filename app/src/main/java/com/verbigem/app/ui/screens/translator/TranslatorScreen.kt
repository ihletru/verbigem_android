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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.res.stringResource
import com.verbigem.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.ui.components.AdBannerView
import com.verbigem.app.ui.components.EnginePicker
import com.verbigem.app.ui.components.FlagIcon
import com.verbigem.app.ui.components.LangSelect
import com.verbigem.app.ui.components.ModelDownloadDialog
import com.verbigem.app.ui.theme.VerbigemTheme

@Composable
fun TranslatorScreen(
    viewModel: TranslatorViewModel,
    onNavigateToOcr: () -> Unit,
    isPro: Boolean = false
) {
    val context = LocalContext.current
    val sourceLang by viewModel.sourceLang.collectAsState()
    val targetLang by viewModel.targetLang.collectAsState()
    val engineChoice by viewModel.engineChoice.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val primaryResult by viewModel.primaryResult.collectAsState()
    val secondaryResult by viewModel.secondaryResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
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
                            contentDescription = "Zamień kierunek",
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
                    modifier = Modifier
                        .fillMaxWidth()
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
                    onClick = { viewModel.translate() },
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
                        lang = targetLang,
                        onSpeak = { viewModel.speak(primaryResult, targetLang) },
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
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, "Udostępnij tłumaczenie")
                            context.startActivity(shareIntent)
                        }
                    )
                }

                if (secondaryResult.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ResultCard(
                        text = secondaryResult,
                        lang = targetLang,
                        label = stringResource(R.string.accurate_label),
                        onSpeak = { viewModel.speak(secondaryResult, targetLang) },
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
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, "Udostępnij tłumaczenie")
                            context.startActivity(shareIntent)
                        }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(0.5.dp, VerbigemTheme.colors.border, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlagIcon(lang = LangCode.fromCode(item.sourceLang), size = 16.dp)
                Text(" → ", color = VerbigemTheme.colors.muted, fontSize = 12.sp)
                FlagIcon(lang = LangCode.fromCode(item.targetLang), size = 16.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.sourceText, color = VerbigemTheme.colors.muted, fontSize = 12.sp)
                    Text(text = item.translatedText, color = VerbigemTheme.colors.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
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
fun ResultCard(
    text: String,
    lang: LangCode,
    label: String? = null,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VerbigemTheme.colors.accent.copy(alpha = 0.1f))
            .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        if (label != null) {
            Text(label, color = VerbigemTheme.colors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(text = text, color = VerbigemTheme.colors.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Kopiuj", tint = VerbigemTheme.colors.accent)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Udostępnij", tint = VerbigemTheme.colors.accent)
            }
            IconButton(onClick = onSpeak) {
                Icon(Icons.Default.VolumeUp, contentDescription = "Przeczytaj", tint = VerbigemTheme.colors.accent)
            }
        }
    }
}
