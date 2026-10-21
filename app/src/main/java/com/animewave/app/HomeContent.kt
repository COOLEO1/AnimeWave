package com.animewave.app

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.animewave.app.source.AnimeItem

@Composable
internal fun HeroBanner(item: AnimeItem, onClick: (AnimeItem) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF392452), Color(0xFF151622))))
            .clickable { onClick(item) }
    ) {
        AsyncImage(model = posterRequest(item.coverUrl ?: item.posterUrl, LocalContext.current), contentDescription = item.title,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0090A12)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text("FEATURED SERIES", color = Pink, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(item.title, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(listOfNotNull(item.subtype, item.year, item.episodeCount?.let { it.toString() + " episodes" }).joinToString(" • "), color = Color(0xFFE2DDED), fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onClick(item) }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text("Explore series") }
        }
    }
}

@Composable
internal fun SectionHeading(eyebrow: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(eyebrow, color = Violet, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
internal fun AnimeRow(animeItems: List<AnimeItem>, onClick: (AnimeItem) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(end = 4.dp)) {
        items(animeItems, key = { it.id }) { item ->
            Column(Modifier.width(148.dp).clickable { onClick(item) }) {
                Box(Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(16.dp)).background(Panel2)) {
                    AsyncImage(model = posterRequest(item.posterUrl, LocalContext.current), contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD090A12)))).padding(9.dp)) {
                        Text(item.averageRating?.let { "★ " + it } ?: item.subtype.orEmpty(), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.year ?: "Anime series", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
internal fun GenreChip(label: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = Panel2, modifier = Modifier.clickable(onClick = onClick)) {
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

@Composable
internal fun LoadingState() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Finding your next anime…", color = Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Column(Modifier.width(132.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(178.dp).clip(RoundedCornerShape(16.dp)).background(Panel2))
                    Box(Modifier.width(104.dp).height(11.dp).clip(RoundedCornerShape(6.dp)).background(Panel2))
                    Box(Modifier.width(66.dp).height(9.dp).clip(RoundedCornerShape(6.dp)).background(Panel))
                }
            }
        }
    }
}

@Composable
internal fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}


@Composable
internal fun SettingsContent(
    tmdbKey: String,
    onKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onGetKey: () -> Unit,
    onClearKey: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("CATALOG PROVIDERS", color = Violet, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text("AnimeWave works without an API key using Kitsu. Add your free TMDB API key or read access token to switch to TMDB anime-series metadata and artwork.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        OutlinedTextField(
            value = tmdbKey,
            onValueChange = onKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("TMDB API key or read token") },
            placeholder = { Text("Paste your free TMDB credential") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFF303246),
                focusedBorderColor = Violet,
                unfocusedContainerColor = Panel,
                focusedContainerColor = Panel
            )
        )
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
            Text("Save key and refresh catalog")
        }
        OutlinedButton(onClick = onGetKey, modifier = Modifier.fillMaxWidth()) {
            Text("Get a free TMDB API key")
        }
        TextButton(onClick = onClearKey, modifier = Modifier.fillMaxWidth()) {
            Text("Clear key and use Kitsu instead", color = Pink)
        }
        Divider(color = Color(0xFF303246))
        Text("YOUR PRIVACY", fontSize = 10.sp, letterSpacing = 2.sp, color = Violet, fontWeight = FontWeight.Bold)
        Text("Your API key is saved in this app's local preferences on this device. Catalog searches are sent to the provider you choose. Never paste a private or paid account password here.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Text("TMDB provides catalog metadata and artwork, not video streams. Kitsu is the automatic no-key option. Neither provider grants AnimeWave access to CartoonsArea's protected media.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Text("AnimeWave • built for anime discovery", color = Muted, fontSize = 10.sp)
    }
}

internal fun posterRequest(url: String?, context: Context): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .crossfade(true)
        .placeholder(ColorDrawable(0xFF202334.toInt()))
        .error(ColorDrawable(0xFF392452.toInt()))
        .build()
