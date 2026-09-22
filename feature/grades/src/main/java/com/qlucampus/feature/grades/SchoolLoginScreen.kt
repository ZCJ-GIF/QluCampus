// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.qlucampus.feature.grades

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dawncourse.core.domain.repository.SchoolSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONTokener
import java.net.URI
import javax.inject.Inject

object SchoolNavigationPolicy {
    const val SSO_URL = "https://jw.qlu.edu.cn/sso/ddlogin"
    fun allowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
            (uri.host == "qlu.edu.cn" || uri.host.endsWith(".qlu.edu.cn"))
    }.getOrDefault(false)
}

@HiltViewModel
class SchoolLoginViewModel @Inject constructor(private val sessions: SchoolSessionRepository) : ViewModel() {
    private val readyState = MutableStateFlow(false)
    val ready = readyState.asStateFlow()
    private val messageState = MutableStateFlow("请在学校原始页面登录，然后点击“完成登录”。")
    val message = messageState.asStateFlow()
    init { viewModelScope.launch {
        try { sessions.beginLogin(); readyState.value = true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error("无法准备学校登录，请返回后重试") }
    } }
    fun error(text: String) { messageState.value = text }
    fun finish(url: String, studentNumber: String, onDone: () -> Unit) = viewModelScope.launch {
        try { sessions.completeLogin(url, studentNumber); onDone() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error(e.message ?: "未完成学校登录") }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolLoginScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: SchoolLoginViewModel = hiltViewModel()) {
    val ready by viewModel.ready.collectAsState()
    val message by viewModel.message.collectAsState()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else onBack() }
    DisposableEffect(Unit) { onDispose { webView?.stopLoading(); webView?.destroy(); webView = null } }
    Scaffold(topBar = { TopAppBar(title = { Text("学校登录") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(message, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { webView?.reload() }) { Text("重新加载") }
                OutlinedButton(onClick = { webView?.loadUrl(SchoolNavigationPolicy.SSO_URL) }, enabled = ready) { Text("统一认证") }
                Button(onClick = {
                    val web = webView ?: return@Button
                    val url = web.url.orEmpty()
                    if (!SchoolNavigationPolicy.allowed(url)) return@Button
                    web.evaluateJavascript("""(function(){
                        if(location.protocol!=='https:' || location.hostname!=='jw.qlu.edu.cn') return '';
                        var selectors=['#sessionUserKey','#xh','#xh_id','input[name="xh"]','input[name="xh_id"]'];
                        for(var i=0;i<selectors.length;i++) {var e=document.querySelector(selectors[i]);if(e){var v=(e.value||e.textContent||'').trim();if(/^[A-Za-z0-9_-]{5,32}$/.test(v))return v;}}
                        var m=(document.body.innerText||'').match(/(?:学号|学工号)\s*[:：]\s*([A-Za-z0-9_-]{5,32})/);
                        return m?m[1]:'';
                    })()""".trimIndent()) { raw ->
                        val student = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
                        if (web.url == url) viewModel.finish(url, student, onDone)
                    }
                }, enabled = ready && !loading) { Text("完成登录") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (ready) AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val allowed = SchoolNavigationPolicy.allowed(request.url.toString())
                            if (!allowed) viewModel.error("该跳转不属于学校 HTTPS 页面，已停止跳转")
                            return !allowed
                        }
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { loading = true }
                        override fun onPageFinished(view: WebView, url: String) { loading = false }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) { loading = false; viewModel.error("学校页面无法打开，请连接校园网或 VPN 后重试") }
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                            handler.cancel(); loading = false; viewModel.error("学校证书校验失败，连接已停止，请检查网络或 VPN")
                        }
                    }
                    loadUrl("https://jw.qlu.edu.cn/")
                }
            })
        }
    }
}
