package com.verbigem.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.R
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.ui.theme.VerbigemTheme

/**
 * The contact card — per-conversation settings for one person.
 *
 * Deliberately honest about what is and is not enforced server-side: "block" and
 * "mute" are local for now (there is no Cloud Function yet), and "delete
 * conversation" can only hide the thread because Firestore messages are
 * append-only. The copy says so, so nobody is surprised later.
 */
@Composable
fun ContactCardScreen(
    viewModel: ContactCardViewModel,
    otherUid: String,
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val defaultLang by viewModel.defaultLang.collectAsState()
    val isFriend by viewModel.isFriend.collectAsState()
    val isHidden by viewModel.isHidden.collectAsState()

    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(otherUid) { viewModel.open(otherUid) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(VerbigemTheme.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ------------------------------------------------------------- header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = VerbigemTheme.colors.ink
                )
            }
            Text(
                text = stringResource(R.string.contact_card_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = VerbigemTheme.colors.ink
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ------------------------------------------------------------- identity
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(VerbigemTheme.colors.surface)
                .border(1.dp, VerbigemTheme.colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(profile?.photoURL?.takeIf { it.isNotBlank() } ?: "🙂", fontSize = 34.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = profile?.nickname?.takeIf { it.isNotBlank() } ?: otherUid.take(6),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = VerbigemTheme.colors.ink
        )
        profile?.let {
            Text(
                text = stringResource(
                    R.string.chat_other_lang,
                    LangCode.fromCode(it.speakLangSource).displayName
                ),
                fontSize = 12.sp,
                color = VerbigemTheme.colors.muted
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---------------------------------------------------------------- alias
        SettingsCard(title = stringResource(R.string.contact_card_alias)) {
            OutlinedTextField(
                value = settings.alias,
                onValueChange = viewModel::setAlias,
                placeholder = {
                    Text(
                        profile?.nickname?.takeIf { it.isNotBlank() } ?: otherUid.take(6),
                        fontSize = 14.sp,
                        color = VerbigemTheme.colors.muted
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors()
            )
            Text(
                text = stringResource(R.string.contact_card_alias_note),
                fontSize = 11.sp,
                color = VerbigemTheme.colors.muted
            )
        }

        // ------------------------------------------------- translation language
        SettingsCard(title = stringResource(R.string.contact_card_lang_title)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LangChip(
                    label = stringResource(R.string.contact_card_lang_auto, defaultLang.displayName),
                    flag = "✨",
                    selected = settings.langOverride.isBlank(),
                    onClick = { viewModel.setLangOverride("") }
                )
                LangCode.entries.forEach { lang ->
                    LangChip(
                        label = lang.displayName,
                        flag = lang.flag,
                        selected = settings.langOverride.equals(lang.code, ignoreCase = true),
                        onClick = { viewModel.setLangOverride(lang.code) }
                    )
                }
            }
            Text(
                text = stringResource(R.string.contact_card_lang_note),
                fontSize = 11.sp,
                color = VerbigemTheme.colors.muted
            )
        }

        // -------------------------------------------------------------- toggles
        SettingsCard(title = stringResource(R.string.contact_card_prefs)) {
            ToggleRow(
                icon = { Icon(Icons.Default.PushPin, null, tint = VerbigemTheme.colors.accent, modifier = it) },
                title = stringResource(R.string.contact_card_pin),
                checked = settings.pinned,
                onCheckedChange = viewModel::setPinned
            )
            ToggleRow(
                icon = { Icon(Icons.AutoMirrored.Filled.VolumeOff, null, tint = VerbigemTheme.colors.accent, modifier = it) },
                title = stringResource(R.string.contact_card_mute),
                subtitle = stringResource(R.string.contact_card_mute_note),
                checked = settings.muted,
                onCheckedChange = viewModel::setMuted
            )
            ToggleRow(
                icon = { Icon(Icons.Default.Block, null, tint = VerbigemTheme.colors.accent, modifier = it) },
                title = stringResource(R.string.contact_card_block),
                subtitle = stringResource(R.string.contact_card_block_note),
                checked = settings.blocked,
                onCheckedChange = viewModel::setBlocked
            )
        }

        // ----------------------------------------------------------------- note
        SettingsCard(title = stringResource(R.string.contact_card_note)) {
            OutlinedTextField(
                value = settings.note,
                onValueChange = viewModel::setNote,
                placeholder = {
                    Text(stringResource(R.string.contact_card_note_placeholder), fontSize = 14.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --------------------------------------------------------------- actions
        Button(
            onClick = { onOpenThread(otherUid) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent)
        ) {
            Text(stringResource(R.string.contact_card_write), color = Color.White, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isHidden) {
            OutlinedButton(
                onClick = viewModel::restoreConversation,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    stringResource(R.string.contact_card_restore),
                    color = VerbigemTheme.colors.accent,
                    fontSize = 15.sp
                )
            }
        } else {
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VerbigemTheme.colors.danger)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = VerbigemTheme.colors.danger
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.contact_card_delete),
                    color = VerbigemTheme.colors.danger,
                    fontSize = 15.sp
                )
            }
        }

        if (!isFriend) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.contact_card_not_friend),
                fontSize = 11.sp,
                color = VerbigemTheme.colors.muted
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.contact_card_delete_title)) },
            text = { Text(stringResource(R.string.contact_card_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.hideConversation()
                    onBack()
                }) {
                    Text(stringResource(R.string.action_delete), color = VerbigemTheme.colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VerbigemTheme.colors.surface)
            .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = VerbigemTheme.colors.ink
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun LangChip(
    label: String,
    flag: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) VerbigemTheme.colors.accent else VerbigemTheme.colors.bg)
            .border(
                1.dp,
                if (selected) VerbigemTheme.colors.accent else VerbigemTheme.colors.border,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(flag, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (selected) Color.White else VerbigemTheme.colors.ink
        )
    }
}

@Composable
private fun ToggleRow(
    icon: @Composable (Modifier) -> Unit,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon(Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = VerbigemTheme.colors.ink)
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = VerbigemTheme.colors.muted)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VerbigemTheme.colors.accent
            )
        )
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VerbigemTheme.colors.accent,
    unfocusedBorderColor = VerbigemTheme.colors.border
)
