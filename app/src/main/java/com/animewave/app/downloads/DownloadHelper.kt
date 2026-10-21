package com.animewave.app.downloads

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import org.json.JSONObject
import java.io.File

/**
 * Shared download logic: starts downloads through Android's DownloadManager (which keeps running
 * in the background, with its own notification), avoids duplicates and filename clashes,
 * supports Wi-Fi-only, and can retry a failed download.
 */
object DownloadHelper {
    private const val PREFS = "animewave_settings"
    const val KEY_WIFI_ONLY = "download_wifi_only"
    private const val KEY_META = "download_meta_"

    sealed class Result {
        data class Started(val id: Long) : Result()
        data class Duplicate(val name: String) : Result()
        data class Error(val message: String) : Result()
    }

    /** True on Android 9 and older when saving to the public Downloads folder and permission isn't granted yet. */
    fun needsLegacyStoragePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("download_storage", "phone") != "app" &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
    }

    fun pausedText(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "Connection dropped — retrying automatically"
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for a network connection"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
        else -> "Paused"
    }

    fun failedText(reason: Int): String = when (reason) {
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Connection broke mid-download. Try Wi-Fi, then Retry"
        DownloadManager.ERROR_CANNOT_RESUME -> "Couldn't resume. Retry to start over"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage isn't available"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "A file with that name already exists"
        DownloadManager.ERROR_FILE_ERROR -> "Couldn't write the file"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "The link kept redirecting"
        401, 403 -> "The site refused the download. The link may have expired; open the episode again"
        404 -> "That file is gone (404)"
        else -> if (reason in 400..599) "Server error (HTTP $reason)" else "Download failed (code $reason)"
    }

    fun enqueue(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        referer: String?,
        fileName: String? = null
    ): Result = try {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" || uri.scheme == "http") { "Unsupported download URL" }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val baseName = (fileName ?: URLUtil.guessFileName(url, contentDisposition, mimeType))
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .take(120)
            .ifBlank { "anime_download" }

        if (isActive(manager, url)) {
            Result.Duplicate(baseName)
        } else {
            // On Android 9 and older the public Downloads folder needs a runtime permission;
            // without it, fall back to AnimeWave's own storage instead of failing.
            val publicStorage = prefs.getString("download_storage", "phone") != "app" &&
                !needsLegacyStoragePermission(context)
            val name = uniqueName(baseName, takenTitles(manager), context, publicStorage)

            val request = DownloadManager.Request(uri)
                .setTitle(name)
                .setDescription("AnimeWave episode download")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            if (prefs.getBoolean(KEY_WIFI_ONLY, false)) {
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            }
            if (publicStorage) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AnimeWave/$name")
            } else {
                request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
            }
            if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                request.addRequestHeader("Cookie", it)
            }
            if (!referer.isNullOrBlank()) request.addRequestHeader("Referer", referer)
            if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)

            val id = manager.enqueue(request)
            // Remember how this download was started so Retry can repeat it.
            val meta = JSONObject()
                .put("ua", userAgent.orEmpty())
                .put("ref", referer.orEmpty())
                .put("mime", mimeType.orEmpty())
            prefs.edit().putString(KEY_META + id, meta.toString()).apply()
            Result.Started(id)
        }
    } catch (e: Exception) {
        Result.Error(e.message ?: "unsupported link")
    }

    /** Removes the failed entry and starts the same download again. */
    fun retry(context: Context, failedId: Long): Result {
        return try {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            var url: String? = null
            var title: String? = null
            manager.query(DownloadManager.Query().setFilterById(failedId))?.use { c ->
                if (c.moveToFirst()) {
                    val u = c.getColumnIndex(DownloadManager.COLUMN_URI)
                    val t = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
                    if (u >= 0) url = c.getString(u)
                    if (t >= 0) title = c.getString(t)
                }
            }
            val link = url ?: return Result.Error("The original link wasn't saved. Open the episode again.")
            val meta = prefs.getString(KEY_META + failedId, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            manager.remove(failedId)
            prefs.edit().remove(KEY_META + failedId).apply()
            enqueue(
                context, link,
                userAgent = meta?.optString("ua")?.takeIf { it.isNotBlank() },
                contentDisposition = null,
                mimeType = meta?.optString("mime")?.takeIf { it.isNotBlank() },
                referer = meta?.optString("ref")?.takeIf { it.isNotBlank() },
                fileName = title
            )
        } catch (e: Exception) {
            Result.Error(e.message ?: "retry failed")
        }
    }

    private fun isActive(manager: DownloadManager, url: String): Boolean {
        val mask = DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED
        return runCatching {
            manager.query(DownloadManager.Query().setFilterByStatus(mask))?.use { c ->
                val i = c.getColumnIndex(DownloadManager.COLUMN_URI)
                var found = false
                while (!found && c.moveToNext()) if (i >= 0 && c.getString(i) == url) found = true
                found
            } ?: false
        }.getOrDefault(false)
    }

    private fun takenTitles(manager: DownloadManager): Set<String> {
        val titles = mutableSetOf<String>()
        runCatching {
            manager.query(DownloadManager.Query())?.use { c ->
                val i = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
                while (c.moveToNext()) if (i >= 0) c.getString(i)?.let { titles += it }
            }
        }
        return titles
    }

    private fun uniqueName(base: String, taken: Set<String>, context: Context, publicStorage: Boolean): String {
        val dir = if (publicStorage) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AnimeWave")
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        }
        fun clash(n: String) = n in taken || (dir != null && File(dir, n).exists())
        if (!clash(base)) return base
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        var n = 2
        while (clash("$stem-$n$ext")) n++
        return "$stem-$n$ext"
    }
}
