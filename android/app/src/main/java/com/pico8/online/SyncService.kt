package com.pico8.online

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SyncService : Service() {
    
    companion object {
        private const val TAG = "SyncService"
        const val ACTION_SYNC = "com.pico8.online.ACTION_SYNC"
        const val ACTION_SYNC_NOW = "com.pico8.online.ACTION_SYNC_NOW"
    }

    private lateinit var cacheManager: CacheManager
    private var syncJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        cacheManager = CacheManager(applicationContext)
        Log.d(TAG, "SyncService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SYNC -> {
                startSync()
            }
            ACTION_SYNC_NOW -> {
                performSync()
            }
        }
        return START_NOT_STICKY
    }

    private fun startSync() {
        if (!cacheManager.isNetworkAvailable()) {
            Log.d(TAG, "No network available, sync aborted")
            stopSelf()
            return
        }
        
        syncJob = coroutineScope.launch {
            try {
                Log.d(TAG, "Starting sync...")
                performSync()
                Log.d(TAG, "Sync completed")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
            } finally {
                stopSelf()
            }
        }
    }

    private suspend fun performSync() {
        val baseUrl = cacheManager.getBaseUrl()
        
        // Synchroniser l'index.html
        val indexUrl = "$baseUrl/index.html"
        cacheManager.fetchAndCache(indexUrl, "${CacheManager.CACHE_PREFIX_ASSETS}index.html")
        
        // Synchroniser cartouches.json
        val cartouchesUrl = "$baseUrl/cartouches.json"
        cacheManager.fetchAndCache(cartouchesUrl, "${CacheManager.CACHE_PREFIX_CARTOUCHE}cartouches.json")
        
        // Synchroniser les fichiers .p8.png
        syncCartouches()
    }

    private suspend fun syncCartouches() {
        try {
            // Récupérer la liste des cartouches
            val cartouchesJson = cacheManager.fetchAndCache(
                "${cacheManager.getBaseUrl()}/cartouches.json",
                "${CacheManager.CACHE_PREFIX_CARTOUCHE}cartouches.json",
                true // Force refresh pour obtenir la liste la plus récente
            )
            
            if (cartouchesJson != null) {
                val jsonString = String(cartouchesJson)
                Log.d(TAG, "Fetched cartouches.json: $jsonString")
                
                // Pour l'instant, on ne parse pas le JSON pour simplifier
                // Dans une version améliorée, on pourrait parser et télécharger chaque cartouche
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync cartouches", e)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
        coroutineScope.cancel()
        Log.d(TAG, "SyncService destroyed")
    }
}
