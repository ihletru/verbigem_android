package com.verbigem.app.ui.screens.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.verbigem.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.ui.theme.VerbigemTheme

@Composable
fun OcrScreen(
    viewModel: OcrViewModel
) {
    val context = LocalContext.current
    val recognizedText by viewModel.recognizedText.collectAsState()
    val translatedText by viewModel.translatedText.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedBitmap by viewModel.selectedBitmap.collectAsState()

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.processBitmap(bitmap)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.processImageUri(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.ocr_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = VerbigemTheme.colors.ink
        )
        Text(
            text = stringResource(R.string.ocr_subtitle),
            fontSize = 13.sp,
            color = VerbigemTheme.colors.muted
        )

        // Przyciski akcji (Aparat / Galeria)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { cameraLauncher.launch() },
                colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.camera), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { galleryLauncher.launch("image/*") },
                colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = VerbigemTheme.colors.ink, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.gallery), color = VerbigemTheme.colors.ink, fontSize = 14.sp)
            }
        }

        // Podgląd zdjęcia
        if (selectedBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
            ) {
                Image(
                    bitmap = selectedBitmap!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.ocr_photo),
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { viewModel.clear() },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove), tint = Color.White)
                }
            }
        }

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = VerbigemTheme.colors.accent)
            }
        }

        // Rozpoznany tekst
        if (recognizedText.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(stringResource(R.string.recognized_text), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = recognizedText, color = VerbigemTheme.colors.ink, fontSize = 14.sp)
            }
        }

        // Przetłumaczony tekst
        if (!translatedText.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VerbigemTheme.colors.accent.copy(alpha = 0.12f))
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(stringResource(R.string.ocr_translation), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.accent)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = translatedText ?: "", color = VerbigemTheme.colors.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("ocr_translation", translatedText))
                        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copied), tint = VerbigemTheme.colors.accent)
                    }
                    IconButton(onClick = { viewModel.speak(translatedText ?: "") }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = stringResource(R.string.speak_again), tint = VerbigemTheme.colors.accent)
                    }
                }
            }
        }

        if (errorMessage != null) {
            Text(text = errorMessage ?: "", color = VerbigemTheme.colors.danger, fontSize = 13.sp)
        }
    }
}
