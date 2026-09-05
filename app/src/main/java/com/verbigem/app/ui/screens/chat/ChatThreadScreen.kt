package com.verbigem.app.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.R
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.ui.components.FlagIcon
import com.verbigem.app.ui.components.ProFeatureButton
import com.verbigem.app.ui.theme.VerbigemTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A 1:1 thread.
 *
 * Translation happens HERE, on the receiving device, into the receiver's own language
 * (decision D1). The sender's hint is what you see while the model is still working —
 * which is why a thread is usable the moment it arrives, even with no model downloaded.
 */
@Composable
fun ChatThreadScreen(
    viewModel: ChatThreadViewModel,
    otherUid: String,
    onBack: () -> Unit,
    onOpenContactCard: () -> Unit
) {
    val bubbles by viewModel.bubbles.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val translationLang by viewModel.translationLang.collectAsState()
    val contactSettings by viewModel.contactSettings.collectAsState()
    val otherLang by viewModel.otherLang.collectAsState()
    val translations by viewModel.translations.collectAsState()
    val translating by viewModel.translating.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val showOriginal by viewModel.showOriginal.collectAsState()
    val readReceipts by viewModel.readReceipts.collectAsState()
    val otherProfile by viewModel.otherProfile.collectAsState()
    val otherTyping by viewModel.otherTyping.collectAsState()
    val isPro by viewModel.isPro.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    val loadingOlder by viewModel.loadingOlder.collectAsState()

    val listState = rememberLazyListState()
    var menuFor by remember { mutableStateOf<String?>(null) }
    // Faza 5.4: URL zdjęcia otwartego w podglądzie na pełnym ekranie (null = zamknięte).
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    // Faza 5.3: głosówki — stan nagrywania + uprawnienie mikrofonu.
    val isListening by viewModel.isListening.collectAsState()
    val voiceInterim by viewModel.voiceInterim.collectAsState()
    val context = LocalContext.current
    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoice()
        else Log.w("ChatThread", "Brak zgody na mikrofon (RECORD_AUDIO)")
    }

    val labelToday = stringResource(R.string.chat_today)
    val labelYesterday = stringResource(R.string.chat_yesterday)
    val openCardLabel = stringResource(R.string.action_open_contact_card)

    // The alias lives in MY contact settings, so it is mine alone — the other
    // person's own nickname keeps whatever they set for themselves.
    val headerName = contactSettings.alias.takeIf { it.isNotBlank() }
        ?: otherProfile?.nickname?.takeIf { it.isNotBlank() }
        ?: otherUid.take(6)

    LaunchedEffect(otherUid) {
        viewModel.openThread(otherUid)
    }

    // Only stick to the bottom when the user is already there — yanking the list down
    // while they are reading history is the classic chat bug.
    LaunchedEffect(bubbles.size) {
        if (bubbles.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (lastVisible >= bubbles.lastIndex - 3) {
            listState.animateScrollToItem(bubbles.lastIndex)
        }
    }

    // Reaching the top of the thread pulls in the previous page.
    LaunchedEffect(listState, canLoadMore, bubbles.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index == 0 && canLoadMore && bubbles.isNotEmpty()) viewModel.loadOlder()
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(VerbigemTheme.colors.bg)
    ) {
        // ------------------------------------------------------------- header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VerbigemTheme.colors.surface)
                .border(1.dp, VerbigemTheme.colors.border)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = VerbigemTheme.colors.ink
                )
            }
            // Tapping the name opens the contact card (alias, translation language,
            // pin / mute / block). The whole block is the target, not just the text —
            // a 38 dp avatar plus a name is a much easier hit than a line of text.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenContactCard)
                    .padding(4.dp)
                    .semantics { contentDescription = openCardLabel },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(VerbigemTheme.colors.bg)
                        .border(1.dp, VerbigemTheme.colors.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(otherProfile?.photoURL ?: "🙂", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headerName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VerbigemTheme.colors.ink,
                        maxLines = 1
                    )
                    Text(
                        text = if (otherTyping) {
                            stringResource(R.string.chat_typing)
                        } else {
                            stringResource(R.string.chat_other_lang, otherLang.displayName)
                        },
                        fontSize = 11.sp,
                        color = if (otherTyping) VerbigemTheme.colors.accent else VerbigemTheme.colors.muted
                    )
                }
            }
        }

        // ----------------------------------------------------------- messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (canLoadMore) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (loadingOlder) {
                            CircularProgressIndicator(
                                color = VerbigemTheme.colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            TextButton(onClick = { viewModel.loadOlder() }) {
                                Text(
                                    stringResource(R.string.chat_load_older),
                                    fontSize = 12.sp,
                                    color = VerbigemTheme.colors.accent
                                )
                            }
                        }
                    }
                }
            }

            itemsIndexed(bubbles, key = { _, bubble -> bubble.id }) { index, bubble ->
                val previousStamp = if (index > 0) bubbles[index - 1].createdAt else -1L
                val showDayHeader =
                    dayLabel(bubble.createdAt, labelToday, labelYesterday) !=
                        dayLabel(previousStamp, labelToday, labelYesterday)
                Column {
                    if (showDayHeader) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayLabel(bubble.createdAt, labelToday, labelYesterday),
                                fontSize = 11.sp,
                                color = VerbigemTheme.colors.muted
                            )
                        }
                    }
                    MessageBubble(
                        bubble = bubble,
                        targetLang = translationLang,
                        translated = translations[bubble.id],
                        isTranslating = bubble.id in translating,
                        hasFailed = bubble.id in failed,
                        showOriginal = bubble.id in showOriginal,
                        readByOther = bubble.isMine && (readReceipts[otherUid] ?: 0L) >= bubble.createdAt,
                        isMenuOpen = menuFor == bubble.id,
                        isPro = isPro,
                        onDismissMenu = { menuFor = null },
                        onOpenMenu = { menuFor = bubble.id },
                        onToggleOriginal = { viewModel.toggleOriginal(bubble.id) },
                        onRetranslate = { viewModel.retranslate(bubble.id) },
                        onRetrySend = { viewModel.retryFailed() },
                        onQuote = { viewModel.quote(bubble.id) },
                        onDelete = { viewModel.deleteForMe(bubble.id) },
                        onSpeak = { text, lang -> viewModel.speak(text, lang) },
                        onSpeakPro = { text, lang -> viewModel.speakPro(text, lang) },
                        onOpenImage = { previewImageUrl = it }
                    )
                }
            }

            if (bubbles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.chat_new_thread_hint),
                            fontSize = 13.sp,
                            color = VerbigemTheme.colors.muted
                        )
                    }
                }
            }
        }

        // Faza 5.2: wybór zdjęcia z galerii → wysyłka przez ViewModel.
        val imagePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> uri?.let { viewModel.sendImage(it) } }

        // -------------------------------------------------------------- input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VerbigemTheme.colors.surface)
                .border(1.dp, VerbigemTheme.colors.border)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isListening) {
                // Faza 5.3: trwa rozpoznawanie — czerwona kropka + bieżący tekst + stop.
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = voiceInterim.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.voice_listening),
                    fontSize = 14.sp,
                    color = VerbigemTheme.colors.ink,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.stopVoice() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.voice_stop),
                        tint = VerbigemTheme.colors.accent
                    )
                }
            } else {
                // Faza 5.3: mikrofon — nagrywanie głosówki (transkrypcja na żywo).
                IconButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) viewModel.startVoice()
                        else recordPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.record_voice),
                        tint = VerbigemTheme.colors.muted
                    )
                }
                IconButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = stringResource(R.string.attach_image),
                        tint = VerbigemTheme.colors.muted
                    )
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { viewModel.onInputChanged(it) },
                    placeholder = { Text(stringResource(R.string.message_hint), fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VerbigemTheme.colors.accent,
                        unfocusedBorderColor = VerbigemTheme.colors.border
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.sendMessage() },
                    enabled = inputText.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VerbigemTheme.colors.accent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = stringResource(R.string.send),
                        tint = Color.White
                    )
                }
            }
        }

        // Faza 5.4: podgląd zdjęcia na pełnym ekranie.
        if (previewImageUrl != null) {
            Dialog(
                onDismissRequest = { previewImageUrl = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { previewImageUrl = null },
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = previewImageUrl,
                        contentDescription = stringResource(R.string.photo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        loading = {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        },
                        error = {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    )
                    IconButton(
                        onClick = { previewImageUrl = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.image_close),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    bubble: ChatBubble,
    /** Language the incoming text was translated into (profile default or per-contact override). */
    targetLang: LangCode,
    translated: String?,
    isTranslating: Boolean,
    hasFailed: Boolean,
    showOriginal: Boolean,
    readByOther: Boolean,
    isMenuOpen: Boolean,
    isPro: Boolean,
    onDismissMenu: () -> Unit,
    onOpenMenu: () -> Unit,
    onToggleOriginal: () -> Unit,
    onRetranslate: () -> Unit,
    onRetrySend: () -> Unit,
    onQuote: () -> Unit,
    onDelete: () -> Unit,
    onSpeak: (String, String) -> Unit,
    onSpeakPro: (String, String) -> Unit,
    /** Faza 5.4: otwiera zdjęcie w podglądzie na pełnym ekranie. */
    onOpenImage: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val isTranslated = !bubble.isMine && translated != null

    val displayText = when {
        bubble.isMine -> bubble.text
        showOriginal -> bubble.text
        translated != null -> translated
        else -> bubble.hintText.takeIf { it.isNotBlank() } ?: bubble.text
    }
    val displayLang = when {
        bubble.isMine -> bubble.sourceLang
        showOriginal -> bubble.sourceLang
        translated != null -> targetLang.code
        else -> bubble.hintLang.takeIf { it.isNotBlank() } ?: bubble.sourceLang
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (bubble.isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (bubble.isMine) VerbigemTheme.colors.accent else VerbigemTheme.colors.surface)
                .border(
                    width = if (bubble.isMine) 0.dp else 1.dp,
                    color = VerbigemTheme.colors.border,
                    shape = RoundedCornerShape(14.dp)
                )
                .combinedClickable(onClick = {}, onLongClick = onOpenMenu)
                .padding(10.dp)
        ) {
            // Faza 5.2/5.4: miniatura zdjęcia (Coil) + klik → podgląd na pełnym ekranie,
            // wskaźnik postępu pobierania (loading) i ikona błędu (error).
            if (bubble.type == "image" && bubble.attachmentUrl.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = bubble.attachmentUrl,
                    contentDescription = stringResource(R.string.photo),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenImage(bubble.attachmentUrl) },
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            Modifier.fillMaxWidth().height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = VerbigemTheme.colors.accent,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            Modifier.fillMaxWidth().height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = VerbigemTheme.colors.muted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            // Faza 5.3: głosówka — ikona mikrofonu przed transkrybowanym tekstem.
            if (bubble.type == "audio") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (bubble.isMine) Color.White.copy(alpha = 0.85f) else VerbigemTheme.colors.muted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = displayText,
                        color = if (bubble.isMine) Color.White else VerbigemTheme.colors.ink,
                        fontSize = 15.sp
                    )
                }
            } else {
                Text(
                    text = displayText,
                    color = if (bubble.isMine) Color.White else VerbigemTheme.colors.ink,
                    fontSize = 15.sp
                )
            }

            // Translated incoming message: keep the original visible under it.
            if (isTranslated && !showOriginal) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlagIcon(lang = LangCode.fromCode(bubble.sourceLang), size = 13.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.original_prefix, bubble.text),
                        color = if (bubble.isMine) Color.White.copy(alpha = 0.75f) else VerbigemTheme.colors.muted,
                        fontSize = 11.sp
                    )
                }
            }

            if (isTranslating) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = VerbigemTheme.colors.accent,
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.chat_translating),
                        fontSize = 11.sp,
                        color = VerbigemTheme.colors.muted
                    )
                }
            }

            // No model (or the run failed): offer a manual retry instead of
            // silently leaving the message in a foreign language.
            if (hasFailed && !isTranslating) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onRetranslate) {
                    Icon(
                        Icons.Default.Translate,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = VerbigemTheme.colors.accent
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.chat_translate_now),
                        fontSize = 11.sp,
                        color = VerbigemTheme.colors.accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(bubble.createdAt)),
                    fontSize = 10.sp,
                    color = if (bubble.isMine) Color.White.copy(alpha = 0.75f) else VerbigemTheme.colors.muted
                )

                if (bubble.isMine) {
                    // Hoisted out of the `semantics` lambda — that lambda is not @Composable,
                    // so it cannot call stringResource directly.
                    val sendingLabel = stringResource(R.string.chat_msg_sending)
                    Spacer(modifier = Modifier.width(4.dp))
                    when (bubble.status) {
                        BubbleStatus.SENDING -> Text(
                            text = "🕓",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            // The clock glyph carries no meaning for a screen reader.
                            modifier = Modifier.semantics {
                                contentDescription = sendingLabel
                            }
                        )
                        BubbleStatus.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.chat_msg_failed),
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = stringResource(R.string.chat_retry_send),
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier.clickable(onClick = onRetrySend)
                            )
                        }
                        BubbleStatus.SENT -> Row {
                            if (readByOther) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.chat_read_receipt),
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.chat_sent),
                                tint = Color.White.copy(alpha = if (readByOther) 1f else 0.6f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { onSpeak(displayText, displayLang) },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.action_read),
                        tint = if (bubble.isMine) Color.White.copy(alpha = 0.8f) else VerbigemTheme.colors.muted,
                        modifier = Modifier.size(15.dp)
                    )
                }
                ProFeatureButton(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.action_read_pro),
                    isPro = isPro,
                    onProClick = { onSpeakPro(displayText, displayLang) },
                    modifier = Modifier.size(22.dp),
                    tooltipText = stringResource(R.string.pro_speaker_tooltip)
                )
            }

            DropdownMenu(expanded = isMenuOpen, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_copy)) },
                    onClick = { clipboard.setText(AnnotatedString(displayText)); onDismissMenu() },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
                if (translated != null || bubble.hintText.isNotBlank()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (showOriginal) stringResource(R.string.chat_show_translation)
                                else stringResource(R.string.chat_show_original)
                            )
                        },
                        onClick = { onToggleOriginal(); onDismissMenu() },
                        leadingIcon = {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
                if (!bubble.isMine) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_translate_now)) },
                        onClick = { onRetranslate(); onDismissMenu() },
                        leadingIcon = {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_quote)) },
                    onClick = { onQuote(); onDismissMenu() },
                    leadingIcon = {
                        Icon(Icons.Default.FormatQuote, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_delete_for_me)) },
                    onClick = { onDelete(); onDismissMenu() },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}

/**
 * Today / Yesterday / dd.MM.yyyy — the day divider in the thread. The two words are
 * passed in as resources: this function is not a @Composable, so it cannot call
 * stringResource itself, and hardcoding them would break the 6-language rule.
 */
private fun dayLabel(millis: Long, today: String, yesterday: String): String {
    if (millis <= 0L) return ""
    val cal = Calendar.getInstance()
    val startOfToday = cal.apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return when {
        millis >= startOfToday -> today
        millis >= startOfToday - 24 * 3600 * 1000L -> yesterday
        else -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(millis))
    }
}
