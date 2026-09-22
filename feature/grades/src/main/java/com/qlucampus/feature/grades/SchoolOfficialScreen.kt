// QluCampus 0.2.0, GPL-3.0. Shared WebView cookies; opening an official page does not reset the session.
package com.qlucampus.feature.grades

import android.annotation.SuppressLint
import android.webkit.*
import android.net.http.SslError
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolOfficialScreen(url: String, onBack: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    BackHandler { if (web?.canGoBack() == true) web?.goBack() else onBack() }
    DisposableEffect(Unit) { onDispose { web?.stopLoading(); web?.destroy() } }
    Scaffold(topBar = { TopAppBar(title = { Text("学校官方查询") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let { Text(it) }
            Row {
                OutlinedButton(onClick = { error = null; web?.reload() }) { Text("重新加载") }
                OutlinedButton(onClick = { error = null; web?.loadUrl(SchoolNavigationPolicy.SSO_URL) }) { Text("统一认证") }
                OutlinedButton(onClick = { error = null; web?.loadUrl(url) }) { Text("返回查询") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (SchoolNavigationPolicy.allowed(url)) AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context -> WebView(context).apply {
                web = this; settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                settings.useWideViewPort = true; settings.loadWithOverviewMode = true
                settings.builtInZoomControls = true; settings.displayZoomControls = false
                settings.allowFileAccess = false; settings.allowContentAccess = false; settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { loading = true }
                    override fun onPageFinished(view: WebView, url: String) { loading = false }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = !SchoolNavigationPolicy.allowed(request.url.toString())
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) { if (request.isForMainFrame) error = "学校页面无法打开，请检查 VPN 或重新登录" }
                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, failure: SslError) { handler.cancel(); error = "学校连接证书验证失败" }
                }
                loadUrl(url)
            } })
        }
    }
}
