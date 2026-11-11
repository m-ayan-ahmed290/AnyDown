@file:OptIn(ExperimentalMaterial3Api::class)

package com.social_downloader.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import androidx.compose.runtime.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val holder = remember { arrayOfNulls<WebView>(1) }
    val close = { vm.loginSite = null }
    BackHandler { if (holder[0]?.canGoBack() == true) holder[0]?.goBack() else close() }

    DisposableEffect(Unit) {
        onDispose {
            holder[0]?.destroy()
            holder[0] = null
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
                        if (text == null || !CookieFile.hasSession(text, site.platform)) {
                            error = "No signed-in session found. Finish signing in with the site’s own login, or import browser cookies in Settings."
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
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = { holder[0]?.reload(); error = null }) { Text("Reload") }
                TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(site.loginUrl)), "Choose a browser")) }) { Text("Open browser") }
            }
            error?.let { Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val cm = CookieManager.getInstance()
                    // Do not asynchronously clear cookies while a login page is loading.
                    cm.setAcceptCookie(true)
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        cm.setAcceptThirdPartyCookies(this, true)
                        webChromeClient = WebChromeClient()
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(false)
                        settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(" Version/4.0", "")
                        webViewClient = object : WebViewClient() {
                            // Stay on web pages; never hand off to intent://, file:// etc.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (request.url.host == "accounts.google.com") {
                                    error = "Google blocks embedded sign-in. Use the site's email login, or open your browser and import cookies in Settings. Browser cookies cannot be read automatically."
                                    loading = false
                                    return true
                                }
                                val scheme = request.url.scheme?.lowercase()
                                val blocked = scheme != "https" && scheme != "http"
                                if (blocked) { loading = false; error = "This link opens another app. Use the website's own login or Open browser, then import cookies." }
                                return blocked
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { loading = true; error = null }
                            override fun onPageFinished(view: WebView, url: String?) { loading = false }
                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
                                if (request.isForMainFrame && response.statusCode >= 400) {
                                    loading = false
                                    error = "Site returned HTTP ${response.statusCode}. Reload, or use your browser and import cookies."
                                }
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                                if (request.isForMainFrame) { loading = false; error = "Page couldn't load. Reload, or use your browser and import cookies." }
                            }
                        }
                        holder[0] = this
                        loadUrl(site.loginUrl)
                    }
                },
            )
        }
    }
}
