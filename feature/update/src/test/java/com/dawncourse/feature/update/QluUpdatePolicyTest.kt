package com.dawncourse.feature.update

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class QluUpdatePolicyTest {
    @Test fun `未配置关闭及间隔内不自动联网`() {
        val now = 1_000_000_000L
        val configured = UpdatePreferences("https://example.com/version.json", true, 0)
        assertTrue(shouldAutomaticallyCheck(configured, now))
        assertFalse(shouldAutomaticallyCheck(configured.copy(sourceUrl = ""), now))
        assertFalse(shouldAutomaticallyCheck(configured.copy(automatic = false), now))
        assertFalse(shouldAutomaticallyCheck(configured.copy(lastCheckAt = now - 60_000), now))
        assertTrue(shouldAutomaticallyCheck(configured.copy(lastCheckAt = now - 6 * 60 * 60 * 1000), now))
        assertTrue(shouldAutomaticallyCheck(configured.copy(lastCheckAt = now + 1), now))
    }

    @Test fun `公开仓库与直接地址只解析为自身源`() {
        assertEquals("https://raw.githubusercontent.com/owner/QluCampus/main/version.json", normalizeUpdateSourceUrl("https://github.com/owner/QluCampus.git/"))
        assertEquals("https://updates.example.com/qlu/version.json", normalizeUpdateSourceUrl("https://updates.example.com/qlu/"))
        val url = "https://api.github.com/repos/owner/app/contents/version.json?ref=stable"
        assertEquals(url, buildUpdateEndpointConfigs(url).single().versionInfoUrl)
        listOf("http://example.com/version.json", "https://user:pass@example.com/v.json", "file:///v.json", "https://example.com/v.json#fragment").forEach {
            assertNull(normalizeUpdateSourceUrl(it))
        }
    }

    @Test fun `不完整错误应用或异常枚举元数据不能导致崩溃`() {
        val json = """{"applicationId":"com.qlucampus.app","versionCode":8,"versionName":"0.2.6","downloadUrl":"https://example.com/app.apk","sha256":"${"a".repeat(64)}","type":"future","forceUpdate":true}"""
        val info = requireNotNull(validateUpdateInfo(Gson().fromJson(json, UpdateInfo::class.java)))
        assertEquals(UpdateType.STANDARD, info.type)
        assertFalse(info.isForce)
        assertNull(validateUpdateInfo(Gson().fromJson("{}", UpdateInfo::class.java)))
        assertNull(validateUpdateInfo(info.copy(applicationId = "other.app")))
        assertNull(validateUpdateInfo(info.copy(versionCode = 0)))
        assertNull(validateUpdateInfo(info.copy(versionName = "")))
    }
}
