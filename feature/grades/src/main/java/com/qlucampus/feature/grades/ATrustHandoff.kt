// QluCampus, GPL-3.0. One user query, one external app handoff.
package com.qlucampus.feature.grades

/** 不把回到前台当成 VPN 已连接；只消费用户原来的查询，网络结果由原仓库校验。 */
internal class ATrustHandoff {
    private var pending: (() -> Unit)? = null
    private var leftApp = false
    val hasPending: Boolean get() = pending != null

    fun prepare(action: () -> Unit): Boolean {
        if (hasPending) return false
        pending = action
        leftApp = false
        return true
    }

    fun leftForeground() { if (hasPending) leftApp = true }
    fun resume(): (() -> Unit)? = if (leftApp) take() else null
    fun take(): (() -> Unit)? = pending.also { cancel() }
    fun cancel() { pending = null; leftApp = false }
}
