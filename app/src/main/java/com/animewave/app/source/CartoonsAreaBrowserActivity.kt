package com.animewave.app.source

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * In-app source browser. Search terms are passed from the selected anime/episode.
 * It does not scrape protected stream URLs or bypass DRM/access controls.
 */
class CartoonsAreaBrowserActivity : ComponentActivity() {
    private var sourceWebView: WebView? = null

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
                                    webChromeClient = WebChromeClient()
                                    var initialSearchAttempted = false
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView,
                                            request: WebResourceRequest
                                        ): Boolean = !isAllowedSourceUrl(request.url.toString())

                                        override fun onPageFinished(view: WebView, url: String) {
                                            super.onPageFinished(view, url)
                                            if (!initialSearchAttempted && initialQuery.isNotBlank()) {
                                                initialSearchAttempted = true
                                                view.postDelayed({ runSourceSearch(initialQuery) }, 900)
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
        if (query.isBlank()) return
        val safeQuery = org.json.JSONObject.quote(query)
        // Interact with the visible search form like a user would; never read or extract media URLs.
        val script = """
            (function() {
              var q = $safeQuery;
              var input = document.querySelector('input[type="search"], input[name="s"], input[name="search"], input[placeholder*="search" i], input[type="text"]');
              if (!input) { return 'search-box-not-found'; }
              input.focus();
              input.value = q;
              input.dispatchEvent(new Event('input', {bubbles:true}));
              input.dispatchEvent(new Event('change', {bubbles:true}));
              var form = input.form || input.closest('form');
              if (form) { form.requestSubmit ? form.requestSubmit() : form.submit(); return 'submitted'; }
              var button = document.querySelector('button[type="submit"], input[type="submit"], .search-submit');
              if (button) { button.click(); return 'clicked'; }
              return 'filled';
            })();
        """.trimIndent()
        sourceWebView?.evaluateJavascript(script, null)
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

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        val webView = sourceWebView
        if (webView?.canGoBack() == true) webView.goBack() else finish()
    }

    companion object {
        const val EXTRA_URL = "animewave_source_url"
        const val EXTRA_QUERY = "animewave_source_query"

        fun isAllowedSourceUrl(value: String): Boolean = runCatching {
            val uri = android.net.Uri.parse(value)
            val host = uri.host?.lowercase() ?: return@runCatching false
            uri.scheme == "https" &&
                (host == "cartoonsarea.cc" || host.endsWith(".cartoonsarea.cc") ||
                 host == "cartoonsarea.com" || host.endsWith(".cartoonsarea.com"))
        }.getOrDefault(false)
    }
}
