package com.pico8.online

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "Pico8OnlinePrefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_OFFLINE_MODE = "offline_mode"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setBaseUrl(url: String) {
        sharedPreferences.edit().putString(KEY_BASE_URL, url).apply()
    }

    fun getBaseUrl(): String {
        return sharedPreferences.getString(KEY_BASE_URL, Config.DEFAULT_BASE_URL) ?: Config.DEFAULT_BASE_URL
    }

    fun setLastSyncTimestamp(timestamp: Long) {
        sharedPreferences.edit().putLong(KEY_LAST_SYNC, timestamp).apply()
    }

    fun getLastSyncTimestamp(): Long {
        return sharedPreferences.getLong(KEY_LAST_SYNC, 0)
    }

    fun isFirstLaunch(): Boolean {
        return sharedPreferences.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchCompleted() {
        sharedPreferences.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    fun setOfflineMode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_OFFLINE_MODE, enabled).apply()
    }

    fun isOfflineModeEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_OFFLINE_MODE, false)
    }

    fun clearAllPreferences() {
        sharedPreferences.edit().clear().apply()
    }

    // Méthodes étendues utilisées par UpdateManager
    fun putLong(key: String, value: Long) {
        sharedPreferences.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return sharedPreferences.getLong(key, defaultValue)
    }

    fun putString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }
}
