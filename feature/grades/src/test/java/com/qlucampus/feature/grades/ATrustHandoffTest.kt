package com.qlucampus.feature.grades

import org.junit.Assert.*
import org.junit.Test

class ATrustHandoffTest {
    @Test fun `首次resume不能误触发查询且返回只执行一次`() {
        val gate = ATrustHandoff()
        var calls = 0
        assertTrue(gate.prepare { calls++ })
        assertNull(gate.resume())
        assertEquals(0, calls)
        gate.leftForeground()
        gate.resume()?.invoke()
        gate.resume()?.invoke()
        assertEquals(1, calls)
        assertFalse(gate.hasPending)
    }
    @Test fun `重复点击不能覆盖原查询`() {
        val gate = ATrustHandoff()
        var called = ""
        gate.prepare { called = "原查询" }
        assertFalse(gate.prepare { called = "迟到查询" })
        gate.leftForeground(); gate.resume()?.invoke()
        assertEquals("原查询", called)
    }
    @Test fun `取消及账号切换后不能执行旧查询`() {
        val gate = ATrustHandoff()
        gate.prepare { fail("不应查询旧账号") }
        gate.leftForeground(); gate.cancel()
        assertNull(gate.resume()); assertNull(gate.take())
    }
    @Test fun `未安装时可直接继续但不会重复执行`() {
        val gate = ATrustHandoff()
        var calls = 0
        gate.prepare { calls++ }
        gate.take()?.invoke(); gate.leftForeground(); gate.resume()?.invoke()
        assertEquals(1, calls)
    }
    @Test fun `下一次主动查询仍会等待重新返回`() {
        val gate = ATrustHandoff()
        var calls = 0
        gate.prepare { calls++ }; gate.leftForeground(); gate.resume()?.invoke()
        gate.prepare { calls++ }
        assertNull(gate.resume())
        gate.leftForeground(); gate.resume()?.invoke()
        assertEquals(2, calls)
    }
}
