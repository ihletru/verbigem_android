package com.verbigem.app

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verbigem.app.data.ProfileLinks
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.engine.UpdateManager
import com.verbigem.app.ui.navigation.AppNavigation
import com.verbigem.app.ui.theme.VerbigemAppTheme
import com.verbigem.app.ui.theme.VerbigemTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private val updateManager = UpdateManager(this)

    /**
     * Download progress lives in the Activity, NOT in a composable `remember`.
     *
     * Reason: the progress dialog is a separate Compose subcomposition (AlertDialog renders
     * into its own window). If the state is only ever read inside that subcomposition, the
     * parent never recomposes and the dialog keeps re-showing the value it was first composed
     * with — the "bar frozen at 0%" symptom. Holding the flow here and reading it in the
     * parent scope (see [StartupGate]) sidesteps the whole class of problem, and the state
     * also survives a configuration change mid-download.
     */
    private val downloadProgress = MutableStateFlow(UpdateManager.DownloadProgress())

    /**
     * The conversation a notification tap wants to open, or null.
     *
     * A flow rather than a plain field because the value can arrive twice: once on a
     * cold start (in `onCreate`'s intent) and again later via [onNewIntent] when the
     * activity is already alive. Only a flow gets the second one into Compose.
     */
    private val openChatUid = MutableStateFlow<String?>(null)

    /**
     * Uid profilu z App Linku (`https://mini.verbigem.com/u/<uid>`), lub null.
     * Flow, bo link może przyjść drugi raz przez [onNewIntent], gdy aktywność
     * już żyje — tylko flow wpycha to do Compose.
     */
    private val deepLinkUid = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferencesManager = PreferencesManager(this)
        handleChatIntent(intent)
        handleDeepLink(intent)

        setContent {
            val themeName by preferencesManager.themeFlow.collectAsState(initial = "calm")
            val modeName by preferencesManager.modeFlow.collectAsState(initial = "day")
            val uiLang by preferencesManager.uiLangFlow.collectAsState(initial = "pl")
            // Read here, in the parent scope, so a notification that lands while the
            // app is open still pushes the new value down into AppNavigation.
            val chatUid by openChatUid.collectAsState()
            val profileUid by deepLinkUid.collectAsState()

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
                        StartupGate(
                            updateManager = updateManager,
                            progressState = downloadProgress
                        ) {
                            AppNavigation(openChatUid = chatUid, openProfileUid = profileUid)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Required, otherwise getIntent() keeps handing back the original launch
        // intent and a second notification tap re-opens the first conversation.
        setIntent(intent)
        handleChatIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleChatIntent(intent: Intent?) {
        val uid = intent?.getStringExtra(EXTRA_OPEN_CHAT_UID)
        if (!uid.isNullOrBlank()) openChatUid.value = uid
    }

    /**
     * Wyciąga uid z App Linku do profilu. Ignorujemy inne akcje (np. LAUNCHER z
     * ikony) i linki, które nie są Verbigem `/u/<uid>` — [ProfileLinks.uidFromUrl]
     * sam odsiewa obce adresy.
     */
    private fun handleDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.data?.toString() ?: return
        val uid = ProfileLinks.uidFromUrl(data)
        if (!uid.isNullOrBlank()) deepLinkUid.value = uid
    }

    companion object {
        const val EXTRA_OPEN_CHAT_UID = "open_chat_uid"

        /**
         * Intent that brings the app to the front on a given conversation.
         *
         * `chatId` here is the *other person's uid* — that is what `Screen.ChatThread`
         * routes on, and the thread derives the real chat id from the two uids.
         * An empty string means "just open the inbox".
         */
        fun intentForChat(context: Context, chatId: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_CHAT_UID, chatId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (chatId.isBlank()) removeExtra(EXTRA_OPEN_CHAT_UID)
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
    progressState: MutableStateFlow<UpdateManager.DownloadProgress>,
    content: @Composable () -> Unit
) {
    var phase by remember { mutableStateOf<GatePhase>(GatePhase.Checking) }
    var updateInfo by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    // IMPORTANT: `progress` is collected here, in StartupGate's own scope — not inside the
    // AlertDialog's content lambda. Every emission therefore recomposes StartupGate and
    // pushes a fresh value down into the dialog, so the bar cannot freeze on the value it
    // was first composed with.
    val progress by progressState.collectAsState()

    val proceed = { phase = GatePhase.Ready }

    val startDownload = { info: UpdateManager.UpdateInfo ->
        failure = null
        downloading = true
        progressState.value = UpdateManager.DownloadProgress()
        updateManager.downloadAndInstall(
            info,
            progressState = progressState,
            onComplete = {
                // Install intent already fired; let the system complete the flow — do NOT
                // proceed() here, otherwise the Activity re-creates in the old version.
                Log.i("StartupGate", "Download/Install complete; install intent launched.")
            },
            onError = { error ->
                // Show it. Previously a failed download left the dialog sitting at 0%
                // with no explanation and no way out.
                Log.e("StartupGate", "Update failed: $error")
                failure = error
            }
        )
    }

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
                    UpdateDownloadDialog(
                        progress = progress,
                        failure = failure,
                        onRetry = { startDownload(info) },
                        onDismiss = { proceed() }
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
                                        startDownload(info)
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

/**
 * Progress dialog shown while the APK is being fetched.
 *
 * Deliberately a **separate composable taking [progress] as a parameter** (instead of an
 * inline `text = { ... }` lambda): the value is then read in the caller's scope, so the
 * caller recomposes on every tick and re-invokes this composable with fresh data.
 *
 * Shows three things rather than a bare percentage:
 *  - a determinate bar (or an indeterminate one when the server sent no Content-Length),
 *  - the percentage,
 *  - **megabytes downloaded / total** — this is what makes a stalled or slow download
 *    obvious instead of an apparently frozen "0%".
 */
@Composable
private fun UpdateDownloadDialog(
    progress: UpdateManager.DownloadProgress,
    failure: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val indeterminate = progress.fraction < 0f
    AlertDialog(
        onDismissRequest = { if (failure != null) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (failure == null) R.string.update_downloading_title else R.string.update_failed_title
                )
            )
        },
        text = {
            Column {
                if (failure != null) {
                    Text(
                        text = stringResource(R.string.update_failed, failure),
                        color = VerbigemTheme.colors.danger,
                        fontSize = 13.sp
                    )
                } else {
                    Text(stringResource(R.string.update_downloading_body))
                    Spacer(Modifier.height(12.dp))
                    if (indeterminate) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!indeterminate) {
                            Text(
                                text = stringResource(
                                    R.string.update_download_percent,
                                    (progress.fraction * 100).toInt()
                                ),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = VerbigemTheme.colors.ink
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = if (progress.totalBytes > 0) {
                                stringResource(
                                    R.string.update_download_bytes,
                                    progress.megabytesRead,
                                    progress.megabytesTotal
                                )
                            } else {
                                stringResource(
                                    R.string.update_download_bytes_unknown,
                                    progress.megabytesRead
                                )
                            },
                            fontSize = 12.sp,
                            color = VerbigemTheme.colors.muted
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (failure != null) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.update_retry)) }
            }
        },
        dismissButton = {
            if (failure != null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
            }
        }
    )
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
