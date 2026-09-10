package com.verbigem.app.ui.screens.profile

import kotlin.math.ceil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.verbigem.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.verbigem.app.ui.components.buildOpenRouterLinkedText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.BuildConfig
import com.verbigem.app.ads.AdsConsent
import com.verbigem.app.ui.screens.phone.findActivity
import kotlinx.coroutines.launch
import android.util.Log
import com.verbigem.app.data.AppLinks
import com.verbigem.app.data.openUrl
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.model.ModelTier
import com.verbigem.app.data.model.OnlineModels
import com.verbigem.app.ui.components.HelpWindow
import com.verbigem.app.ui.components.LangSelect
import com.verbigem.app.ui.components.ScreenHeader
import com.verbigem.app.ui.components.UiLangSelect
import com.verbigem.app.ui.components.helpClickable
import com.verbigem.app.ui.components.rememberHelpWindowState
import com.verbigem.app.ui.theme.VerbigemTheme

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLogout: () -> Unit,
    onOpenPhoneVerification: () -> Unit,
    onOpenMyQr: () -> Unit,
    onOpenGlossary: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsState()
    val phoneVerified by viewModel.phoneVerified.collectAsState()
    val nicknameInput by viewModel.nicknameInput.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState(initial = "calm")
    val currentMode by viewModel.currentMode.collectAsState(initial = "day")
    val currentUiLang by viewModel.currentUiLang.collectAsState(initial = "pl")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // UMP (zgody reklamowe) mówi, czy użytkownik MUSI mieć wejście do ustawień
    // prywatności — prawda tylko w EOG / UK / CH.
    val privacyOptionsRequired by AdsConsent.privacyOptionsRequired.collectAsState()

    // Online translation models (OpenRouter) + own API key.
    val onlineModel by viewModel.onlineModelFlow.collectAsState(initial = OnlineModels.DEFAULT_ID)
    val hasOwnKey by viewModel.hasOwnKeyFlow.collectAsState(initial = false)
    val openRouterKey by viewModel.openRouterKeyFlow.collectAsState(initial = "")
    val walletCents by viewModel.walletCents.collectAsState(initial = 0L)
    val topUpLoading by viewModel.topUpLoading.collectAsState(initial = false)
    val topUpUrl by viewModel.topUpUrl.collectAsState(initial = null)
    val freeModels by viewModel.freeModels.collectAsState(initial = emptyList())
    val freeModelsLoading by viewModel.freeModelsLoading.collectAsState(initial = false)
    val downloadedModels by viewModel.downloadedModels.collectAsState(initial = emptyList())

    // Which local model (if any) is awaiting delete confirmation.
    var pendingDelete by remember { mutableStateOf<ModelTier?>(null) }

    // Whether the wallet top-up package chooser is open.
    var showTopUp by remember { mutableStateOf(false) }

    // Whether the "Remove ads" (one-time prepaid) package chooser is open.
    var showNoAds by remember { mutableStateOf(false) }

    val help = rememberHelpWindowState()
    HelpWindow(help)

    // Open the Paddle checkout URL returned by createCheckout, then consume it.
    LaunchedEffect(topUpUrl) {
        topUpUrl?.let {
            showTopUp = false
            showNoAds = false
            context.openUrl(it)
            viewModel.consumeTopUpUrl()
        }
    }

    val helpNicknameTitle = stringResource(R.string.nick_label)
    val helpNicknameText = stringResource(R.string.help_profile_nickname)
    val helpUiLangTitle = stringResource(R.string.ui_lang_label)
    val helpUiLangText = stringResource(R.string.help_profile_ui_lang)
    val helpConvLangTitle = stringResource(R.string.speak_langs_label)
    val helpConvLangText = stringResource(R.string.help_profile_conv_langs)
    val helpAccountTitle = stringResource(R.string.account_status)
    val helpAccountText = stringResource(R.string.help_profile_account)
    val helpPhoneTitle = stringResource(R.string.profile_phone_title)
    val helpPhoneText = stringResource(R.string.help_profile_phone)
    val helpThemeTitle = stringResource(R.string.theme_label)
    val helpThemeText = stringResource(R.string.help_profile_theme)
    val helpQrTitle = stringResource(R.string.qr_my_code)
    val helpQrText = stringResource(R.string.help_profile_qr)
    val helpGlossaryTitle = stringResource(R.string.glossary_title)
    val helpGlossaryText = stringResource(R.string.glossary_help)
    val helpPrivacyTitle = stringResource(R.string.privacy_label)
    val helpPrivacyText = stringResource(R.string.help_profile_privacy)
    val helpLogoutTitle = stringResource(R.string.logout)
    val helpLogoutText = stringResource(R.string.help_profile_logout)

    // Nowe karty: pobrane modele, model online, własne API.
    val helpDownloadedTitle = stringResource(R.string.downloaded_models_title)
    val helpDownloadedText = stringResource(R.string.help_downloaded_models)
    val helpOnlineTitle = stringResource(R.string.online_model_title)
    val helpOnlineText = stringResource(R.string.help_online_model)
    val helpOwnApiTitle = stringResource(R.string.own_api_title)
    val helpOwnApiText = stringResource(R.string.help_own_api)


    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.profile_title),
                helpState = help,
                helpTitle = stringResource(R.string.help_intro_profile_title),
                helpText = stringResource(R.string.help_intro_profile)
            )
        }

        // Nickname
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .helpClickable(
                    onClick = {},
                    onLongClick = { help.show(helpNicknameTitle, helpNicknameText) }
                )
                .padding(16.dp)
            ) {
                Text(stringResource(R.string.nick_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = nicknameInput,
                        onValueChange = { viewModel.onNicknameChanged(it) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VerbigemTheme.colors.accent,
                            unfocusedBorderColor = VerbigemTheme.colors.border
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.saveNickname() },
                        colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }

        // Język interfejsu
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .helpClickable(
                    onClick = {},
                    onLongClick = { help.show(helpUiLangTitle, helpUiLangText) }
                )
                .padding(16.dp)
            ) {
                Text(stringResource(R.string.ui_lang_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(8.dp))
                UiLangSelect(
                    currentLangCode = currentUiLang,
                    onLangSelected = { viewModel.setUiLang(it) }
                )
            }
        }

        // Domyślne języki rozmowy
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.speak_langs_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        LangSelect(
                            selectedLang = LangCode.fromCode(profile?.speakLangSource ?: "pl"),
                            onLangSelected = { viewModel.setSpeakLangs(it, LangCode.fromCode(profile?.speakLangTarget ?: "en")) },
                            helpState = help,
                            helpTitle = stringResource(R.string.speak_langs_label),
                            helpText = stringResource(R.string.help_profile_conv_langs)
                        )
                    }
                    Text(" ⇄ ", color = VerbigemTheme.colors.muted, modifier = Modifier.padding(horizontal = 8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        LangSelect(
                            selectedLang = LangCode.fromCode(profile?.speakLangTarget ?: "en"),
                            onLangSelected = { viewModel.setSpeakLangs(LangCode.fromCode(profile?.speakLangSource ?: "pl"), it) },
                            helpState = help,
                            helpTitle = stringResource(R.string.speak_langs_label),
                            helpText = stringResource(R.string.help_profile_conv_langs)
                        )
                    }
                }
            }
        }

        // Pobrane modele lokalne (Szybki, Dokładny) z opcją usunięcia.
        item {
            val models = downloadedModels
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .helpClickable(
                        onClick = {},
                        onLongClick = { help.show(helpDownloadedTitle, helpDownloadedText) }
                    )
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.downloaded_models_title), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(8.dp))
                if (models.isEmpty()) {
                    Text(stringResource(R.string.downloaded_models_empty), fontSize = 13.sp, color = VerbigemTheme.colors.muted)
                } else {
                    models.forEach { tier ->
                        val name = stringResource(
                            if (tier == ModelTier.FAST) R.string.model_name_fast else R.string.model_name_accurate
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = VerbigemTheme.colors.ink)
                                Text(
                                    text = "${tier.sizeLabel} · ${stringResource(R.string.model_local_offline)}",
                                    fontSize = 12.sp, color = VerbigemTheme.colors.muted
                                )
                            }
                            IconButton(onClick = { pendingDelete = tier }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = VerbigemTheme.colors.danger,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Status konta
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                .helpClickable(
                    onClick = {},
                    onLongClick = { help.show(helpAccountTitle, helpAccountText) }
                )
                .padding(16.dp)
            ) {
                Text(stringResource(R.string.account_status), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (profile?.isPro == true) stringResource(R.string.pro_status) else stringResource(R.string.free_status),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (profile?.isPro == true) VerbigemTheme.colors.accent else VerbigemTheme.colors.ink
                )
                // Licznik czasu do wznowienia reklam (dni, zaokrąglane w górę — min. 1).
                val noAdsUntilMs = profile?.noAdsUntilMs ?: 0L
                if (noAdsUntilMs > System.currentTimeMillis()) {
                    val daysLeft = maxOf(
                        1,
                        ceil((noAdsUntilMs - System.currentTimeMillis()) / 86_400_000.0).toInt()
                    )
                    Text(
                        text = stringResource(
                            if (daysLeft == 1) R.string.noads_resume_in_one else R.string.noads_resume_in,
                            daysLeft
                        ),
                        fontSize = 13.sp,
                        color = VerbigemTheme.colors.success,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                // Saldo portfela pokazujemy ZAWSZE dla konta PRO — również 0.00,
                // żeby od razu było widać, że „Bez reklam" nie doładowuje portfela.
                if (profile?.isPro == true) {
                    Text(
                        text = stringResource(R.string.api_wallet, (profile?.walletCreditsCents ?: 0) / 100f),
                        fontSize = 13.sp,
                        color = VerbigemTheme.colors.success,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showTopUp = true },
                    enabled = !topUpLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (topUpLoading) stringResource(R.string.topup_loading) else stringResource(R.string.topup_button),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showNoAds = true },
                    enabled = !topUpLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.bg),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.noads_title), fontSize = 13.sp)
                }
            }
        }

        // Numer telefonu.
        // To jest jedyna droga powrotna po „Pomiń" na bramce weryfikacji — czat
        // działa bez numeru, więc bez tego wpisu pominięcie byłoby nieodwracalne.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .helpClickable(
                    onClick = {},
                    onLongClick = { help.show(helpPhoneTitle, helpPhoneText) }
                )
                .padding(16.dp)
            ) {
                Text(stringResource(R.string.profile_phone_title), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (phoneVerified) R.string.profile_phone_verified
                        else R.string.profile_phone_unverified
                    ),
                    fontSize = 14.sp,
                    color = if (phoneVerified) VerbigemTheme.colors.success else VerbigemTheme.colors.ink
                )
                if (!phoneVerified) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenPhoneVerification,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent)
                    ) {
                        Text(stringResource(R.string.profile_phone_action), fontSize = 13.sp)
                    }
                }
            }
        }

        // Wygląd i motywy
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .helpClickable(
                    onClick = {},
                    onLongClick = { help.show(helpThemeTitle, helpThemeText) }
                )
                .padding(16.dp)
            ) {
                Text(stringResource(R.string.theme_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("calm" to stringResource(R.string.theme_calm), "sharp" to stringResource(R.string.theme_sharp), "playful" to stringResource(R.string.theme_playful)).forEach { (key, label) ->
                        val isSelected = currentTheme == key
                        Button(
                            onClick = { viewModel.setTheme(key) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) VerbigemTheme.colors.accent else VerbigemTheme.colors.bg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else VerbigemTheme.colors.ink,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("day" to stringResource(R.string.mode_day), "night" to stringResource(R.string.mode_night)).forEach { (key, label) ->
                        val isSelected = currentMode == key
                        Button(
                            onClick = { viewModel.setMode(key) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) VerbigemTheme.colors.accent else VerbigemTheme.colors.bg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (key == "day") Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else VerbigemTheme.colors.ink,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else VerbigemTheme.colors.ink,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Wybór modelu tłumaczenia online (OpenRouter).
        item {
            val selectedModel = onlineModel
            val canUsePaid = walletCents > 0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .helpClickable(
                        onClick = {},
                        onLongClick = { help.show(helpOnlineTitle, helpOnlineText) }
                    )
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.online_model_title), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(8.dp))
                OnlineModels.CURATED.forEach { model ->
                    OnlineModelRow(
                        label = stringResource(model.labelResId),
                        desc = stringResource(model.descResId),
                        isSelected = selectedModel == model.id,
                        isRecommended = model.id == OnlineModels.DEFAULT_ID,
                        enabled = canUsePaid,
                        disabledNote = if (!canUsePaid) stringResource(R.string.online_paid_no_credits) else null,
                        onClick = { if (canUsePaid) viewModel.setOnlineModel(model.id) }
                    )
                }
                if (hasOwnKey) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.free_models_section), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                    Spacer(modifier = Modifier.height(6.dp))
                    when {
                        freeModelsLoading -> Text(stringResource(R.string.free_models_loading), fontSize = 13.sp, color = VerbigemTheme.colors.muted)
                        freeModels.isEmpty() -> Text(stringResource(R.string.free_models_empty), fontSize = 13.sp, color = VerbigemTheme.colors.muted)
                        else -> freeModels.forEach { fm ->
                            OnlineModelRow(
                                label = fm.name,
                                desc = if (fm.contextLength > 0) "${fm.contextLength / 1000}K ctx" else "",
                                isSelected = selectedModel == fm.id,
                                isRecommended = false,
                                enabled = true,
                                onClick = { viewModel.setOnlineModel(fm.id) }
                            )
                        }
                    }
                }
            }
        }

        // Własne API OpenRouter (darmowe modele na własny klucz).
        item {
            var keyInput by remember { mutableStateOf(openRouterKey) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .helpClickable(
                        onClick = {},
                        onLongClick = { help.show(helpOwnApiTitle, helpOwnApiText) }
                    )
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.own_api_title), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(4.dp))
                val ownApiExplainer = buildOpenRouterLinkedText(stringResource(R.string.own_api_explainer))
                ClickableText(
                    text = ownApiExplainer,
                    style = TextStyle(fontSize = 13.sp, color = VerbigemTheme.colors.ink),
                    onClick = { offset ->
                        ownApiExplainer.getStringAnnotations(tag = "url", start = offset, end = offset)
                            .firstOrNull()?.let { context.openUrl(it.item) }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (hasOwnKey) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VerbigemTheme.colors.success, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.own_api_status_on), fontSize = 13.sp, color = VerbigemTheme.colors.success, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.clearOpenRouterKey(); keyInput = "" },
                        colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.danger),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.own_api_clear))
                    }
                } else {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text(stringResource(R.string.own_api_placeholder)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VerbigemTheme.colors.accent,
                            unfocusedBorderColor = VerbigemTheme.colors.border
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row {
                        Button(
                            onClick = { viewModel.saveOpenRouterKey(keyInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.own_api_save))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { context.openUrl(AppLinks.openRouterKeys()) },
                            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.bg),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.own_api_howto))
                        }
                    }
                }
            }
        }

        // Mój kod QR (Faza 4.1) — udostępniasz go znajomym, by Cię znaleźli.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .helpClickable(
                    onClick = {},
                    onLongClick = { help.show(helpQrTitle, helpQrText) }
                )
                .padding(16.dp)
            ) {
                Text(stringResource(R.string.qr_my_code), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.qr_my_code_hint),
                    fontSize = 13.sp,
                    color = VerbigemTheme.colors.ink
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onOpenMyQr,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_my_code), fontSize = 13.sp)
                }
            }
        }

        // Słownik użytkownika (Room v9). Własne tłumaczenia wybranych słów —
        // jedyna dźwignia jakości, która nie wymaga ani GPU, ani pobierania
        // większego modelu. Wpis działa tylko na jednej parze języków i tylko
        // wtedy, gdy słowo faktycznie pada w tłumaczonym tekście.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .helpClickable(
                    onClick = {},
                    onLongClick = { help.show(helpGlossaryTitle, helpGlossaryText) }
                )
                .padding(16.dp)
            ) {
                Text(stringResource(R.string.glossary_title), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerbigemTheme.colors.muted)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.glossary_profile_hint),
                    fontSize = 13.sp,
                    color = VerbigemTheme.colors.ink
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onOpenGlossary,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.accent)
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.glossary_title), fontSize = 13.sp)
                }
            }
        }

        // Prywatność — link do opublikowanej polityki (wymóg Google Play).
        // Treść leży na mini.verbigem.com/privacy/<uiLang>/, więc zmiana nie wymaga
        // nowego wydania APK. Otwieramy w przeglądarce, nie w WebView — użytkownik
        // widzi pasek adresu i ma pewność, że to nasza domena.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .helpClickable(
                    onClick = {},
                    onLongClick = { help.show(helpPrivacyTitle, helpPrivacyText) }
                )
                .padding(16.dp)
            ) {
                Text(
                    stringResource(R.string.privacy_label),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerbigemTheme.colors.muted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { context.openUrl(AppLinks.privacyPolicy(currentUiLang)) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = VerbigemTheme.colors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.privacy_policy),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VerbigemTheme.colors.ink
                        )
                        Text(
                            stringResource(R.string.privacy_policy_desc),
                            fontSize = 12.sp,
                            color = VerbigemTheme.colors.muted
                        )
                    }
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = VerbigemTheme.colors.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Zgody reklamowe (UMP) — karta pokazuje się TYLKO gdy UMP tego
                // wymaga, czyli dla użytkowników z EOG / UK / CH. Reszta świata
                // jej nie zobaczy. Konta Pro nie widzą reklam, więc też jej nie
                // dostają — nie ma czego wycofywać.
                if (privacyOptionsRequired && profile?.isPro != true) {
                    val activity = context.findActivity()
                    if (activity != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch {
                                        runCatching { AdsConsent.showPrivacyOptions(activity) }
                                            .onFailure {
                                                Log.e("ProfileScreen", "Privacy options failed", it)
                                            }
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = VerbigemTheme.colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.privacy_ad_settings),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VerbigemTheme.colors.ink
                                )
                                Text(
                                    stringResource(R.string.privacy_ad_settings_desc),
                                    fontSize = 12.sp,
                                    color = VerbigemTheme.colors.muted
                                )
                            }
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = VerbigemTheme.colors.muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // O aplikacji — wersja + link do „co nowego".
        item {
            // `stringResource` musi być wywołane w composable scope — lambdy
            // onClick/onLongClick w helpClickable nie są composable, więc
            // rozwiązujemy tytuły tutaj i przekazujemy gotowe Stringi.
            val aboutTitle = stringResource(R.string.about_label)
            val aboutText = stringResource(R.string.help_about)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
                    .helpClickable(
                        onClick = {},
                        onLongClick = { help.show(aboutTitle, aboutText) }
                    )
                    .padding(16.dp)
            ) {
                Text(
                    stringResource(R.string.about_label),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerbigemTheme.colors.muted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerbigemTheme.colors.ink
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { context.openUrl(AppLinks.whatsNew(currentUiLang)) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = VerbigemTheme.colors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.whats_new_label),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VerbigemTheme.colors.ink
                        )
                        Text(
                            stringResource(R.string.whats_new_desc),
                            fontSize = 12.sp,
                            color = VerbigemTheme.colors.muted
                        )
                    }
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = VerbigemTheme.colors.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Wylogowanie
        item {
            Box(
                modifier = Modifier
                    .helpClickable(
                        onClick = {},
                        onLongClick = { help.show(helpLogoutTitle, helpLogoutText) }
                    )
            ) {
                Button(
                    onClick = {
                        viewModel.signOut()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.danger),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.logout), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Potwierdzenie usunięcia pobranego modelu.
    if (pendingDelete != null) {
        val tier = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteModel(tier); pendingDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.danger)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                Button(
                    onClick = { pendingDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.bg)
                ) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.downloaded_models_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.downloaded_models_delete_body,
                        if (tier == ModelTier.FAST) stringResource(R.string.model_name_fast)
                        else stringResource(R.string.model_name_accurate)
                    )
                )
            }
        )
    }

    // Wybór pakietu doładowania portfela (Paddle). Po wyborze wywołujemy
    // createCheckout — zwraca URL checkoutu, który LaunchedEffect wyżej otwiera.
    if (showTopUp) {
        val dialogLoading by viewModel.topUpLoading.collectAsState(initial = false)
        val dialogError by viewModel.topUpError.collectAsState(initial = null)
        AlertDialog(
            onDismissRequest = { showTopUp = false; viewModel.clearTopUpError() },
            confirmButton = {},
            dismissButton = {
                Button(
                    onClick = { showTopUp = false; viewModel.clearTopUpError() },
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.bg)
                ) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.topup_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.topup_dialog_subtitle), fontSize = 13.sp, color = VerbigemTheme.colors.muted)
                    Spacer(modifier = Modifier.height(10.dp))
                    listOf(
                        Triple("wallet3", R.string.topup_small, 300),
                        Triple("wallet5", R.string.topup_medium, 500),
                        Triple("wallet10", R.string.topup_large, 1000),
                    ).forEach { (type, labelRes, credits) ->
                        Button(
                            onClick = { showTopUp = false; viewModel.topUp(type) },
                            enabled = !dialogLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.bg),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Text(stringResource(labelRes), color = VerbigemTheme.colors.ink, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.topup_credits, credits), color = VerbigemTheme.colors.muted, fontSize = 12.sp)
                        }
                    }
                    dialogError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = VerbigemTheme.colors.danger, fontSize = 12.sp)
                    }
                }
            }
        )
    }

    // Wybór pakietu "Bez reklam" (Paddle, JEDNORAZOWA przedpłata — nie abonament).
    // createCheckout mapuje noAds1..10 na ceny jednorazowe; paddleWebhook ustawia
    // noAdsUntil na odpowiednią liczbę miesięcy. Status konta to wciąż PRO / Free.
    if (showNoAds) {
        val dialogLoading by viewModel.topUpLoading.collectAsState(initial = false)
        val dialogError by viewModel.topUpError.collectAsState(initial = null)
        AlertDialog(
            onDismissRequest = { showNoAds = false; viewModel.clearTopUpError() },
            confirmButton = {},
            dismissButton = {
                Button(
                    onClick = { showNoAds = false; viewModel.clearTopUpError() },
                    colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.bg)
                ) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.noads_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(stringResource(R.string.noads_subtitle), fontSize = 13.sp, color = VerbigemTheme.colors.muted)
                    Spacer(modifier = Modifier.height(10.dp))
                    listOf(
                        "noAds1" to R.string.noads_1,
                        "noAds3" to R.string.noads_3,
                        "noAds5" to R.string.noads_5,
                        "noAds10" to R.string.noads_10,
                    ).forEach { (type, labelRes) ->
                        Button(
                            onClick = { showNoAds = false; viewModel.topUp(type) },
                            enabled = !dialogLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = VerbigemTheme.colors.bg),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Text(stringResource(labelRes), color = VerbigemTheme.colors.ink, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    dialogError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = VerbigemTheme.colors.danger, fontSize = 12.sp)
                    }
                }
            }
        )
    }
}

/**
 * Pojedynczy wiersz wyboru modelu online (kuratorski lub darmowy).
 * Zaznaczony = wypełniony kolorem akcentu; nieaktywny = wyszarzony.
 */
@Composable
private fun OnlineModelRow(
    label: String,
    desc: String,
    isSelected: Boolean,
    isRecommended: Boolean,
    enabled: Boolean,
    disabledNote: String? = null,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) VerbigemTheme.colors.accent else Color.Transparent
    val textColor = if (isSelected) {
        Color.White
    } else if (enabled) {
        VerbigemTheme.colors.ink
    } else {
        VerbigemTheme.colors.muted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                if (isRecommended) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "• ${stringResource(R.string.online_recommended)}",
                        fontSize = 11.sp,
                        color = if (isSelected) Color.White else VerbigemTheme.colors.accent
                    )
                }
            }
            if (desc.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, fontSize = 12.sp, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (disabledNote != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(disabledNote, fontSize = 11.sp, color = if (isSelected) Color.White else VerbigemTheme.colors.danger)
            }
        }
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
}
