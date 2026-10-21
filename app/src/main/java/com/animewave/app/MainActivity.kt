package com.animewave.app

import android.app.DownloadManager
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.animewave.app.source.AnimeApi
import com.animewave.app.downloads.DownloadHelper
import com.animewave.app.source.AnimeEpisode
import com.animewave.app.source.AnimeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Night = Color(0xFF090A12)
internal val Panel = Color(0xFF171925)
internal val Panel2 = Color(0xFF202334)
internal val Violet = Color(0xFF9B72FF)
internal val Pink = Color(0xFFFF6DAE)
internal val Muted = Color(0xFFAAAFC3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AnimeWaveApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimeWaveApp() {
    var tab by rememberSaveable { mutableStateOf("Home") }
    var query by rememberSaveable { mutableStateOf("") }
    var catalog by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
    var catalogPage by remember { mutableIntStateOf(1) }
    var hasMoreCatalog by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<AnimeItem?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var episodes by remember { mutableStateOf<List<AnimeEpisode>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var detailLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("animewave_settings", android.content.Context.MODE_PRIVATE) }
    var savedIds by remember { mutableStateOf(preferences.getStringSet("saved_anime_ids", emptySet())?.toSet() ?: emptySet()) }
    var tmdbKey by remember { mutableStateOf(preferences.getString("tmdb_api_key", "").orEmpty()) }
    var draftTmdbKey by remember(tmdbKey) { mutableStateOf(tmdbKey) }
    val api = remember(tmdbKey) { AnimeApi(tmdbKey.trim()) }

    fun loadCatalog(search: String = "", page: Int = 1, append: Boolean = false) {
        scope.launch {
            loading = true
            error = null
            try {
                val fetched = withContext(Dispatchers.IO) {
                    if (search.isBlank()) api.trending(page) else api.search(search, page)
                }
                catalog = if (append) {
                    (catalog + fetched).distinctBy { it.id }
                } else fetched
                catalogPage = page
                hasMoreCatalog = fetched.size >= if (tmdbKey.isBlank()) 24 else 20
                preferences.edit().putString("catalog_cache_json", encodeCatalog(catalog)).apply()
            } catch (e: Exception) {
                error = e.message ?: "Could not load the anime catalog."
            } finally {
                loading = false
            }
        }
    }
    fun openDetails(item: AnimeItem) {
        selected = item
        selectedId = item.id
    }
    fun closeDetails() {
        selected = null
        selectedId = null
        episodes = emptyList()
    }

    LaunchedEffect(Unit) {
        catalog = decodeCatalog(preferences.getString("catalog_cache_json", null))
        loadCatalog()
    }

    LaunchedEffect(selectedId, catalog) {
        val id = selectedId
        if (id != null && selected?.id != id) {
            selected = catalog.firstOrNull { it.id == id }
        }
    }

    LaunchedEffect(selected?.id, api) {
        val item = selected ?: return@LaunchedEffect
        detailLoading = true
        episodes = withContext(Dispatchers.IO) {
            runCatching { api.episodes(item.id) }.getOrDefault(emptyList())
        }
        detailLoading = false
    }

    BackHandler(enabled = selected != null || selectedId != null) { closeDetails() }

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
                        Triple("Downloads", Icons.Default.Download, "Downloads"),
                        Triple("Settings", Icons.Default.Settings, "Settings")
                    ).forEach { (label, icon, route) ->
                        NavigationBarItem(
                            selected = tab == route,
                            onClick = { tab = route; closeDetails() },
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
            PullToRefreshBox(
                isRefreshing = loading && catalog.isNotEmpty(),
                onRefresh = {
                    if (tab == "Home" || tab == "Discover") loadCatalog(query.trim())
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
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
                        onBack = { closeDetails() },
                        onSave = {
                            val id = selected!!.id
                            val updated = if (id in savedIds) savedIds - id else savedIds + id
                            savedIds = updated
                            preferences.edit().putStringSet("saved_anime_ids", updated).apply()
                        },
                        onOpenSource = { sourceQuery ->
                            context.startActivity(
                                Intent(context, com.animewave.app.source.CartoonsAreaBrowserActivity::class.java)
                                    .putExtra(
                                        com.animewave.app.source.CartoonsAreaBrowserActivity.EXTRA_URL,
                                        "https://www.cartoonsarea.cc/"
                                    )
                                    .putExtra(
                                        com.animewave.app.source.CartoonsAreaBrowserActivity.EXTRA_QUERY,
                                        sourceQuery
                                    )
                            )
                        }
                    )
                } else when (tab) {
                    "Settings" -> SettingsContent(
                        tmdbKey = draftTmdbKey,
                        onKeyChange = { draftTmdbKey = it },
                        onSave = {
                            val cleaned = draftTmdbKey.trim()
                            preferences.edit().putString("tmdb_api_key", cleaned).apply()
                            tmdbKey = cleaned
                            tab = "Home"
                            closeDetails()
                            scope.launch {
                                loading = true
                                error = null
                                try {
                                    catalog = withContext(Dispatchers.IO) { AnimeApi(cleaned).trending() }
                                } catch (e: Exception) {
                                    error = e.message ?: "Could not load the anime catalog."
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        onGetKey = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org/settings/api")))
                        },
                        onClearKey = {
                            draftTmdbKey = ""
                            preferences.edit().remove("tmdb_api_key").apply()
                            tmdbKey = ""
                            scope.launch {
                                loading = true
                                error = null
                                try {
                                    catalog = withContext(Dispatchers.IO) { AnimeApi().trending() }
                                } catch (e: Exception) {
                                    error = e.message ?: "Could not load the anime catalog."
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    )
                    "Saved" -> {
                        SectionHeading("YOUR WATCHLIST", "Saved anime")
                        val saved = catalog.filter { it.id in savedIds }
                        if (saved.isEmpty()) EmptyState("Your list is waiting", "Tap the heart on an anime to save it.")
                        else AnimeRow(saved, ::openDetails)
                    }
                    "Downloads" -> {
                        DownloadsContent(
                            preferences = preferences,
                            onPlay = { uri, title ->
                                context.startActivity(
                                    Intent(context, PlayerActivity::class.java)
                                        .putExtra(PlayerActivity.EXTRA_MEDIA_URL, uri.toString())
                                        .putExtra(PlayerActivity.EXTRA_TITLE, title)
                                )
                            }
                        )
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
                            else -> {
                                AnimeRow(catalog, ::openDetails)
                                if (hasMoreCatalog) {
                                    TextButton(
                                        onClick = { loadCatalog(query.trim(), catalogPage + 1, append = true) },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !loading
                                    ) { Text(if (loading) "Loading more…" else "Load more anime") }
                                }
                            }
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
}

private fun encodeCatalog(items: List<AnimeItem>): String {
    val array = org.json.JSONArray()
    items.forEach { item ->
        array.put(org.json.JSONObject()
            .put("id", item.id)
            .put("title", item.title)
            .put("synopsis", item.synopsis)
            .put("posterUrl", item.posterUrl ?: "")
            .put("coverUrl", item.coverUrl ?: "")
            .put("episodeCount", item.episodeCount ?: -1)
            .put("year", item.year ?: "")
            .put("subtype", item.subtype ?: "")
            .put("averageRating", item.averageRating ?: "")
        )
    }
    return array.toString()
}

private fun decodeCatalog(json: String?): List<AnimeItem> = runCatching {
    val array = org.json.JSONArray(json.orEmpty())
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id")
        val title = item.optString("title")
        if (id.isBlank() || title.isBlank()) return@mapNotNull null
        AnimeItem(
            id = id,
            title = title,
            synopsis = item.optString("synopsis"),
            posterUrl = item.optString("posterUrl").takeIf { it.startsWith("https://") },
            coverUrl = item.optString("coverUrl").takeIf { it.startsWith("https://") },
            episodeCount = item.optInt("episodeCount", -1).takeIf { it > 0 },
            year = item.optString("year").takeIf { it.isNotBlank() },
            subtype = item.optString("subtype").takeIf { it.isNotBlank() },
            averageRating = item.optString("averageRating").takeIf { it.isNotBlank() }
        )
    }
}.getOrDefault(emptyList())
