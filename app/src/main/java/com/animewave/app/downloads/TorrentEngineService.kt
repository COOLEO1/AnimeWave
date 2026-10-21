package com.animewave.app.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.IBinder
import org.json.JSONArray
import org.json.JSONObject
import org.libtorrent4j.SessionManager
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Owns the native libtorrent session in a foreground service so transfers can continue
 * when AnimeWave is not visible. Torrent records are persisted and re-added after restart.
 *
 * Downloads are stored in app-specific external storage; broad storage permissions are
 * intentionally not requested.
 */
class TorrentEngineService : Service() {
    private data class Record(
        val hash: String,
        val magnet: String,
        val title: String,
        var userPaused: Boolean = false
    )

    private val commandExecutor = Executors.newSingleThreadExecutor()
    private var poller: ScheduledExecutorService? = null
    @Volatile private var session: SessionManager? = null
    private val appliedPause = ConcurrentHashMap<String, Boolean>()
    private val lock = Any()
    private var records = mutableListOf<Record>()
    private var lastEngineError: String? = null

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Preparing torrent engine…"))
        commandExecutor.execute {
            runCatching {
                val engine = SessionManager()
                engine.start()
                session = engine
                loadRecords()
                records.toList().forEach { record ->
                    runCatching {
                        engine.download(record.magnet, downloadRoot(), TorrentFlags.SEQUENTIAL_DOWNLOAD)
                    }.onFailure { lastEngineError = it.message ?: "Could not restore torrent" }
                }
                poller = Executors.newSingleThreadScheduledExecutor()
                poller?.scheduleWithFixedDelay({ updateSnapshots() }, 0, 1, TimeUnit.SECONDS)
            }.onFailure {
                lastEngineError = it.message ?: "Torrent engine failed to start"
                saveSnapshots(JSONArray())
                updateNotification("Engine error — open AnimeWave to retry")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_REFRESH
        commandExecutor.execute {
            try {
                when (action) {
                    ACTION_ADD -> addTorrent(
                        intent?.getStringExtra(EXTRA_MAGNET).orEmpty(),
                        intent?.getStringExtra(EXTRA_TITLE).orEmpty()
                    )
                    ACTION_PAUSE -> setPaused(intent?.getStringExtra(EXTRA_HASH).orEmpty(), true)
                    ACTION_RESUME -> setPaused(intent?.getStringExtra(EXTRA_HASH).orEmpty(), false)
                    ACTION_REMOVE -> removeTorrent(intent?.getStringExtra(EXTRA_HASH).orEmpty())
                    ACTION_REFRESH -> Unit
                }
                updateSnapshots()
            } catch (e: Exception) {
                lastEngineError = e.message ?: "Torrent action failed"
                updateSnapshots()
            }
        }
        return START_STICKY
    }

    private fun addTorrent(magnet: String, title: String) {
        val hash = Regex("(?i)btih:([a-f0-9]{40})").find(magnet)?.groupValues?.get(1)?.lowercase()
            ?: throw IllegalArgumentException("This magnet link does not contain a supported 40-character info hash.")
        require(magnet.startsWith("magnet:?")) { "Invalid magnet link." }
        val engine = session ?: throw IllegalStateException("Torrent engine is still starting. Try again.")
        synchronized(lock) {
            if (records.any { it.hash == hash }) return
            records.add(Record(hash, magnet, title.ifBlank { "Torrent $hash" }))
            persistRecords()
        }
        engine.download(magnet, downloadRoot(), TorrentFlags.SEQUENTIAL_DOWNLOAD)
        lastEngineError = null
    }

    private fun setPaused(hash: String, paused: Boolean) {
        synchronized(lock) {
            records.firstOrNull { it.hash == hash }?.let {
                it.userPaused = paused
                persistRecords()
            }
        }
        applyHandleState(hash)
    }

    private fun removeTorrent(hash: String) {
        val engine = session
        val handle = runCatching { engine?.find(Sha1Hash.parseHex(hash)) }.getOrNull()
        if (handle != null && handle.isValid()) runCatching { engine?.remove(handle) }
        synchronized(lock) {
            records.removeAll { it.hash == hash }
            persistRecords()
        }
        appliedPause.remove(hash)
        // Intentionally keep downloaded files; removing a torrent must not silently delete user data.
    }

    private fun loadRecords() {
        val loaded = mutableListOf<Record>()
        runCatching {
            val array = JSONArray(prefs.getString(KEY_RECORDS, "[]"))
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val hash = item.optString("hash").lowercase()
                val magnet = item.optString("magnet")
                if (!hash.matches(Regex("^[a-f0-9]{40}$")) || !magnet.startsWith("magnet:?")) continue
                loaded.add(Record(hash, magnet, item.optString("title", "Torrent"), item.optBoolean("paused")))
            }
        }
        synchronized(lock) { records = loaded }
    }

    private fun persistRecords() {
        val array = JSONArray()
        synchronized(lock) {
            records.forEach { record ->
                array.put(JSONObject()
                    .put("hash", record.hash)
                    .put("magnet", record.magnet)
                    .put("title", record.title)
                    .put("paused", record.userPaused))
            }
            prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
        }
    }

