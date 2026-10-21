package com.animewave.app.nyaa

data class NyaaRelease(
    val title: String,
    val pageUrl: String,
    val published: String?,
    val size: String?,
    val seeders: Int?,
    val leechers: Int?,
    val downloads: Int?,
    val infoHash: String?,
    val category: String?
) {
    fun magnetUri(): String? {
        val hash = infoHash?.trim()?.takeIf { it.matches(Regex("(?i)^[a-f0-9]{40}$")) } ?: return null
        val displayName = java.net.URLEncoder.encode(title, Charsets.UTF_8.name()).replace("+", "%20")
        return "magnet:?xt=urn:btih:$hash&dn=$displayName"
    }
}
