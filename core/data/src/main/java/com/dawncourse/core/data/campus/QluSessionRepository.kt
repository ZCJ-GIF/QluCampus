// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.campus

import android.content.Context
import android.webkit.CookieManager
import com.dawncourse.core.domain.model.*
import com.dawncourse.core.domain.repository.SchoolSessionRepository
import com.dawncourse.core.domain.repository.TimetableProfileRepository
import com.dawncourse.core.data.repository.OperationalDataMutationGate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class QluSessionRepository @Inject constructor(@ApplicationContext context: Context,
    private val dao: CampusDao, private val profiles: TimetableProfileRepository,
    private val mutationGate: OperationalDataMutationGate) : SchoolSessionRepository {
    private val prefs = context.getSharedPreferences("qlu_session", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(prefs.getString("account", null)?.let(::SchoolAccount))
    override val account = current.asStateFlow()
    private val revision = AtomicLong(0)
    private val mutation = Mutex()
    data class Ticket(val accountId: String, val revision: Long)

    fun ticket(accountId: String): Ticket {
        if (current.value?.studentNumber != accountId) throw SchoolLoginRequired()
        return Ticket(accountId, revision.get())
    }
    fun check(ticket: Ticket) {
        if (current.value?.studentNumber != ticket.accountId || revision.get() != ticket.revision) throw SchoolLoginRequired()
    }
    suspend fun <T> commit(ticket: Ticket, block: suspend () -> T): T = mutation.withLock { check(ticket); block() }

    override suspend fun beginLogin() = logout()
    override suspend fun logout() = mutation.withLock {
        revision.incrementAndGet()
        current.value = null
        prefs.edit().remove("account").commit()
        val guest = prefs.getLong("guest_profile", 0L)
        if (guest == 0L || profiles.switch(guest) !is ProfileMutationResult.Success) {
            val result = profiles.create(ProfileCreationRequest.Empty("本地课表"))
            if (result is ProfileMutationResult.Success) prefs.edit().putLong("guest_profile", result.activeContext.profile.id).commit()
        }
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                CookieManager.getInstance().removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
            }
            CookieManager.getInstance().flush()
        }
    }

    override suspend fun completeLogin(pageUrl: String, studentNumber: String) = mutation.withLock {
        val uri = runCatching { URI(pageUrl) }.getOrNull()
        if (uri?.scheme != "https" || uri.host != "jw.qlu.edu.cn" || !uri.path.startsWith("/jwglxt/") || uri.path.contains("login", true)) {
            throw SchoolDataException("请先完成学校登录，进入教务系统后再继续")
        }
        if (!studentNumber.matches(Regex("[A-Za-z0-9_-]{5,32}"))) throw SchoolDataException("未识别到学号，请打开个人信息或个人课表页后重试")
        val cookie = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(QluSchoolApi.ROOT) }
        if (cookie.isNullOrBlank()) throw SchoolLoginRequired()
        val stored = dao.account(studentNumber)
        if (stored == null || profiles.switch(stored.profileId) !is ProfileMutationResult.Success) {
            val result = profiles.create(ProfileCreationRequest.Empty("齐鲁工大 · ${SchoolAccount(studentNumber).maskedNumber}"))
            if (result !is ProfileMutationResult.Success) throw SchoolDataException("无法创建账号课表，请重试")
            mutationGate.withMutation { dao.saveAccount(CampusAccountEntity(studentNumber, result.activeContext.profile.id)) }
        }
        revision.incrementAndGet()
        prefs.edit().putString("account", studentNumber).commit()
        current.value = SchoolAccount(studentNumber)
        withContext(Dispatchers.Main) { CookieManager.getInstance().flush() }
    }

    suspend fun cookie(url: String, ticket: Ticket): String {
        check(ticket)
        return withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(url) }
            ?.takeIf { it.isNotBlank() } ?: throw SchoolLoginRequired()
    }
    suspend fun acceptCookies(url: String, cookies: List<String>, ticket: Ticket) = withContext(Dispatchers.Main) {
        check(ticket)
        cookies.forEach { CookieManager.getInstance().setCookie(url, it) }
    }
}
