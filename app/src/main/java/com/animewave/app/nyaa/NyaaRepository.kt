package com.animewave.app.nyaa

import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL

/** Reads public Nyaa RSS search metadata. Invoke search() from a background dispatcher. */
class NyaaRepository {
    fun search(query: String): List<NyaaRelease> {
        require(query.isNotBlank()) { "Enter an anime or release title." }
        val feedUrl = Uri.parse("https://nyaa.si/").buildUpon()
            .appendQueryParameter("page", "rss")
            .appendQueryParameter("q", query.trim())
            .appendQueryParameter("c", "1_0")
            .appendQueryParameter("f", "0")
            .build().toString()
        val connection = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AnimeWave/0.1 (Android RSS reader)")
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Nyaa returned HTTP $status. Try again later.")
            connection.inputStream.buffered().use { input ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(input, "UTF-8")
                val results = mutableListOf<NyaaRelease>()
                var inItem = false
                var currentTag = ""
                var title = ""
                var link = ""
                var published: String? = null
                var size: String? = null
                var seeders: Int? = null
                var leechers: Int? = null
                var downloads: Int? = null
                var infoHash: String? = null
                var category: String? = null
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name.substringAfter(':').lowercase()
                            if (currentTag == "item") {
                                inItem = true
                                title = ""
                                link = ""
                                published = null
                                size = null
                                seeders = null
                                leechers = null
                                downloads = null
                                infoHash = null
                                category = null
                            }
                        }
                        XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem) {
                            val value = parser.text?.trim().orEmpty()
                            if (value.isNotEmpty()) when (currentTag) {
                                "title" -> title = value
                                "link" -> if (value.startsWith("https://nyaa.si/")) link = value
                                "pubdate" -> published = value
                                "size" -> size = value
                                "seeders" -> seeders = value.toIntOrNull()
                                "leechers" -> leechers = value.toIntOrNull()
                                "downloads" -> downloads = value.toIntOrNull()
                                "infohash" -> infoHash = value.takeIf { it.matches(Regex("(?i)^[a-f0-9]{40}$")) }
                                "category" -> category = value
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            val ended = parser.name.substringAfter(':').lowercase()
                            if (ended == "item") {
                                if (title.isNotBlank() && link.startsWith("https://nyaa.si/")) {
                                    results += NyaaRelease(title, link, published, size, seeders, leechers, downloads, infoHash, category)
                                }
                                inItem = false
                                currentTag = ""
                            } else if (inItem && ended == currentTag) {
                                currentTag = ""
                            }
                        }
                    }
                    event = parser.next()
                }
                return results
            }
        } finally {
            connection.disconnect()
        }
    }
}
