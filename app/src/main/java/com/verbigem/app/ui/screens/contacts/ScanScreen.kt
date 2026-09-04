package com.verbigem.app.ui.screens.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.verbigem.app.R
import com.verbigem.app.data.ProfileLinks
import com.verbigem.app.ui.theme.VerbigemTheme

/**
 * Skaner kodów QR (Faza 4.2).
 *
 * Używa GMS Code Scanner (`play-services-code-scanner`) — gotowy interfejs z
 * Play Services, który sam prosi o uprawnienie aparatu i nie wymaga własnej
 * implementacji CameraX. Wynik (surowy URL) parsujemy przez `ProfileLinks.uidFromUrl`:
 *  - Verbigem `/u/<uid>` → otwieramy kartę kontaktu tej osoby,
 *  - cokolwiek innego → komunikat „to nie kod Verbigem" (nie otwieramy obcej strony).
 */
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onScannedUid: (String) -> Unit
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ScanState.Scanning) }

    val startScan: () -> Unit = {
        state = ScanState.Scanning
        runCatching {
            val scanner = GmsBarcodeScanning.getClient(context)
            scanner.startScan()
                .addOnSuccessListener { barcode: Barcode ->
                    val uid = ProfileLinks.uidFromUrl(barcode.rawValue)
                    if (uid != null) {
                        onScannedUid(uid)
                    } else {
                        state = ScanState.NotVerbigem
                    }
                }
                .addOnCanceledListener { onBack() }
                .addOnFailureListener { state = ScanState.Failed }
        }.onFailure { state = ScanState.Failed }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startScan() else state = ScanState.NoCamera
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startScan()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
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
                text = stringResource(R.string.qr_scan),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = VerbigemTheme.colors.ink
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(VerbigemTheme.colors.surface)
                .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                ScanState.Scanning ->
                    CircularProgressIndicator(color = VerbigemTheme.colors.accent, modifier = Modifier.size(36.dp))
                ScanState.NoCamera, ScanState.Failed ->
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = VerbigemTheme.colors.danger, modifier = Modifier.size(44.dp))
                ScanState.NotVerbigem ->
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = VerbigemTheme.colors.danger, modifier = Modifier.size(44.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val message = when (state) {
            ScanState.Scanning -> stringResource(R.string.qr_scan_hint)
            ScanState.NoCamera -> stringResource(R.string.qr_scan_no_camera)
            ScanState.Failed -> stringResource(R.string.qr_scan_failed)
            ScanState.NotVerbigem -> stringResource(R.string.qr_not_verbigem)
        }
        Text(
            text = message,
            fontSize = 14.sp,
            color = if (state == ScanState.Scanning) VerbigemTheme.colors.muted else VerbigemTheme.colors.danger,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ----------------------------------------------------- actions
        when (state) {
            ScanState.Scanning -> { /* scanner overlay is up; nothing here */ }
            ScanState.NoCamera -> {
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                    modifier = Modifier.fillMaxWidth(0.7f).height(44.dp)
                ) {
                    Text(stringResource(R.string.action_back), color = Color.White, fontSize = 14.sp)
                }
            }
            ScanState.Failed, ScanState.NotVerbigem -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(stringResource(R.string.action_back), color = VerbigemTheme.colors.ink, fontSize = 14.sp)
                    }
                    Button(
                        onClick = startScan,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(stringResource(R.string.qr_scan_retry), color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private enum class ScanState { Scanning, NoCamera, Failed, NotVerbigem }
