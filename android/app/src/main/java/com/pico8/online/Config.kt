package com.pico8.online

object Config {
    // URL par défaut pour l'application web
    const val DEFAULT_BASE_URL = "https://cyba.github.io/Pico8_Online"
    
    // Alternatives possibles
    const val VERCEL_URL = "https://pico8-online.vercel.app"
    const val LOCALHOST_URL = "http://10.0.2.2:5173" // Pour développement local
    
    // Chemins des ressources
    const val INDEX_PATH = "index.html"
    const val CARTOUCHES_JSON_PATH = "cartouches.json"
    const val PUBLIC_PATH = "public"
    
    // Paramètres de cache
    const val CACHE_EXPIRY_HOURS = 24L
    const val MAX_CACHE_SIZE_BYTES = 100 * 1024 * 1024 // 100 Mo
    
    // Clé pour les préférences
    const val PREFS_NAME = "Pico8OnlinePrefs"
    const val KEY_BASE_URL = "base_url"
    const val KEY_FIRST_LAUNCH = "first_launch"
    const val KEY_LAST_SYNC = "last_sync"
    const val KEY_OFFLINE_MODE = "offline_mode"
}
