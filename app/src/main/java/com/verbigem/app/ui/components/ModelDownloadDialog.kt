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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.verbigem.app.data.model.ModelDownloadState
import com.verbigem.app.ui.theme.VerbigemTheme

@Composable
fun ModelDownloadDialog(
    downloadState: ModelDownloadState,
    onStartDownload: () -> Unit,
    onDismiss: () -> Unit
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
                    text = "📥 Pobierz model Szybki",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerbigemTheme.colors.ink
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Do natywnego tłumaczenia offline na Twoim telefonie pobierz raz pakiet modelu Szybki (~1 GB). Po pobraniu działa w 100% bez internetu.",
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
                            Text("Pobierz teraz (~1 GB)", fontWeight = FontWeight.Bold)
                        }
                    }
                    is ModelDownloadState.Downloading -> {
                        Text(
                            text = "Pobieranie modelu Szybki: ${downloadState.progressPercent}%",
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
                            text = "Ładowanie modelu do pamięci NDK...",
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
                            text = "✓ Model Szybki jest gotowy do użycia!",
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
                            Text("OK", fontWeight = FontWeight.Bold)
                        }
                    }
                    is ModelDownloadState.Error -> {
                        Text(
                            text = "Błąd: ${downloadState.message}",
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
                            Text("Spróbuj ponownie")
                        }
                    }
                }
            }
        }
    }
}
