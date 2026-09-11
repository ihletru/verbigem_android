package com.verbigem.app.ui.screens.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.rememberLazyListState
import com.verbigem.app.data.model.EngineChoice
import com.verbigem.app.data.model.ModelTier
import com.verbigem.app.data.model.TranslationHistory
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.ui.components.EnginePicker
import com.verbigem.app.ui.components.FlagIcon
import com.verbigem.app.ui.components.HelpIconButton
import com.verbigem.app.ui.components.ModelDownloadDialog
import com.verbigem.app.ui.components.HelpWindow
import com.verbigem.app.ui.components.HelpWindowState
import com.verbigem.app.ui.components.ProFeatureButton
import com.verbigem.app.ui.components.ScreenHeader
import com.verbigem.app.ui.components.helpClickable
import com.verbigem.app.ui.components.rememberHelpWindowState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    val help = rememberHelpWindowState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val translatedText by viewModel.translatedText.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedBitmap by viewModel.selectedBitmap.collectAsState()
    val isSpeaking by viewModel.resultSpeaking.collectAsState()
    val isSpeakingPro by viewModel.resultSpeakingPro.collectAsState()
    val speakingSyncId by viewModel.speakingSyncId.collectAsState()
    val speakingProSyncId by viewModel.speakingProSyncId.collectAsState()
    val isPro by viewModel.isPro.collectAsState()
    val targetLang by viewModel.targetLang.collectAsState()
    val engineChoice by viewModel.engineChoice.collectAsState()
    val availableEngines by viewModel.availableEngines.collectAsState()
    val openRouterKey by viewModel.openRouterKey.collectAsState()
    val hasOwnKey = openRouterKey.isNotBlank()
    val secondaryTranslatedText by viewModel.secondaryTranslatedText.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val showDownloadDialog by viewModel.showDownloadDialog.collectAsState()
    val cropRect by viewModel.cropRectFlow.collectAsState()
    val historyItems by viewModel.historyItems.collectAsState()
    val historyListState = rememberLazyListState()
    LaunchedEffect(historyListState) {
        snapshotFlow {
            val info = historyListState.layoutInfo
            info.totalItemsCount > 0 &&
                (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 1
        }.collect { atEnd -> if (atEnd) viewModel.loadMoreHistory() }
    }

    // TakePicture writes the FULL-RESOLUTION photo to a temp file via a FileProvider
    // URI (NOT a downscaled thumbnail). The gallery path already decodes at full res,
    // so we reuse processImageUri to keep OCR quality identical for both sources.
    val cameraTempUri = remember {
        val dir = File(context.cacheDir, "ocr_camera").also { it.mkdirs() }
        val file = File(dir, "ocr_capture_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.processImageUri(cameraTempUri)
        } else {
            viewModel.setError(context.getString(R.string.ocr_camera_cancelled))
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(cameraTempUri)
        } else {
            viewModel.setError(context.getString(R.string.ocr_permission_denied))
        }
    }

    fun launchCameraWithPermissionCheck() {
        val permission = android.Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(cameraTempUri)
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
        ScreenHeader(
            title = stringResource(R.string.ocr_title),
            helpState = help,
            helpTitle = stringResource(R.string.help_intro_ocr_title),
            helpText = stringResource(R.string.help_intro_ocr)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HelpActionBox(
                onClick = { launchCameraWithPermissionCheck() },
                helpState = help,
                helpTitle = stringResource(R.string.camera),
                helpText = stringResource(R.string.help_ocr_camera),
                container = VerbigemTheme.colors.accent,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.camera), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            HelpActionBox(
                onClick = { galleryLauncher.launch("image/*") },
                helpState = help,
                helpTitle = stringResource(R.string.gallery),
                helpText = stringResource(R.string.help_ocr_gallery),
                container = VerbigemTheme.colors.surface,
                contentColor = VerbigemTheme.colors.ink,
                borderColor = VerbigemTheme.colors.border,
                modifier = Modifier.weight(1f)
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

                HelpIconButton(
                    onClick = { viewModel.clearCrop() },
                    helpState = help,
                    helpTitle = stringResource(R.string.action_delete),
                    helpText = stringResource(R.string.help_action_delete),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
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

            HelpActionBox(
                onClick = { viewModel.runOcrFromCrop() },
                enabled = !isProcessing,
                helpState = help,
                helpTitle = stringResource(R.string.ocr_read_selected),
                helpText = stringResource(R.string.help_ocr_text_field),
                container = VerbigemTheme.colors.surface,
                contentColor = VerbigemTheme.colors.ink,
                borderColor = VerbigemTheme.colors.border,
                modifier = Modifier.fillMaxWidth()
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

        // Wybór silnika — ten sam komponent i ten sam zapis w DataStore co w
        // Tłumaczu. Do v1.0.52 OCR był na sztywno na modelu Szybkim, więc
        // nagłówek wyniku zawsze mówił „(Szybki)" w każdym języku.
        EnginePicker(
            selectedEngine = engineChoice,
            onEngineSelected = { viewModel.setEngine(it) },
            isPro = isPro,
            hasOwnKey = hasOwnKey,
            helpState = help,
            availableEngines = availableEngines
        )

        HelpActionBox(
            onClick = { viewModel.translateText() },
            enabled = !isProcessing && recognizedText.isNotBlank(),
            helpState = help,
            helpTitle = stringResource(R.string.ocr_translate_button),
            helpText = stringResource(R.string.help_ocr_translate),
            container = VerbigemTheme.colors.accent,
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
                    // %1$s = nazwa wybranego silnika (Szybki / Dokładny / Oba /
                    // Online). Wcześniej była tu twardo wpisana nazwa „Szybki" —
                    // również w locale EN/DE/ES/TR/ZH, gdzie zostawało polskie
                    // słowo.
                    stringResource(
                        R.string.ocr_translation,
                        stringResource(engineChoice.shortNameResId)
                    ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerbigemTheme.colors.accent
                )
                Spacer(modifier = Modifier.height(6.dp))
                // ⚖️ BOTH: każdy wynik ma własną etykietę, bo inaczej nie
                // wiadomo, który jest który.
                if (engineChoice == EngineChoice.BOTH) {
                    Text(
                        text = "${EngineChoice.LOCAL_FAST.icon} ${
                            stringResource(EngineChoice.LOCAL_FAST.shortNameResId)
                        }",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VerbigemTheme.colors.muted
                    )
                }
                Text(
                    text = translatedText ?: "",
                    color = VerbigemTheme.colors.ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (engineChoice == EngineChoice.BOTH && secondaryTranslatedText != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${EngineChoice.LOCAL_ACCURATE.icon} ${
                            stringResource(EngineChoice.LOCAL_ACCURATE.shortNameResId)
                        }",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VerbigemTheme.colors.muted
                    )
                    Text(
                        text = secondaryTranslatedText ?: "",
                        color = VerbigemTheme.colors.ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    HelpIconButton(
                        onClick = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("ocr_translation", translatedText))
                            Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                        },
                        helpState = help,
                        helpTitle = stringResource(R.string.action_copy),
                        helpText = stringResource(R.string.help_action_copy),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copied), tint = VerbigemTheme.colors.accent)
                    }
                    HelpIconButton(
                        onClick = { viewModel.speak(translatedText ?: "", targetLang) },
                        helpState = help,
                        helpTitle = stringResource(R.string.action_read),
                        helpText = stringResource(R.string.help_action_read),
                        modifier = Modifier.size(48.dp)
                    ) {
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

        // Historia OCR (jak w tłumaczeniach) — ładuje się przy scrollowaniu (infinity window).
        if (historyItems.isNotEmpty()) {
            Text(
                text = stringResource(R.string.recent_translations),
                color = VerbigemTheme.colors.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            LazyColumn(
                state = historyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(historyItems) { item ->
                    OcrHistoryItem(
                        item = item,
                        helpState = help,
                        isPro = isPro,
                        isSpeaking = item.syncId == speakingSyncId,
                        isSpeakingPro = item.syncId == speakingProSyncId,
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
                            val shareIntent = android.content.Intent.createChooser(sendIntent, context.getString(R.string.action_share))
                            shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(shareIntent)
                        },
                        onRead = { viewModel.speakHistory(item) },
                        onReadPro = { viewModel.speakProHistory(item) },
                        onDelete = { viewModel.deleteHistory(item) }
                    )
                }
            }
        }
    }

    // Brak wag dla wybranego silnika → ten sam dialog pobierania co w Tłumaczu.
    if (showDownloadDialog) {
        val tier = engineChoice.modelTier
            ?: ModelTier.fromAccurate(engineChoice == EngineChoice.LOCAL_ACCURATE)
        ModelDownloadDialog(
            downloadState = downloadState,
            tier = tier,
            onStartDownload = { viewModel.startModelDownload(tier) },
            onConfirmMetered = { viewModel.startModelDownload(tier, allowMetered = true) },
            onDismiss = { viewModel.setShowDownloadDialog(false) }
        )
    }

    HelpWindow(help)
}

/**
 * Szeroki przycisk akcji: kliknięcie wykonuje zadanie, długie kliknięcie
 * otwiera okno pomocy. Jeden `clickable` na cały komponent — `Button` ma
 * własny, więc dokładanie drugiego zdławiłoby długie kliknięcie.
 */
@Composable
private fun HelpActionBox(
    onClick: () -> Unit,
    helpState: HelpWindowState,
    helpTitle: String,
    helpText: String,
    container: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.White,
    borderColor: Color? = null,
    content: @Composable RowScope.() -> Unit
) {
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container.copy(alpha = alpha))
            .then(
                if (borderColor != null) {
                    Modifier.border(1.dp, borderColor.copy(alpha = alpha), RoundedCornerShape(12.dp))
                } else Modifier
            )
            .helpClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = { helpState.show(helpTitle, helpText) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
fun OcrHistoryItem(
    item: TranslationHistory,
    isPro: Boolean,
    helpState: HelpWindowState,
    isSpeaking: Boolean,
    isSpeakingPro: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRead: () -> Unit,
    onReadPro: () -> Unit,
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
        // Górny pasek: flagi języków + wszystkie ikony akcji (jak w Translatorze).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FlagIcon(lang = LangCode.fromCode(item.sourceLang), size = 18.dp)
            Text("→", color = VerbigemTheme.colors.muted, fontSize = 13.sp)
            FlagIcon(lang = LangCode.fromCode(item.targetLang), size = 18.dp)
            Spacer(modifier = Modifier.weight(1f))
            // Kopiuj
            HelpIconButton(
                onClick = onCopy,
                helpState = helpState,
                helpTitle = stringResource(R.string.action_copy),
                helpText = stringResource(R.string.help_action_copy),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy), tint = VerbigemTheme.colors.accent)
            }
            // Udostępnij
            HelpIconButton(
                onClick = onShare,
                helpState = helpState,
                helpTitle = stringResource(R.string.action_share),
                helpText = stringResource(R.string.help_action_share),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share), tint = VerbigemTheme.colors.accent)
            }
            // Czytaj (darmowy TTS lokalny)
            HelpIconButton(
                onClick = onRead,
                helpState = helpState,
                helpTitle = stringResource(R.string.action_read),
                helpText = stringResource(R.string.help_action_read),
                modifier = Modifier.size(32.dp)
            ) {
                if (isSpeaking) {
                    CircularProgressIndicator(color = VerbigemTheme.colors.accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.action_read), tint = VerbigemTheme.colors.accent)
                }
            }
            // Czytaj Pro (płatne API — dla Pro aktywne, dla free szare + tooltip)
            if (isPro) {
                HelpIconButton(
                    onClick = onReadPro,
                    helpState = helpState,
                    helpTitle = stringResource(R.string.action_read_pro),
                    helpText = stringResource(R.string.help_action_read_pro),
                    modifier = Modifier.size(32.dp)
                ) {
                    if (isSpeakingPro) {
                        CircularProgressIndicator(color = VerbigemTheme.colors.accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(painter = painterResource(R.drawable.ic_speaker_pro), contentDescription = stringResource(R.string.action_read_pro), tint = VerbigemTheme.colors.accent)
                    }
                }
            } else {
                ProFeatureButton(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.action_read_pro),
                    isPro = false,
                    onProClick = {},
                    modifier = Modifier.size(32.dp),
                    tooltipText = stringResource(R.string.pro_speaker_tooltip),
                    helpState = helpState,
                    helpTitle = stringResource(R.string.action_read_pro),
                    helpText = stringResource(R.string.help_action_read_pro)
                )
            }
            // Skasuj z historii
            HelpIconButton(
                onClick = onDelete,
                helpState = helpState,
                helpTitle = stringResource(R.string.action_delete),
                helpText = stringResource(R.string.help_action_delete),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = VerbigemTheme.colors.danger)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = item.sourceText, color = VerbigemTheme.colors.muted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = item.translatedText, color = VerbigemTheme.colors.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
