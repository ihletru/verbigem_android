package com.verbigem.app

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                        // The update gate runs BEFORE the rest of the app. A broken/old
                        // install can therefore always self-heal by downloading a fixed APK,
                        // even if the rest of the app (DB, UI) would otherwise crash on launch.
                        StartupGate(updateManager = updateManager) {
                            AppNavigation()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Startup gate: checks for an update *before* the main UI is shown.
 *
 * Flow:
 *  1. Probe connectivity (≤5s). If offline, skip straight to the app (no endless spinner).
 *  2. Fetch update metadata. If a newer version exists, show the update dialog (blocks the
 *     app until the user decides). After install the process restarts into the new version.
 *  3. If no update / error / timeout, always proceed to [content] after a hard cap (~6s) so
 *     the app never gets stuck on a loading screen.
 *
 * The gate deliberately does NOT touch the Room database — that's the thing most likely to
 * crash on a bad migration, and we want the update check to succeed even then.
 */
@Composable
private fun StartupGate(
    updateManager: UpdateManager,
    content: @Composable () -> Unit
) {
    var phase by remember { mutableStateOf<GatePhase>(GatePhase.Checking) }
    var updateInfo by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val progressState = remember { MutableStateFlow<Float?>(null) }
    val progress by progressState.collectAsState()

    val proceed = { phase = GatePhase.Ready }

    LaunchedEffect(Unit) {
        var newer: UpdateManager.UpdateInfo? = null
        // Hard cap: never block the app longer than this even if the network hangs.
        kotlinx.coroutines.withTimeoutOrNull(6000) {
            val online = runCatching { updateManager.hasInternet(5000) }.getOrDefault(false)
            if (online) {
                val current = updateManager.currentVersionCode()
                val info = updateManager.fetchUpdateInfo()
                if (info != null && info.isNewerThan(current)) {
                    newer = info
                }
            }
        }
        if (newer != null) {
            updateInfo = newer
            phase = GatePhase.Prompt
        } else {
            proceed()
        }
    }

    when (phase) {
        GatePhase.Checking -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = VerbigemTheme.colors.accent)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.update_checking),
                        color = VerbigemTheme.colors.muted,
                        fontSize = 13.sp
                    )
                }
            }
        }
        GatePhase.Prompt -> {
            updateInfo?.let { info ->
                if (downloading) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text(stringResource(R.string.update_downloading_title)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.update_downloading_body))
                                Spacer(Modifier.height(12.dp))
                                val p = progress
                                if (p == null || p < 0f) {
                                    // Indeterminate: either not started yet, or the server
                                    // sent no Content-Length so we can't show a fraction.
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        progress = { p },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text("${(p * 100).toInt()}%")
                                }
                            }
                        },
                        confirmButton = { }
                    )
                } else {
                    AlertDialog(
                        onDismissRequest = { proceed() },
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
                                        downloading = true
                                        progressState.value = 0f
                                        updateManager.downloadAndInstall(
                                            info,
                                            progressState = progressState,
                                            onComplete = {
                                                // Install intent already fired; let the system
                                                // complete the flow — do NOT proceed() here,
                                                // otherwise the Activity re-creates in the old version.
                                                Log.i("StartupGate", "Download/Install complete; install intent launched.")
                                            },
                                            onError = { error ->
                                                Log.e("StartupGate", "Update failed: $error")
                                                // Stay on the error state — do NOT proceed() to avoid
                                                // a broken self-heal loop. Let the user retry.
                                            }
                                        )
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.update_action))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { proceed() }) {
                                Text(stringResource(R.string.update_later))
                            }
                        }
                    )
                }
            } ?: proceed()
        }
        GatePhase.Ready -> {
            content()
        }
    }
}

private enum class GatePhase { Checking, Prompt, Ready }

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
