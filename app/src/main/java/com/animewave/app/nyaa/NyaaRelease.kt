package com.animewave.app.nyaa

import java.util.Locale

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
    /** Converts common Nyaa size strings (for example, 850.4 MiB or 1.2 GiB) to bytes. */
    fun sizeBytes(): Long? {
        val raw = size?.trim() ?: return null
        val match = Regex("""(?i)^([0-9]+(?:[.,][0-9]+)?)\\s*(bytes?|b|kib|mib|gib|tib|kb|mb|gb|tb)$""")
            .matchEntire(raw) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].lowercase(Locale.ROOT)
        val multiplier = when (unit) {
            "b", "byte", "bytes" -> 1.0
            "kb" -> 1_000.0
            "mb" -> 1_000_000.0
            "gb" -> 1_000_000_000.0
            "tb" -> 1_000_000_000_000.0
            "kib" -> 1_024.0
            "mib" -> 1_048_576.0
            "gib" -> 1_073_741_824.0
            "tib" -> 1_099_511_627_776.0
            else -> return null
        }
        return (amount * multiplier).takeIf { it.isFinite() && it >= 0.0 && it <= Long.MAX_VALUE.toDouble() }?.toLong()
    }

    /** Display compact, readable MB/GB labels instead of raw or inconsistent feed values. */
    fun sizeLabel(): String {
        val bytes = sizeBytes() ?: return size?.takeIf { it.isNotBlank() } ?: "Size unknown"
        val kib = 1024.0
        return when {
            bytes >= (kib * kib * kib).toLong() ->
                String.format(Locale.ROOT, "%.2f GB", bytes / (kib * kib * kib))
            bytes >= (kib * kib).toLong() ->
                String.format(Locale.ROOT, "%.0f MB", bytes / (kib * kib))
            bytes >= kib.toLong() ->
                String.format(Locale.ROOT, "%.0f KB", bytes / kib)
            else -> "$bytes B"
        }
    }

    fun magnetUri(): String? {
        val hash = infoHash?.trim()?.takeIf { it.matches(Regex("(?i)^[a-f0-9]{40}$")) } ?: return null
        val displayName = java.net.URLEncoder.encode(title, Charsets.UTF_8.name()).replace("+", "%20")
        return "magnet:?xt=urn:btih:$hash&dn=$displayName"
    }
}
