package com.animewave.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animewave.app.downloads.DownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class AnimeWaveDownload(
    val id: Long, val title: String, val status: Int,
    val downloadedBytes: Long, val totalBytes: Long, val reason: Int,
    val animeFolder: String?
)

@Composable
internal fun DownloadsContent(
    preferences: android.content.SharedPreferences,
    onPlay: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    var storageChoice by remember { mutableStateOf(preferences.getString("download_storage", "phone") ?: "phone") }
    var refreshToken by remember { mutableIntStateOf(0) }
    var downloads by remember { mutableStateOf<List<AnimeWaveDownload>>(emptyList()) }
    var loadingDownloads by remember { mutableStateOf(false) }
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(downloads) {
        if (downloads.any {
                it.status == DownloadManager.STATUS_PENDING ||
                it.status == DownloadManager.STATUS_RUNNING ||
                it.status == DownloadManager.STATUS_PAUSED
            }) {
            delay(1000)
            refreshToken++
        }
    }

    LaunchedEffect(refreshToken) {
        loadingDownloads = true
        downloads = withContext(Dispatchers.IO) {
            runCatching {
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val mask = DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or
                    DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_FAILED
                val found = mutableListOf<AnimeWaveDownload>()
                manager.query(DownloadManager.Query().setFilterByStatus(mask))?.use { cursor ->
                    while (cursor.moveToNext()) {
                        fun longColumn(name: String): Long {
                            val index = cursor.getColumnIndex(name)
                            return if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
                        }
                        fun intColumn(name: String): Int {
                            val index = cursor.getColumnIndex(name)
                            return if (index >= 0 && !cursor.isNull(index)) cursor.getInt(index) else 0
                        }
                        val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                        val title = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "Anime download" else "Anime download"
                        val downloadId = longColumn(DownloadManager.COLUMN_ID)
                        val folder = context.getSharedPreferences("animewave_download_library", Context.MODE_PRIVATE)
                            .getString("anime_$downloadId", null)
                        found += AnimeWaveDownload(
                            downloadId, title,
                            intColumn(DownloadManager.COLUMN_STATUS),
                            longColumn(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                            longColumn(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                            intColumn(DownloadManager.COLUMN_REASON), folder
                        )
                    }
                }
                found.sortedByDescending { it.id }
            }.getOrDefault(emptyList())
        }
        loadingDownloads = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("OFFLINE LIBRARY", "Downloads")
        Text("Choose where new episodes are saved.", color = Muted, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Wi-Fi only", fontWeight = FontWeight.SemiBold)
                Text("Avoid mobile-data downloads", color = Muted, fontSize = 11.sp)
            }
            Switch(
                checked = preferences.getBoolean("wifi_only", false),
                onCheckedChange = { preferences.edit().putBoolean("wifi_only", it).apply() }
            )
        }
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(storageChoice == "phone", onClick = {
                    storageChoice = "phone"; preferences.edit().putString("download_storage", "phone").apply()
                })
                Column(Modifier.weight(1f)) {
                    Text("Phone Downloads folder", fontWeight = FontWeight.SemiBold)
                    Text("Visible in your phone's Files app", color = Muted, fontSize = 11.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(storageChoice == "app", onClick = {
                    storageChoice = "app"; preferences.edit().putString("download_storage", "app").apply()
                })
                Column(Modifier.weight(1f)) {
                    Text("Inside AnimeWave", fontWeight = FontWeight.SemiBold)
                    Text("App-specific storage; removed if you uninstall the app", color = Muted, fontSize = 11.sp)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Download activity", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TextButton(onClick = { refreshToken++ }) { Text("Refresh") }
        }
        if (loadingDownloads) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Violet)
        } else if (downloads.isEmpty()) {
            EmptyState("Nothing downloaded yet", "Start a supported download from a source page or the player. Your downloads will appear here.")
        } else {
            downloads.groupBy { it.animeFolder?.takeIf { name -> name.isNotBlank() } ?: "Other downloads" }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .forEach { (folderName, episodes) ->
                    val expanded = folderName in expandedFolders
                    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                expandedFolders = if (expanded) expandedFolders - folderName else expandedFolders + folderName
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📁", fontSize = 20.sp)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(folderName, fontWeight = FontWeight.Bold)
                                Text(episodes.size.toString() + if (episodes.size == 1) " episode" else " episodes", color = Muted, fontSize = 11.sp)
                            }
                            Text(if (expanded) "⌄" else "›", fontSize = 22.sp, color = Muted)
                        }
                        if (expanded) episodes.forEach { item ->
                val statusText = when (item.status) {
                    DownloadManager.STATUS_PENDING -> "Queued"
                    DownloadManager.STATUS_RUNNING -> "Downloading"
                    DownloadManager.STATUS_PAUSED -> "Paused • ${pausedReasonText(item.reason)}"
                    DownloadManager.STATUS_SUCCESSFUL -> "Complete"
                    DownloadManager.STATUS_FAILED -> "Failed • ${downloadErrorText(item.reason)}"
                    else -> "Checking status"
                }
                val progress = if (item.totalBytes > 0) (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(15.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (item.totalBytes > 0) "\$statusText • ${(progress * 100).toInt()}% • ${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
                        else statusText + if (item.downloadedBytes > 0) " • ${formatBytes(item.downloadedBytes)}" else "",
                        color = Muted, fontSize = 11.sp
                    )
                    if (item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Violet)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
                            Button(
                                onClick = {
                                    runCatching {
                                        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                        manager.getUriForDownloadedFile(item.id)?.let { onPlay(it, item.title) }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Violet)
                            ) { Text("Watch offline") }
                            OutlinedButton(onClick = {
                                runCatching {
                                    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(item.id)
                                }
                                refreshToken++
                            }) { Text("Remove") }
                        } else if (
                            item.status == DownloadManager.STATUS_PENDING ||
                            item.status == DownloadManager.STATUS_RUNNING ||
                            item.status == DownloadManager.STATUS_PAUSED ||
                            item.status == DownloadManager.STATUS_FAILED
                        ) {
                            if (item.status == DownloadManager.STATUS_FAILED) {
                                Button(
                                    onClick = {
                                        runCatching { DownloadHelper.retry(context, item.id) }
                                        refreshToken++
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Violet)
                                ) { Text("Retry") }
                                OutlinedButton(onClick = {
                                    runCatching {
                                        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(item.id)
                                    }
                                    refreshToken++
                                }) { Text("Remove") }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(item.id)
                                        }
                                        refreshToken++
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Cancel download") }
                            }
                        }
                    }
                }
                        }
                    }
                }
        }
        Text("Notifications depend on Android notification settings. Only direct, accessible media files can be downloaded and played here.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "Unknown size"
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0)
    if (bytes < 1024L * 1024L * 1024L) return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    return String.format(java.util.Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun pausedReasonText(reason: Int): String = when (reason) {
    DownloadManager.PAUSED_WAITING_TO_RETRY -> "connection interrupted; Android will retry"
    DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "waiting for an allowed network"
    DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "waiting for Wi-Fi; check Wi-Fi-only setting"
    else -> "Android has paused the transfer (reason $reason)"
}

private fun downloadErrorText(reason: Int): String = when (reason) {
    DownloadManager.ERROR_CANNOT_RESUME -> "cannot resume"
    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "storage unavailable"
    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "file already exists"
    DownloadManager.ERROR_FILE_ERROR -> "file or storage error"
    DownloadManager.ERROR_HTTP_DATA_ERROR -> "network data error"
    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "not enough storage space"
    DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "too many redirects"
    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "server rejected the request"
    DownloadManager.ERROR_UNKNOWN -> "unknown download error"
    else -> "network or source error"
}
