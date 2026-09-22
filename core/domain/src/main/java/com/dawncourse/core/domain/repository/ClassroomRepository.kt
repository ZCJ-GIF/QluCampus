// QluCampus 0.2.0, GPL-3.0.
package com.dawncourse.core.domain.repository

import com.dawncourse.core.domain.model.AcademicTerm
import java.time.LocalDate

data class ClassroomOption(val id: String, val label: String)
data class ClassroomOptions(val officialUrl: String, val campuses: List<ClassroomOption>, val buildings: List<ClassroomOption>, val nativeQuery: Boolean, val message: String,
    val selectedCampus: String = "", val roomTypes: List<ClassroomOption> = emptyList(),
    val sections: List<Int> = emptyList(), val term: AcademicTerm? = null)
data class ClassroomQuery(val term: AcademicTerm, val firstMonday: LocalDate, val date: LocalDate,
    val startSection: Int, val endSection: Int, val campus: String, val building: String, val roomType: String = "")
data class AvailableClassroom(val id: String, val name: String, val campus: String, val building: String, val capacity: String)
data class ClassroomResult(val query: ClassroomQuery, val rooms: List<AvailableClassroom>, val fetchedAt: Long)
interface ClassroomRepository {
    val officialUrl: String
    suspend fun options(accountId: String, campus: String = "", term: AcademicTerm? = null): ClassroomOptions
    suspend fun query(accountId: String, query: ClassroomQuery): ClassroomResult
}
