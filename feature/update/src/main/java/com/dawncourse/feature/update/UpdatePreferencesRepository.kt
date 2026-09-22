// QluCampus, GPL-3.0. Independent of school credentials and account data.
package com.dawncourse.feature.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UpdatePreferences(val sourceUrl: String, val automatic: Boolean, val lastCheckAt: Long)

internal fun shouldAutomaticallyCheck(settings: UpdatePreferences, now: Long): Boolean =
    settings.automatic && settings.sourceUrl.isNotBlank() &&
        (settings.lastCheckAt <= 0L || now < settings.lastCheckAt || now - settings.lastCheckAt >= 6L * 60 * 60 * 1000)

@Singleton
class UpdatePreferencesRepository @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("qlu_updates", Context.MODE_PRIVATE)
    private fun read() = UpdatePreferences(
        preferences.getString("source_url", null) ?: normalizeUpdateSourceUrl(BuildConfig.DEFAULT_UPDATE_URL).orEmpty(),
        preferences.getBoolean("automatic", true), preferences.getLong("last_check_at", 0L))
    private val mutableState = MutableStateFlow(read())
    val state = mutableState.asStateFlow()

    fun setSource(value: String) {
        val normalized = if (value.isBlank()) "" else requireNotNull(normalizeUpdateSourceUrl(value)) { "请输入 HTTPS 更新地址" }
        preferences.edit().putString("source_url", normalized).remove("last_check_at").apply()
        mutableState.value = read()
    }
    fun setAutomatic(enabled: Boolean) {
        preferences.edit().putBoolean("automatic", enabled).apply()
        mutableState.value = read()
    }
    fun recordCheck(now: Long) {
        preferences.edit().putLong("last_check_at", now).apply()
        mutableState.value = read()
    }
}
