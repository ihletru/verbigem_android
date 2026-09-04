package com.verbigem.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.R
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.notifications.VerbigemNotifications
import com.verbigem.app.ui.theme.VerbigemTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Inbox: one row per conversation, newest first.
 *
 * This is the screen that was missing before phase 1 — without it the only way into a
 * thread was Contacts, so an ongoing conversation could not be resumed.
 */
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onOpenThread: (String) -> Unit,
    onOpenContacts: () -> Unit
) {
    val rows by viewModel.rows.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchHits by viewModel.searchHits.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchDone by viewModel.searchDone.collectAsState()

    // Notification permission, asked here rather than at startup.
    //
    // Android 13+ treats POST_NOTIFICATIONS like any dangerous permission, but asking
    // before the user has any reason to want a push mostly earns a "Deny" — and after
    // two denials the system stops offering. The inbox is the moment it makes sense:
    // they are looking at conversations, so "tell me when one of these answers" is a
    // coherent question. Asked once, then remembered.
    val context = LocalContext.current
    val preferences = remember(context) { PreferencesManager(context) }
    val askedBefore by preferences.askedNotifPermFlow.collectAsState(initial = false)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Granted or not, we never ask again — see KEY_ASKED_NOTIF_PERM. */ }

    LaunchedEffect(askedBefore) {
        if (askedBefore) return@LaunchedEffect
        // `hasPermission` is true below Android 13, where the prompt does not exist.
        if (VerbigemNotifications.hasPermission(context)) return@LaunchedEffect
        preferences.setAskedNotifPerm(true)
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.chat_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = VerbigemTheme.colors.ink,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onOpenContacts,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent)
            ) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = stringResource(R.string.chat_open_contacts),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.chat_open_contacts), fontSize = 12.sp)
            }
        }

        // Szukanie ma sens dopiero gdy jest w czym — puste konto nie ma wiadomości,
        // a pole obiecywałoby coś, czego nie da się spełnić.
        if (rows.isNotEmpty()) {
            MessageSearchField(
                query = searchQuery,
                onQueryChanged = viewModel::onSearchQueryChanged,
                onSearch = viewModel::search,
                onClear = viewModel::clearSearch
            )
        }

        when {
            isLoading && rows.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VerbigemTheme.colors.accent)
                }
            }
            isSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VerbigemTheme.colors.accent)
                }
            }
            searchDone && searchQuery.isNotBlank() -> {
                SearchResults(
                    hits = searchHits,
                    query = searchQuery,
                    onOpenThread = onOpenThread
                )
            }
            rows.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.chat_no_conversations),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VerbigemTheme.colors.ink
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.chat_start_hint),
                        fontSize = 13.sp,
                        color = VerbigemTheme.colors.muted
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows, key = { it.chatId }) { row ->
                        ChatRowCard(row = row, onClick = { onOpenThread(row.otherUid) })
                    }
                }
            }
        }
    }
}

/**
 * Pole wyszukiwania w wiadomościach (1.12).
 *
 * Wyszukiwanie odpala się akcją „szukaj" na klawiaturze, nie przy każdej literze:
 * kosztem jest jedno zapytanie do Firestore na rozmowę, więc szukanie na żywo
 * mieliłoby bazę bez żadnej korzyści dla użytkownika.
 */
@Composable
private fun MessageSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(14.dp),
        placeholder = {
            Text(
                stringResource(R.string.chat_search_placeholder),
                fontSize = 14.sp,
                color = VerbigemTheme.colors.muted
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = VerbigemTheme.colors.muted,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.chat_search_clear),
                        tint = VerbigemTheme.colors.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VerbigemTheme.colors.accent,
            unfocusedBorderColor = VerbigemTheme.colors.border
        )
    )
}

/**
 * Wyniki wyszukiwania.
 *
 * Podpowiedź o prefiksach jest wyświetlana zawsze — Firestore nie ma wyszukiwania
 * pełnotekstowego i dopasowuje początek zindeksowanego ciągu. Napisane wprost
 * oszczędza użytkownikowi domyślania się, czemu „kota" nic nie znalazło.
 */
@Composable
private fun SearchResults(
    hits: List<MessageSearchHit>,
    query: String,
    onOpenThread: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.chat_search_prefix_hint),
                fontSize = 11.sp,
                color = VerbigemTheme.colors.muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (hits.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.trim().length < ChatListViewModel.MIN_SEARCH_LENGTH) {
                            stringResource(
                                R.string.chat_search_min_length,
                                ChatListViewModel.MIN_SEARCH_LENGTH
                            )
                        } else {
                            stringResource(R.string.chat_search_no_results, query.trim())
                        },
                        fontSize = 14.sp,
                        color = VerbigemTheme.colors.muted
                    )
                }
            }
        } else {
            items(hits, key = { it.chatId + "/" + it.createdAt + "/" + it.text.hashCode() }) { hit ->
                MessageHitCard(hit = hit, onClick = { onOpenThread(hit.otherUid) })
            }
        }
    }
}

@Composable
private fun MessageHitCard(hit: MessageSearchHit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VerbigemTheme.colors.surface)
            .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(VerbigemTheme.colors.bg)
                .border(1.dp, VerbigemTheme.colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(hit.avatar, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.nickname,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerbigemTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (hit.isMine) {
                    stringResource(R.string.chat_you_prefix, hit.text)
                } else {
                    hit.text
                },
                fontSize = 13.sp,
                color = VerbigemTheme.colors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatListTimestamp(hit.createdAt),
            fontSize = 11.sp,
            color = VerbigemTheme.colors.muted
        )
    }
}

@Composable
private fun ChatRowCard(row: ChatRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VerbigemTheme.colors.surface)
            .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(VerbigemTheme.colors.bg)
                .border(1.dp, VerbigemTheme.colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(row.avatar, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A pinned conversation is the only row that carries a marker; muted
                // ones deliberately show nothing, since silence is the absence of a
                // badge, not a badge of its own.
                if (row.pinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.contact_card_pin),
                        tint = VerbigemTheme.colors.accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = row.nickname,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerbigemTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (row.lastMessageIsMine) {
                    stringResource(R.string.chat_you_prefix, row.lastMessage)
                } else {
                    row.lastMessage
                },
                fontSize = 13.sp,
                color = VerbigemTheme.colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatListTimestamp(row.lastMessageAt),
                fontSize = 11.sp,
                color = VerbigemTheme.colors.muted
            )
            if (row.unread) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(VerbigemTheme.colors.accent)
                )
            }
        }
    }
}

/** Today → HH:mm, this year → day.month, older → day.month.year. */
internal fun formatListTimestamp(millis: Long): String {
    if (millis <= 0L) return ""
    val date = Date(millis)
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return when {
        millis >= startOfToday -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        millis >= startOfToday - 6 * 24 * 3600 * 1000L ->
            SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(date)
    }
}
