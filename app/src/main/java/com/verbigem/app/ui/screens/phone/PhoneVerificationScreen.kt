package com.verbigem.app.ui.screens.phone

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.R
import com.verbigem.app.ui.theme.VerbigemTheme
import androidx.compose.foundation.text.KeyboardOptions

/**
 * The phone-verification gate shown the first time the user opens Chat or Contacts.
 *
 * Skipping is a first-class outcome, not a failure: the chat works without a number.
 * What a skip costs is discoverability — nobody with this person in their address
 * book will ever find them.
 *
 * NOTE ON WORDING: this screen is the only place that explains what leaves the phone,
 * so it says it plainly. Promise the user nothing the code does not do — we send a
 * hash, so we say "a short code", not "your number is safe with us".
 */
@Composable
fun PhoneVerificationScreen(
    viewModel: PhoneVerificationViewModel,
    onDone: () -> Unit,
    onSkip: () -> Unit
) {
    val step by viewModel.step.collectAsState()
    val phoneInput by viewModel.phoneInput.collectAsState()
    val codeInput by viewModel.codeInput.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val error by viewModel.error.collectAsState()
    val sentTo by viewModel.sentTo.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(
                when (step) {
                    PhoneVerificationStep.NUMBER -> R.string.phone_verify_title
                    PhoneVerificationStep.CODE -> R.string.phone_verify_code_title
                    PhoneVerificationStep.DONE -> R.string.phone_verify_done
                }
            ),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = VerbigemTheme.colors.ink
        )

        when (step) {
            PhoneVerificationStep.NUMBER -> {
                Text(
                    text = stringResource(R.string.phone_verify_intro),
                    fontSize = 14.sp,
                    color = VerbigemTheme.colors.muted
                )
                if (viewModel.detectedCountry.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.phone_verify_country, viewModel.detectedCountry),
                        fontSize = 12.sp,
                        color = VerbigemTheme.colors.muted
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = viewModel::onPhoneInputChanged,
                    placeholder = { Text(stringResource(R.string.phone_verify_hint), fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VerbigemTheme.colors.accent,
                        unfocusedBorderColor = VerbigemTheme.colors.border
                    )
                )
                PrimaryButton(
                    text = stringResource(R.string.phone_verify_send),
                    enabled = phoneInput.trim().length >= 6 && !isBusy,
                    isBusy = isBusy,
                    onClick = { viewModel.sendCode(context) }
                )
                TextButton(onClick = {
                    viewModel.skip()
                    onSkip()
                }) {
                    Text(stringResource(R.string.phone_verify_skip), color = VerbigemTheme.colors.muted)
                }
            }

            PhoneVerificationStep.CODE -> {
                Text(
                    text = stringResource(R.string.phone_verify_code_sent, sentTo.orEmpty()),
                    fontSize = 14.sp,
                    color = VerbigemTheme.colors.muted
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = viewModel::onCodeInputChanged,
                    placeholder = { Text(stringResource(R.string.phone_verify_code_hint), fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VerbigemTheme.colors.accent,
                        unfocusedBorderColor = VerbigemTheme.colors.border
                    )
                )
                PrimaryButton(
                    text = stringResource(R.string.phone_verify_confirm),
                    enabled = codeInput.trim().length >= 6 && !isBusy,
                    isBusy = isBusy,
                    onClick = { viewModel.confirm(context) }
                )
                TextButton(
                    enabled = !isBusy,
                    onClick = { viewModel.sendCode(context, resend = true) }
                ) {
                    Text(stringResource(R.string.phone_verify_resend), color = VerbigemTheme.colors.muted)
                }
            }

            PhoneVerificationStep.DONE -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(VerbigemTheme.colors.surface)
                        .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = VerbigemTheme.colors.success,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = stringResource(R.string.phone_verify_done_body),
                        fontSize = 14.sp,
                        color = VerbigemTheme.colors.ink
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryButton(
                    text = stringResource(R.string.phone_verify_close),
                    enabled = true,
                    isBusy = false,
                    onClick = onDone
                )
            }
        }

        if (error != null) {
            Text(
                text = error.orEmpty(),
                fontSize = 13.sp,
                color = VerbigemTheme.colors.danger
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent)
    ) {
        if (isBusy) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
        } else {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
