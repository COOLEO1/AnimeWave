package com.animewave.app.downloads

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animewave.app.Muted
import com.animewave.app.Panel
import com.animewave.app.Violet
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.io.File
import java.util.Locale

private data class TorrentDownloadItem(
    val hash: String,
    val title: String,
    val paused: Boolean,
    val waitingForWifi: Boolean,
    val progress: Float,
    val downloaded: Long,
    val total: Long,
    val rate: Long,
    val complete: Boolean,
    val state: String
)

@Composable
internal fun TorrentDownloadsContent(onPlay: (Uri, String) -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<TorrentDownloadItem>>(emptyList()) }
    var videos by remember { mutableStateOf<List<File>>(emptyList()) }
    var engineError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            val prefs = context.getSharedPreferences(TorrentEngineService.PREFS, Context.MODE_PRIVATE)
            val snapshotJson = prefs.getString(TorrentEngineService.KEY_SNAPSHOTS, "[]").orEmpty()
            items = runCatching {
                val array = JSONArray(snapshotJson)
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val hash = item.optString("hash")
                    if (hash.isBlank()) return@mapNotNull null
                    TorrentDownloadItem(
                        hash = hash,
                        title = item.optString("title", "Torrent download"),
                        paused = item.optBoolean("paused"),
                        waitingForWifi = item.optBoolean("waitingForWifi"),
                        progress = item.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
                        downloaded = item.optLong("downloaded", 0L),
                        total = item.optLong("total", 0L),
                        rate = item.optLong("rate", 0L),
                        complete = item.optBoolean("complete"),
                        state = item.optString("state", "Connecting")
                    )
                }.sortedByDescending { it.complete.not() }
            }.getOrDefault(emptyList())
            engineError = prefs.getString(TorrentEngineService.KEY_ERROR, null)
            if (tick % 5 == 0) videos = findVideoFiles(context)
            tick++
            delay(1000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("BUILT-IN TORRENT ENGINE", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Text("Torrent downloads", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Downloads continue in the foreground service. Files are saved in AnimeWave's app-specific Downloads folder; no broad storage permission is needed.",
            color = Muted, fontSize = 11.sp
        )
        engineError?.let { Text("Engine: $it", color = Violet, fontSize = 12.sp) }
        if (items.isEmpty()) {
            Text("No torrent downloads yet", color = Muted, fontSize = 13.sp)
            Text("Open Nyaa, choose a release, then tap Download in AnimeWave.", color = Muted, fontSize = 11.sp)
        } else {
            items.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append(item.state)
                                if (item.total > 0L) {
                                    append(" • ")
                                    append((item.progress * 100).toInt())
                                    append("% • ")
                                    append(formatTorrentBytes(item.downloaded))
                                    append(" / ")
                                    append(formatTorrentBytes(item.total))
                                }
                                if (item.rate > 0L) {
                                    append(" • ")
                                    append(formatTorrentBytes(item.rate))
                                    append("/s")
                                }
                            },
                            color = Muted, fontSize = 10.sp
                        )
                        if (!item.complete) {
                            LinearProgressIndicator(
                                progress = { item.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Violet
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.complete) {
                                Text("Ready to watch", color = Violet, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            } else if (item.paused || item.waitingForWifi) {
                                Button(onClick = { TorrentEngineService.resume(context, item.hash) }, modifier = Modifier.weight(1f)) {
                                    Text(if (item.waitingForWifi) "Resume on current network" else "Resume")
                                }
                            } else {
                                OutlinedButton(onClick = { TorrentEngineService.pause(context, item.hash) }, modifier = Modifier.weight(1f)) {
                                    Text("Pause")
                                }
                            }
                            TextButton(onClick = { TorrentEngineService.remove(context, item.hash) }) {
                                Text("Remove torrent")
                            }
                        }
                    }
                }
            }
        }

        if (videos.isNotEmpty()) {
            Text("LOCAL VIDEO FILES", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text("Tap a file to open it in the AnimeWave player.", color = Muted, fontSize = 11.sp)
            videos.forEach { file ->
                Row(
                    modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(formatTorrentBytes(file.length()), color = Muted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onPlay(Uri.fromFile(file), file.name) }) { Text("Play") }
                }
            }
        }
        TextButton(onClick = { TorrentEngineService.refresh(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh torrent engine")
        }
    }
}

private fun findVideoFiles(context: Context): List<File> {
    val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
    val root = File(base, "AnimeWaveTorrents")
    if (!root.exists()) return emptyList()
    val extensions = setOf("mkv", "mp4", "avi", "webm", "mov", "m4v", "ts")
    return runCatching {
        root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in extensions && it.length() > 0L }
            .take(100)
            .toList()
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }.getOrDefault(emptyList())
}

private fun formatTorrentBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    if (bytes < 1024L * 1024L) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
