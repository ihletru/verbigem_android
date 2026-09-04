package com.verbigem.app.ui.screens.profile

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.data.AppLinks
import com.verbigem.app.data.openUrl
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.ui.components.LangSelect
import com.verbigem.app.ui.components.UiLangSelect
import com.verbigem.app.ui.theme.VerbigemTheme

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLogout: () -> Unit,
    onOpenPhoneVerification: () -> Unit,
    onOpenMyQr: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsState()
    val phoneVerified by viewModel.phoneVerified.collectAsState()
    val nicknameInput by viewModel.nicknameInput.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState(initial = "calm")
    val currentMode by viewModel.currentMode.collectAsState(initial = "day")
    val currentUiLang by viewModel.currentUiLang.collectAsState(initial = "pl")
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VerbigemTheme.colors.bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.profile_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = VerbigemTheme.colors.ink
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
                            onLangSelected = { viewModel.setSpeakLangs(it, LangCode.fromCode(profile?.speakLangTarget ?: "en")) }
                        )
                    }
                    Text(" ⇄ ", color = VerbigemTheme.colors.muted, modifier = Modifier.padding(horizontal = 8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        LangSelect(
                            selectedLang = LangCode.fromCode(profile?.speakLangTarget ?: "en"),
                            onLangSelected = { viewModel.setSpeakLangs(LangCode.fromCode(profile?.speakLangSource ?: "pl"), it) }
                        )
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
                if (profile?.walletCreditsCents ?: 0 > 0) {
                    Text(
                        text = stringResource(R.string.api_wallet, (profile?.walletCreditsCents ?: 0) / 100f),
                        fontSize = 13.sp,
                        color = VerbigemTheme.colors.success,
                        modifier = Modifier.padding(top = 4.dp)
                    )
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

        // Mój kod QR (Faza 4.1) — udostępniasz go znajomym, by Cię znaleźli.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VerbigemTheme.colors.surface)
                    .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(20.dp))
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
            }
        }

        // Wylogowanie
        item {
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
