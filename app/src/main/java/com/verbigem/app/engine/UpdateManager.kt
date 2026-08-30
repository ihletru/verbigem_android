package com.verbigem.app.engine

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.File

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
     * Downloads the APK (via DownloadManager) and installs it. The [onComplete] callback
     * fires once the download finishes and the install intent has been launched.
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

        val fileName = "verbigem-update-${info.versionName}.apk"
        val downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir
        val targetFile = File(downloadDir, fileName)

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Verbigem update ${info.versionName}")
            .setDescription("Downloading update…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(targetFile))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (id != downloadId) return
                context.unregisterReceiver(this)
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        launchInstall(targetFile, onComplete, onError)
                    } else {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        onError("Download failed (reason $reason)")
                    }
                    cursor.close()
                } else {
                    onError("Download failed")
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
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
