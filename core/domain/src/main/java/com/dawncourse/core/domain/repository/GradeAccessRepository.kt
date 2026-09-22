// QluCampus 0.2.0, GPL-3.0.
package com.dawncourse.core.domain.repository
import kotlinx.coroutines.flow.StateFlow

interface GradeAccessRepository {
    val unlocked: StateFlow<Boolean>
    suspend fun unlock(password: String): Boolean
    suspend fun lock()
}
