package com.verbigem.app.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/**
 * Self-update controller.
 *
 * Firestore holds the canonical release metadata at `app_config/update`:
 *   {
 *     "versionCode": 12,
 *     "versionName": "1.0.11",
 *     "apkUrl": "https://github.com/.../releases/download/v1.0.11/app-release.apk",
 *     "playStoreUrl": "https://play.google.com/store/apps/details?id=com.verbigem.app",
 *     "minSupportedCode": 1
 *   }
 *
 * Behaviour (per product spec):
 *  - If the app is published on Google Play, we only show a dialog that opens the store.
 *  - Otherwise (current state) we download the APK and install it.
 * The user must consent before anything is downloaded/installed.
 */
class UpdateManager(private val context: Context) {

    data class UpdateInfo(
        val versionCode: Long = 0,
        val versionName: String = "",
        val apkUrl: String = "",
        val playStoreUrl: String = "",
        val onPlayStore: Boolean = false
    ) {
        fun isNewerThan(currentCode: Long): Boolean = versionCode > currentCode
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Release metadata source. Primary: a public JSON file in the GitHub repo (no auth/token needed,
     * always reachable). Fallback: Firestore `app_config/update` (in case the repo file is missing).
     */
    private val updateJsonUrl =
        "https://mini.verbigem.com/updates/version.json"

    /** Reads the release metadata. Tries the public Hosting JSON first, then Firestore. */
    suspend fun fetchUpdateInfo(): UpdateInfo? {
        val fromHosting = fetchFromHosting()
        if (fromHosting != null) return fromHosting
        return fetchFromFirestore()
    }

    private val okHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            // Some ROMs (e.g. Xiaomi MIUI) fail to resolve hosts via the platform
            // resolver used by InetAddress / com.android.okhttp when Private DNS (DoT) is on.
            // We resolve through DNS-over-HTTPS (Cloudflare 1.1.1.1) instead, which works
            // on any network that has plain HTTPS access.
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    try {
                        val url = java.net.URL("https://1.1.1.1/dns-query?name=$hostname&type=A")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.setRequestProperty("Accept", "application/dns-json")
                        conn.connectTimeout = 10_000
                        conn.readTimeout = 10_000
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        conn.disconnect()
                        val addrs = org.json.JSONArray(
                            org.json.JSONObject(resp).optJSONArray("Answer")?.toString() ?: "[]"
                        )
                        val list = mutableListOf<java.net.InetAddress>()
                        for (i in 0 until addrs.length()) {
                            val ip = addrs.getJSONObject(i).optString("data")
                            if (ip.isNotBlank()) list.add(java.net.InetAddress.getByName(ip))
                        }
                        if (list.isNotEmpty()) return list
                    } catch (_: Exception) {
                        // fall through to system resolver
                    }
                    return java.net.InetAddress.getAllByName(hostname).toList()
                }
            })
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private suspend fun fetchFromHosting(): UpdateInfo? {
        return try {
            val request = okhttp3.Request.Builder().url(updateJsonUrl).build()
            val resp = withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
            if (!resp.isSuccessful) {
                Log.w(TAG, "Hosting update JSON unavailable (HTTP ${resp.code})")
                return null
            }
            val json = resp.body?.string() ?: return null
            parseUpdateInfo(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read update info from Hosting: url=$updateJsonUrl msg=${e.localizedMessage}", e)
            null
        }
    }

    private fun parseUpdateInfo(json: String): UpdateInfo? {
        // Minimal JSON parse without extra deps (org.json is on the Android classpath).
        val obj = org.json.JSONObject(json)
        return UpdateInfo(
            versionCode = obj.optLong("versionCode", 0L),
            versionName = obj.optString("versionName", ""),
            apkUrl = obj.optString("apkUrl", ""),
            playStoreUrl = obj.optString("playStoreUrl", ""),
            onPlayStore = obj.optBoolean("onPlayStore", false)
        )
    }

    private suspend fun fetchFromFirestore(): UpdateInfo? {
        return try {
            val snap = firestore.collection("app_config").document("update").get().await()
            if (!snap.exists()) return null
            UpdateInfo(
                versionCode = (snap.getLong("versionCode") ?: 0L),
                versionName = snap.getString("versionName") ?: "",
                apkUrl = snap.getString("apkUrl") ?: "",
                playStoreUrl = snap.getString("playStoreUrl") ?: "",
                onPlayStore = snap.getBoolean("onPlayStore") ?: false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read update info from Firestore", e)
            null
        }
    }

    fun currentVersionCode(): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            0L
        }
    }

    /**
     * Quick connectivity probe (used to gate the startup update check). Resolves true only if
     * the update JSON host is actually reachable within [timeoutMs]. Uses the same DoH-capable
     * client as the rest of the updater so it works on ROMs with broken system DNS.
     */
    suspend fun hasInternet(timeoutMs: Long = 5000): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(updateJsonUrl)
                .head()
                .build()
            val resp = withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(timeoutMs) {
                    okHttpClient.newCall(request).execute()
                }
            }
            resp.use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Downloads the APK via OkHttp (GitHub Releases requires an explicit
     * `Accept: application/octet-stream` header, otherwise it returns an HTML page
     * instead of the binary) and installs it.
     *
     * Progress is reported through [progressState] (a [MutableStateFlow]) so the UI can read it
     * with `collectAsState()` — the same pattern as the model downloader, which works reliably
     * under Compose. Values: `null` = not started, `-1f` = indeterminate (server sent no
     * Content-Length), `0f..1f` = definite fraction downloaded. [onComplete] fires once the
     * install intent has been launched.
     */
    fun downloadAndInstall(
        info: UpdateInfo,
        progressState: MutableStateFlow<Float?> = MutableStateFlow(null),
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (info.apkUrl.isBlank()) {
            onError("Missing APK URL")
            return
        }
        Log.i(TAG, "Starting APK download from ${info.apkUrl}")

        val fileName = "verbigem-update-${info.versionName}.apk"
        val downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir
        val targetFile = File(downloadDir, fileName)
        try { targetFile.delete() } catch (_: Exception) { }

        // Network + file IO must NOT run on the main thread (StrictMode blocks it).
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = okhttp3.Request.Builder()
                    .url(info.apkUrl)
                    .header("Accept", "application/octet-stream")
                    .build()
                okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val msg = "Download failed: HTTP ${resp.code} ${resp.message}"
                        Log.e(TAG, msg)
                        withContext(Dispatchers.Main) { onError(msg) }
                        return@launch
                    }
                    val body = resp.body
                    if (body == null) {
                        withContext(Dispatchers.Main) { onError("Empty response body") }
                        return@launch
                    }
                    val total = body.contentLength()
                    Log.i(TAG, "Download started, content-length=$total")
                    // tryEmit works from any thread and is picked up by collectAsState() in the UI.
                    progressState.value = if (total > 0) 0f else -1f
                    targetFile.outputStream().use { out ->
                        body.byteStream().use { `in` ->
                            val buf = ByteArray(32 * 1024)
                            var read: Int
                            var downloaded = 0L
                            var lastEmit = 0L
                            while (`in`.read(buf).also { read = it } != -1) {
                                out.write(buf, 0, read)
                                downloaded += read
                                // Emit at most a few times per MB to avoid flooding the snapshot system.
                                if (downloaded - lastEmit >= 256_000 || total <= 0) {
                                    lastEmit = downloaded
                                    progressState.value = if (total > 0) {
                                        (downloaded.toFloat() / total).coerceIn(0f, 1f)
                                    } else {
                                        -1f // indeterminate: server gave no Content-Length
                                    }
                                }
                            }
                            Log.i(TAG, "Download finished, bytes=$downloaded")
                            progressState.value = if (total > 0) 1f else -1f
                        }
                    }
                    if (targetFile.length() < 1_000_000) {
                        val msg = "Downloaded file too small (${targetFile.length()} B) — likely HTML, not APK"
                        Log.e(TAG, msg)
                        progressState.value = null
                        withContext(Dispatchers.Main) { onError(msg) }
                        return@launch
                    }
                    withContext(Dispatchers.Main) { launchInstall(targetFile, onComplete, onError) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                progressState.value = null
                withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Download error") }
            }
        }
    }

    private fun launchInstall(file: File, onComplete: () -> Unit, onError: (String) -> Unit) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            onComplete()
        } catch (e: Exception) {
            Log.e(TAG, "Install launch failed", e)
            onError(e.localizedMessage ?: "Install failed")
        }
    }

    /** Opens the Play Store listing (used once the app is published). */
    fun openPlayStore(info: UpdateInfo) {
        val url = info.playStoreUrl.ifBlank {
            "https://play.google.com/store/apps/details?id=${context.packageName}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        private const val TAG = "UpdateManager"
    }
}
