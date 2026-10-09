package fr.cybai.p8

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread

/**
 * Le lanceur du site, dans un WebView, utilisable hors ligne.
 *
 * Le site est servi depuis une origine locale fixe (appassets.androidplatform.net,
 * réservée à cet usage) : l'IndexedDB où la page range le pico8.dat survit donc
 * d'un lancement à l'autre, avec ou sans réseau. Voir SiteStore.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var store: SiteStore
    private var pendingFile: ValueCallback<Array<Uri>>? = null

    // Le bouton « déposer pico8.dat » de la page ouvre le sélecteur de fichiers.
    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingFile?.onReceiveValue(uri?.let { arrayOf(it) })
        pendingFile = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SiteStore(applicationContext)

        web = WebView(this).apply { setBackgroundColor(Color.parseColor("#0d0d1a")) }
        setContentView(web)
        hideSystemBars()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = true
            // La page s'en sert pour masquer ce qui n'a pas de sens dans l'app.
            userAgentString = "$userAgentString P8App/${versionName()}"
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.host != APP_HOST) return null
                val page = store.open(request.url)
                    ?: return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), "".byteInputStream())
                return WebResourceResponse(page.mime, "utf-8", page.stream)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (!request.isForMainFrame || url.host == APP_HOST || url.scheme !in setOf("http", "https")) return false
                startActivity(Intent(Intent.ACTION_VIEW, url))
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams,
            ): Boolean {
                pendingFile?.onReceiveValue(null)
                pendingFile = callback
                // Android ne connaît pas de type MIME pour .dat : on montre tout.
                pickFile.launch("*/*")
                return true
            }
        }

        // Retour : quitte le jeu en cours avant de quitter l'app.
        onBackPressedDispatcher.addCallback(this) {
            web.evaluateJavascript(CLOSE_GAME_JS) { closed ->
                if (closed != "true") {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }

        web.loadUrl("https://$APP_HOST/index.html")
        checkForUpdate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Coupe le son et le jeu quand l'app passe en arrière-plan.
    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Compare la version installée à releases/version.json sur le site, et
     * propose une seule fois chaque nouvelle version. Silencieux hors ligne.
     */
    private fun checkForUpdate() = thread(isDaemon = true) {
        val latest = try {
            JSONObject(URL("${SiteStore.SITE_URL}/releases/version.json?t=${System.currentTimeMillis()}").readText())
        } catch (e: Exception) {
            return@thread
        }
        val code = latest.optLong("versionCode")
        val prefs = getSharedPreferences("update", MODE_PRIVATE)
        if (code <= versionCode() || code == prefs.getLong("ignored", 0)) return@thread
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Mise à jour disponible")
                .setMessage("La version ${latest.optString("versionName")} de l'app est disponible.")
                .setPositiveButton("Télécharger") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${SiteStore.SITE_URL}/releases/Pico8_Online.apk")))
                }
                .setNegativeButton("Plus tard") { _, _ -> prefs.edit().putLong("ignored", code).apply() }
                .show()
        }
    }

    private fun packageInfo() = packageManager.getPackageInfo(packageName, 0)
    private fun versionCode() = PackageInfoCompat.getLongVersionCode(packageInfo())
    private fun versionName() = packageInfo().versionName

    companion object {
        private const val APP_HOST = "appassets.androidplatform.net"
        private const val CLOSE_GAME_JS =
            "(function(){var g=document.getElementById('game');" +
                "if(g&&g.style.display==='block'){exitGame();return true}return false})()"
    }
}
