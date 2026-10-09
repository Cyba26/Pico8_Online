package com.pico8.online

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Extensions utiles pour Android
 */

// ============================================================
// Permissions
// ============================================================

fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

fun Activity.requestPermission(
    permission: String,
    requestCode: Int,
    onGranted: () -> Unit = {},
    onDenied: () -> Unit = {}
) {
    if (hasPermission(permission)) {
        onGranted()
    } else {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }
}

fun Activity.requestPermissions(
    permissions: Array<String>,
    requestCode: Int
) {
    val permissionsToRequest = permissions.filter { !hasPermission(it) }.toTypedArray()
    if (permissionsToRequest.isNotEmpty()) {
        ActivityCompat.requestPermissions(this, permissionsToRequest, requestCode)
    }
}

// ============================================================
// Toast
// ============================================================

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Context.showToast(resId: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, resId, duration).show()
}

// ============================================================
// Connectivité
// ============================================================

fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } else {
        val networkInfo = connectivityManager.activeNetworkInfo ?: return false
        return networkInfo.isConnected
    }
}

fun Context.isConnectedToWifi(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    } else {
        val networkInfo = connectivityManager.activeNetworkInfo ?: return false
        return networkInfo.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
    }
}

fun Context.isConnectedToMobile(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    } else {
        val networkInfo = connectivityManager.activeNetworkInfo ?: return false
        return networkInfo.type == ConnectivityManager.TYPE_MOBILE && networkInfo.isConnected
    }
}

// ============================================================
// Logging
// ============================================================

fun Any.logDebug(tag: String = this::class.java.simpleName, message: String) {
    Log.d(tag, message)
}

fun Any.logError(tag: String = this::class.java.simpleName, message: String, throwable: Throwable? = null) {
    if (throwable != null) {
        Log.e(tag, message, throwable)
    } else {
        Log.e(tag, message)
    }
}

fun Any.logInfo(tag: String = this::class.java.simpleName, message: String) {
    Log.i(tag, message)
}

// ============================================================
// Validation
// ============================================================

fun String?.isValidUrl(): Boolean {
    if (this == null) return false
    return this.startsWith("http://") || this.startsWith("https://")
}

fun String?.isValidEmail(): Boolean {
    if (this == null) return false
    return android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

// ============================================================
// Conversion
// ============================================================

fun Long.formatFileSize(): String {
    if (this <= 0) return "0 B"
    
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = this.toDouble()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024.0
        unitIndex++
    }
    
    return "%.2f ${units[unitIndex]}".format(size)
}

fun Int.formatTimeMs(): String {
    val seconds = this / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        days > 0 -> "$days j"
        hours > 0 -> "$hours h"
        minutes > 0 -> "$minutes m"
        else -> "$seconds s"
    }
}
