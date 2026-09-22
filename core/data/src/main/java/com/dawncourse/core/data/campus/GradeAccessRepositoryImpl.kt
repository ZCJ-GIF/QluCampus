// QluCampus 0.2.0, GPL-3.0. Local feature lock, persisted until explicitly locked by the owner.
package com.dawncourse.core.data.campus
import android.content.Context
import com.dawncourse.core.domain.repository.GradeAccessRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

object GradeAccessPassword {
    private const val DIGEST = "15d45117fb3418bbb52c5bb576ea78fa0bc02608dc5113f21b4d574a76a4aa67"
    fun matches(password: String): Boolean {
        if (!password.matches(Regex("[0-9]{6}"))) return false
        val actual = MessageDigest.getInstance("SHA-256").digest(("QluCampus.grade-access.v1:" + password).toByteArray(Charsets.UTF_8))
        val expected = DIGEST.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return MessageDigest.isEqual(actual, expected)
    }
}

@Singleton
class GradeAccessRepositoryImpl @Inject constructor(@ApplicationContext context: Context) : GradeAccessRepository {
    private val prefs = context.getSharedPreferences("grade_access", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getBoolean("unlocked", false))
    override val unlocked = state.asStateFlow()
    override suspend fun unlock(password: String): Boolean {
        if (!GradeAccessPassword.matches(password)) return false
        if (!prefs.edit().putBoolean("unlocked", true).commit()) return false
        state.value = true
        return true
    }
    override suspend fun lock() {
        state.value = false
        check(prefs.edit().putBoolean("unlocked", false).commit()) { "无法保存锁定状态，请重试" }
    }
}
