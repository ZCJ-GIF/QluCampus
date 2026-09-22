package com.dawncourse.core.domain.repository

/** VPN_CONNECTED 不表示已识别 aTrust，也不表示学校登录有效。 */
enum class SchoolNetworkState { VPN_CONNECTED, SCHOOL_REACHABLE, NEED_CONNECTION }

interface SchoolNetworkRepository {
    suspend fun check(): SchoolNetworkState
}

/** 先保留现有 VPN；学校可直连时同样不打扰用户。 */
suspend fun detectSchoolNetwork(
    vpnConnected: () -> Boolean,
    schoolReachable: suspend () -> Boolean
): SchoolNetworkState = when {
    vpnConnected() -> SchoolNetworkState.VPN_CONNECTED
    schoolReachable() -> SchoolNetworkState.SCHOOL_REACHABLE
    else -> SchoolNetworkState.NEED_CONNECTION
}
