package com.pico8.online

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

class ConnectivityReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ConnectivityReceiver"
        var connectivityChangedListeners = mutableListOf<(Boolean) -> Unit>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val hasConnection = isNetworkAvailable(context)
        Log.d(TAG, "Connectivity changed, has connection: $hasConnection")
        
        // Notifier tous les listeners
        connectivityChangedListeners.forEach { listener ->
            listener(hasConnection)
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun registerListener(listener: (Boolean) -> Unit) {
        connectivityChangedListeners.add(listener)
    }

    fun unregisterListener(listener: (Boolean) -> Unit) {
        connectivityChangedListeners.remove(listener)
    }
}
