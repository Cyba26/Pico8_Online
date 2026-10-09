package com.pico8.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class CacheManager(private val context: Context) {
    private val cacheDao = CacheDatabase.getDatabase(context).cacheDao()
    private val okHttpClient = OkHttpClient()
    
    companion object {
        private const val TAG = "CacheManager"
        const val CACHE_PREFIX_RUNTIME = "runtime_"
        const val CACHE_PREFIX_CARTRIDGES = "cartridges_"
        const val CACHE_PREFIX_CARTOUCHE = "cartouche_"
        const val CACHE_PREFIX_ASSETS = "assets_"
        
        // Durée de validité du cache en millisecondes (24 heures)
        const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000
    }

    suspend fun getFromCache(key: String): ByteArray? {
        return try {
            val entity = cacheDao.getByKey(key)
            if (entity != null && !isExpired(entity)) {
                Log.d(TAG, "Cache hit for key: $key")
                entity.content
            } else {
                Log.d(TAG, "Cache miss or expired for key: $key")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from cache: $key", e)
            null
        }
    }

    suspend fun saveToCache(key: String, content: ByteArray, contentType: String = "application/octet-stream", etag: String? = null): Boolean {
        return try {
            val entity = CacheEntity(
                key = key,
                content = content,
                lastModified = System.currentTimeMillis(),
                etag = etag,
                contentType = contentType
            )
            cacheDao.insert(entity)
            Log.d(TAG, "Saved to cache: $key (${content.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to cache: $key", e)
            false
        }
    }

    suspend fun fetchAndCache(url: String, cacheKey: String, forceRefresh: Boolean = false): ByteArray? {
        // Vérifier si on a déjà en cache et si ce n'est pas expiré
        if (!forceRefresh) {
            val cached = getFromCache(cacheKey)
            if (cached != null) {
                return cached
            }
        }

        // Vérifier la connectivité
        if (!isNetworkAvailable()) {
            Log.w(TAG, "No network available, returning cached data if exists")
            return getFromCache(cacheKey)
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP error: ${response.code} for URL: $url")
                // Retourner le cache existant si disponible
                return getFromCache(cacheKey)
            }

            val content = response.body?.bytes() ?: return null
            val contentType = response.header("Content-Type") ?: "application/octet-stream"
            val etag = response.header("ETag")
            
            // Sauvegarder en cache
            saveToCache(cacheKey, content, contentType, etag)
            
            Log.d(TAG, "Successfully fetched and cached: $url (${content.size} bytes)")
            content
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching: $url", e)
            // Retourner le cache existant si disponible
            getFromCache(cacheKey)
        }
    }

    suspend fun invalidateCacheForKey(key: String) {
        cacheDao.delete(key)
        Log.d(TAG, "Invalidated cache for key: $key")
    }

    suspend fun invalidateCacheForPrefix(prefix: String) {
        cacheDao.deleteByPrefix(prefix)
        Log.d(TAG, "Invalidated cache for prefix: $prefix")
    }

    suspend fun clearAllCache() {
        cacheDao.clearAll()
        Log.d(TAG, "Cleared all cache")
    }

    private fun isExpired(entity: CacheEntity): Boolean {
        return System.currentTimeMillis() - entity.lastModified > CACHE_EXPIRY_MS
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun isConnectedToWifi(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    // Récupérer l'URL base depuis les préférences ou utiliser une valeur par défaut
    fun getBaseUrl(): String {
        return "https://cyba.github.io/Pico8_Online" // À adapter selon ton deployment
    }
}
