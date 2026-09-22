// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.campus

import com.dawncourse.core.data.local.AppDatabase
import com.dawncourse.core.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
abstract class CampusModule {
    @Binds @Singleton abstract fun schoolNetwork(impl: QluSchoolNetworkRepository): SchoolNetworkRepository
    @Binds @Singleton abstract fun gradeAccess(impl: GradeAccessRepositoryImpl): GradeAccessRepository
    @Binds @Singleton abstract fun session(impl: QluSessionRepository): SchoolSessionRepository
    @Binds @Singleton abstract fun grades(impl: QluGradeRepository): GradeRepository
    @Binds @Singleton abstract fun timetable(impl: QluTimetableRepository): SchoolTimetableRepository
    @Binds @Singleton abstract fun classrooms(impl: QluClassroomRepository): ClassroomRepository
    companion object { @Provides fun dao(db: AppDatabase): CampusDao = db.campusDao() }
}
