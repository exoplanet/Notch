package com.exoplanet.notch.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal data class RemoteRelease(
    val tag: String,
    val apkName: String?,
    val apkUrl: String?,
)

internal sealed class UpdateCheck {
    data class UpToDate(val current: String) : UpdateCheck()
    data class Available(val version: String, val url: String) : UpdateCheck()
}

private const val MAX_BYTES = 40_000_000

internal object GitHubUpdate {
    private const val LATEST = "https://api.github.com/repos/exoplanet/Notch/releases/latest"

    fun check(currentVersionName: String): UpdateCheck {
        val release = parseLatestRelease(httpGet(LATEST))
        val apkUrl = release.apkUrl ?: error("The latest release has no APK.")
        if (!isAllowedDownloadUrl(apkUrl)) error("The release APK is not on GitHub.")
        val version = release.tag.removePrefix("v").removePrefix("V")
        return if (isNewerRelease(release.tag, currentVersionName)) {
            UpdateCheck.Available(version, apkUrl)
        } else {
            UpdateCheck.UpToDate(currentVersionName)
        }
    }

    fun download(context: Context, apkUrl: String): File {
        if (!isAllowedDownloadUrl(apkUrl)) error("The release APK is not on GitHub.")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val destination = File(dir, "notch-update.apk")
        val temporary = File(dir, "notch-update.apk.part")
        temporary.delete()
        downloadFollowingRedirects(apkUrl, temporary)
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        return destination
    }

    fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun canInstallPackages(context: Context): Boolean {
        return Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
    }

    fun requestInstallPermission(context: Context) {
        val intent = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

internal fun parseLatestRelease(json: String): RemoteRelease {
    val root = JSONObject(json)
    val tag = root.getString("tag_name")
    val assets = root.getJSONArray("assets")
    var name: String? = null
    var url: String? = null
    for (index in 0 until assets.length()) {
        val asset = assets.getJSONObject(index)
        val assetName = asset.getString("name")
        if (!assetName.endsWith(".apk", ignoreCase = true)) continue
        val download = asset.getString("browser_download_url")
        val preferred = assetName.startsWith("notch-", ignoreCase = true)
        if (url == null || preferred) {
            name = assetName
            url = download
        }
    }
    return RemoteRelease(tag, name, url)
}

internal fun isNewerRelease(latestTag: String, currentVersionName: String): Boolean {
    val latest = versionParts(latestTag)
    val current = versionParts(currentVersionName)
    val count = maxOf(latest.size, current.size)
    for (index in 0 until count) {
        val difference = latest.getOrElse(index) { 0 } - current.getOrElse(index) { 0 }
        if (difference != 0) return difference > 0
    }
    return false
}

internal fun isAllowedDownloadUrl(raw: String): Boolean {
    val uri = try {
        URI(raw)
    } catch (_: Exception) {
        return false
    }
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    if (uri.userInfo != null) return false
    val host = uri.host?.lowercase() ?: return false
    val path = uri.path ?: ""
    return when (host) {
        "github.com" ->
            path.startsWith("/exoplanet/Notch/releases/download/") &&
                path.endsWith(".apk", ignoreCase = true)
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com" -> true
        else -> false
    }
}

private fun versionParts(raw: String): List<Int> {
    val cleaned = raw.trim().removePrefix("v").removePrefix("V").substringBefore("-")
    if (cleaned.isEmpty()) return emptyList()
    return cleaned.split(".").map { it.toIntOrNull() ?: 0 }
}

private fun httpGet(url: String): String {
    val connection = open(url, "application/vnd.github+json")
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code == 404) error("No release has been published yet.")
        if (code !in 200..299) error("GitHub returned $code.")
        return body
    } finally {
        connection.disconnect()
    }
}

private fun downloadFollowingRedirects(startUrl: String, destination: File) {
    var url = startUrl
    repeat(5) {
        if (!isAllowedDownloadUrl(url)) error("The release APK is not on GitHub.")
        val connection = open(url, "*/*")
        try {
            connection.instanceFollowRedirects = false
            val code = connection.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location") ?: error("GitHub redirect was empty.")
                url = URL(URL(url), location).toString()
                return@repeat
            }
            if (code !in 200..299) error("Download failed ($code).")
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    var header = ByteArray(0)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (header.size < 4) {
                            header += buffer.copyOf(read)
                        }
                        total += read
                        if (total > MAX_BYTES) error("The release APK is larger than expected.")
                        output.write(buffer, 0, read)
                    }
                    if (header.size < 4 || header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                        destination.delete()
                        error("The download was not an APK.")
                    }
                }
            }
            return
        } finally {
            connection.disconnect()
        }
    }
    error("GitHub redirected too many times.")
}

private fun open(url: String, accept: String): HttpURLConnection {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = false
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.setRequestProperty("User-Agent", "Notch")
    connection.setRequestProperty("Accept", accept)
    return connection
}
