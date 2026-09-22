// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.feature.import_module.engine

import android.content.Context
import com.dawncourse.core.domain.model.Course
import com.dawncourse.core.domain.model.SchoolDataException
import com.dawncourse.core.domain.repository.SchoolHtmlParser
import com.dawncourse.feature.import_module.model.ParsedCourse
import com.dawncourse.feature.import_module.model.toDomainCourse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** 齐鲁课表：复用原项目随 APK 发布的正方解析器，不下载远程脚本。 */
class SchoolHtmlParserImpl @Inject constructor(@ApplicationContext private val context: Context,
    private val engine: ScriptEngine) : SchoolHtmlParser {
    override suspend fun parse(html: String): List<Course> {
        fun asset(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }
        val result = engine.parseHtml(asset("parsers/zhengfang.js"), html, asset("runtime/script_host.js"),
            listOf(asset("parsers/common_parser_utils.js")))
        if (!result.ok || !result.schemaValid) throw SchoolDataException("网页中未识别到有效课表，请打开个人课表页面")
        val type = object : TypeToken<List<ParsedCourse>>() {}.type
        return Gson().fromJson<List<ParsedCourse>>(result.raw, type).map { it.toDomainCourse() }
    }
}
@Module @InstallIn(SingletonComponent::class)
abstract class SchoolParserModule {
    @Binds abstract fun parser(impl: SchoolHtmlParserImpl): SchoolHtmlParser
}
