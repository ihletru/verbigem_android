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

    /** Reads the release metadata from Firestore. Returns null if unavailable. */
    suspend fun fetchUpdateInfo(): UpdateInfo? {
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
            Log.e(TAG, "Failed to read update info", e)
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
     * Downloads the APK via OkHttp (GitHub Releases requires an explicit
     * `Accept: application/octet-stream` header, otherwise it returns an HTML page
     * instead of the binary) and installs it. [onComplete] fires once the install
     * intent has been launched.
     */
    fun downloadAndInstall(
        info: UpdateInfo,
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

        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url(info.apkUrl)
            .header("Accept", "application/octet-stream")
            .build()

        // Network + file IO must NOT run on the main thread (StrictMode blocks it).
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).execute().use { resp ->
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
                    val len = body.contentLength()
                    Log.i(TAG, "Download started, content-length=$len")
                    targetFile.outputStream().use { out ->
                        body.byteStream().use { `in` ->
                            val buf = ByteArray(32 * 1024)
                            var read: Int
                            var total = 0L
                            while (`in`.read(buf).also { read = it } != -1) {
                                out.write(buf, 0, read)
                                total += read
                            }
                            Log.i(TAG, "Download finished, bytes=$total")
                        }
                    }
                    if (targetFile.length() < 1_000_000) {
                        val msg = "Downloaded file too small (${targetFile.length()} B) — likely HTML, not APK"
                        Log.e(TAG, msg)
                        withContext(Dispatchers.Main) { onError(msg) }
                        return@launch
                    }
                    withContext(Dispatchers.Main) { launchInstall(targetFile, onComplete, onError) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
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
