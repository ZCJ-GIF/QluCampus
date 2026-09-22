// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.campus

import com.dawncourse.core.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QluSchoolApi @Inject constructor(private val sessions: QluSessionRepository) {
    companion object { const val BASE = "https://jw.qlu.edu.cn/"; const val ROOT = "${BASE}jwglxt/" }
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()

    suspend fun page(path: String, ticket: QluSessionRepository.Ticket): String = withContext(Dispatchers.IO) {
        require(!path.contains(":") && !path.startsWith("/") && !path.contains(".."))
        val url = ROOT + path
        val request = Request.Builder().url(url).header("Cookie", sessions.cookie(url, ticket)).get().build()
        client.newCall(request).execute().use { response ->
            if (response.code in 300..399 || response.code == 401) throw SchoolLoginRequired()
            if (response.code == 403) throw SchoolDataException("学校未开放此功能或当前账号无权限")
            if (!response.isSuccessful) throw SchoolDataException("学校页面暂不可用（${response.code}）")
            val input = response.body?.byteStream() ?: throw SchoolDataException("学校返回空页面")
            val output = ByteArrayOutputStream()
            input.use { stream -> val buffer = ByteArray(8192)
                while (true) { val n = stream.read(buffer); if (n < 0) break
                    if (output.size() + n > 4 * 1024 * 1024) throw SchoolDataException("学校页面过大")
                    output.write(buffer, 0, n)
                }
            }
            sessions.acceptCookies(url, response.headers("Set-Cookie"), ticket)
            sessions.check(ticket)
            output.toString("UTF-8").also {
                if (org.jsoup.Jsoup.parse(it).select("input[type=password]").isNotEmpty() || it.contains("统一身份认证")) throw SchoolLoginRequired()
            }
        }
    }

    suspend fun post(path: String, referer: String, fields: List<Pair<String, String>>, ticket: QluSessionRepository.Ticket): ByteArray {
        require(!path.contains(":") && !path.startsWith("/") && !path.contains(".."))
        val url = ROOT + path
        val cookie = sessions.cookie(url, ticket)
        val request = Request.Builder().url(url).header("Cookie", cookie).header("Referer", ROOT + referer)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()).build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (response.code in 300..399 || response.code == 401 || response.code == 403) throw SchoolLoginRequired()
                if (!response.isSuccessful) throw SchoolDataException("学校服务暂不可用（${response.code}），请稍后重试")
                val body = response.body ?: throw SchoolDataException("学校返回了空响应")
                if (body.contentLength() > XlsxTableCodec.MAX_DOWNLOAD) throw SchoolDataException("学校返回的数据过大")
                val output = ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (output.size() + n > XlsxTableCodec.MAX_DOWNLOAD) throw SchoolDataException("学校返回的数据过大")
                        output.write(buffer, 0, n)
                    }
                }
                sessions.acceptCookies(url, response.headers("Set-Cookie"), ticket)
                sessions.check(ticket)
                output.toByteArray()
            }
        }
    }

    suspend fun gradeDetails(term: AcademicTerm, ticket: QluSessionRepository.Ticket): ByteArray = post(
        "cjcx/cjcx_dcXsKccjList.html?gnmkdm=N305005&layout=default",
        "cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default",
        commonGradeFields(term) + listOf("dcclbh" to "JW_N305005_XS") + listOf(
            "kcmc@课程名称", "xnmmc@学年", "xqmmc@学期", "kkbmmc@开课学院", "kch@课程代码",
            "jxbmc@教学班", "xf@学分", "xmcj@成绩", "xmblmc@成绩分项"
        ).map { "exportModel.selectCol" to it }, ticket)

    suspend fun gradeSummary(term: AcademicTerm, ticket: QluSessionRepository.Ticket): ByteArray = post(
        "cjcx/cjcx_dcListByXs.html", "cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default",
        commonGradeFields(term) + listOf("dcclbh" to "JW_N305005_XSCXCJ") +
            listOf("kcmc@课程名称@120", "kch@课程代码@80", "jxbmc@教学班@80", "xf@学分@50", "cj@成绩@50", "jd@绩点@50", "xfjd@学分绩点@80")
                .map { "exportModel.selectCol" to it }, ticket)

    suspend fun gradePoints(term: AcademicTerm, ticket: QluSessionRepository.Ticket): ByteArray = post(
        "cjcx/cjcx_dcListByXs.html", "cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default",
        commonGradeFields(term) + listOf("dcclbh" to "JW_N305005_XSCXCJ", "queryModel.sortOrder" to "asc") +
            listOf("kcmc@课程名称@120", "jd@绩点@50", "xfjd@学分绩点@80").map { "exportModel.selectCol" to it }, ticket)

    private fun commonGradeFields(term: AcademicTerm) = listOf("gnmkdmKey" to "N305005", "xnm" to term.year.toString(),
        "xqm" to term.schoolTermCode, "exportModel.exportWjgs" to "xls", "fileName" to "成绩单")

    suspend fun timetable(term: AcademicTerm, ticket: QluSessionRepository.Ticket) = post(
        "kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151", "kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151&layout=default",
        listOf("xnm" to term.year.toString(), "xqm" to term.schoolTermCode, "kzlx" to "ck"), ticket).toString(Charsets.UTF_8)
}
