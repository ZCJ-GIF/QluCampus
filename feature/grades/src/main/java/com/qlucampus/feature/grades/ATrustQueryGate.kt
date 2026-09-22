// QluCampus, GPL-3.0. External VPN launch is a presentation action, not a school login.
package com.qlucampus.feature.grades

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.dawncourse.core.domain.repository.SchoolNetworkRepository
import com.dawncourse.core.domain.repository.SchoolNetworkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal const val ATRUST_PACKAGE = "com.sangfor.atrust"

@Singleton
class ATrustPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("school_vpn", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(preferences.getBoolean("open_atrust", true))
    val automatic = state.asStateFlow()
    fun setAutomatic(enabled: Boolean) {
        preferences.edit().putBoolean("open_atrust", enabled).apply()
        state.value = enabled
    }
}

@HiltViewModel
class ATrustViewModel @Inject constructor(private val preferences: ATrustPreferences, private val network: SchoolNetworkRepository) : ViewModel() {
    val automatic = preferences.automatic
    fun setAutomatic(enabled: Boolean) = preferences.setAutomatic(enabled)
    suspend fun checkNetwork() = network.check()
}

private enum class ATrustLaunch { OPENED, NOT_INSTALLED, BLOCKED }

private fun openATrust(context: Context): ATrustLaunch = try {
    val intent = context.packageManager.getLaunchIntentForPackage(ATRUST_PACKAGE)
    if (intent == null) ATrustLaunch.NOT_INSTALLED else {
        // 不传递学校账号、密码、Cookie 或查询参数；不尝试控制外部 VPN 服务。
        context.startActivity(intent)
        ATrustLaunch.OPENED
    }
} catch (_: ActivityNotFoundException) { ATrustLaunch.NOT_INSTALLED }
catch (_: SecurityException) { ATrustLaunch.BLOCKED }
catch (_: RuntimeException) { ATrustLaunch.BLOCKED }

@Stable
class ATrustQueryGate internal constructor(private val context: Context, private val automatic: () -> Boolean,
    private val scope: CoroutineScope, private val checkNetwork: suspend () -> SchoolNetworkState) {
    private val handoff = ATrustHandoff()
    var waiting by mutableStateOf(false); private set
    var problem by mutableStateOf<String?>(null); private set
    var checking by mutableStateOf(false); private set
    var status by mutableStateOf<String?>(null); private set
    private var checkJob: Job? = null

    fun request(action: () -> Unit) {
        if (handoff.hasPending || checking) return
        status = null
        if (!automatic()) { action(); return }
        if (!handoff.prepare(action)) return
        checking = true
        checkJob = scope.launch {
            try {
                when (checkNetwork()) {
                    SchoolNetworkState.VPN_CONNECTED -> {
                        status = "检测到 VPN 连接，直接继续；若无法访问学校，可手动打开 aTrust。"
                        checking = false
                        handoff.take()?.invoke()
                    }
                    SchoolNetworkState.SCHOOL_REACHABLE -> {
                        status = "学校网址可访问，直接继续。"
                        checking = false
                        handoff.take()?.invoke()
                    }
                    SchoolNetworkState.NEED_CONNECTION -> { checking = false; launchPrepared() }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                checking = false
                problem = "暂时无法检测连接状态。可直接继续，或取消后手动打开 aTrust。"
            }
        }
    }

    private fun launchPrepared() {
        when (openATrust(context)) {
            ATrustLaunch.OPENED -> waiting = true
            ATrustLaunch.NOT_INSTALLED -> problem = "未找到可打开的 aTrust。请先安装并启用学校提供的 aTrust 客户端；如果已在校园网内，可直接查询。"
            ATrustLaunch.BLOCKED -> problem = "系统未允许打开 aTrust。请手动打开并连接，或在系统设置中允许齐鲁课表打开其他应用，再返回继续。"
        }
    }

    fun openManually() {
        if (handoff.hasPending || checking) return
        status = null
        handoff.prepare { }
        launchPrepared()
    }

    internal fun leftForeground() {
        if (checking) cancel() else if (waiting) handoff.leftForeground()
    }
    internal fun resume() {
        val action = if (waiting) handoff.resume() else null
        if (action != null) { waiting = false; problem = null; action() }
    }
    fun continueQuery() {
        checkJob?.cancel(); checkJob = null; checking = false
        val action = handoff.take()
        waiting = false; problem = null
        action?.invoke()
    }
    fun cancel() {
        checkJob?.cancel(); checkJob = null; checking = false
        handoff.cancel(); waiting = false; problem = null
    }
}

@Composable
fun rememberATrustQueryGate(identity: Any?, viewModel: ATrustViewModel = hiltViewModel()): ATrustQueryGate {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val automatic by viewModel.automatic.collectAsState()
    val latestAutomatic = rememberUpdatedState(automatic)
    val scope = rememberCoroutineScope()
    val gate = remember(context, scope, viewModel) { ATrustQueryGate(context, { latestAutomatic.value }, scope, viewModel::checkNetwork) }
    DisposableEffect(gate, lifecycle, identity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> gate.leftForeground()
                Lifecycle.Event.ON_RESUME -> gate.resume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); gate.cancel() }
    }
    // 待执行操作仅留在当前页面内存；换账号/学期、离页或重建页面都取消，避免旧查询重放。
    if (gate.checking || gate.waiting || gate.problem != null) AlertDialog(
        onDismissRequest = gate::cancel,
        title = { Text(if (gate.checking) "检测学校网络" else if (gate.problem == null) "连接学校网络" else "无法打开 aTrust") },
        text = { if (gate.checking) { Column { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在检测现有连接，已连接时直接继续。") } }
            else Text(gate.problem ?: "请在 aTrust 完成登录和连接，返回齐鲁课表后自动继续本次操作。如果没有弹出，请允许本应用打开 aTrust 或手动打开。") },
        confirmButton = { if (!gate.checking) TextButton(onClick = gate::continueQuery) { Text(if (gate.problem == null) "已连接，继续" else "直接继续") } },
        dismissButton = { TextButton(onClick = gate::cancel) { Text("取消本次操作") } }
    )
    return gate
}

@Composable
fun ATrustQuerySettings(gate: ATrustQueryGate, viewModel: ATrustViewModel = hiltViewModel()) {
    val automatic by viewModel.automatic.collectAsState()
    Column {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("查询前打开 aTrust", style = MaterialTheme.typography.titleMedium)
            Text("已有 VPN 或学校网址可访问时不再弹出；否则打开 aTrust，返回后继续。", style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = automatic, onCheckedChange = viewModel::setAutomatic,
            modifier = Modifier.semantics { contentDescription = "查询前打开 aTrust" })
    }
    OutlinedButton(onClick = gate::openManually, enabled = !gate.checking && !gate.waiting) { Text("手动打开 aTrust") }
    gate.status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
