package com.animewave.app.source

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AnimeEpisode(val id: String, val number: Int?, val title: String, val airdate: String?)

/** Kitsu provides anime metadata/artwork, not playable video streams. */
class AnimeApi {
    private val base = "https://kitsu.io/api/edge"
    fun trending(): List<AnimeItem> = list("/anime?sort=-userCount&page%5Blimit%5D=24")
    fun search(query: String): List<AnimeItem> {
        val q = URLEncoder.encode(query, "UTF-8")
        return list("/anime?filter%5Btext%5D=$q&sort=-userCount&page%5Blimit%5D=24")
    }
    fun episodes(id: String): List<AnimeEpisode> {
        val data = request("/anime/$id/episodes?page%5Blimit%5D=20&sort=number").optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val row = data.optJSONObject(i) ?: return@mapNotNull null
            val a = row.optJSONObject("attributes") ?: JSONObject()
            AnimeEpisode(
                row.optString("id", i.toString()),
                if (a.isNull("number")) null else a.optInt("number"),
                a.optString("canonicalTitle").ifBlank { a.optJSONObject("titles")?.optString("en").orEmpty() },
                a.optString("airdate").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
    private fun list(path: String): List<AnimeItem> {
        val data = request(path).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val row = data.optJSONObject(i) ?: return@mapNotNull null
            val a = row.optJSONObject("attributes") ?: JSONObject()
            val poster = a.optJSONObject("posterImage")
            val cover = a.optJSONObject("coverImage")
            val date = a.optString("startDate")
            val rating = a.optString("averageRating").toDoubleOrNull()?.let {
                String.format(java.util.Locale.US, "%.1f", it / 10.0)
            }
            val title = a.optJSONObject("titles")?.optString("en")?.takeIf { it.isNotBlank() } ?: a.optString("canonicalTitle")
            AnimeItem(
                id = row.optString("id"), title = title,
                synopsis = a.optString("synopsis").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
                posterUrl = poster?.optString("large")?.takeIf { it.startsWith("https://") }
                    ?: poster?.optString("original")?.takeIf { it.startsWith("https://") },
                coverUrl = cover?.optString("large")?.takeIf { it.startsWith("https://") }
                    ?: cover?.optString("original")?.takeIf { it.startsWith("https://") },
                episodeCount = if (a.isNull("episodeCount")) null else a.optInt("episodeCount").takeIf { it > 0 },
                year = date.takeIf { it.length >= 4 }?.take(4),
                subtype = a.optString("subtype").takeIf { it.isNotBlank() && it != "null" },
                averageRating = rating
            ).takeIf { it.id.isNotBlank() && it.title.isNotBlank() }
        }
    }
    private fun request(path: String): JSONObject {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = "GET"
        c.connectTimeout = 12000
        c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/vnd.api+json")
        c.setRequestProperty("User-Agent", "AnimeWave-Android/0.2")
        return try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Catalog service returned HTTP $code")
            JSONObject(body)
        } finally { c.disconnect() }
    }
}
