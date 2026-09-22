// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.qlucampus.feature.grades
import org.junit.Assert.*
import org.junit.Test
class SchoolNavigationPolicyTest {
    @Test fun onlySchoolHttpsOriginsAreAllowed() {
        assertTrue(SchoolNavigationPolicy.allowed("https://jw.qlu.edu.cn/jwglxt/"))
        assertTrue(SchoolNavigationPolicy.allowed("https://sso.qlu.edu.cn/login"))
        assertFalse(SchoolNavigationPolicy.allowed("https://jw.qlu.edu.cn.attacker.test/"))
        assertFalse(SchoolNavigationPolicy.allowed("http://jw.qlu.edu.cn/"))
        assertFalse(SchoolNavigationPolicy.allowed("https://user@jw.qlu.edu.cn/"))
        assertFalse(SchoolNavigationPolicy.allowed("file:///sdcard/a.html"))
    }
}