    private fun applyHandleState(hash: String) {
        val engine = session ?: return
        val record = synchronized(lock) { records.firstOrNull { it.hash == hash } } ?: return
        val waitingForWifi = wifiOnlyEnabled() && !isWifiConnected()
        val shouldPause = record.userPaused || waitingForWifi
        val handle = runCatching { engine.find(Sha1Hash.parseHex(hash)) }.getOrNull() ?: return
        if (!handle.isValid() || appliedPause[hash] == shouldPause) return
        runCatching {
            if (shouldPause) handle.pause() else handle.resume()
            appliedPause[hash] = shouldPause
        }
    }

    private fun updateSnapshots() {
        val engine = session
        val output = JSONArray()
        val copy = synchronized(lock) { records.toList() }
        copy.forEach { record ->
            val waitingForWifi = wifiOnlyEnabled() && !isWifiConnected()
            val handle = runCatching { engine?.find(Sha1Hash.parseHex(record.hash)) }.getOrNull()
            var progress = 0.0
            var downloaded = 0L
            var total = 0L
            var rate = 0L
            var complete = false
            var state = if (record.userPaused) "Paused" else "Connecting"
            if (handle != null && handle.isValid()) {
                applyHandleState(record.hash)
                runCatching {
                    val status = handle.status()
                    progress = status.progress().toDouble().coerceIn(0.0, 1.0)
                    downloaded = status.totalWantedDone()
                    total = status.totalWanted()
                    rate = status.downloadRate().toLong().coerceAtLeast(0)
                    complete = status.isSeeding() || status.isFinished() || progress >= 0.9999
                    state = when {
                        waitingForWifi -> "Waiting for Wi-Fi"
                        record.userPaused -> "Paused"
                        complete -> "Complete"
                        else -> status.state().toString().replace('_', ' ')
                    }
                }
            } else if (waitingForWifi) {
                state = "Waiting for Wi-Fi"
            }
            output.put(JSONObject()
                .put("hash", record.hash)
                .put("title", record.title)
                .put("magnet", record.magnet)
                .put("paused", record.userPaused)
                .put("waitingForWifi", waitingForWifi)
                .put("progress", progress)
                .put("downloaded", downloaded)
                .put("total", total)
                .put("rate", rate)
                .put("complete", complete)
                .put("state", state))
        }
        saveSnapshots(output)
        val active = copy.count { !it.userPaused }
        updateNotification(when {
            lastEngineError != null -> lastEngineError!!
            copy.isEmpty() -> "Ready for authorized torrent downloads"
            else -> "$active active • ${copy.size} in library"
        })
    }

    private fun saveSnapshots(array: JSONArray) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SNAPSHOTS, array.toString())
            .putString(KEY_ERROR, lastEngineError)
            .apply()
    }

    private fun downloadRoot(): File {
        val base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        return File(base, "AnimeWaveTorrents").apply { mkdirs() }
    }

    private fun wifiOnlyEnabled(): Boolean =
        getSharedPreferences("animewave_settings", Context.MODE_PRIVATE).getBoolean("wifi_only", false)

    private fun isWifiConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "AnimeWave torrent downloads", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("AnimeWave")
            .setContentText(text.take(120))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        poller?.shutdownNow()
        commandExecutor.execute { runCatching { session?.stop() }; session = null }
        commandExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val PREFS = "animewave_torrents"
        const val KEY_RECORDS = "records"
        const val KEY_SNAPSHOTS = "snapshots"
        const val KEY_ERROR = "error"
        const val ACTION_ADD = "com.animewave.app.torrent.ADD"
        const val ACTION_PAUSE = "com.animewave.app.torrent.PAUSE"
        const val ACTION_RESUME = "com.animewave.app.torrent.RESUME"
        const val ACTION_REMOVE = "com.animewave.app.torrent.REMOVE"
        const val ACTION_REFRESH = "com.animewave.app.torrent.REFRESH"
        const val EXTRA_MAGNET = "magnet"
        const val EXTRA_TITLE = "title"
        const val EXTRA_HASH = "hash"
        private const val CHANNEL_ID = "animewave_torrent_downloads"
        private const val NOTIFICATION_ID = 7314

        fun add(context: Context, magnet: String, title: String) {
            send(context, ACTION_ADD) {
                putExtra(EXTRA_MAGNET, magnet)
                putExtra(EXTRA_TITLE, title)
            }
        }

        fun pause(context: Context, hash: String) = send(context, ACTION_PAUSE) { putExtra(EXTRA_HASH, hash) }
        fun resume(context: Context, hash: String) = send(context, ACTION_RESUME) { putExtra(EXTRA_HASH, hash) }
        fun remove(context: Context, hash: String) = send(context, ACTION_REMOVE) { putExtra(EXTRA_HASH, hash) }
        fun refresh(context: Context) = send(context, ACTION_REFRESH) {}

        private fun send(context: Context, action: String, configure: Intent.() -> Unit) {
            val intent = Intent(context, TorrentEngineService::class.java).setAction(action).apply(configure)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
