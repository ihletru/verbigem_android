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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.R
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

        when {
            isLoading && rows.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VerbigemTheme.colors.accent)
                }
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
            Text(
                text = row.nickname,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerbigemTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
