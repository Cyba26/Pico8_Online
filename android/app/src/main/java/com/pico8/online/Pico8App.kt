package com.pico8.online

import android.app.Application
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.util.Log

class Pico8App : Application() {
    
    companion object {
        private const val TAG = "Pico8App"
        lateinit var instance: Pico8App
            private set
    }

    private lateinit var connectivityReceiver: ConnectivityReceiver

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialiser le receiver de connectivité
        connectivityReceiver = ConnectivityReceiver()
        registerReceiver(
            connectivityReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        )
        
        Log.d(TAG, "Application started")
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            unregisterReceiver(connectivityReceiver)
        } catch (e: Exception) {
            // Ignorer si le receiver n'est pas enregistré
        }
    }
}
