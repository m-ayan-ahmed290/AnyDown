@file:OptIn(ExperimentalMaterial3Api::class)

package com.social_downloader.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.social_downloader.engine.CookieFile
import com.social_downloader.engine.SiteLogin

/**
 * Lets the user sign in to a site with their own account inside a WebView. Their password is typed into
 * the site's own page and never seen by the app; on "Done" we keep only the session cookies.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(site: SiteLogin, vm: MainViewModel) {
    val holder = remember { arrayOfNulls<WebView>(1) }
    val close = { vm.loginSite = null }
    BackHandler { close() }

    DisposableEffect(Unit) {
        onDispose {
            holder[0]?.destroy()
            holder[0] = null
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to ${site.label}") },
                navigationIcon = {
                    IconButton(onClick = close) { Icon(Icons.Rounded.Close, contentDescription = "Cancel") }
                },
                actions = {
                    TextButton(onClick = {
                        val cm = CookieManager.getInstance()
                        cm.flush()
                        val sources = site.cookieSources.associate { (url, domain) -> domain to cm.getCookie(url) }
                        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
                        val text = CookieFile.build(sources, expiry)
                        if (text == null) {
                            vm.say("No session found. Finish signing in first, then tap Done.")
                        } else {
                            vm.saveCookies(site.platform, text)
                            close()
                        }
                    }) { Text("Done") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Sign in with your own account, then tap Done. Your password goes straight to " +
                        "${site.label}; the app only keeps the session cookies on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val cm = CookieManager.getInstance()
                    cm.removeAllCookies(null) // always start from a clean session
                    cm.setAcceptCookie(true)
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        cm.setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            // Stay on web pages; never hand off to intent://, file:// etc.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val scheme = request.url.scheme?.lowercase()
                                return scheme != "https" && scheme != "http"
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = Unit
                        }
                        holder[0] = this
                        loadUrl(site.loginUrl)
                    }
                },
            )
        }
    }
}
