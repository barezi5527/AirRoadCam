package de.roadcam.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GitHubRelease(
    val tag: String,
    val version: String,
    val name: String,
    val notes: String,
    val apkUrl: String
)

object UpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/barezi5527/AirRoadCam/releases/latest"
    private const val TIMEOUT_MS = 10_000

    fun latestRelease(currentVersion: String): GitHubRelease? {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "AirRoadCam")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseRelease(json, currentVersion)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseRelease(json: String, currentVersion: String): GitHubRelease? {
        val release = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val tag = release.optString("tag_name").orEmpty()
        val version = normalizeVersion(tag) ?: return null
        if (!isNewer(currentVersion, version)) return null
        val assets = release.optJSONArray("assets") ?: return null
        val apkUrl = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?.optString("browser_download_url")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return GitHubRelease(
            tag = tag,
            version = version,
            name = release.optString("name").takeIf { it.isNotBlank() } ?: tag,
            notes = release.optString("body").orEmpty(),
            apkUrl = apkUrl
        )
    }

    internal fun normalizeVersion(raw: String): String? {
        val trimmed = raw.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        return trimmed.takeIf { it.isNotBlank() && it.any { char -> char.isDigit() } }
    }

    internal fun isNewer(current: String, latest: String): Boolean {
        val currentParts = normalizeVersion(current)?.split('.')?.map { it.toIntOrNull() ?: 0 }
            ?: return false
        val latestParts = normalizeVersion(latest)?.split('.')?.map { it.toIntOrNull() ?: 0 }
            ?: return false
        for (index in 0 until maxOf(currentParts.size, latestParts.size)) {
            val left = currentParts.getOrElse(index) { 0 }
            val right = latestParts.getOrElse(index) { 0 }
            if (right > left) return true
            if (right < left) return false
        }
        return false
    }
}
