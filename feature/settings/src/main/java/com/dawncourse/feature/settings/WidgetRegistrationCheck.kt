// QluCampus 0.2.3, GPL-3.0. Read-only diagnostics; never toggle or clear the user's launcher.
package com.dawncourse.feature.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

internal fun widgetRegistrationReport(context: Context): String {
    val component = ComponentName(context.packageName, "com.dawncourse.feature.widget.DawnWidgetReceiver")
    val pm = context.packageManager
    val manager = AppWidgetManager.getInstance(context)
    val declared = runCatching {
        @Suppress("DEPRECATION")
        pm.getReceiverInfo(component, PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS)
    }
    val enabled = declared.getOrNull()?.let { info ->
        val setting = runCatching { pm.getComponentEnabledSetting(component) }.getOrNull()
        info.applicationInfo.enabled && when (setting) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> info.enabled
            null -> false
            else -> false
        }
    }
    val registered = runCatching {
        manager.getInstalledProvidersForPackage(context.packageName, Process.myUserHandle())
            .orEmpty().any { it.provider == component }
    }.getOrNull()
    val pinSupported = runCatching { manager.isRequestPinAppWidgetSupported }.getOrNull()
    val instances = runCatching { manager.getAppWidgetIds(component).size }.getOrNull()
    val version = runCatching {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "未知"
    fun Boolean?.display() = when (this) { true -> "是"; false -> "否"; null -> "未能读取" }
    val conclusion = when {
        declared.isFailure -> "未能读取组件声明，请先覆盖安装最新版并打开一次应用。"
        enabled == false -> "组件当前处于停用状态；请覆盖安装最新版后再次检测。"
        registered == false -> "安装包含组件，但系统尚未登记。请重新打开本应用，仍无变化可重启手机后检测。"
        registered == true -> "系统已登记组件。若安卓小部件列表仍没有，需继续检查桌面筛选或缓存状态。"
        else -> "暂时无法确定系统登记状态，请将下面的检测信息反馈给作者。"
    }
    return "$conclusion\n\n应用：齐鲁课表 $version\n设备：${Build.MANUFACTURER} ${Build.MODEL}\nAndroid：${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）\n组件声明：${declared.isSuccess.display()}\n组件启用：${enabled.display()}\n系统登记：${registered.display()}\n支持弹窗添加：${pinSupported.display()}\n已有组件数量：${instances ?: "未能读取"}"
}
