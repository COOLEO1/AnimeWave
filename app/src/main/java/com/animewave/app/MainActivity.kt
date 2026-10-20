package com.animewave.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animewave.app.source.AnimeApi
import com.animewave.app.source.AnimeEpisode
import com.animewave.app.source.AnimeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Night = Color(0xFF090A12)
private val Panel = Color(0xFF171925)
private val Panel2 = Color(0xFF202334)
private val Violet = Color(0xFF9B72FF)
private val Pink = Color(0xFFFF6DAE)
private val Muted = Color(0xFFAAAFC3)
private val api = AnimeApi()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AnimeWaveApp() }
    }
}

@Composable
private fun AnimeWaveApp() {
    var tab by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
    var selected by remember { mutableStateOf<AnimeItem?>(null) }
    var episodes by remember { mutableStateOf<List<AnimeEpisode>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var detailLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedIds by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun loadCatalog(search: String = "") {
        scope.launch {
            loading = true
            error = null
            try {
                catalog = withContext(Dispatchers.IO) {
                    if (search.isBlank()) api.trending() else api.search(search)
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not load the anime catalog."
            } finally {
                loading = false
            }
        }
    }
    fun openDetails(item: AnimeItem) {
        selected = item
        detailLoading = true
        scope.launch {
            episodes = withContext(Dispatchers.IO) {
                runCatching { api.episodes(item.id) }.getOrDefault(emptyList())
            }
            detailLoading = false
        }
    }

    LaunchedEffect(Unit) { loadCatalog() }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Night, surface = Panel, surfaceVariant = Panel2,
        primary = Violet, secondary = Pink, onBackground = Color.White,
        onSurface = Color.White, onSurfaceVariant = Muted
    )) {
        Scaffold(
            containerColor = Night,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF10121D), contentColor = Muted) {
                    listOf(
                        Triple("Home", Icons.Default.Home, "Home"),
                        Triple("Discover", Icons.Default.Explore, "Discover"),
                        Triple("Saved", Icons.Default.Favorite, "Saved"),
                        Triple("Downloads", Icons.Default.Download, "Downloads")
                    ).forEach { (label, icon, route) ->
                        NavigationBarItem(
                            selected = tab == route,
                            onClick = { tab = route; selected = null },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Violet, selectedTextColor = Color.White,
                                indicatorColor = Color(0xFF34244F),
                                unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).background(
                            Brush.linearGradient(listOf(Violet, Pink)), RoundedCornerShape(15.dp)
                        ), contentAlignment = Alignment.Center
                    ) { Text("波", fontSize = 25.sp, fontWeight = FontWeight.Black) }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AnimeWave", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text("YOUR ANIME UNIVERSE", fontSize = 9.sp, color = Muted, letterSpacing = 2.sp)
                    }
                    Text("LIVE CATALOG", fontSize = 9.sp, color = Violet, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search anime and series…", color = Muted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Muted) },
                    trailingIcon = {
                        TextButton(onClick = { loadCatalog(query.trim()); tab = "Discover" }) {
                            Text("Search", color = Violet)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(17.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFF303246), focusedBorderColor = Violet,
                        unfocusedContainerColor = Panel, focusedContainerColor = Panel
                    )
                )
                Spacer(Modifier.height(20.dp))

                if (selected != null) {
                    DetailContent(
                        item = selected!!, episodes = episodes, loading = detailLoading,
                        saved = selected!!.id in savedIds,
                        onBack = { selected = null },
                        onSave = {
                            savedIds = if (selected!!.id in savedIds) savedIds - selected!!.id
                                else savedIds + selected!!.id
                        },
                        onOpenSource = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cartoonsarea.cc")))
                        }
                    )
                } else when (tab) {
                    "Saved" -> {
                        SectionHeading("YOUR WATCHLIST", "Saved anime")
                        val saved = catalog.filter { it.id in savedIds }
                        if (saved.isEmpty()) EmptyState("Your list is waiting", "Tap the heart on an anime to save it.")
                        else AnimeRow(saved, ::openDetails)
                    }
                    "Downloads" -> {
                        SectionHeading("OFFLINE", "Downloads")
                        EmptyState("No downloads yet", "Episode downloads need an authorized direct media source. AnimeWave is connected to live catalog metadata first.")
                    }
                    else -> {
                        if (tab == "Home" && catalog.isNotEmpty()) {
                            HeroBanner(catalog.first(), ::openDetails)
                            Spacer(Modifier.height(26.dp))
                        }
                        SectionHeading(
                            if (tab == "Discover") "FIND YOUR NEXT SERIES" else "TRENDING NOW",
                            if (query.isBlank()) "Popular anime" else "Search results"
                        )
                        when {
                            loading -> LoadingState()
                            error != null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Couldn't load the catalog", color = Color.White, fontWeight = FontWeight.Bold)
                                Text(error ?: "", color = Muted, fontSize = 12.sp)
                                Button(onClick = { loadCatalog(query.trim()) }) { Text("Try again") }
                            }
                            catalog.isEmpty() -> EmptyState("No anime found", "Try another title or spelling.")
                            else -> AnimeRow(catalog, ::openDetails)
                        }
                        Spacer(Modifier.height(24.dp))
                        if (tab == "Home") {
                            SectionHeading("EXPLORE", "Search by theme")
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GenreChip("Action") { query = "action"; loadCatalog("action"); tab = "Discover" }
                                GenreChip("Fantasy") { query = "fantasy"; loadCatalog("fantasy"); tab = "Discover" }
                                GenreChip("Adventure") { query = "adventure"; loadCatalog("adventure"); tab = "Discover" }
                            }
                            Spacer(Modifier.height(24.dp))
                            Text("Artwork and catalog metadata by Kitsu. Playback depends on authorized providers.", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
            }
        }
    }
}

