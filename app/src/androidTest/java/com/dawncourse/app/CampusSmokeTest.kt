// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.app

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Until
import com.dawncourse.core.data.campus.CampusGradeEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.toArgb

/** 后台无窗口模拟器验收，使用合成数据，不需要学校账号或网络。 */
@RunWith(AndroidJUnit4::class)
class CampusSmokeTest {
    @Test fun upgradeLockGpaAppearanceAndWidgetWorkTogether() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout = 1000
        val account = "TEST00001"
        // 离线 UI 回归不访问真实发布服务，联网下载另行验收。
        val updatePreferences = context.getSharedPreferences("qlu_updates", Context.MODE_PRIVATE)
        updatePreferences.edit().putBoolean("automatic", false).commit()
        context.getSharedPreferences("qlu_session", Context.MODE_PRIVATE).edit().putString("account", account).commit()
        context.getSharedPreferences("grade_access", Context.MODE_PRIVATE).edit().putBoolean("unlocked", false).commit()
        fun tap(text: String) {
            assertTrue("找不到 $text", device.wait(Until.hasObject(By.text(text)), 15000))
            android.os.SystemClock.sleep(250)
            device.findObjects(By.text(text)).last().click()
            android.os.SystemClock.sleep(200)
        }
        fun scrollTo(selector: androidx.test.uiautomator.BySelector): androidx.test.uiautomator.UiObject2 {
            // Returning to a screen may restore its scroll position. Search both directions.
            repeat(44) { step ->
                val node = device.findObject(selector)
                if (node != null && node.visibleBounds.height() > 20 && node.visibleBounds.centerY() in 300..device.displayHeight - 250) {
                    android.os.SystemClock.sleep(500)
                    return requireNotNull(device.findObject(selector))
                }
                val forward = if (node != null) node.visibleBounds.centerY() >= 300 else step < 22
                val upper = device.displayHeight / 3
                val lower = device.displayHeight * 3 / 4
                device.swipe(device.displayWidth / 2, if (forward) lower else upper,
                    device.displayWidth / 2, if (forward) upper else lower, 18)
                android.os.SystemClock.sleep(400)
            }
            throw AssertionError("滚动后仍找不到 $selector")
        }
        fun shot(name: String) {
            android.os.SystemClock.sleep(500)
            device.executeShellCommand("screencap -p /sdcard/Download/qlu-$name")
        }
        fun assertPhotoDiffersFromOpaque(photo: android.graphics.Bitmap, opaque: android.graphics.Bitmap,
            top: Int, bottom: Int, label: String, minimumDifference: Double = 15.0) {
            var difference = 0L
            var samples = 0
            for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(photo.height) step 9) {
                for (x in photo.width / 10 until photo.width * 9 / 10 step 9) {
                    val a = photo.getPixel(x, y); val b = opaque.getPixel(x, y)
                    difference += kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                        kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                        kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
                    samples++
                }
            }
            assertTrue("$label 像素应有可见差异", samples > 0 && difference.toDouble() / samples / 3 > minimumDifference)
        }
        fun assertDisabledButton(text: String) {
            // Compose 的文字子节点可能仍标记 enabled；禁用语义在按钮父节点上。
            val node = requireNotNull(device.findObject(By.text(text)))
            assertTrue("$text 按钮应禁用", generateSequence(node) { it.parent }.any { !it.isEnabled })
        }
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (device.wait(Until.hasObject(By.text("使用说明与免责声明")), 15000)) {
                device.findObject(By.checkable(true))?.click()
                shot("020-notice.png")
                tap("我知道了")
            }
            assertTrue(device.wait(Until.hasObject(By.text("成绩")), 30000))
            assertNull(device.findObject(By.text("平时成绩")))
            val app = context.applicationContext as DawnApp
            val db = app.databaseStartupRuntime.requireReadyDatabase()
            db.openHelper.readableDatabase.query("PRAGMA user_version").use { it.moveToFirst(); assertEquals(8, it.getInt(0)) }
            val now = java.time.LocalDate.now()
            val year = if (now.monthValue >= 8) now.year else now.year - 1
            val term = if (now.monthValue in 2..7) 2 else 1
            val json = """{"accountId":"$account","term":{"year":$year,"semester":$term},"fetchedAt":1789981200000,"details":[{"courseName":"示例有机化学","courseCode":"TEST-C1","className":"测试班","credits":"2.0","component":"平时成绩","score":"92.50","department":"测试学院"},{"courseName":"示例有机化学","courseCode":"TEST-C1","className":"测试班","credits":"2.0","component":"总评","score":"88","department":"测试学院"}],"points":[{"courseName":"示例有机化学","courseCode":"TEST-C1","className":"测试班","point":"3.8","weightedPoint":"7.6"}]}"""
            runBlocking { db.campusDao().saveGrades(CampusGradeEntity(account, year, term, json, 1789981200000)) }
            tap("成绩")
            scrollTo(By.text("手动打开 aTrust")).click()
            assertTrue(device.wait(Until.hasObject(By.text("无法打开 aTrust")), 10000))
            assertNotNull(device.findObject(By.textContains("未找到可打开的 aTrust")))
            shot("026-atrust-missing.png")
            tap("取消本次操作")
            assertEquals("取消跳转不能覆盖有效成绩", 1789981200000L, runBlocking { db.campusDao().observeGrades(account, year, term).first() }?.fetchedAt)
            scrollTo(By.text("示例有机化学"))
            assertNull(device.findObject(By.text("92.50")))
            scrollTo(By.textContains("3.8"))
            assertNotNull(device.findObject(By.textContains("3.8")))
            shot("020-grades-locked.png")
            // 复用同一合成记录截取及格线两侧及非数字值的外观，不更改学校数据。
            listOf("60", "59.5", "未提供", "优秀").forEachIndexed { index, value ->
                val previewJson = json.replace("\"score\":\"88\"", "\"score\":\"$value\"")
                runBlocking { db.campusDao().saveGrades(CampusGradeEntity(account, year, term, previewJson, 1789981200000)) }
                shot("024-grade-$index.png")
            }
            runBlocking { db.campusDao().saveGrades(CampusGradeEntity(account, year, term, json, 1789981200000)) }
            device.swipe(device.displayWidth / 2, device.displayHeight / 3, device.displayWidth / 2, device.displayHeight * 3 / 4, 20)
            android.os.SystemClock.sleep(600)
            tap("选择课程计算 GPA")
            tap("全选本筛选")
            assertTrue(device.wait(Until.hasObject(By.text("3.8000")), 10000))
            shot("020-gpa.png")
            tap("返回")
            tap("课表")
            device.wait(Until.findObject(By.desc("设置")), 10000).click()
            scrollTo(By.text("检查更新")).click()
            assertTrue(device.wait(Until.hasObject(By.text("自动检查更新")), 10000))
            val originalSource = com.dawncourse.feature.update.UpdatePreferencesRepository(context).state.value.sourceUrl
            val toggle = requireNotNull(device.findObject(By.checkable(true)))
            assertFalse(toggle.isChecked)
            toggle.click()
            assertTrue(updatePreferences.getBoolean("automatic", false))
            toggle.click()
            assertFalse(updatePreferences.getBoolean("automatic", true))
            scrollTo(By.text("设置更新地址")).click()
            device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000).text = "http://example.com/version.json"
            assertTrue(device.wait(Until.hasObject(By.text("请输入不含账号密码的有效 HTTPS 地址")), 10000))
            assertDisabledButton("保存")
            device.findObject(By.clazz("android.widget.EditText")).text = ""
            tap("保存")
            assertDisabledButton("检查更新")
            scrollTo(By.text("设置更新地址")).click()
            device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000).text = originalSource
            tap("保存")
            assertEquals(originalSource, updatePreferences.getString("source_url", null))
            shot("025-update-settings.png")
            device.pressBack()
            val version = scrollTo(By.textContains("齐鲁课表 "))
            shot("020-before-unlock.png")
            val bounds = version.visibleBounds
            repeat(5) { device.click(bounds.centerX(), bounds.centerY()); android.os.SystemClock.sleep(160) }
            assertTrue(device.wait(Until.hasObject(By.text("功能解锁")), 10000))
            device.findObject(By.clazz("android.widget.EditText")).text = "000000"
            tap("开启")
            assertTrue(device.wait(Until.hasObject(By.text("密码不正确，请重试")), 10000))
            val password = requireNotNull(InstrumentationRegistry.getArguments().getString("gradeUnlockCode"))
            device.findObject(By.clazz("android.widget.EditText")).text = password
            tap("开启")
            assertTrue(device.wait(Until.gone(By.text("功能解锁")), 10000))
            assertTrue(context.getSharedPreferences("grade_access", Context.MODE_PRIVATE).getBoolean("unlocked", false))
            assertTrue("重新创建状态仓库后仍应保持开启", com.dawncourse.core.data.campus.GradeAccessRepositoryImpl(context).unlocked.value)
            device.pressBack()
            tap("平时成绩")
            scrollTo(By.text("92.50"))
            shot("020-grades-unlocked.png")
            tap("课表")
            val entry = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext,
                com.dawncourse.feature.widget.DawnWidget.WidgetEntryPoint::class.java)
            val settings = entry.settingsRepository()
            val source = File(context.filesDir, "synthetic-background.png")
            val bitmap = android.graphics.Bitmap.createBitmap(800, 1400, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint()
            for (i in 0..13) { paint.color = if (i % 2 == 0) android.graphics.Color.rgb(80, 125, 190) else android.graphics.Color.rgb(220, 180, 140); canvas.drawRect(0f, i * 100f, 800f, (i + 1) * 100f, paint) }
            source.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            runBlocking {
                settings.setWallpaperUri(null)
                settings.setCampusAppearance(com.dawncourse.core.domain.model.CampusAppearance())
                val profile = requireNotNull(db.timetableProfileDao().getFirstProfile())
                val monday = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                val semester = db.semesterDao().insertSemester(com.dawncourse.core.data.local.entity.SemesterEntity(profileId = profile.id, name = "测试秋季", startDate = monday.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), weekCount = 20))
                db.timetableProfileDao().updateActiveSemesterId(profile.id, semester)
                val courses = (0..5).map { i -> com.dawncourse.core.data.local.entity.CourseEntity(semesterId = semester, name = if (i == 0) "高分子材料科学与工程基础" else "测试课程${i+1}", teacher = "张老师", location = "菏泽校区 菏泽北楼207", dayOfWeek = now.dayOfWeek.value, startSection = i * 2 + 1, duration = 2, startWeek = 1, endWeek = 20, weekType = 0, color = "") }
                db.courseDao().insertCourses(courses + (1..5).filter { it != now.dayOfWeek.value }.flatMap { day -> courses.take(4).filterIndexed { i, _ -> (i + day) % 2 == 0 }.map { it.copy(dayOfWeek = day) } })
                val timeline = entry.widgetTimelineBuilder().build(today = now, now = java.time.LocalTime.of(23, 59))
                assertEquals("已结束的课程仍应保留在今日列表", 6, timeline.displayCourses.size)
                assertNotNull(timeline.nextUpdateMillis)
                val palette = timeline.coursePalette
                assertEquals(6, palette.values.distinct().size)
                courses.forEach { c ->
                    val course = com.dawncourse.core.domain.model.Course(semesterId = semester, name = c.name, teacher = c.teacher, location = c.location, dayOfWeek = c.dayOfWeek, startSection = c.startSection, duration = c.duration, startWeek = 1, endWeek = 20, weekType = 0, color = "")
                    val bg = com.dawncourse.core.ui.util.CourseColorUtils.parseColor(com.dawncourse.core.ui.util.CourseColorUtils.getCourseColor(course, true, palette))
                    val fg = com.dawncourse.core.ui.util.CourseColorUtils.getBestContentColor(bg)
                    assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(fg.toArgb(), bg.toArgb()) >= 4.5)
                }
                db.courseDao().insertCourses(listOf(courses.first().copy(name = "下周实验课", dayOfWeek = if (now.dayOfWeek.value == 7) 6 else 7, startWeek = 2)))
            }
            assertTrue(device.wait(Until.hasObject(By.text("高分子材料科学与工程基础")), 15000))
            assertTrue("默认布局应显示完整地址", device.wait(Until.hasObject(By.text("菏泽校区 菏泽北楼207")), 10000))
            assertNotNull("第九节课程必须在首屏可见", device.findObject(By.text("测试课程5")))
            assertNotNull("第十节时间轴必须在首屏可见", device.findObject(By.text("10")))
            assertNull("周表将教师信息留在课程详情", device.findObject(By.text("张老师")))
            val choose = requireNotNull(device.findObject(By.descStartsWith("切换课表：")))
            val refresh = requireNotNull(device.findObject(By.desc("刷新课表")))
            val manage = requireNotNull(device.findObject(By.desc("管理课表")))
            assertEquals("课表操作直接排在同一行", choose.visibleBounds.centerY(), refresh.visibleBounds.centerY())
            assertEquals(choose.visibleBounds.centerY(), manage.visibleBounds.centerY())
            choose.click()
            assertTrue("无需打开日期菜单就能切换课表", device.wait(Until.hasObject(By.text("切换课表")), 10000))
            device.pressBack()
            shot("022-timetable-default.png")
            scrollTo(By.text("测试课程6"))
            shot("022-timetable-evening.png")
            repeat(3) {
                device.swipe(device.displayWidth / 2, device.displayHeight / 3, device.displayWidth / 2, device.displayHeight * 3 / 4, 18)
                android.os.SystemClock.sleep(200)
            }
            // New styles change presentation only; preferences survive repository recreation.
            for (style in com.dawncourse.core.domain.model.CampusStyle.entries.filter { it != com.dawncourse.core.domain.model.CampusStyle.CLASSIC }) {
                runBlocking {
                    settings.setThemeMode(com.dawncourse.core.domain.model.AppThemeMode.LIGHT)
                    settings.setCampusAppearance(settings.settings.first().campusAppearance.copy(style = style))
                }
                android.os.SystemClock.sleep(600)
                assertNotNull(device.findObject(By.text("高分子材料科学与工程基础")))
                shot("029-style-${style.name.lowercase()}.png")
            }
            runBlocking { settings.setCampusAppearance(settings.settings.first().campusAppearance.copy(adaptiveCourseColors = false)) }
            shot("029-wakeup-pastel.png")
            runBlocking { settings.setCampusAppearance(settings.settings.first().campusAppearance.copy(courseBorders = true)) }
            shot("029-wakeup-borders.png")
            runBlocking {
                settings.setWallpaperUri(android.net.Uri.fromFile(source).toString())
                // Match the wallpaper picker ViewModel, which also prepares the blur cache.
                settings.generateBlurredWallpaper(settings.settings.first().wallpaperUri)
                assertNotNull(settings.settings.first().blurredWallpaperUri)
                settings.setCampusAppearance(settings.settings.first().campusAppearance.copy(
                    style = com.dawncourse.core.domain.model.CampusStyle.SAGE, glassOpacity = .4f,
                    wallpaperOnHeader = true, wallpaperOnNavigation = true, wallpaperOnCourses = true,
                    wallpaperOnPanels = true, adaptiveCourseColors = true, courseBorders = false))
            }
            android.os.SystemClock.sleep(1200)
            shot("029-background-all-areas.png")
            val headerBounds = requireNotNull(device.findObject(By.desc("设置"))).visibleBounds
            val navBounds = device.findObjects(By.text("课表")).last().visibleBounds
            val photoBars = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            runBlocking { settings.setCampusAppearance(settings.settings.first().campusAppearance.copy(barWallpaperOpacity = 1f)) }
            android.os.SystemClock.sleep(700)
            val opaqueBars = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            assertPhotoDiffersFromOpaque(photoBars, opaqueBars, headerBounds.top, headerBounds.bottom, "顶栏")
            assertPhotoDiffersFromOpaque(photoBars, opaqueBars, navBounds.top - 55, navBounds.bottom, "导航栏")
            photoBars.recycle(); opaqueBars.recycle()
            runBlocking { settings.setCampusAppearance(settings.settings.first().campusAppearance.copy(barWallpaperOpacity = .18f)) }
            device.wait(Until.findObject(By.desc("设置")), 10000).click()
            scrollTo(By.textStartsWith("云雾蓝")).click()
            android.os.SystemClock.sleep(400)
            assertEquals(com.dawncourse.core.domain.model.CampusStyle.MIST, runBlocking { settings.settings.first().campusAppearance.style })
            scrollTo(By.textStartsWith("Wake Up")).click()
            android.os.SystemClock.sleep(400)
            assertEquals(com.dawncourse.core.domain.model.CampusStyle.WAKE_UP, runBlocking { settings.settings.first().campusAppearance.style })
            shot("029-style-settings.png")
            val appearanceToggles = listOf("课表顶栏与日期栏", "底部导航栏", "课程卡片", "设置与查询面板", "课程颜色适应背景")
            fun checkedNode(node: androidx.test.uiautomator.UiObject2) =
                generateSequence(node) { it.parent }.firstOrNull { it.isCheckable }
                    ?: requireNotNull(node.findObject(By.checkable(true)))
            listOf("课程边缘线", "栏位与面板毛玻璃").forEach { label ->
                val toggle = scrollTo(By.desc(label))
                assertFalse("$label 默认关闭", checkedNode(toggle).isChecked)
                toggle.click()
                android.os.SystemClock.sleep(250)
                assertTrue(checkedNode(requireNotNull(device.findObject(By.desc(label)))).isChecked)
            }
            val extras = runBlocking { com.dawncourse.core.data.repository.SettingsRepositoryImpl(context).settings.first().campusAppearance }
            assertTrue(extras.courseBorders && extras.barWallpaperBlur)
            assertEquals(.18f, extras.barWallpaperOpacity, .001f)
            // A panel must reveal the wallpaper at low tint, regardless of its blur choice.
            scrollTo(By.desc("设置与查询面板"))
            shot("029-panel-blurred.png")
            val blurredPanel = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            runBlocking { settings.setCampusAppearance(settings.settings.first().campusAppearance.copy(barWallpaperBlur = false)) }
            android.os.SystemClock.sleep(700)
            shot("029-panel-photo.png")
            val photoPanel = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            assertPhotoDiffersFromOpaque(photoPanel, blurredPanel, device.displayHeight / 3, device.displayHeight * 2 / 3, "毛玻璃开关", 3.0)
            blurredPanel.recycle()
            runBlocking { settings.setCampusAppearance(settings.settings.first().campusAppearance.copy(barWallpaperOpacity = 1f)) }
            android.os.SystemClock.sleep(700)
            val opaquePanel = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            assertPhotoDiffersFromOpaque(photoPanel, opaquePanel, device.displayHeight / 3, device.displayHeight * 2 / 3, "设置面板")
            photoPanel.recycle(); opaquePanel.recycle()
            runBlocking { settings.setCampusAppearance(settings.settings.first().campusAppearance.copy(barWallpaperOpacity = .18f, barWallpaperBlur = false)) }
            appearanceToggles.forEach { label ->
                val toggle = scrollTo(By.desc(label))
                assertTrue("$label 应开启", checkedNode(toggle).isChecked)
                toggle.click()
                android.os.SystemClock.sleep(200)
                assertFalse("$label 应关闭", checkedNode(requireNotNull(device.findObject(By.desc(label)))).isChecked)
            }
            val persistedOff = runBlocking { com.dawncourse.core.data.repository.SettingsRepositoryImpl(context).settings.first().campusAppearance }
            assertFalse(persistedOff.wallpaperOnHeader || persistedOff.wallpaperOnNavigation || persistedOff.wallpaperOnCourses || persistedOff.wallpaperOnPanels || persistedOff.adaptiveCourseColors)
            appearanceToggles.forEach { label -> scrollTo(By.desc(label)).click(); android.os.SystemClock.sleep(200) }
            val persistedOn = runBlocking { com.dawncourse.core.data.repository.SettingsRepositoryImpl(context).settings.first().campusAppearance }
            assertTrue(persistedOn.wallpaperOnHeader && persistedOn.wallpaperOnNavigation && persistedOn.wallpaperOnCourses && persistedOn.wallpaperOnPanels && persistedOn.adaptiveCourseColors)
            device.pressBack()
            runBlocking { settings.setThemeMode(com.dawncourse.core.domain.model.AppThemeMode.DARK) }
            android.os.SystemClock.sleep(600)
            shot("029-background-dark.png")
            runBlocking {
                settings.setThemeMode(com.dawncourse.core.domain.model.AppThemeMode.LIGHT)
                settings.setCampusAppearance(com.dawncourse.core.domain.model.CampusAppearance())
                settings.generateBlurredWallpaper(settings.settings.first().wallpaperUri)
                settings.setCampusAppearance(settings.settings.first().campusAppearance.copy(highContrast = true))
            }
            shot("020-timetable-contrast.png")
            runBlocking { settings.setCampusAppearance(settings.settings.first().campusAppearance.copy(fontScale = 1.5f, highContrast = false)) }
            android.os.SystemClock.sleep(700)
            shot("020-timetable-glass-large-font.png")
            runBlocking { settings.setCampusAppearance(settings.settings.first().campusAppearance.copy(fontScale = 1f)) }
            device.wait(Until.findObject(By.desc("设置")), 10000).click()
            scrollTo(By.text("当前学期")).click()
            val firstMonday = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            tap(firstMonday.toString())
            assertTrue(device.wait(Until.hasObject(By.text("选择第一教学周的周一")), 10000))
            shot("021-first-monday-calendar.png")
            tap("取消")
            tap("取消")
            scrollTo(By.text("锁定并隐藏平时成绩")).click()
            assertTrue(device.wait(Until.gone(By.text("锁定并隐藏平时成绩")), 10000))
            assertFalse(context.getSharedPreferences("grade_access", Context.MODE_PRIVATE).getBoolean("unlocked", true))
            device.pressBack()
            assertTrue(device.wait(Until.gone(By.text("平时成绩")), 10000))
            tap("空教室")
            assertTrue(device.wait(Until.hasObject(By.text("空教室查询")), 10000))
            scrollTo(By.text("手动打开 aTrust")).click()
            assertTrue(device.wait(Until.hasObject(By.text("无法打开 aTrust")), 10000))
            tap("取消本次操作")
            scrollTo(By.text("查询前打开 aTrust"))
            device.findObject(By.desc("查询前打开 aTrust")).click()
            assertFalse(context.getSharedPreferences("school_vpn", Context.MODE_PRIVATE).getBoolean("open_atrust", true))
            device.findObject(By.desc("查询前打开 aTrust")).click()
            assertTrue(context.getSharedPreferences("school_vpn", Context.MODE_PRIVATE).getBoolean("open_atrust", false))
            scrollTo(By.text("在应用内打开学校查询"))
            assertTrue("官方查询不应依赖原生选项加载成功", device.findObject(By.text("在应用内打开学校查询")).isEnabled)
            shot("020-classrooms.png")
            tap("课表")
            device.wait(Until.findObject(By.desc("设置")), 10000).click()
            repeat(22) {
                if (device.findObject(By.text("添加今日课表到桌面")) == null) {
                    device.swipe(device.displayWidth / 2, device.displayHeight / 3, device.displayWidth / 2, device.displayHeight * 3 / 4, 18)
                    android.os.SystemClock.sleep(200)
                }
            }
            scrollTo(By.text("检测桌面组件")).click()
            assertTrue(device.wait(Until.hasObject(By.textContains("系统登记：是")), 10000))
            assertTrue(device.wait(Until.hasObject(By.textContains("组件启用：是")), 10000))
            shot("023-widget-registration.png")
            tap("关闭检测")
            scrollTo(By.text("桌面小组件添加帮助")).click()
            assertTrue(device.wait(Until.hasObject(By.textContains("安卓小部件")), 10000))
            shot("023-widget-help.png")
            tap("关闭")
            scrollTo(By.text("添加今日课表到桌面")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Add to home screen")), 10000))
            device.pressBack() // 用户取消不能显示成功；帮助仍可进入。
            assertTrue(device.wait(Until.hasObject(By.textContains("尚未确认成功")), 10000))
            assertNull(device.findObject(By.text("桌面已确认添加今日课表")))
            shot("023-widget-unconfirmed.png")
            tap("再次请求系统添加")
            tap("Add to home screen") // 专用 Pixel Launcher 英文测试镜像的系统确认。
            val pinPreferences = context.getSharedPreferences("widget_pin_confirmation", Context.MODE_PRIVATE)
            val requestedToken = pinPreferences.getString("pending_token", null)
            assertNotNull(requestedToken)
            val deadline = android.os.SystemClock.elapsedRealtime() + 10000
            while (pinPreferences.getString("confirmed_token", null) != requestedToken && android.os.SystemClock.elapsedRealtime() < deadline) {
                android.os.SystemClock.sleep(100)
            }
            assertEquals("必须收到实际添加回调", requestedToken, pinPreferences.getString("confirmed_token", null))
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            assertTrue(device.wait(Until.hasObject(By.text("桌面已确认添加今日课表")), 10000))
            shot("023-widget-confirmed.png")
            tap("关闭")
            device.pressHome()
            assertTrue("桌面组件应显示今日完整课程列表", device.wait(Until.hasObject(By.text("高分子材料科学与工程基础")), 15000))
            shot("020-widget.png")
            val host = device.findObjects(By.descContains("今日课表")).firstOrNull { it.findObject(By.text("高分子材料科学与工程基础")) != null }
            assertNotNull(host)
            val list = requireNotNull(host).findObject(By.scrollable(true))
            assertNotNull("组件中应存在可滚动的课程列表", list)
            val area = requireNotNull(list).visibleBounds
            repeat(8) { if (device.findObject(By.text("测试课程6")) == null) {
                device.swipe(area.centerX(), area.bottom - 30, area.centerX(), area.top + 30, 25); android.os.SystemClock.sleep(350)
            } }
            assertNotNull("第六门课应能滚动查看", device.findObject(By.text("测试课程6")))
            shot("020-widget-scrolled.png")
            // 点击组件的标题，核对独立包名对应的启动入口。
            val title = device.findObject(By.text("第1周"))
            assertNotNull(title)
            title.click()
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 10000))
        } finally { shot("020-last-screen.png") }
    }
}
