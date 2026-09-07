package com.verbigem.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.R
import com.verbigem.app.ui.components.HelpIconButton
import com.verbigem.app.ui.components.HelpWindow
import com.verbigem.app.ui.components.HelpWindowState
import com.verbigem.app.ui.components.LangSelect
import com.verbigem.app.ui.components.helpClickable
import com.verbigem.app.ui.components.rememberHelpWindowState
import com.verbigem.app.ui.theme.VerbigemTheme

/**
 * The user's termbase (Room v9, `glossary`).
 *
 * Everything here feeds [com.verbigem.app.engine.GlossaryPrompt]: an entry only
 * reaches the model when its source word actually appears in the text being
 * translated, so adding a term costs nothing until it matches.
 *
 * Not Pro-gated on purpose — there is no purchase flow in the app yet, so a
 * Pro-only screen would be invisible to everyone. Flip it later by checking
 * `UserProfile.isPro` at the Profile entry point.
 */
@Composable
fun GlossaryScreen(
    viewModel: GlossaryViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val sourceLang by viewModel.sourceLang.collectAsState()
    val targetLang by viewModel.targetLang.collectAsState()

    var sourceTerm by rememberSaveable { mutableStateOf("") }
    var targetTerm by rememberSaveable { mutableStateOf("") }
    var caseSensitive by rememberSaveable { mutableStateOf(false) }

    val help = rememberHelpWindowState()
    HelpWindow(help)

    val titleText = stringResource(R.string.glossary_title)
    val helpText = stringResource(R.string.glossary_help)
    val pairText = stringResource(R.string.glossary_pair)
    val deleteText = stringResource(R.string.glossary_delete)

    // The pair select lets you pick the same language on both sides; the model
    // cannot honour a term that is already in the target language, so refuse.
    val samePair = sourceLang == targetLang
    val canAdd = !samePair && sourceTerm.isNotBlank() && targetTerm.isNotBlank()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HelpIconButton(
                    onClick = onBack,
                    helpState = help,
                    helpTitle = titleText,
                    helpText = helpText,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = VerbigemTheme.colors.ink
                    )
                }
                Text(
                    text = titleText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerbigemTheme.colors.ink
                )
            }
        }

        // ------------------------------------------------------ language pair
        item {
            GlossaryCard(help = help, helpTitle = pairText, helpText = helpText) {
                Text(pairText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LangSelect(
                        selectedLang = sourceLang,
                        onLangSelected = { viewModel.setSourceLang(it) },
                        modifier = Modifier.weight(1f),
                        helpState = help,
                        helpTitle = pairText,
                        helpText = helpText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("→", fontSize = 18.sp, color = VerbigemTheme.colors.muted)
                    Spacer(modifier = Modifier.width(8.dp))
                    LangSelect(
                        selectedLang = targetLang,
                        onLangSelected = { viewModel.setTargetLang(it) },
                        modifier = Modifier.weight(1f),
                        helpState = help,
                        helpTitle = pairText,
                        helpText = helpText
                    )
                }
            }
        }

        // ------------------------------------------------------------- add row
        item {
            GlossaryCard(help = help, helpTitle = titleText, helpText = helpText) {
                OutlinedTextField(
                    value = sourceTerm,
                    onValueChange = { sourceTerm = it },
                    label = { Text(stringResource(R.string.glossary_source_term)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VerbigemTheme.colors.accent,
                        unfocusedBorderColor = VerbigemTheme.colors.border
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetTerm,
                    onValueChange = { targetTerm = it },
                    label = { Text(stringResource(R.string.glossary_target_term)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VerbigemTheme.colors.accent,
                        unfocusedBorderColor = VerbigemTheme.colors.border
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .helpClickable(onClick = { caseSensitive = !caseSensitive }, onLongClick = { help.show(titleText, helpText) }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = caseSensitive,
                        onCheckedChange = { caseSensitive = it }
                    )
                    Text(
                        text = stringResource(R.string.glossary_case_sensitive),
                        fontSize = 13.sp,
                        color = VerbigemTheme.colors.ink
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.add(sourceTerm, targetTerm, caseSensitive)
                        sourceTerm = ""
                        targetTerm = ""
                        caseSensitive = false
                    },
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent)
                ) {
                    Text(stringResource(R.string.glossary_add), fontSize = 13.sp)
                }
            }
        }

        // -------------------------------------------------------------- entries
        if (entries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.glossary_empty),
                    fontSize = 13.sp,
                    color = VerbigemTheme.colors.muted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        items(entries, key = { it.id }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.sourceTerm,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = VerbigemTheme.colors.ink
                    )
                    Text(
                        text = entry.targetTerm,
                        fontSize = 14.sp,
                        color = VerbigemTheme.colors.ink
                    )
                }
                IconButton(onClick = { viewModel.delete(entry.id) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = deleteText,
                        tint = VerbigemTheme.colors.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** The standard surface card used across Profile, with long-press help. */
@Composable
private fun GlossaryCard(
    help: HelpWindowState,
    helpTitle: String,
    helpText: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VerbigemTheme.colors.surface)
            .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
            .helpClickable(onClick = {}, onLongClick = { help.show(helpTitle, helpText) })
            .padding(16.dp)
    ) {
        content()
    }
}
