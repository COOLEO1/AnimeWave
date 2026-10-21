package com.animewave.app.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import java.util.Locale

/**
 * Shared entry point for direct HTTP(S) downloads. Uses Android DownloadManager so
 * transfers can continue while the user leaves the source browser or player.
 */
object DownloadHelper {
    data class EnqueueResult(val id: Long, val alreadyQueued: Boolean)

    fun enqueue(
        context: Context,
        uri: Uri,
        title: String,
        userAgent: String? = null,
        referer: String? = null,
        cookie: String? = null,
        mimeType: String? = null,
        bypassDuplicateCheck: Boolean = false
    ): EnqueueResult {
        require((uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()) {
            "This link is not a direct HTTP(S) URL."
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        if (!bypassDuplicateCheck) {
            findExisting(manager, uri.toString())?.let { return EnqueueResult(it, true) }
        }

        val preferences = context.getSharedPreferences("animewave_settings", Context.MODE_PRIVATE)
        val fileName = URLUtil.guessFileName(uri.toString(), null, mimeType)
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .take(120)
            .ifBlank { safeTitle(title) + ".mp4" }

        val request = DownloadManager.Request(uri)
            .setTitle(title.ifBlank { "AnimeWave download" })
            .setDescription("AnimeWave episode download")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)

        val wifiOnly = preferences.getBoolean("wifi_only", false)
        if (wifiOnly) {
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
        } else {
            request.setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
            )
        }

        if (preferences.getString("download_storage", "phone") == "app") {
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        } else {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AnimeWave/$fileName")
        }

        userAgent?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("User-Agent", it) }
        referer?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("Referer", it) }
        cookie?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("Cookie", it) }
        mimeType?.takeIf { it.isNotBlank() }?.let { request.setMimeType(it) }

        return EnqueueResult(manager.enqueue(request), false)
    }

    /**
     * Retry removes the failed/old DownloadManager row and queues the same URI again.
     * Authentication headers are not exposed by DownloadManager, so protected links
     * that require temporary cookies may need to be started again from the source page.
     */
    fun retry(context: Context, downloadId: Long): EnqueueResult? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var uri: Uri? = null
        var title = "AnimeWave download"
        manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
            if (cursor.moveToFirst()) {
                val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
                val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                if (uriIndex >= 0) uri = runCatching { Uri.parse(cursor.getString(uriIndex)) }.getOrNull()
                if (titleIndex >= 0) title = cursor.getString(titleIndex) ?: title
            }
        }
        val retryUri = uri ?: return null
        manager.remove(downloadId)
        return enqueue(context, retryUri, title, bypassDuplicateCheck = true)
    }

    private fun findExisting(manager: DownloadManager, url: String): Long? {
        manager.query(DownloadManager.Query())?.use { cursor ->
            val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
            val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (uriIndex < 0 || idIndex < 0) return null
            while (cursor.moveToNext()) {
                if (cursor.getString(uriIndex) == url) {
                    val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1
                    if (status != DownloadManager.STATUS_FAILED) return cursor.getLong(idIndex)
                }
            }
        }
        return null
    }

    private fun safeTitle(title: String): String =
        title.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]+"), "_").trim('_').ifBlank { "anime_download" }
}