@Composable
private fun HeroBanner(item: AnimeItem, onClick: (AnimeItem) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF392452), Color(0xFF151622))))
            .clickable { onClick(item) }
    ) {
        AsyncImage(model = item.coverUrl ?: item.posterUrl, contentDescription = item.title,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0090A12)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text("FEATURED SERIES", color = Pink, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(item.title, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(listOfNotNull(item.subtype, item.year, item.episodeCount?.let { it.toString() + " episodes" }).joinToString(" • "), color = Color(0xFFE2DDED), fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onClick(item) }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text("Explore series") }
        }
    }
}

@Composable
private fun SectionHeading(eyebrow: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(eyebrow, color = Violet, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun AnimeRow(items: List<AnimeItem>, onClick: (AnimeItem) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(end = 4.dp)) {
        items(items, key = { it.id }) { item ->
            Column(Modifier.width(148.dp).clickable { onClick(item) }) {
                Box(Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(16.dp)).background(Panel2)) {
                    AsyncImage(model = item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
private fun DetailContent(
    item: AnimeItem, episodes: List<AnimeEpisode>, loading: Boolean, saved: Boolean,
    onBack: () -> Unit, onSave: () -> Unit, onOpenSource: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text("‹  Back to AnimeWave", color = Violet, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 5.dp))
        Box(Modifier.fillMaxWidth().height(265.dp).clip(RoundedCornerShape(22.dp)).background(Panel2)) {
            AsyncImage(model = item.coverUrl ?: item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
        Button(onClick = onOpenSource, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
            Text("Open streaming website")
        }
        Text("Episodes", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        when {
            loading -> LoadingState()
            episodes.isEmpty() -> Text("Episode listing isn't available for this title yet.", color = Muted, fontSize = 12.sp)
            else -> episodes.forEach { ep ->
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(13.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Text("Episode metadata comes from Kitsu. The website button opens the source in your browser; in-app playback and downloads need a verified, authorized media URL.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun GenreChip(label: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = Panel2, modifier = Modifier.clickable(onClick = onClick)) {
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

@Composable
private fun LoadingState() {
    Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = Violet, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("Loading anime…", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
