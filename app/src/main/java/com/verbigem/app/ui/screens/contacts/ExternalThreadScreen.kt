package com.verbigem.app.ui.screens.contacts

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.R
import com.verbigem.app.data.OutboundChannel
import com.verbigem.app.data.OutboundChannels
import com.verbigem.app.data.OutboundTarget
import com.verbigem.app.data.local.ExternalContactEntity
import com.verbigem.app.data.local.ExternalOutboxEntity
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.ui.components.LangSelect
import com.verbigem.app.ui.theme.VerbigemTheme
import kotlinx.coroutines.launch

/**
 * One-way thread with someone who does not have Verbigem (3.6).
 *
 * It looks like a chat and deliberately is not one: there is no incoming side, and
 * the banner says so. The alternative — a plain "share this text" screen — would
 * lose the history, and the history is the whole point: if you write to someone in
 * another language every week, you want last week's wording back.
 */
@Composable
fun ExternalThreadScreen(
    phone: String,
    viewModel: ExternalThreadViewModel,
    onBack: () -> Unit
) {
    val contact by viewModel.contact.collectAsState()
    val history by viewModel.history.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val translation by viewModel.translation.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val error by viewModel.error.collectAsState()
    val sourceLang by viewModel.sourceLang.collectAsState()

    LaunchedEffect(phone) { viewModel.load(phone) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entity = contact

    // Liczone raz na kontakt: `isAvailable` pyta PackageManagera, a to nie jest
    // coś, co chcemy robić przy każdej rekompozycji.
    val channels: List<OutboundChannel> = remember(entity, context) {
        val target = entity?.let {
            OutboundTarget(name = it.name, e164 = it.e164, loose = it.phone, email = it.email)
        }
        if (target == null) emptyList() else OutboundChannels.availableFor(context, target)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
    ) {
        ThreadHeader(
            title = entity?.name?.takeIf { it.isNotBlank() } ?: phone,
            subtitle = entity?.email?.takeIf { it.isNotBlank() } ?: phone,
            onBack = onBack
        )

        // The honesty banner. Everything about this screen follows from it: we do
        // not send, we hand over, and nothing comes back.
        Text(
            text = stringResource(R.string.external_one_way_notice),
            fontSize = 11.sp,
            color = VerbigemTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (entity == null) {
            // Nagłówek zostaje widoczny: bez strzałki „wstecz" ekran ładujący byłby
            // pułapką, z której wychodzi się tylko przyciskiem systemowym.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = VerbigemTheme.colors.accent)
            }
        } else {
            ThreadBody(
                entity = entity,
                history = history,
                sourceLang = sourceLang,
                onTargetLangChanged = viewModel::setTargetLang,
                modifier = Modifier.weight(1f)
            )
            ThreadComposer(
                entity = entity,
                draft = draft,
                translation = translation,
                isTranslating = isTranslating,
                error = error,
                channels = channels,
                onDraftChanged = viewModel::onDraftChanged,
                onTranslate = viewModel::translate,
                onHandOff = { channel -> scope.launch { viewModel.handOff(channel) } }
            )
        }
    }
}

@Composable
private fun ThreadHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = VerbigemTheme.colors.ink
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerbigemTheme.colors.ink,
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = VerbigemTheme.colors.muted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ThreadBody(
    entity: ExternalContactEntity,
    history: List<ExternalOutboxEntity>,
    sourceLang: LangCode,
    onTargetLangChanged: (LangCode) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.external_target_lang),
                    fontSize = 12.sp,
                    color = VerbigemTheme.colors.muted
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Chosen by hand, per person: an external contact has no profile,
                // so there is nothing to read the language from. Until it is set we
                // show the source language as a placeholder, and the composer says
                // out loud that a choice is still owed.
                LangSelect(
                    selectedLang = if (entity.lang.isBlank()) sourceLang
                    else LangCode.fromCode(entity.lang),
                    onLangSelected = onTargetLangChanged
                )
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.external_history_empty),
                    fontSize = 12.sp,
                    color = VerbigemTheme.colors.muted,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else {
            items(history, key = { it.id }) { entry ->
                // Nazwa kanału rozwiązuje się tu, nie w bazie: zapisujemy id,
                // więc stary wiersz czyta się w języku, który obowiązuje dziś.
                HistoryCard(
                    entry = entry,
                    channelLabel = stringResource(OutboundChannels.labelResFor(entry.channel))
                )
            }
        }
    }
}

