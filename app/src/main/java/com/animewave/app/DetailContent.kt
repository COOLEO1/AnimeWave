package com.animewave.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animewave.app.source.AnimeEpisode
import com.animewave.app.source.AnimeItem

@Composable
internal fun DetailContent(
    item: AnimeItem, episodes: List<AnimeEpisode>, loading: Boolean, saved: Boolean,
    onBack: () -> Unit, onSave: () -> Unit, onOpenSource: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text("‹  Back to AnimeWave", color = Violet, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 5.dp))
        Box(Modifier.fillMaxWidth().height(265.dp).clip(RoundedCornerShape(22.dp)).background(Panel2)) {
            AsyncImage(model = posterRequest(item.coverUrl ?: item.posterUrl, LocalContext.current), contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE090A12)))))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold)
                Text(listOfNotNull(item.year, item.subtype, item.episodeCount?.let { it.toString() + " episodes" }).joinToString(" • "), color = Muted, fontSize = 11.sp)
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Default.Favorite, contentDescription = "Save anime", tint = if (saved) Pink else Muted)
            }
        }
        if (item.synopsis.isNotBlank()) Text(item.synopsis, color = Color(0xFFD3D5E1), fontSize = 13.sp, lineHeight = 20.sp)
        Button(onClick = { onOpenSource(item.title) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
            Text("Find on CartoonsArea")
        }
        Text("Episodes", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        when {
            loading -> LoadingState()
            episodes.isEmpty() -> Text("Episode listing isn't available for this title yet.", color = Muted, fontSize = 12.sp)
            else -> episodes.forEach { ep ->
                Row(
                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(13.dp))
                        .clickable { onOpenSource(item.title + " episode " + (ep.number?.toString() ?: ep.title)) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).background(Panel2, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Text(ep.number?.toString() ?: "•", color = Violet, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ep.title.ifBlank { "Episode " + (ep.number?.toString() ?: "") }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        ep.airdate?.let { Text(it, color = Muted, fontSize = 10.sp) }
                    }
                    Text("EP", color = Muted, fontSize = 10.sp)
                }
            }
        }
        Text("Tap an episode to search CartoonsArea inside AnimeWave. Playback and downloads depend on the source offering media you are authorized to access.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}
