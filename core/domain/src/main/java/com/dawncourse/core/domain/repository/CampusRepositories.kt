// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.domain.repository

import com.dawncourse.core.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SchoolSessionRepository {
    val account: StateFlow<SchoolAccount?>
    suspend fun beginLogin()
    suspend fun completeLogin(pageUrl: String, studentNumber: String)
    suspend fun logout()
}

interface GradeRepository {
    fun observe(accountId: String, term: AcademicTerm): Flow<GradeSnapshot?>
    fun observeAll(accountId: String): Flow<List<GradeSnapshot>>
    suspend fun refresh(accountId: String, term: AcademicTerm)
    suspend fun exportXlsx(snapshot: GradeSnapshot): ByteArray
}

interface SchoolTimetableRepository {
    suspend fun preview(accountId: String, term: AcademicTerm): SchoolTimetablePreview
    suspend fun commit(preview: SchoolTimetablePreview, semesterStart: Long): ImportCommitResult
    suspend fun lastImportedAt(accountId: String, term: AcademicTerm): Long?
    fun observeImports(accountId: String): Flow<List<SchoolImportTarget>>
    suspend fun save(preview: SchoolTimetablePreview, semesterStart: Long, target: SchoolImportTarget?, newName: String): ImportCommitResult
    suspend fun refresh(target: SchoolImportTarget): ImportCommitResult
}

/** 由既有导入模块提供正方 HTML 解析，其他 feature 只依赖此接口。 */
interface SchoolHtmlParser { suspend fun parse(html: String): List<Course> }