@Composable
private fun ThreadComposer(
    entity: ExternalContactEntity,
    draft: String,
    translation: String,
    isTranslating: Boolean,
    error: String?,
    channels: List<OutboundChannel>,
    onDraftChanged: (String) -> Unit,
    onTranslate: () -> Unit,
    onHandOff: (OutboundChannel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VerbigemTheme.colors.surface)
            .border(
                width = 1.dp,
                color = VerbigemTheme.colors.border,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(14.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChanged,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            placeholder = {
                Text(
                    stringResource(R.string.external_composer_hint),
                    fontSize = 13.sp,
                    color = VerbigemTheme.colors.muted
                )
            },
            minLines = 2,
            maxLines = 5,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VerbigemTheme.colors.accent,
                unfocusedBorderColor = VerbigemTheme.colors.border
            )
        )

        if (translation.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VerbigemTheme.colors.bg)
                    .border(1.dp, VerbigemTheme.colors.accent, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.external_will_send),
                    fontSize = 10.sp,
                    color = VerbigemTheme.colors.muted
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = translation,
                    fontSize = 14.sp,
                    color = VerbigemTheme.colors.ink
                )
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = error,
                fontSize = 12.sp,
                color = VerbigemTheme.colors.danger
            )
        }

        // Wyłączony przycisk bez wyjaśnienia wygląda na awarię. Język trzeba
        // wybrać, bo dla kogoś bez profilu nie da się go odgadnąć.
        if (entity.lang.isBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.external_pick_lang_first),
                fontSize = 12.sp,
                color = VerbigemTheme.colors.muted
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onTranslate,
            enabled = draft.isNotBlank() && !isTranslating && entity.lang.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent)
        ) {
            if (isTranslating) {
                CircularProgressIndicator(
                    color = VerbigemTheme.colors.ink,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.external_translate), fontSize = 14.sp)
        }

        // An untranslated hand-off is allowed — but it has to be a deliberate tap
        // on a channel, never a side effect of translating.
        if (draft.isNotBlank() || translation.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                channels.forEach { channel ->
                    Button(
                        onClick = { onHandOff(channel) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VerbigemTheme.colors.bg
                        )
                    ) {
                        Text(
                            text = stringResource(channel.labelRes),
                            fontSize = 11.sp,
                            color = VerbigemTheme.colors.ink,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: ExternalOutboxEntity, channelLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VerbigemTheme.colors.surface)
            .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.external_sent_via, channelLabel),
                fontSize = 10.sp,
                color = VerbigemTheme.colors.muted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatExternalTimestamp(entry.createdAt),
                fontSize = 10.sp,
                color = VerbigemTheme.colors.muted
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = entry.translatedText.ifBlank { entry.originalText },
            fontSize = 14.sp,
            color = VerbigemTheme.colors.ink
        )
        // Oryginał zostaje pod spodem: za miesiąc nikt nie pamięta, co właściwie
        // chciał powiedzieć, a tłumaczenie maszynowe nie jest oryginałem.
        if (entry.translatedText.isNotBlank() && entry.originalText != entry.translatedText) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.originalText,
                fontSize = 11.sp,
                color = VerbigemTheme.colors.muted
            )
        }
    }
}

/** Today → HH:mm, else day.month. */
private fun formatExternalTimestamp(millis: Long): String {
    if (millis <= 0L) return ""
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    val startOfToday = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    return if (millis >= startOfToday) {
        fmt.format(java.util.Date(millis))
    } else {
        java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }
}
