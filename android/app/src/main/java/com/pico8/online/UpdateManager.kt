package com.pico8.online

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import com.squareup.okhttp3.OkHttpClient
import com.squareup.okhttp3.Request
import com.squareup.okhttp3.Response
import org.json.JSONObject
import java.io.File

class UpdateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateManager"
        
        // URL pour vérifier les mises à jour (à adapter selon ton infrastructure)
        // Format attendu: {"version": "1.1", "versionCode": 2, "apkUrl": "https://.../app-release.apk", "changelog": "..."}
        const val UPDATE_CHECK_URL = "https://cyba.github.io/Pico8_Online/update.json"
        
        // Alternative pour développement local
        const val LOCAL_UPDATE_CHECK_URL = "http://10.0.2.2:5173/update.json"
        
        // URL par défaut pour le téléchargement de l'APK
        const val DEFAULT_APK_URL = "https://github.com/cyba/Pico8_Android/releases/latest/download/app-release.apk"
        
        // Clé pour stocker la dernière version vérifiée
        private const val PREFS_LAST_CHECK = "last_update_check"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000 // 6 heures
        
        // Actions pour le BroadcastReceiver
        const val ACTION_UPDATE_DOWNLOADED = "com.pico8.online.ACTION_UPDATE_DOWNLOADED"
        const val ACTION_UPDATE_PROGRESS = "com.pico8.online.ACTION_UPDATE_PROGRESS"
    }

    private val okHttpClient = OkHttpClient()
    private val preferencesManager = PreferencesManager(context)
    private var downloadManager: DownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadReceiver: DownloadCompleteReceiver? = null

    data class UpdateInfo(
        val version: String,
        val versionCode: Int,
        val apkUrl: String,
        val changelog: String? = null,
        val isMandatory: Boolean = false
    )

    /**
     * Initialise le receiver pour le téléchargement
     */
    fun initDownloadReceiver(context: Context) {
        if (downloadReceiver == null) {
            downloadReceiver = DownloadCompleteReceiver(context, this)
            context.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    /**
     * Désenregistre le receiver
     */
    fun cleanup() {
        downloadReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering download receiver", e)
            }
        }
        downloadReceiver = null
    }

    /**
     * Vérifie s'il y a une mise à jour disponible
     */
    suspend fun checkForUpdate(force: Boolean = false): UpdateInfo? {
        // Vérifier si on a déjà vérifié récemment
        if (!force) {
            val lastCheck = preferencesManager.getLong(PREFS_LAST_CHECK, 0L)
            val now = System.currentTimeMillis()
            if (now - lastCheck < CHECK_INTERVAL_MS) {
                Log.d(TAG, "Update check skipped (recently checked)")
                return null
            }
        }

        try {
            val updateInfo = fetchUpdateInfo()
            val currentVersionCode = getCurrentVersionCode()
            
            // Mettre à jour le timestamp de la dernière vérification
            preferencesManager.putLong(PREFS_LAST_CHECK, System.currentTimeMillis())
            
            if (updateInfo != null && updateInfo.versionCode > currentVersionCode) {
                Log.d(TAG, "Update available: ${updateInfo.version} (code: ${updateInfo.versionCode})")
                return updateInfo
            } else {
                Log.d(TAG, "No update available (current: $currentVersionCode)")
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update", e)
            return null
        }
    }

    /**
     * Récupère les infos de mise à jour depuis le serveur
     */
    private suspend fun fetchUpdateInfo(): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(getUpdateCheckUrl())
                .get()
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP error: ${response.code}")
                return null
            }
            
            val jsonString = response.body?.string() ?: return null
            val json = JSONObject(jsonString)
            
            UpdateInfo(
                version = json.getString("version"),
                versionCode = json.getInt("versionCode"),
                apkUrl = json.getString("apkUrl"),
                changelog = json.optString("changelog", null),
                isMandatory = json.optBoolean("isMandatory", false)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing update info", e)
            null
        }
    }

    /**
     * Récupère le versionCode de l'application actuelle
     */
    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting version code", e)
            1 // Version par défaut
        }
    }

    /**
     * Récupère le nom de la version actuelle
     */
    fun getCurrentVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting version name", e)
            "1.0"
        }
    }

    /**
     * Télécharge et installe la mise à jour
     */
    fun downloadAndInstallUpdate(updateInfo: UpdateInfo): Long {
        try {
            val apkUrl = updateInfo.apkUrl.ifEmpty { DEFAULT_APK_URL }
            
            // Créer une requête de téléchargement
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Pico8 Online v${updateInfo.version}")
                .setDescription("Téléchargement de la mise à jour")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "Pico8_Online_v${updateInfo.version}.apk"
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            
            // Stocker l'URL de l'APK pour plus tard
            preferencesManager.putString("current_apk_url", apkUrl)
            preferencesManager.putString("current_apk_version", updateInfo.version)
            
            val downloadId = downloadManager.enqueue(request)
            
            Log.d(TAG, "Download started: $downloadId")
            
            // Stocker l'ID du téléchargement
            preferencesManager.putLong("current_download_id", downloadId)
            
            return downloadId
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading update", e)
            return -1
        }
    }

    /**
     * Installe l'APK téléchargé
     */
    fun installDownloadedApk() {
        try {
            val version = preferencesManager.getString("current_apk_version", "")
            val filename = "Pico8_Online_v$version.apk"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val apkFile = File(downloadsDir, filename)
            
            if (apkFile.exists()) {
                installApk(apkFile)
            } else {
                Log.e(TAG, "APK file not found: ${apkFile.absolutePath}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error installing downloaded APK", e)
        }
    }

    /**
     * Installe un APK spécifique
     */
    fun installApk(apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
            } else {
                intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
            }
            
            // Vérifier si on a la permission d'installer des APK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val canInstall = context.packageManager.canRequestPackageInstalls()
                if (!canInstall) {
                    // Demander la permission
                    val installIntent = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    )
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(installIntent)
                    return
                }
            }
            
            context.startActivity(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
        }
    }

    /**
     * Récupère l'URL de vérification des mises à jour
     */
    private fun getUpdateCheckUrl(): String {
        // Pour le développement, on peut utiliser une URL locale
        // Pour la production, utiliser l'URL de ton serveur
        return when {
            BuildConfig.DEBUG -> LOCAL_UPDATE_CHECK_URL
            else -> UPDATE_CHECK_URL
        }
    }

    /**
     * Vérifie si une mise à jour est disponible de manière synchrone (pour UI thread)
     * Note: Cette méthode bloque le thread courant
     */
    fun checkForUpdateBlocking(): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(getUpdateCheckUrl())
                .get()
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return null
            }
            
            val jsonString = response.body?.string() ?: return null
            val json = JSONObject(jsonString)
            
            val updateInfo = UpdateInfo(
                version = json.getString("version"),
                versionCode = json.getInt("versionCode"),
                apkUrl = json.getString("apkUrl"),
                changelog = json.optString("changelog", null),
                isMandatory = json.optBoolean("isMandatory", false)
            )
            
            val currentVersionCode = getCurrentVersionCode()
            
            if (updateInfo.versionCode > currentVersionCode) {
                updateInfo
            } else {
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update (blocking)", e)
            null
        }
    }

    /**
     * Receiver pour détecter la fin du téléchargement
     */
    class DownloadCompleteReceiver(
        private val context: Context,
        private val updateManager: UpdateManager
    ) : android.content.BroadcastReceiver() {
        
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val storedId = updateManager.preferencesManager.getLong("current_download_id", -1L)
            
            if (id == storedId && id != -1L) {
                Log.d(TAG, "Download completed: $id")
                
                // Notifier que le téléchargement est terminé
                val broadcastIntent = Intent(ACTION_UPDATE_DOWNLOADED)
                context.sendBroadcast(broadcastIntent)
                
                // Installer automatiquement l'APK
                Handler(Looper.getMainLooper()).post {
                    updateManager.installDownloadedApk()
                }
            }
        }
    }
}

// Extensions pour PreferencesManager
fun PreferencesManager.putLong(key: String, value: Long) {
    edit().putLong(key, value).apply()
}

fun PreferencesManager.putString(key: String, value: String) {
    edit().putString(key, value).apply()
}

fun PreferencesManager.getString(key: String, defaultValue: String = ""): String {
    return getString(key, defaultValue) ?: defaultValue
}
