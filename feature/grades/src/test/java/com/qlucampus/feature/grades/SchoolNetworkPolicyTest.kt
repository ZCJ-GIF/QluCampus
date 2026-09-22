package com.qlucampus.feature.grades

import com.dawncourse.core.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SchoolNetworkPolicyTest {
    @Test fun `已有VPN不探测学校也不要求打开客户端`() = runBlocking {
        assertEquals(SchoolNetworkState.VPN_CONNECTED, detectSchoolNetwork({ true }, { fail("已有VPN不必增加探测请求"); false }))
    }
    @Test fun `无VPN但校园网可达时直接查询`() = runBlocking {
        assertEquals(SchoolNetworkState.SCHOOL_REACHABLE, detectSchoolNetwork({ false }, { true }))
    }
    @Test fun `无VPN且学校不可达才请求连接`() = runBlocking {
        assertEquals(SchoolNetworkState.NEED_CONNECTION, detectSchoolNetwork({ false }, { false }))
    }
    @Test fun `每次检查使用当前状态不沿用上次连接`() = runBlocking {
        var connected = true
        assertEquals(SchoolNetworkState.VPN_CONNECTED, detectSchoolNetwork({ connected }, { false }))
        connected = false
        assertEquals(SchoolNetworkState.NEED_CONNECTION, detectSchoolNetwork({ connected }, { false }))
    }
    @Test fun `取消探测不会被解释成应打开客户端`() {
        assertThrows(CancellationException::class.java) { runBlocking {
            detectSchoolNetwork({ false }, { throw CancellationException("cancel") })
        } }
    }
}
