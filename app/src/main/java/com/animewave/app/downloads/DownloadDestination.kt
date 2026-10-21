package com.animewave.app.downloads

enum class DownloadDestination {
    APP_PRIVATE,
    PUBLIC_DOWNLOADS
}

/** Metadata model only. Live downloads are started and tracked through Android's DownloadManager (see DownloadHelper). */
data class DownloadRecord(
    val id: String,
    val title: String,
    val destination: DownloadDestination,
    val status: Status = Status.QUEUED
) {
    enum class Status { QUEUED, DOWNLOADING, COMPLETE, FAILED }
}
