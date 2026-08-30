package com.verbigem.app.ui.screens.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Translate
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import com.verbigem.app.data.model.TranslationHistory
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.ui.components.FlagIcon
import com.verbigem.app.ui.components.ProFeatureButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.verbigem.app.R
import com.verbigem.app.ui.theme.VerbigemTheme

@Composable
fun OcrScreen(
    viewModel: OcrViewModel,
    isPro: Boolean
) {
    val context = LocalContext.current
    val recognizedText by viewModel.recognizedText.collectAsState()
    val translatedText by viewModel.translatedText.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedBitmap by viewModel.selectedBitmap.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val cropRect by viewModel.cropRectFlow.collectAsState()
    val historyList by viewModel.historyList.collectAsState()

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.processBitmap(bitmap)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            viewModel.setError(context.getString(R.string.ocr_permission_denied))
        }
    }

    fun launchCameraWithPermissionCheck() {
        val permission = android.Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(permission)
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding(),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { launchCameraWithPermissionCheck() },
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

        if (selectedBitmap != null) {
            val bmp = selectedBitmap!!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
            ) {
                // The picture itself (fills the image area in pixels, so the overlay
                // Canvas coordinates map 1:1 to the picture).
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )

                // Draggable crop frame. Touching OUTSIDE the frame does not consume the
                // gesture, so the page keeps scrolling (needed when the photo is taller
                // than the screen and the lower handle sits below the fold).
                if (cropRect != null) {
                    CropOverlay(
                        rect = cropRect!!,
                        onRect = { viewModel.updateCropRect(it) }
                    )
                }

                IconButton(
                    onClick = { viewModel.clearCrop() },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = VerbigemTheme.colors.danger)
                }

                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(color = VerbigemTheme.colors.accent)
                    }
                }
            }

            Text(
                text = stringResource(R.string.ocr_crop_hint),
                fontSize = 11.sp,
                color = VerbigemTheme.colors.muted
            )

            Button(
                onClick = { viewModel.runOcrFromCrop() },
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.CropFree, contentDescription = null, tint = VerbigemTheme.colors.ink, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.ocr_read_selected), color = VerbigemTheme.colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        OutlinedTextField(
            value = recognizedText,
            onValueChange = { viewModel.updateRecognizedText(it) },
            label = { Text(stringResource(R.string.recognized_text)) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(14.dp))
                .padding(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VerbigemTheme.colors.accent,
                unfocusedBorderColor = VerbigemTheme.colors.border
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            ),
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.translateText() })
        )

        Button(
            onClick = { viewModel.translateText() },
            enabled = !isProcessing && recognizedText.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.ocr_translate_button), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        if (!recognizedText.isBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VerbigemTheme.colors.accent.copy(alpha = 0.12f))
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    stringResource(R.string.ocr_translation),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerbigemTheme.colors.accent
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = translatedText ?: "",
                    color = VerbigemTheme.colors.ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("ocr_translation", translatedText))
                        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copied), tint = VerbigemTheme.colors.accent)
                    }
                    IconButton(onClick = { viewModel.speak(translatedText ?: "") }) {
                        if (isSpeaking) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = VerbigemTheme.colors.accent,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.speak_again), tint = VerbigemTheme.colors.accent)
                        }
                    }
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = VerbigemTheme.colors.danger,
                fontSize = 13.sp
            )
        }

        // Historia OCR (jak w tłumaczeniach) — ostatnie wpisy z ikonami akcji.
        if (historyList.isNotEmpty()) {
            Text(
                text = stringResource(R.string.recent_translations),
                color = VerbigemTheme.colors.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(historyList.take(8)) { item ->
                    OcrHistoryItem(
                        item = item,
                        onCopy = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("translation", item.translatedText))
                            Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                        },
                        onShare = {
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, item.translatedText)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, context.getString(R.string.action_share)))
                        },
                        onRead = { viewModel.speak(item.translatedText) },
                        onDelete = { viewModel.deleteHistory(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun OcrHistoryItem(
    item: TranslationHistory,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRead: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VerbigemTheme.colors.surface)
            .border(0.5.dp, VerbigemTheme.colors.border, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FlagIcon(lang = LangCode.fromCode(item.sourceLang), size = 18.dp)
            Text("→", color = VerbigemTheme.colors.muted, fontSize = 13.sp)
            FlagIcon(lang = LangCode.fromCode(item.targetLang), size = 18.dp)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy), tint = VerbigemTheme.colors.accent)
            }
            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share), tint = VerbigemTheme.colors.accent)
            }
            IconButton(onClick = onRead, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.action_read), tint = VerbigemTheme.colors.accent)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = VerbigemTheme.colors.danger)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = item.sourceText, color = VerbigemTheme.colors.muted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = item.translatedText, color = VerbigemTheme.colors.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
