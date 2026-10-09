package com.pico8.online

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class WebViewErrorHandler(private val context: Context) : WebViewClient() {
    
    companion object {
        private const val TAG = "WebViewErrorHandler"
    }

    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        
        // Gérer les erreurs spécifiques
        when (errorCode) {
            ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_TIMEOUT -> {
                // Erreurs réseau
                showErrorToast("Network error: Unable to connect")
            }
            ERROR_FILE_NOT_FOUND -> {
                showErrorToast("File not found")
            }
            ERROR_FILE, ERROR_IO -> {
                showErrorToast("I/O error: $description")
            }
            ERROR_UNSUPPORTED_AUTH_SCHEME, ERROR_AUTHENTICATION -> {
                showErrorToast("Authentication error")
            }
            ERROR_PROXY_AUTHENTICATION -> {
                showErrorToast("Proxy authentication required")
            }
            ERROR_UNKNOWN -> {
                showErrorToast("Unknown error: $description")
            }
            else -> {
                showErrorToast("Error $errorCode: $description")
            }
        }
    }

    private fun showErrorToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
