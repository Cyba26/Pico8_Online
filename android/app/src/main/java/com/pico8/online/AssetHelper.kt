package com.pico8.online

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object AssetHelper {
    
    private const val TAG = "AssetHelper"
    
    /**
     * Lit un fichier depuis les assets
     */
    fun readAssetFile(context: Context, path: String): String? {
        return try {
            val inputStream = context.assets.open(path)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
                stringBuilder.append("\n")
            }
            
            reader.close()
            inputStream.close()
            
            stringBuilder.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading asset file: $path", e)
            null
        }
    }
    
    /**
     * Lit un fichier binaire depuis les assets
     */
    fun readAssetBytes(context: Context, path: String): ByteArray? {
        return try {
            val inputStream = context.assets.open(path)
            val bytes = inputStream.readBytes()
            inputStream.close()
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "Error reading asset bytes: $path", e)
            null
        }
    }
    
    /**
     * Vérifie si un fichier existe dans les assets
     */
    fun assetExists(context: Context, path: String): Boolean {
        return try {
            context.assets.list("").contains(path) ||
            context.assets.open(path).use { true }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Liste les fichiers dans un dossier des assets
     */
    fun listAssets(context: Context, path: String = ""): List<String> {
        return try {
            context.assets.list(path)?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error listing assets: $path", e)
            emptyList()
        }
    }
    
    /**
     * Liste récursivement tous les fichiers dans un dossier des assets
     */
    fun listAssetsRecursive(context: Context, path: String = ""): List<String> {
        val files = mutableListOf<String>()
        
        try {
            val list = context.assets.list(path) ?: return files
            
            for (file in list) {
                val fullPath = if (path.isEmpty()) file else "$path/$file"
                
                // Vérifier si c'est un dossier
                try {
                    context.assets.list(fullPath)
                    // C'est un dossier, récursion
                    files.addAll(listAssetsRecursive(context, fullPath))
                } catch (e: Exception) {
                    // C'est un fichier
                    files.add(fullPath)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error listing assets recursively: $path", e)
        }
        
        return files
    }
}
