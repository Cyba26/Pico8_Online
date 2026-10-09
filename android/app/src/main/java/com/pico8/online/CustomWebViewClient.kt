package com.pico8.online

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.runBlocking

class CustomWebViewClient(
    private val context: Context,
    private val cacheManager: CacheManager
) : WebViewClient() {
    
    companion object {
        private const val TAG = "CustomWebViewClient"
        private const val ASSET_BASE_PATH = ""
    }

    private val assetHelper = AssetHelper

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d(TAG, "Page started: $url")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d(TAG, "Page finished: $url")
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        // Ne pas override, laisser le WebView gérer les URLs
        return false
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null
        
        Log.d(TAG, "Intercepting request: $url")
        
        // Essayer de servir depuis les assets en premier
        val assetResponse = tryServeFromAssets(url)
        if (assetResponse != null) {
            Log.d(TAG, "Served from assets: $url")
            return assetResponse
        }
        
        // Puis essayer le cache
        return handleRequest(url)
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        // Accepter les erreurs SSL en développement (À améliorer pour la production)
        handler?.proceed()
    }

    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        Log.e(TAG, "Error $errorCode: $description for URL: $failingUrl")
    }

    /**
     * Essaie de servir la ressource depuis les assets
     */
    private fun tryServeFromAssets(url: String): WebResourceResponse? {
        val baseUrl = cacheManager.getBaseUrl()
        
        // Extraire le chemin de l'URL
        val path = when {
            url == baseUrl || url == "$baseUrl/" -> "index.html"
            url.startsWith(baseUrl) -> url.substring(baseUrl.length)
            else -> null
        } ?: return null
        
        // Normaliser le chemin (supprimer les slashes de début)
        val normalizedPath = path.replaceFirst("/+".toRegex(), "")
        
        // Vérifier si le fichier existe dans les assets
        val assetContent = assetHelper.readAssetBytes(context, normalizedPath)
        
        if (assetContent != null) {
            val mimeType = getMimeType(normalizedPath)
            Log.d(TAG, "Found in assets: $normalizedPath (${assetContent.size} bytes)")
            return WebResourceResponse(mimeType, "UTF-8", java.io.ByteArrayInputStream(assetContent))
        }
        
        return null
    }

    private fun handleRequest(url: String): WebResourceResponse? {
        val baseUrl = cacheManager.getBaseUrl()
        
        // Analyser l'URL
        when {
            // Index HTML principal
            url.endsWith("index.html") || url.endsWith("/") || url == baseUrl -> {
                val cacheKey = "${CacheManager.CACHE_PREFIX_ASSETS}index.html"
                return serveFromCache(url, cacheKey, "text/html")
            }
            
            // cartouches.json
            url.endsWith("cartouches.json") -> {
                val cacheKey = "${CacheManager.CACHE_PREFIX_CARTOUCHE}cartouches.json"
                return serveFromCache(url, cacheKey, "application/json")
            }
            
            // Fichiers .p8.png (cartouches)
            url.endsWith(".p8.png") -> {
                val filename = url.substringAfterLast("/")
                val cacheKey = "${CacheManager.CACHE_PREFIX_CARTOUCHE}$filename"
                return serveFromCache(url, cacheKey, "image/png")
            }
            
            // Thumbnails
            url.contains("cartouches/") && url.endsWith(".png") -> {
                val filename = url.substringAfterLast("/")
                val cacheKey = "${CacheManager.CACHE_PREFIX_CARTOUCHE}thumb_$filename"
                return serveFromCache(url, cacheKey, "image/png")
            }
            
            // Autres ressources
            url.contains("/public/") -> {
                val path = url.substringAfter("/public/")
                val cacheKey = "${CacheManager.CACHE_PREFIX_ASSETS}$path"
                val mimeType = getMimeType(path)
                return serveFromCache(url, cacheKey, mimeType)
            }
            
            else -> {
                // Ne pas intercepter, laisser le WebView gérer normalement
                return null
            }
        }
    }

    private fun serveFromCache(url: String, cacheKey: String, defaultMimeType: String): WebResourceResponse? {
        return try {
            val cachedData = runBlocking {
                // Essayer de récupérer depuis le cache
                cacheManager.getFromCache(cacheKey)
            }
            
            if (cachedData != null) {
                Log.d(TAG, "Serving from cache: $cacheKey (${cachedData.size} bytes)")
                WebResourceResponse(
                    defaultMimeType,
                    "UTF-8",
                    java.io.ByteArrayInputStream(cachedData)
                )
            } else {
                // Si pas en cache, essayer de récupérer et mettre en cache
                Log.d(TAG, "Cache miss for: $cacheKey, fetching from network")
                
                // Mettre à jour le cache en arrière-plan
                kotlinx.coroutines.GlobalScope.launch {
                    try {
                        cacheManager.fetchAndCache(url, cacheKey)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch and cache: $url", e)
                    }
                }
                
                // Retourner null pour laisser le WebView faire la requête normalement
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error serving from cache: $cacheKey", e)
            null
        }
    }

    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html") -> "text/html"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".woff") -> "font/woff"
            path.endsWith(".woff2") -> "font/woff2"
            path.endsWith(".dat") -> "application/octet-stream"
            path.endsWith(".p8.png") -> "image/png"
            else -> "application/octet-stream"
        }
    }
}
