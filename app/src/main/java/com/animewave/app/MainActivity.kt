package com.animewave.app

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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF10091D)
private val Panel = Color(0xFF201332)
private val Violet = Color(0xFF9A63FF)
private val Muted = Color(0xFFB8ADC9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AnimeWaveApp() }
    }
}

private data class Show(val title: String, val genre: String, val color: Color)
private val demos = listOf(
    Show("Crimson Eclipse", "ACTION • FANTASY", Color(0xFF713D91)),
    Show("Neon Ronin", "SCI-FI • ADVENTURE", Color(0xFF315B87)),
    Show("Moonlit Academy", "DRAMA • MYSTERY", Color(0xFF8B466C)),
    Show("Skybound", "ADVENTURE • MAGIC", Color(0xFF3D786E))
)

@Composable
private fun AnimeWaveApp() {
    var tab by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Show?>(null) }
    MaterialTheme(colorScheme = darkColorScheme(
        background = Night, surface = Panel, primary = Violet,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(
            containerColor = Night,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF170F24), contentColor = Muted) {
                    listOf(
                        Triple("Home", Icons.Default.Home, "Home"),
                        Triple("Discover", Icons.Default.Explore, "Discover"),
                        Triple("Downloads", Icons.Default.Download, "Downloads"),
                        Triple("Library", Icons.Default.VideoLibrary, "Library")
                    ).forEach { (label, icon, route) ->
                        NavigationBarItem(
                            selected = tab == route,
                            onClick = { tab = route; selected = null },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Violet,
                                selectedTextColor = Color.White,
                                indicatorColor = Color(0xFF39234F),
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).background(
                            Brush.linearGradient(listOf(Violet, Color(0xFFED68B7))),
                            RoundedCornerShape(14.dp)
                        ), contentAlignment = Alignment.Center
                    ) { Text("波", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White) }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AnimeWave", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        Text("YOUR NEXT OBSESSION", fontSize = 9.sp, color = Muted, letterSpacing = 2.sp)
                    }
                    Text("●", color = Violet, fontSize = 18.sp)
                }
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search anime, genres...", color = Muted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFF392849),
                        focusedBorderColor = Violet,
                        unfocusedContainerColor = Panel,
                        focusedContainerColor = Panel
                    )
                )
                Spacer(Modifier.height(24.dp))
                if (selected != null) {
                    Text("SHOW PREVIEW", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    PosterCard(selected!!, Modifier.fillMaxWidth().height(230.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(selected!!.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Demo preview only — connect a licensed provider to enable episodes.", color = Muted)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { selected = null }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                        Text("Back to browse")
                    }
                } else if (tab == "Downloads" || tab == "Library") {
                    Text(tab, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(if (tab == "Downloads") "Your offline queue will appear here once an authorized download provider is configured." else "Save shows you love to find them here.", color = Muted)
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.fillMaxWidth().height(160.dp).background(Panel, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                        Text(if (tab == "Downloads") "No downloads yet" else "Your library is waiting", color = Muted)
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(218.dp).background(
                            Brush.linearGradient(listOf(Color(0xFF442363), Color(0xFF171027), Color(0xFF7C376D))),
                            RoundedCornerShape(26.dp)
                        ).padding(22.dp)
                    ) {
                        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.8f)) {
                            Text("THE UNIVERSE\nOF ANIME", fontSize = 10.sp, color = Color(0xFFE1C8FF), letterSpacing = 2.sp)
                            Spacer(Modifier.height(10.dp))
                            Text("Find your\nnext world.", fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(13.dp))
                            Text("Discover stories worth staying up for.", fontSize = 12.sp, color = Color(0xFFE2D8ED))
                        }
                        Text("✦", Modifier.align(Alignment.TopEnd), color = Color(0xFFFFC6F2), fontSize = 34.sp)
                    }
                    Spacer(Modifier.height(26.dp))
                    Text(if (tab == "Discover") "EXPLORE THE CATALOG" else "TRENDING NOW", fontSize = 11.sp, color = Violet, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(7.dp))
                    Text(if (query.isBlank()) "Made for your watchlist" else "Search results", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    val shown = demos.filter { it.title.contains(query, true) || it.genre.contains(query, true) }
                    if (shown.isEmpty()) {
                        Text("No demo matches. A real catalog provider can power search here.", color = Muted)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(shown) { show ->
                                Column(Modifier.width(156.dp).clickable { selected = show }) {
                                    PosterCard(show, Modifier.fillMaxWidth().height(190.dp))
                                    Spacer(Modifier.height(9.dp))
                                    Text(show.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(Modifier.height(3.dp))
                                    Text(show.genre, color = Muted, fontSize = 9.sp, letterSpacing = 0.5.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("BUILT FOR YOUR NEXT BINGE", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FeaturePill("✦  Curated", Modifier.weight(1f))
                        FeaturePill("◉  Dark mode", Modifier.weight(1f))
                        FeaturePill("↓  Offline-ready", Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text("AnimeWave • A community-built starter", color = Color(0xFF746781), fontSize = 10.sp)
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun PosterCard(show: Show, modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.linearGradient(listOf(show.color, Color(0xFF181021), show.color.copy(alpha = 0.75f))),
            RoundedCornerShape(20.dp)
        ).padding(14.dp)
    ) {
        Text("✦", Modifier.align(Alignment.TopEnd), color = Color.White.copy(alpha = 0.75f), fontSize = 22.sp)
        Column(Modifier.align(Alignment.BottomStart)) {
            Text("ANIMEWAVE ORIGINAL", color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(4.dp))
            Text(show.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun FeaturePill(text: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Panel, RoundedCornerShape(12.dp)).padding(vertical = 13.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, fontSize = 10.sp)
    }
}
