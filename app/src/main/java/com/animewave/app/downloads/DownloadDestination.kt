package com.animewave.app.downloads

enum class DownloadDestination {
    APP_PRIVATE,
    PUBLIC_DOWNLOADS
}

/** Metadata for downloads managed through Android DownloadManager from direct, authorized media URLs. */
data class DownloadRecord(
    val id: String,
    val title: String,
    val destination: DownloadDestination,
    val status: Status = Status.QUEUED
) {
    enum class Status { QUEUED, DOWNLOADING, COMPLETE, FAILED }
}
