package com.animewave.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animewave.app.nyaa.NyaaRelease
import com.animewave.app.nyaa.NyaaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NyaaSearchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var releases by remember { mutableStateOf<List<NyaaRelease>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<NyaaRelease?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("NYAA RELEASES", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Find releases", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text("Search public index metadata. AnimeWave does not host the indexed files.", color = Muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Anime or release title") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(enabled = query.isNotBlank() && !loading, onClick = {
                val term = query.trim()
                loading = true
                searched = true
                error = null
                selected = null
                scope.launch {
                    try {
                        releases = withContext(Dispatchers.IO) { NyaaRepository().search(term) }
                    } catch (e: Exception) {
                        releases = emptyList()
                        error = e.message ?: "Search failed. Check your connection and try again."
                    } finally {
                        loading = false
                    }
                }
            }) { Text("Search") }
        }
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(color = Violet)
                Text("Searching Nyaa…", color = Muted)
            }
        }
        error?.let {
            Text(it, color = Color(0xFFFF9AAE), fontSize = 13.sp)
            TextButton(onClick = {
                if (query.isNotBlank() && !loading) {
                    val term = query.trim()
                    loading = true
                    error = null
                    scope.launch {
                        try { releases = withContext(Dispatchers.IO) { NyaaRepository().search(term) } }
                        catch (e: Exception) { error = e.message ?: "Search failed." }
                        finally { loading = false }
                    }
                }
            }) { Text("Retry", color = Violet) }
        }
        if (searched && !loading && error == null && releases.isEmpty()) {
            Text("No releases found", fontWeight = FontWeight.SemiBold)
            Text("Try the original title, an English title, or a shorter query.", color = Muted, fontSize = 12.sp)
        }
        releases.forEach { release ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(release.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Size: ${release.size ?: "—"}", color = Muted, fontSize = 11.sp)
                        Text("↑ ${release.seeders?.toString() ?: "—"}", color = Color(0xFF79D9A3), fontSize = 11.sp)
                        Text("↓ ${release.leechers?.toString() ?: "—"}", color = Muted, fontSize = 11.sp)
                    }
                    release.published?.let { Text(it, color = Muted, fontSize = 10.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { selected = release }) { Text("Release options") }
                        TextButton(onClick = { openExternal(context, release.pageUrl) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("View page")
                        }
                    }
                }
            }
        }
    }
    selected?.let { release ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Open release") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(release.title, fontWeight = FontWeight.SemiBold)
                    Text("Only continue for content you are authorized to download. The torrent client controls transfer progress.", color = Muted, fontSize = 12.sp)
                    if (release.magnetUri() == null) {
                        Text("This feed did not provide a usable info hash. Open the release page to inspect available options.", color = Color(0xFFFFD28A), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val magnet = release.magnetUri()
                    selected = null
                    if (magnet != null) openExternal(context, magnet) else openExternal(context, release.pageUrl)
                }) { Text(if (release.magnetUri() != null) "Open torrent client" else "Open release page") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } }
        )
    }
}

private fun openExternal(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No app can open this link. Install a compatible torrent client or browser.", android.widget.Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "Could not open this link.", android.widget.Toast.LENGTH_LONG).show()
    }
}
