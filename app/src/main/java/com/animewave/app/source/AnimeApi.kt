package com.animewave.app.source

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class AnimeEpisode(val id: String, val number: Int?, val title: String, val airdate: String?)

/**
 * Kitsu works without a key. If the user supplies a free TMDB API key/read token in Settings,
 * TMDB becomes the catalog provider. Both APIs provide metadata/artwork, not playable video.
 */
class AnimeApi(private val tmdbKey: String = "") {
    private val kitsuBase = "https://kitsu.io/api/edge"
    private val hasTmdb = tmdbKey.isNotBlank()

    fun trending(page: Int = 1): List<AnimeItem> = if (hasTmdb) tmdbList(
        "/discover/tv",
        mapOf(
            "with_genres" to "16",
            "with_origin_country" to "JP",
            "sort_by" to "popularity.desc",
            "include_null_first_air_dates" to "false",
            "page" to page.coerceAtLeast(1).toString()
        )
    ) else kitsuList(
        "/anime?sort=-userCount&page%5Blimit%5D=24&page%5Boffset%5D=${(page - 1).coerceAtLeast(0) * 24}"
    )

    fun search(query: String, page: Int = 1): List<AnimeItem> = if (hasTmdb) {
        tmdbList("/search/tv", mapOf(
            "query" to query,
            "include_adult" to "false",
            "page" to page.coerceAtLeast(1).toString()
        )).filter { it.subtype?.contains("animation", ignoreCase = true) == true ||
            it.title.isNotBlank() }
    } else {
        val q = Uri.encode(query)
        kitsuList(
            "/anime?filter%5Btext%5D=$q&sort=-userCount&page%5Blimit%5D=24&page%5Boffset%5D=${(page - 1).coerceAtLeast(0) * 24}"
        )
    }

    fun episodes(id: String): List<AnimeEpisode> {
        if (id.startsWith("tmdbtv:")) {
            val tvId = id.removePrefix("tmdbtv:")
            val show = tmdbRequest("/tv/$tvId")
            val seasons = show.optJSONArray("seasons")
            if (seasons != null) {
                for (i in 0 until seasons.length()) {
                    val season = seasons.optJSONObject(i) ?: continue
                    val seasonNumber = season.optInt("season_number", -1)
                    if (seasonNumber < 0) continue
                    val data = tmdbRequest("/tv/$tvId/season/$seasonNumber").optJSONArray("episodes")
                        ?: continue
                    val parsed = (0 until data.length()).mapNotNull { j ->
                        val ep = data.optJSONObject(j) ?: return@mapNotNull null
                        AnimeEpisode(
                            id = ep.optString("id", j.toString()),
                            number = if (ep.isNull("episode_number")) null else ep.optInt("episode_number"),
                            title = ep.optString("name", "Episode"),
                            airdate = ep.optString("air_date").takeIf { it.isNotBlank() && it != "null" }
                        )
                    }
                    if (parsed.isNotEmpty()) return parsed
                }
            }
            return emptyList()
        }
        val data = kitsuRequest("/anime/$id/episodes?page%5Blimit%5D=20&sort=number")
            .optJSONArray("data") ?: return emptyList()
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

    private fun kitsuList(path: String): List<AnimeItem> {
        val data = kitsuRequest(path).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val row = data.optJSONObject(i) ?: return@mapNotNull null
            val a = row.optJSONObject("attributes") ?: JSONObject()
            val poster = a.optJSONObject("posterImage")
            val cover = a.optJSONObject("coverImage")
            val date = a.optString("startDate")
            val rating = a.optString("averageRating").toDoubleOrNull()?.let {
                String.format(Locale.US, "%.1f", it / 10.0)
            }
            val title = a.optJSONObject("titles")?.optString("en")?.takeIf { it.isNotBlank() }
                ?: a.optString("canonicalTitle")
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

    private fun tmdbList(path: String, query: Map<String, String>): List<AnimeItem> {
        val data = tmdbRequest(path, query).optJSONArray("results") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val row = data.optJSONObject(i) ?: return@mapNotNull null
            if (path == "/search/tv") {
                if (row.optString("original_language") != "ja") return@mapNotNull null
                val genres = row.optJSONArray("genre_ids")
                if (genres == null || (0 until genres.length()).none { genres.optInt(it) == 16 }) return@mapNotNull null
            }
            val date = row.optString("first_air_date")
            val poster = row.optString("poster_path")
            val backdrop = row.optString("backdrop_path")
            val rating = row.optDouble("vote_average", 0.0).takeIf { it > 0.0 }
                ?.let { String.format(Locale.US, "%.1f", it) }
            val title = row.optString("name").ifBlank { row.optString("original_name") }
            AnimeItem(
                id = "tmdbtv:" + row.optString("id"),
                title = title,
                synopsis = row.optString("overview").takeIf { it != "null" }.orEmpty(),
                posterUrl = poster.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w500$it" },
                coverUrl = backdrop.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w780$it" },
                year = date.takeIf { it.length >= 4 }?.take(4),
                subtype = "TV anime",
                averageRating = rating
            ).takeIf { it.id != "tmdbtv:" && it.title.isNotBlank() }
        }
    }

    private fun kitsuRequest(path: String): JSONObject = request(kitsuBase + path, mapOf(
        "Accept" to "application/vnd.api+json",
        "User-Agent" to "AnimeWave-Android/0.3"
    ))

    private fun tmdbRequest(path: String, query: Map<String, String> = emptyMap()): JSONObject {
        val builder = Uri.parse("https://api.themoviedb.org/3$path").buildUpon()
            .appendQueryParameter("language", "en-US")
        query.forEach { (key, value) -> builder.appendQueryParameter(key, value) }
        val headers = if (tmdbKey.length > 40) mapOf("Authorization" to "Bearer $tmdbKey") else emptyMap()
        if (tmdbKey.length <= 40) builder.appendQueryParameter("api_key", tmdbKey)
        return request(builder.build().toString(), headers)
    }

    private fun request(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                if (url.contains("api.themoviedb.org")) error("TMDB returned HTTP $code. Check the API key in Settings.")
                error("Anime catalog returned HTTP $code")
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
