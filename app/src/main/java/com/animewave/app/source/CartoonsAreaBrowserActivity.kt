package com.animewave.app.source

import android.annotation.SuppressLint
import android.Manifest
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/**
 * CartoonsArea source browser. Normal embedded HTML5 video playback and direct downloads
 * are supported where the source provides them; protected streams are not extracted.
 */
class CartoonsAreaBrowserActivity : ComponentActivity() {
    private var sourceWebView: WebView? = null
    private var customVideoView: View? = null
    private var customVideoCallback: WebChromeClient.CustomViewCallback? = null
    private var sourceChromeClient: WebChromeClient? = null
    private var initialSearchAttempted = false
    private var pendingDownload: PendingDownload? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingDownload
        pendingDownload = null
        if (!granted) {
            Toast.makeText(this, "Notifications are off; the download will still be attempted.", Toast.LENGTH_LONG).show()
        }
        pending?.let { enqueueSourceDownload(it.url, it.userAgent, it.contentDisposition, it.mimeType) }
    }

    private data class PendingDownload(
        val url: String,
        val userAgent: String?,
        val contentDisposition: String?,
        val mimeType: String?
    )
    private var originalSystemUiVisibility = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startUrl = intent.getStringExtra(EXTRA_URL)
            ?.takeIf(::isAllowedSourceUrl)
            ?: "https://www.cartoonsarea.cc/"
        val initialQuery = intent.getStringExtra(EXTRA_QUERY).orEmpty()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090A12)) {
                    var query by remember { mutableStateOf(initialQuery) }
                    Column(Modifier.fillMaxSize()) {
                        Button(onClick = { finish() }, modifier = Modifier.padding(8.dp)) {
                            Text("‹ Back to AnimeWave")
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                label = { Text("Search CartoonsArea") }
                            )
                            Button(
                                onClick = { runSourceSearch(query) },
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                            ) { Text("Find") }
                        }
                        AndroidView(
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    sourceWebView = this
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = true
                                    settings.loadsImagesAutomatically = true
                                    sourceChromeClient = object : WebChromeClient() {
                                        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                            if (customVideoView != null) {
                                                callback.onCustomViewHidden()
                                                return
                                            }
                                            customVideoView = view
                                            customVideoCallback = callback
                                            val decor = window.decorView as? FrameLayout
                                            if (decor == null) {
                                                customVideoView = null
                                                customVideoCallback = null
                                                callback.onCustomViewHidden()
                                                return
                                            }
                                            originalSystemUiVisibility = decor.systemUiVisibility
                                            decor.addView(view, FrameLayout.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            ))
                                            decor.systemUiVisibility = (
                                                View.SYSTEM_UI_FLAG_FULLSCREEN or
                                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                            )
                                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                        }

                                        override fun onHideCustomView() {
                                            val view = customVideoView ?: return
                                            (window.decorView as? FrameLayout)?.removeView(view)
                                            customVideoView = null
                                            window.decorView.systemUiVisibility = originalSystemUiVisibility
                                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                            customVideoCallback?.onCustomViewHidden()
                                            customVideoCallback = null
                                        }
                                    }
                                    webChromeClient = sourceChromeClient
                                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                                        startSourceDownload(url, userAgent, contentDisposition, mimeType)
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView,
                                            request: WebResourceRequest
                                        ): Boolean {
                                            val target = request.url.toString()
                                            if (request.isForMainFrame && isDirectMediaUrl(target)) {
                                                startActivity(
                                                    Intent(this@CartoonsAreaBrowserActivity, com.animewave.app.PlayerActivity::class.java)
                                                        .putExtra(com.animewave.app.PlayerActivity.EXTRA_MEDIA_URL, target)
                                                        .putExtra(com.animewave.app.PlayerActivity.EXTRA_TITLE, initialQuery.ifBlank { "AnimeWave episode" })
                                                )
                                                return true
                                            }
                                            // Permit cross-site iframe players to load; otherwise the episode page
                                            // can appear but its actual player is silently blocked.
                                            if (!request.isForMainFrame) return false
                                            // Keep top-level navigation within the source domain.
                                            return !isAllowedSourceUrl(target)
                                        }

                                        override fun shouldInterceptRequest(
                                            view: WebView,
                                            request: WebResourceRequest
                                        ): WebResourceResponse? {
                                            return if (isAdOrTrackerUrl(request.url.toString())) {
                                                WebResourceResponse(
                                                    "text/plain", "utf-8", 204, "No Content",
                                                    emptyMap(), ByteArrayInputStream(ByteArray(0))
                                                )
                                            } else {
                                                super.shouldInterceptRequest(view, request)
                                            }
                                        }

                                        override fun onPageFinished(view: WebView, url: String) {
                                            super.onPageFinished(view, url)
                                            if (!initialQuery.isBlank() && !initialSearchAttempted) {
                                                initialSearchAttempted = true
                                                view.postDelayed({ runSourceSearch(initialQuery) }, 1200)
                                            }
                                        }
                                    }
                                    loadUrl(startUrl)
                                }
                            },
                            update = { sourceWebView = it }
                        )
                    }
                }
            }
        }
    }

    private fun runSourceSearch(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            Toast.makeText(this, "Enter an anime or episode name first.", Toast.LENGTH_SHORT).show()
            return
        }
        val safeQuery = org.json.JSONObject.quote(query)
        val script = """
            (function() {
              var q = $safeQuery;
              var selectors = [
                'input[placeholder*="search anime" i]',
                'input[aria-label*="search" i]',
                'input[placeholder*="search" i]',
                'input[type="search"]',
                'input[name="s"]',
                'input[name="search"]'
              ];
              var input = null;
              for (var s = 0; s < selectors.length && !input; s++) input = document.querySelector(selectors[s]);
              if (!input) {
                var candidates = document.querySelectorAll('input[type="text"], input:not([type])');
                for (var i = 0; i < candidates.length; i++) {
                  if (candidates[i].offsetParent !== null) { input = candidates[i]; break; }
                }
              }
              if (!input) return 'no-search-field';
              input.focus();
              var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
              setter.call(input, q);
              input.dispatchEvent(new Event('input', {bubbles:true}));
              input.dispatchEvent(new Event('change', {bubbles:true}));
              var form = input.form || input.closest('form');
              if (form) {
                if (form.requestSubmit) form.requestSubmit();
                else form.submit();
                return 'submitted-form';
              }
              var node = input;
              for (var depth = 0; depth < 5 && node; depth++, node = node.parentElement) {
                var button = node.querySelector && node.querySelector('button, input[type="submit"], [role="button"], a[aria-label*="search" i]');
                if (button) { button.click(); return 'clicked-search-button'; }
              }
              input.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));
              input.dispatchEvent(new KeyboardEvent('keypress', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));
              input.dispatchEvent(new KeyboardEvent('keyup', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));
              return 'submitted-enter';
            })();
        """.trimIndent()
        val webView = sourceWebView
        if (webView == null) {
            Toast.makeText(this, "Source page is still loading. Try again in a moment.", Toast.LENGTH_SHORT).show()
            return
        }
        webView.evaluateJavascript(script) { rawResult ->
            val result = rawResult?.trim('"') ?: "no-result"
            when (result) {
                "no-search-field" -> Toast.makeText(
                    this,
                    "Couldn't find CartoonsArea's search field. The source page may still be loading.",
                    Toast.LENGTH_LONG
                ).show()
                "submitted-form", "clicked-search-button", "submitted-enter" ->
                    Toast.makeText(this, "Searching CartoonsArea…", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(this, "Search response: $result", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startSourceDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = PendingDownload(url, userAgent, contentDisposition, mimeType)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        enqueueSourceDownload(url, userAgent, contentDisposition, mimeType)
    }

    private fun enqueueSourceDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        runCatching {
            val uri = Uri.parse(url)
            require(uri.scheme == "https" || uri.scheme == "http") { "Unsupported download URL" }
            val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                .replace(Regex("[^A-Za-z0-9._ -]"), "_")
                .take(120)
                .ifBlank { "anime_download" }
            val request = DownloadManager.Request(uri)
                .setTitle(guessedName)
                .setDescription("AnimeWave episode download")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            val preferences = getSharedPreferences("animewave_settings", Context.MODE_PRIVATE)
            if (preferences.getString("download_storage", "phone") == "app") {
                request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, guessedName)
            } else {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AnimeWave/$guessedName")
            }
            if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                request.addRequestHeader("Cookie", it)
            }
            sourceWebView?.url?.let { request.addRequestHeader("Referer", it) }
            if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "Download queued. Track progress in AnimeWave → Downloads.", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "Download couldn't start: ${it.message ?: "unsupported link"}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (customVideoView != null) {
            sourceChromeClient?.onHideCustomView()
            return
        }
        val webView = sourceWebView
        if (webView?.canGoBack() == true) webView.goBack() else finish()
    }

    override fun onDestroy() {
        sourceWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        sourceWebView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "animewave_source_url"
        const val EXTRA_QUERY = "animewave_source_query"

        fun isAllowedSourceUrl(value: String): Boolean = runCatching {
            val uri = Uri.parse(value)
            val host = uri.host?.lowercase() ?: return@runCatching false
            uri.scheme == "https" &&
                (host == "cartoonsarea.cc" || host.endsWith(".cartoonsarea.cc") ||
                 host == "cartoonsarea.com" || host.endsWith(".cartoonsarea.com") ||
                 host == "eng.cartoonsarea.cc" || host.endsWith(".cartoonsarea.com"))
        }.getOrDefault(false)

        fun isDirectMediaUrl(value: String): Boolean = runCatching {
            val uri = Uri.parse(value)
            val path = uri.path.orEmpty().lowercase()
            (uri.scheme == "https" || uri.scheme == "http") &&
                !uri.host.isNullOrBlank() &&
                listOf(".mp4", ".m4v", ".webm", ".mkv", ".mp3", ".m3u8", ".mpd").any { path.endsWith(it) }
        }.getOrDefault(false)

        fun isAdOrTrackerUrl(value: String): Boolean = runCatching {
            val host = Uri.parse(value).host?.lowercase() ?: return@runCatching false
            val blocked = listOf(
                "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                "adservice.google.com", "popads.net", "popcash.net", "propellerads.com",
                "adsterra.com", "exoclick.com", "trafficjunky.com", "adcash.com",
                "clickadu.com", "hilltopads.net", "onclickalgo.com", "juicyads.com",
                "taboola.com", "outbrain.com"
            )
            blocked.any { host == it || host.endsWith(".$it") }
        }.getOrDefault(false)
    }
}
