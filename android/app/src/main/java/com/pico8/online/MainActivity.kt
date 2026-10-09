package com.pico8.online

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val SYNC_DELAY_MS = 5000L // 5 secondes
        private const val REQUEST_CODE_INSTALL_PACKAGES = 1001
    }

    private lateinit var webView: WebView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var offlineBanner: View
    private lateinit var offlineText: TextView
    private lateinit var retryButton: Button
    private lateinit var updateButton: MaterialButton
    private lateinit var updateBadge: FrameLayout
    
    private lateinit var cacheManager: CacheManager
    private lateinit var updateManager: UpdateManager
    private lateinit var customWebViewClient: CustomWebViewClient
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    private var connectivityReceiver: BroadcastReceiver? = null
    private var downloadCompleteReceiver: BroadcastReceiver? = null
    private var isOnline = true
    private var syncHandler: Handler? = null
    private var syncRunnable: Runnable? = null
    private var hasUpdate = false
    private var currentUpdateInfo: UpdateManager.UpdateInfo? = null

    // Pour demander la permission d'installer des packages
    private val requestInstallPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Permission accordée, essayer d'installer à nouveau
            currentUpdateInfo?.let { updateInfo ->
                updateManager.downloadAndInstallUpdate(updateInfo)
            }
        } else {
            Toast.makeText(
                this,
                "Permission refusée. Impossible d'installer les mises à jour.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialiser les vues
        webView = findViewById(R.id.webView)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        offlineBanner = findViewById(R.id.offlineBanner)
        offlineText = findViewById(R.id.offlineText)
        retryButton = findViewById(R.id.retryButton)
        updateButton = findViewById(R.id.updateButton)
        updateBadge = findViewById(R.id.updateBadge)
        
        // Initialiser les gestionnaires
        cacheManager = CacheManager(applicationContext)
        updateManager = UpdateManager(applicationContext)
        
        // Initialiser le receiver de téléchargement
        updateManager.initDownloadReceiver(applicationContext)
        
        // Configurer le WebView
        setupWebView()
        
        // Configurer les listeners
        setupListeners()
        
        // Configurer le receiver pour les changements de connectivité
        setupConnectivityReceiver()
        
        // Configurer le receiver pour la fin du téléchargement
        setupDownloadCompleteReceiver()
        
        // Vérifier la connectivité initiale
        updateConnectivityStatus()
        
        // Vérifier les mises à jour
        checkForUpdates()
        
        Log.d(TAG, "Activity created")
    }

    private fun setupWebView() {
        customWebViewClient = CustomWebViewClient(this, cacheManager)
        
        val webSettings: WebSettings = webView.settings
        webSettings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            javaScriptCanOpenWindowsAutomatically = true
            
            // Optimisation pour mobile
            loadWithOverviewMode = true
            useWideViewPort = true
            
            // Cache
            cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
            setAppCacheEnabled(true)
            setAppCachePath(cacheDir.path)
        }
        
        webView.webViewClient = customWebViewClient
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                loadingIndicator.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }
        
        // Permettre le zoom
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
    }

    private fun setupListeners() {
        retryButton.setOnClickListener {
            syncNow()
        }
        
        updateButton.setOnClickListener {
            handleUpdateClick()
        }
    }

    private fun handleUpdateClick() {
        currentUpdateInfo?.let { updateInfo ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Vérifier la permission d'installer des packages
                if (!packageManager.canRequestPackageInstalls()) {
                    // Demander la permission
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                    requestInstallPermissionLauncher.launch(intent)
                    return
                }
            }
            showUpdateDialog(updateInfo)
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateManager.UpdateInfo) {
        val message = buildString {
            append("Nouvelle version disponible: v${updateInfo.version}")
            append("\n\n")
            if (!updateInfo.changelog.isNullOrEmpty()) {
                append("Nouveautés:\n")
                append(updateInfo.changelog)
                append("\n\n")
            }
            if (updateInfo.isMandatory) {
                append("⚠️ Cette mise à jour est obligatoire pour continuer à utiliser l'application.")
            } else {
                append("Voulez-vous télécharger et installer cette mise à jour ?")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Mise à jour disponible")
            .setMessage(message)
            .setPositiveButton("Télécharger") { dialog, _ ->
                dialog.dismiss()
                val downloadId = updateManager.downloadAndInstallUpdate(updateInfo)
                if (downloadId != -1L) {
                    Toast.makeText(this, "Téléchargement de la mise à jour...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Erreur de téléchargement", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(if (updateInfo.isMandatory) "Quitter" else "Plus tard") { dialog, _ ->
                dialog.dismiss()
                if (updateInfo.isMandatory) {
                    finish()
                }
            }
            .setCancelable(!updateInfo.isMandatory)
            .show()
    }

    private fun checkForUpdates() {
        coroutineScope.launch {
            try {
                // Vérification bloquante pour ne pas bloquer l'UI
                val updateInfo = withContext(Dispatchers.IO) {
                    updateManager.checkForUpdateBlocking()
                }
                
                hasUpdate = updateInfo != null
                currentUpdateInfo = updateInfo
                
                // Mettre à jour l'UI sur le thread principal
                withContext(Dispatchers.Main) {
                    updateButton.visibility = if (hasUpdate) View.VISIBLE else View.GONE
                    updateBadge.visibility = if (hasUpdate) View.VISIBLE else View.GONE
                    
                    if (hasUpdate) {
                        Log.d(TAG, "Update available: ${updateInfo?.version}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
            }
        }
    }

    private fun setupConnectivityReceiver() {
        connectivityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                updateConnectivityStatus()
            }
        }
        
        registerReceiver(
            connectivityReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        )
    }

    private fun setupDownloadCompleteReceiver() {
        downloadCompleteReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == UpdateManager.ACTION_UPDATE_DOWNLOADED) {
                    // Le téléchargement est terminé
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            "Téléchargement terminé ! Installation en cours...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        
        registerReceiver(
            downloadCompleteReceiver,
            IntentFilter(UpdateManager.ACTION_UPDATE_DOWNLOADED)
        )
    }

    private fun updateConnectivityStatus() {
        val hasConnection = cacheManager.isNetworkAvailable()
        
        if (hasConnection != isOnline) {
            isOnline = hasConnection
            Log.d(TAG, "Connectivity changed: isOnline = $isOnline")
            
            if (isOnline) {
                // Si on reprend la connexion, synchroniser automatiquement
                scheduleSync()
                // Re-vérifier les mises à jour
                checkForUpdates()
            }
            
            updateUIForConnectivity()
        }
    }

    private fun updateUIForConnectivity() {
        coroutineScope.launch {
            if (isOnline) {
                offlineBanner.visibility = View.GONE
                offlineText.text = getString(R.string.no_connection)
                retryButton.text = getString(R.string.sync_in_progress)
                
                // Charger la page web
                loadWebContent()
                
            } else {
                offlineBanner.visibility = View.VISIBLE
                offlineText.text = getString(R.string.offline_mode)
                retryButton.text = getString(R.string.sync_in_progress)
            }
        }
    }

    private fun scheduleSync() {
        // Annuler le sync précédent
        syncHandler?.removeCallbacks(syncRunnable)
        syncHandler = Handler(Looper.getMainLooper())
        
        syncRunnable = Runnable {
            syncNow()
        }
        
        syncHandler?.postDelayed(syncRunnable, SYNC_DELAY_MS)
    }

    private fun syncNow() {
        coroutineScope.launch {
            try {
                retryButton.text = getString(R.string.sync_in_progress)
                retryButton.isEnabled = false
                
                // Lancer le service de synchronisation
                val syncIntent = Intent(this@MainActivity, SyncService::class.java)
                syncIntent.action = SyncService.ACTION_SYNC_NOW
                startService(syncIntent)
                
                // Attendre un peu puis recharger
                Handler(Looper.getMainLooper()).postDelayed({
                    loadWebContent()
                    retryButton.text = getString(R.string.sync_complete)
                    retryButton.isEnabled = true
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        retryButton.text = getString(R.string.sync_in_progress)
                    }, 2000)
                }, 3000)
                
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                Toast.makeText(
                    this@MainActivity,
                    "Sync failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                retryButton.isEnabled = true
            }
        }
    }

    private fun loadWebContent() {
        coroutineScope.launch {
            try {
                val baseUrl = cacheManager.getBaseUrl()
                
                // Vérifier si on a le fichier index.html en cache
                val cachedIndex = cacheManager.getFromCache("${CacheManager.CACHE_PREFIX_ASSETS}index.html")
                
                if (isOnline || cachedIndex != null) {
                    // Charger l'URL principale
                    webView.loadUrl(baseUrl)
                    Log.d(TAG, "Loading URL: $baseUrl")
                } else {
                    // Pas de connexion et pas de cache
                    showNoContentDialog()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading web content", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showNoContentDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage("No content available offline. Please connect to the Internet to download the content.")
            .setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                loadWebContent()
            }
            .setNegativeButton("Exit") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Gérer le bouton retour pour le WebView
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityReceiver?.let { unregisterReceiver(it) }
        downloadCompleteReceiver?.let { unregisterReceiver(it) }
        syncHandler?.removeCallbacks(syncRunnable)
        syncHandler = null
        
        // Nettoyer le UpdateManager
        updateManager.cleanup()
        
        // Libérer les ressources du WebView
        webView.destroy()
        
        Log.d(TAG, "Activity destroyed")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
