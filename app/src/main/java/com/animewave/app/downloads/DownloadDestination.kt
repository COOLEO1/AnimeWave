package com.animewave.app.downloads

enum class DownloadDestination {
    APP_PRIVATE,
    PUBLIC_DOWNLOADS
}

/** Metadata model only; downloading is not enabled until an authorized source is configured. */
data class DownloadRecord(
    val id: String,
    val title: String,
    val destination: DownloadDestination,
    val status: Status = Status.QUEUED
) {
    enum class Status { QUEUED, DOWNLOADING, COMPLETE, FAILED }
}
