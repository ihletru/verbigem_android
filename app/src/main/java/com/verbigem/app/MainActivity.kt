package com.verbigem.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.engine.UpdateManager
import com.verbigem.app.ui.navigation.AppNavigation
import com.verbigem.app.ui.theme.VerbigemAppTheme
import com.verbigem.app.ui.theme.VerbigemTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private val updateManager = UpdateManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferencesManager = PreferencesManager(this)

        setContent {
            val themeName by preferencesManager.themeFlow.collectAsState(initial = "calm")
            val modeName by preferencesManager.modeFlow.collectAsState(initial = "day")
            val uiLang by preferencesManager.uiLangFlow.collectAsState(initial = "pl")

            LocalizationWrapper(uiLang) {
                VerbigemAppTheme(
                    themeName = themeName,
                    modeName = modeName
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = VerbigemTheme.colors.bg
                    ) {
                        AppNavigation()
                        UpdatePromptHost(updateManager = updateManager)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatePromptHost(updateManager: UpdateManager) {
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        checking = true
        val info = updateManager.fetchUpdateInfo()
        val current = updateManager.currentVersionCode()
        if (info != null && info.isNewerThan(current)) {
            updateInfo = info
        }
        checking = false
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.update_available_body,
                        info.versionName.ifBlank { info.versionCode.toString() }
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (info.onPlayStore) {
                            updateManager.openPlayStore(info)
                        } else {
                            updateManager.downloadAndInstall(
                                info,
                                onComplete = { updateInfo = null },
                                onError = { /* leave dialog open so the user can retry */ }
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.update_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) {
                    Text(stringResource(R.string.update_later))
                }
            }
        )
    }
}

@Composable
fun LocalizationWrapper(langCode: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // LocalizationWrapper is only invoked inside MainActivity.setContent, where the
    // surrounding context is the ComponentActivity itself, so this cast is safe.
    val activity = context as ComponentActivity
    val locale = remember(langCode) { Locale.forLanguageTag(langCode) }
    val configuration = remember(langCode) {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config
    }
    val localizedContext = remember(langCode) {
        context.createConfigurationContext(configuration)
    }

    // Provide both the localized context AND the ActivityResultRegistryOwner.
    // The localized context is a ConfigurationContext wrapper (not the ComponentActivity),
    // so without re-providing LocalActivityResultRegistryOwner, compose navigation screens
    // that call rememberLauncherForActivityResult (e.g. OcrScreen) crash with
    // "No ActivityResultRegistryOwner was provided via LocalActivityResultRegistryOwner".
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalActivityResultRegistryOwner provides activity
    ) {
        content()
    }
}
