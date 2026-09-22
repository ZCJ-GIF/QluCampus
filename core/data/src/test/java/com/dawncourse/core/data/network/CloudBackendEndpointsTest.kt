// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 齐鲁版不得继承上游上传或脚本下载服务器。 */
class CloudBackendEndpointsTest {

    @Test
    fun `齐鲁版没有上游诊断和解析上传端点`() {
        val endpoints = CloudBackendEndpoints.sensitiveApiBaseUrls

        assertTrue(endpoints.isEmpty())
    }

    @Test
    fun `齐鲁版只加载随 APK 发布的学校适配`() {
        val sensitiveUrls = CloudBackendEndpoints.sensitiveApiBaseUrls.map { it.baseUrl }.toSet()
        val readOnlyUrls = CloudBackendEndpoints.signedReadOnlyBaseUrls.map { it.baseUrl }.toSet()

        assertTrue(readOnlyUrls.isEmpty())
        assertTrue(sensitiveUrls.isEmpty())
    }
}
