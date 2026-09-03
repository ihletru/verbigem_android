package com.verbigem.app.ui.screens.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.res.stringResource
import com.verbigem.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.ui.components.FlagIcon
import com.verbigem.app.ui.components.LangSelect
import com.verbigem.app.ui.theme.VerbigemTheme

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel
) {
    val langA by viewModel.langA.collectAsState()
    val langB by viewModel.langB.collectAsState()
    val currentSide by viewModel.currentSide.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val interimSpeech by viewModel.interimSpeech.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val translatedResult by viewModel.translatedResult.collectAsState()
    val resultLang by viewModel.resultLang.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val textInput by viewModel.textInput.collectAsState()

    val currentLang = if (currentSide == ConvSide.SIDE_A) langA else langB

    // Scroll the focused text field into view when the IME covers it.
    val scrollState = rememberScrollState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val isImeVisible = WindowInsets.isImeVisible

    // The keyboard animates in *after* the field gains focus (~250 ms), so a single
    // bringIntoView() fired from onFocusEvent runs against a viewport that is still full
    // height — the field ends up at the bottom of the screen, i.e. behind the keyboard.
    // Re-run it once the IME is actually up (and again on every focus while it stays up).
    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            delay(250)
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
            // imePadding() must come BEFORE verticalScroll(): it then shrinks the scroll
            // *viewport* by the keyboard height. Placed after (inside) the scroll it only
            // appends empty space at the end of the content, so "scroll the field into
            // view" still means "put it at the bottom of the screen" — hidden by the IME.
            .imePadding()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.conv_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = VerbigemTheme.colors.ink
        )
        Text(
        text = stringResource(R.string.conv_subtitle),
        fontSize = 13.sp,
        color = VerbigemTheme.colors.muted
        )

        // Wybór stron i języków
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(VerbigemTheme.colors.surface)
                .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.side_a_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(4.dp))
                LangSelect(selectedLang = langA, onLangSelected = { viewModel.setLangA(it) })
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { viewModel.setSide(if (currentSide == ConvSide.SIDE_A) ConvSide.SIDE_B else ConvSide.SIDE_A) },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VerbigemTheme.colors.accent)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = stringResource(R.string.swap_direction),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.side_b_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(4.dp))
                LangSelect(selectedLang = langB, onLangSelected = { viewModel.setLangB(it) })
            }
        }

        // Duży bąbel rozmowy
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isListening) VerbigemTheme.colors.surface
                    else VerbigemTheme.colors.accent.copy(alpha = 0.12f)
                )
                .border(
                    1.dp,
                    if (isListening) VerbigemTheme.colors.accent else VerbigemTheme.colors.border,
                    RoundedCornerShape(20.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isListening || interimSpeech.isNotBlank()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.recognizing_speech, currentLang.displayName),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = VerbigemTheme.colors.accent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = interimSpeech.ifBlank { stringResource(R.string.listening_hint) },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = VerbigemTheme.colors.ink
                    )
                }
            } else if (isTranslating) {
                CircularProgressIndicator(color = VerbigemTheme.colors.accent)
            } else if (!translatedResult.isNullOrBlank()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FlagIcon(lang = resultLang, size = 18.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(resultLang.displayName, fontSize = 12.sp, color = VerbigemTheme.colors.muted)
                    }
                    Text(
                        text = translatedResult ?: "",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = VerbigemTheme.colors.ink
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { viewModel.speakAgain() }) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = stringResource(R.string.speak_again),
                                tint = VerbigemTheme.colors.accent
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.now_speaking, if (currentSide == ConvSide.SIDE_A) "A" else "B", currentLang.displayName),
                    fontSize = 14.sp,
                    color = VerbigemTheme.colors.muted
                )
            }
        }

        // Duży okrągły przycisk mikrofonu
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(if (isListening) VerbigemTheme.colors.danger else VerbigemTheme.colors.accent)
                    .clickable { viewModel.toggleSpeechRecognition() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Mów",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isListening) stringResource(R.string.tap_to_stop) else stringResource(R.string.tap_to_speak, currentLang.displayName),
                fontSize = 12.sp,
                color = VerbigemTheme.colors.muted
            )
        }

        // Wprowadzanie tekstowe (zapasowe)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(VerbigemTheme.colors.surface)
                .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.or_type_text),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = VerbigemTheme.colors.muted,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { viewModel.onTextInputChanged(it) },
                    placeholder = {
                        Text(
                            text = recognizedText.ifEmpty { stringResource(R.string.type_as_side, if (currentSide == ConvSide.SIDE_A) "A" else "B") },
                            fontSize = 13.sp,
                            color = if (recognizedText.isNotBlank()) VerbigemTheme.colors.muted else VerbigemTheme.colors.muted
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                scope.launch { bringIntoViewRequester.bringIntoView() }
                            }
                        },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VerbigemTheme.colors.accent,
                        unfocusedBorderColor = VerbigemTheme.colors.border
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { viewModel.sendTextMessage() },
                    enabled = textInput.isNotBlank() && !isTranslating,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VerbigemTheme.colors.accent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Wyślij",
                        tint = Color.White
                    )
                }
            }

            AnimatedVisibility(visible = errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = VerbigemTheme.colors.danger,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
