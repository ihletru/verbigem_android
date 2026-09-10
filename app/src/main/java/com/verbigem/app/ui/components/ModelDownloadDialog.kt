package com.verbigem.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.verbigem.app.R
import com.verbigem.app.data.model.ModelDownloadState
import com.verbigem.app.data.model.ModelTier
import com.verbigem.app.ui.theme.VerbigemTheme

@Composable
fun ModelDownloadDialog(
    downloadState: ModelDownloadState,
    onStartDownload: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * Który model pobieramy. Nazwa i rozmiar idą z niego, więc dialog przy
     * modelu Dokładnym mówi o Dokładnym, a nie o Szybkim.
     *
     * ⚠️ Historycznie `sizeLabel` był osobnym parametrem i tekst
     * `model_download_body` miał sztywno wpisane „Szybki / ~440 MB" — przy
     * pobieraniu Dokładnego okno wciąż informowało o Szybkim.
     */
    tier: ModelTier = ModelTier.FAST,
    /**
     * „Pobierz mimo to" z ekranu [ModelDownloadState.MeteredWarning]. Musi
     * przekazywać `allowMetered = true` do `ModelDownloader`, inaczej
     * użytkownik kręci się w kółko: kliknięcie znów wyląduje w tym samym
     * ostrzeżeniu.
     */
    onConfirmMetered: () -> Unit = onStartDownload
) {
    Dialog(onDismissRequest = {
        if (downloadState !is ModelDownloadState.Downloading) {
            onDismiss()
        }
    }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = VerbigemTheme.colors.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.model_download_title, stringResource(tier.displayNameResId)),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerbigemTheme.colors.ink
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(
                        R.string.model_download_body,
                        stringResource(tier.displayNameResId),
                        tier.sizeLabel,
                    ),
                    fontSize = 14.sp,
                    color = VerbigemTheme.colors.muted,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (downloadState) {
                    is ModelDownloadState.Idle -> {
                        Button(
                            onClick = onStartDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.download_now, tier.sizeLabel),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    is ModelDownloadState.Downloading -> {
                        Text(
                            text = stringResource(
                                R.string.downloading_model,
                                stringResource(tier.displayNameResId),
                                downloadState.progressPercent,
                            ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VerbigemTheme.colors.accent
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadState.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = VerbigemTheme.colors.accent,
                            trackColor = VerbigemTheme.colors.border,
                        )
                    }
                    is ModelDownloadState.LoadingToMemory -> {
                        Text(
                            text = stringResource(R.string.loading_model_memory),
                            fontSize = 13.sp,
                            color = VerbigemTheme.colors.accent
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = VerbigemTheme.colors.accent
                        )
                    }
                    is ModelDownloadState.Ready -> {
                        Text(
                            text = stringResource(R.string.model_ready, stringResource(tier.displayNameResId)),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = VerbigemTheme.colors.success
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.ok), fontWeight = FontWeight.Bold)
                        }
                    }
                    is ModelDownloadState.MeteredWarning -> {
                        Text(
                            text = stringResource(
                                R.string.download_metered_warning,
                                downloadState.tier.sizeLabel
                            ),
                            fontSize = 13.sp,
                            color = VerbigemTheme.colors.danger,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onConfirmMetered,
                            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.download_anyway), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.cancel), color = VerbigemTheme.colors.muted)
                        }
                    }
                    is ModelDownloadState.Error -> {
                        Text(
                            text = stringResource(R.string.error_with_message, downloadState.message),
                            fontSize = 13.sp,
                            color = VerbigemTheme.colors.danger
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onStartDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}
