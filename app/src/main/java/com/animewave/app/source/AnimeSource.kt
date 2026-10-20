package com.animewave.app.source

/** Plug in a catalog provider you own or are licensed to use. */
interface AnimeSource {
    suspend fun search(query: String): List<AnimeItem>
    suspend fun trending(): List<AnimeItem>
}

data class AnimeItem(
    val id: String,
    val title: String,
    val synopsis: String = "",
    val posterUrl: String? = null,
    val coverUrl: String? = null,
    val episodeCount: Int? = null,
    val year: String? = null,
    val subtype: String? = null,
    val averageRating: String? = null
)
