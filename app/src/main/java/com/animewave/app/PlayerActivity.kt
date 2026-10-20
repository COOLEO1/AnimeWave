package com.animewave.app

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.Locale

/**
 * Native in-app player for a direct, accessible media URL supplied by the user/source.
 * This does not extract hidden stream URLs or bypass DRM.
 */
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var pendingDownload: Pair<Uri, String>? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingDownload
        pendingDownload = null
        if (!granted) Toast.makeText(this, "Notifications are off; the download will still be attempted.", Toast.LENGTH_LONG).show()
        pending?.let { enqueueDownloadNow(it.first, it.second) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rawUrl = intent.getStringExtra(EXTRA_MEDIA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "AnimeWave Player" }
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull()
        if (uri == null || !(
                (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank() ||
                    uri.scheme == "content" || uri.scheme == "file"
            )) {
            Toast.makeText(this, "This is not a valid direct video URL.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090A12)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            factory = { context ->
                                PlayerView(context).apply {
                                    player = exoPlayer
                                    useController = true
                                    controllerAutoShow = true
                                }
                            },
                            update = { it.player = exoPlayer }
                        )
                        Text(
                            "Playback controls are available on the video. Quality, audio and subtitle choices appear when the source provides those tracks.",
                            color = Color(0xFFAAAFC3),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = uri.scheme == "https" || uri.scheme == "http",
                                onClick = { enqueueDownload(uri, title) }
                            ) { Text("Download episode") }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { finish() }
                            ) { Text("Back") }
                        }
                        Text(
                            "Downloads are saved to AnimeWave's app-specific Downloads folder. Only use media you are authorized to download.",
                            color = Color(0xFFAAAFC3),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    private fun enqueueDownload(uri: Uri, title: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = uri to title
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        enqueueDownloadNow(uri, title)
    }

    private fun enqueueDownloadNow(uri: Uri, title: String) {
        runCatching {
            require(uri.scheme == "https" || uri.scheme == "http") { "Only direct HTTP(S) media links can be downloaded" }
            val fileName = safeFileName(title, uri)
            val request = DownloadManager.Request(uri)
                .setTitle(title)
                .setDescription("AnimeWave episode download")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            val preferences = getSharedPreferences("animewave_settings", Context.MODE_PRIVATE)
            if (preferences.getString("download_storage", "phone") == "app") {
                request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
            } else {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AnimeWave/$fileName")
            }
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "Download queued. Track progress in AnimeWave → Downloads.", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "Could not start download: ${it.message ?: "unsupported source"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun safeFileName(title: String, uri: Uri): String {
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
            ?.takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "mp4"
        val base = title.replace(Regex("[^A-Za-z0-9._ -]"), "").trim()
            .replace(Regex("\\s+"), "_").take(70).ifBlank { "anime_episode" }
        return "$base.$ext"
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MEDIA_URL = "animewave_media_url"
        const val EXTRA_TITLE = "animewave_media_title"
    }
}
