package com.verbigem.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.R
import com.verbigem.app.ui.theme.VerbigemTheme

/**
 * Bramka weryfikacji e-maila. Pokazuje się zamiast aplikacji każdemu kontu
 * e-mail+hasło, które nie potwierdziło jeszcze adresu. Google i telefon omijają ją
 * (brak providera "password" albo `isEmailVerified` == true).
 */
@Composable
fun EmailVerificationScreen(
    viewModel: EmailVerificationViewModel,
    onVerified: () -> Unit,
    onSignOut: () -> Unit
) {
    val email by viewModel.email.collectAsState()
    val status by viewModel.status.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(VerbigemTheme.colors.surface)
                .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.email_verify_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = VerbigemTheme.colors.ink
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.email_verify_intro, email),
                fontSize = 14.sp,
                color = VerbigemTheme.colors.muted
            )

            PrimaryButton(
                text = stringResource(R.string.email_verify_check),
                enabled = !isBusy,
                isBusy = isBusy,
                onClick = { viewModel.check(context, onVerified) }
            )
            TextButton(
                onClick = { viewModel.resend(context) },
                enabled = !isBusy
            ) {
                Text(
                    stringResource(R.string.email_verify_resend),
                    color = VerbigemTheme.colors.accent,
                    fontSize = 13.sp
                )
            }
            TextButton(
                onClick = { viewModel.signOut(); onSignOut() },
                enabled = !isBusy
            ) {
                Text(
                    stringResource(R.string.email_verify_signout),
                    color = VerbigemTheme.colors.muted,
                    fontSize = 13.sp
                )
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = error ?: "",
                    color = VerbigemTheme.colors.danger,
                    fontSize = 12.sp
                )
            }
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
            CircularProgressIndicator(color = Color.White, modifier = Modifier.height(20.dp))
        } else {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
