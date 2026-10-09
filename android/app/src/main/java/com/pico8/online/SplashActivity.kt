package com.pico8.online

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_DELAY_MS = 2000L // 2 secondes
    }

    private lateinit var statusText: TextView
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        statusText = findViewById(R.id.statusText)
        
        Log.d(TAG, "SplashActivity created")
        
        // Initialiser le cache et vérifier la disponibilité
        initializeApp()
    }

    private fun initializeApp() {
        coroutineScope.launch {
            try {
                // Mettre à jour le statut
                updateStatus("Initialisation...")
                
                // Initialiser le cache manager
                val cacheManager = CacheManager(applicationContext)
                
                // Vérifier si on a déjà du contenu en cache
                val hasCachedContent = checkForCachedContent(cacheManager)
                
                if (hasCachedContent) {
                    updateStatus("Contenu disponible hors ligne")
                } else {
                    updateStatus("Premier lancement...")
                }
                
                // Vérifier la connectivité
                val hasConnection = cacheManager.isNetworkAvailable()
                
                if (hasConnection) {
                    updateStatus("Connexion détectée")
                    
                    // Précharger les données essentielles
                    preloadEssentialData(cacheManager)
                } else {
                    updateStatus("Mode hors ligne")
                }
                
                // Attendre puis lancer l'activité principale
                Handler(Looper.getMainLooper()).postDelayed({
                    startMainActivity()
                }, SPLASH_DELAY_MS)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during initialization", e)
                // Continuer vers l'activité principale quand même
                startMainActivity()
            }
        }
    }

    private suspend fun checkForCachedContent(cacheManager: CacheManager): Boolean {
        return try {
            val cacheDao = CacheDatabase.getDatabase(applicationContext).cacheDao()
            val count = cacheDao.count()
            count > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking cached content", e)
            false
        }
    }

    private suspend fun preloadEssentialData(cacheManager: CacheManager) {
        try {
            // Précharger l'index.html
            val baseUrl = cacheManager.getBaseUrl()
            cacheManager.fetchAndCache(
                "$baseUrl/index.html",
                "${CacheManager.CACHE_PREFIX_ASSETS}index.html"
            )
            
            // Précharger cartouches.json
            cacheManager.fetchAndCache(
                "$baseUrl/cartouches.json",
                "${CacheManager.CACHE_PREFIX_CARTOUCHE}cartouches.json"
            )
            
            Log.d(TAG, "Essential data preloaded")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading essential data", e)
        }
    }

    private fun updateStatus(text: String) {
        coroutineScope.launch {
            statusText.text = text
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SplashActivity destroyed")
    }
}
