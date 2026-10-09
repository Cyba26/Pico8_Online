package fr.cybai.p8

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Sert le site au WebView sans dépendre du réseau.
 *
 * Trois sources, de la plus fraîche à la plus ancienne : le site en ligne, la
 * copie gardée dans filesDir/site, et le site embarqué dans l'APK au build.
 *
 * - index.html et cartouches.json : servis tout de suite depuis la copie (ou
 *   l'APK), puis rafraîchis en arrière-plan. Une nouveauté du site apparaît
 *   donc au lancement suivant, et l'app ne fait jamais attendre le réseau.
 * - une cartouche : son URL porte l'empreinte de son contenu (`?v=`, posée par
 *   scripts/build-cartouches.mjs). Une version déjà connue ne se retélécharge
 *   pas ; hors ligne, on sert la dernière version connue.
 */
class SiteStore(context: Context) {

    private val assets = context.assets
    private val root = File(context.filesDir, "site")
    private val background = Executors.newSingleThreadExecutor()
    private val refreshed = ConcurrentHashMap.newKeySet<String>()

    class Page(val mime: String, val stream: InputStream)

    fun open(uri: Uri): Page? {
        val path = uri.path.orEmpty().trimStart('/').ifEmpty { "index.html" }
        if (path.split('/').any { it == ".." }) return null
        val stream = when (val version = uri.getQueryParameter("v")) {
            null -> openLatest(path, uri.encodedPath ?: "/$path")
            else -> openVersion(path, uri.encodedPath ?: "/$path", version)
        } ?: return null
        return Page(mimeOf(path), stream)
    }

    private fun openLatest(path: String, encodedPath: String): InputStream? {
        val cached = File(root, path)
        if (refreshed.add(path)) {
            background.execute { download("$encodedPath?t=${System.currentTimeMillis()}", cached) }
        }
        return if (cached.exists()) cached.inputStream() else asset(path)
    }

    private fun openVersion(path: String, encodedPath: String, version: String): InputStream? {
        val cached = File(root, "$path@$version")
        if (cached.exists()) return cached.inputStream()
        // La plupart du temps, la cartouche embarquée est déjà la bonne.
        assetBytes(path)?.let { if (sha1(it).startsWith(version)) return it.inputStream() }
        if (download("$encodedPath?v=$version", cached)) {
            olderVersions(path).filter { it != cached }.forEach { it.delete() }
            return cached.inputStream()
        }
        return olderVersions(path).maxByOrNull { it.lastModified() }?.inputStream() ?: asset(path)
    }

    private fun olderVersions(path: String): List<File> {
        val file = File(root, path)
        val prefix = file.name + "@"
        return file.parentFile?.listFiles { f -> f.name.startsWith(prefix) }?.toList().orEmpty()
    }

    /** Écrit dans un fichier temporaire puis renomme : jamais de copie à moitié écrite. */
    private fun download(encodedPathAndQuery: String, target: File): Boolean {
        val connection = URL(SITE_URL + encodedPathAndQuery).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 4000
            connection.readTimeout = 10000
            connection.useCaches = false
            if (connection.responseCode != 200) return false
            target.parentFile?.mkdirs()
            val tmp = File(target.path + ".part")
            connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(target)
        } catch (e: Exception) {
            Log.i(TAG, "hors ligne ? $encodedPathAndQuery : ${e.message}")
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun asset(path: String): InputStream? =
        try { assets.open(path) } catch (e: Exception) { null }

    private fun assetBytes(path: String): ByteArray? = asset(path)?.use { it.readBytes() }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun mimeOf(path: String) = when (path.substringAfterLast('.').lowercase()) {
        "html" -> "text/html"
        "json" -> "application/json"
        "png" -> "image/png"
        "js" -> "text/javascript"
        "css" -> "text/css"
        else -> "application/octet-stream"
    }

    companion object {
        const val SITE_URL = "https://p8.cybai.fr"
        private const val TAG = "SiteStore"
    }
}
