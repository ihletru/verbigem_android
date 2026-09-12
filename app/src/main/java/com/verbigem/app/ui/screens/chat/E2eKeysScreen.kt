package com.verbigem.app.ui.screens.chat

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.R
import com.verbigem.app.data.AppLinks
import com.verbigem.app.data.openUrl
import com.verbigem.app.ui.components.HelpIconButton
import com.verbigem.app.ui.components.HelpWindow
import com.verbigem.app.ui.components.LocalizedAlertDialog
import com.verbigem.app.ui.components.rememberHelpWindowState
import com.verbigem.app.ui.theme.VerbigemTheme

/**
 * Tożsamość E2E konta: założenie klucza, hasło odzyskiwania, odtworzenie na nowym
 * urządzeniu — patrz `docs/czat-e2e.md` par. 5 i 6.
 *
 * Wejście jest z nagłówka skrzynki (ikona kłódki), bo to jedyne miejsce, w którym
 * użytkownik i tak myśli o czacie. Trzy stany pokazujemy wprost i każdy innym
 * zdaniem: „brak tożsamości" to nie awaria, „brak klucza na tym urządzeniu" to
 * nowy telefon, a tylko prawdziwy błąd jest błędem.
 */
@Composable
fun E2eKeysScreen(
    viewModel: E2eKeysViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val messageRes by viewModel.message.collectAsState()
    val confirmForget by viewModel.confirmForget.collectAsState()

    val help = rememberHelpWindowState()
    HelpWindow(help)

    var passphrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<Int?>(null) }

    val shownError = localError ?: messageRes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ------------------------------------------------------------- nagłówek
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HelpIconButton(
                onClick = onBack,
                helpState = help,
                helpTitle = stringResource(R.string.help_e2e_back),
                helpText = stringResource(R.string.help_e2e_back),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = VerbigemTheme.colors.ink
                )
            }
            Text(
                text = stringResource(R.string.e2e_keys_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = VerbigemTheme.colors.ink
            )
            Spacer(modifier = Modifier.weight(1f))
            HelpIconButton(
                onClick = {},
                helpState = help,
                helpTitle = stringResource(R.string.e2e_keys_title),
                helpText = stringResource(R.string.help_e2e_keys),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.e2e_keys_title),
                    tint = VerbigemTheme.colors.ink
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ------------------------------------------------------------ stan konta
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(VerbigemTheme.colors.surface)
                .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = when (state) {
                        E2eKeysViewModel.State.READY -> VerbigemTheme.colors.success
                        E2eKeysViewModel.State.NEEDS_PASSPHRASE -> VerbigemTheme.colors.danger
                        else -> VerbigemTheme.colors.muted
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        when (state) {
                            E2eKeysViewModel.State.READY -> R.string.e2e_keys_state_ready
                            E2eKeysViewModel.State.NEEDS_PASSPHRASE -> R.string.e2e_keys_state_needs_passphrase
                            E2eKeysViewModel.State.LOADING -> R.string.e2e_keys_state_loading
                            E2eKeysViewModel.State.NOT_CONFIGURED -> R.string.e2e_keys_state_not_configured
                        }
                    ),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerbigemTheme.colors.ink
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    when (state) {
                        E2eKeysViewModel.State.READY -> R.string.e2e_keys_explain_ready
                        E2eKeysViewModel.State.NEEDS_PASSPHRASE -> R.string.e2e_keys_explain_needs_passphrase
                        E2eKeysViewModel.State.LOADING -> R.string.e2e_keys_explain_loading
                        E2eKeysViewModel.State.NOT_CONFIGURED -> R.string.e2e_keys_explain_not_configured
                    }
                ),
                fontSize = 13.sp,
                color = VerbigemTheme.colors.muted
            )
        }

        if (state == E2eKeysViewModel.State.LOADING) {
            Spacer(modifier = Modifier.height(28.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VerbigemTheme.colors.accent)
            }
            return@Column
        }

        // --------------------------------------------------------------- formularz
        Spacer(modifier = Modifier.height(22.dp))

        val needsRepeat = state != E2eKeysViewModel.State.NEEDS_PASSPHRASE

        OutlinedTextField(
            value = passphrase,
            onValueChange = {
                passphrase = it
                localError = null
                viewModel.consumeMessage()
            },
            label = { Text(stringResource(R.string.e2e_keys_passphrase_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (needsRepeat) ImeAction.Next else ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VerbigemTheme.colors.accent,
                unfocusedBorderColor = VerbigemTheme.colors.border
            )
        )

        if (needsRepeat) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = repeat,
                onValueChange = {
                    repeat = it
                    localError = null
                    viewModel.consumeMessage()
                },
                label = { Text(stringResource(R.string.e2e_keys_passphrase_repeat_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VerbigemTheme.colors.accent,
                    unfocusedBorderColor = VerbigemTheme.colors.border
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.e2e_keys_passphrase_hint, E2eKeysViewModel.MIN_PASSPHRASE),
            fontSize = 12.sp,
            color = VerbigemTheme.colors.muted
        )

        Spacer(modifier = Modifier.height(18.dp))

        val submit = {
            localError = null
            if (passphrase.length < E2eKeysViewModel.MIN_PASSPHRASE) {
                localError = R.string.e2e_keys_too_short
            } else if (needsRepeat && passphrase != repeat) {
                localError = R.string.e2e_keys_mismatch
            } else {
                val secret = passphrase.toCharArray()
                when (state) {
                    E2eKeysViewModel.State.NEEDS_PASSPHRASE -> viewModel.restore(secret)
                    E2eKeysViewModel.State.READY -> viewModel.changePassphrase(secret)
                    else -> viewModel.create(secret)
                }
                passphrase = ""
                repeat = ""
            }
            Unit
        }

        Button(
            onClick = submit,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = VerbigemTheme.colors.ink,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = stringResource(
                    when (state) {
                        E2eKeysViewModel.State.NEEDS_PASSPHRASE -> R.string.e2e_keys_restore
                        E2eKeysViewModel.State.READY -> R.string.e2e_keys_change
                        else -> R.string.e2e_keys_create
                    }
                ),
                fontSize = 15.sp
            )
        }

        if (state == E2eKeysViewModel.State.READY) {
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = { viewModel.askForget() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.e2e_keys_forget),
                    fontSize = 13.sp,
                    color = VerbigemTheme.colors.danger
                )
            }
        }

        // ------------------------------------------------------------- komunikat
        if (shownError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            val isBad = shownError == R.string.e2e_keys_error ||
                shownError == R.string.e2e_keys_wrong_passphrase ||
                shownError == R.string.e2e_keys_too_short ||
                shownError == R.string.e2e_keys_mismatch ||
                shownError == R.string.e2e_keys_no_backup
            Text(
                text = stringResource(shownError),
                fontSize = 13.sp,
                color = if (isBad) VerbigemTheme.colors.danger else VerbigemTheme.colors.success
            )
        }

        // ------------------------------------------------- Faza 7: uczciwe wyjaśnienie
        // To samo co webappowe `chat.e2eHelp*` — pełna prawda o szyfrowaniu, w tym
        // to, czego NIE chronimy (metadane, brak weryfikacji tożsamości) i że
        // zapomniane hasło = utracona historia. Treść jest w zasobach (6 języków).
        Spacer(modifier = Modifier.height(24.dp))
        val ctx = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(VerbigemTheme.colors.surface)
                .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.e2e_help_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerbigemTheme.colors.ink
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.e2e_help_intro),
                fontSize = 13.sp,
                color = VerbigemTheme.colors.muted
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.e2e_help_protected),
                fontSize = 13.sp,
                color = VerbigemTheme.colors.muted
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.e2e_help_limits),
                fontSize = 13.sp,
                color = VerbigemTheme.colors.muted
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.e2e_help_new_device),
                fontSize = 13.sp,
                color = VerbigemTheme.colors.muted
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.e2e_help_pass_lost),
                fontSize = 13.sp,
                color = VerbigemTheme.colors.muted
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { ctx.openUrl(AppLinks.privacyPolicyFor(ctx)) }) {
                Text(
                    text = stringResource(R.string.e2e_help_learn_more),
                    fontSize = 13.sp,
                    color = VerbigemTheme.colors.accent
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    if (confirmForget) {
        LocalizedAlertDialog(
            onDismissRequest = { viewModel.dismissForget() },
            title = { Text(stringResource(R.string.e2e_keys_forget_confirm_title)) },
            text = { Text(stringResource(R.string.e2e_keys_forget_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.forgetLocal() }) {
                    Text(
                        text = stringResource(R.string.e2e_keys_forget_confirm_action),
                        color = VerbigemTheme.colors.danger
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissForget() }) {
                    Text(stringResource(R.string.cancel), color = VerbigemTheme.colors.muted)
                }
            }
        )
    }
}
