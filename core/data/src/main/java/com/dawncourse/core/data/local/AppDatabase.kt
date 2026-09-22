// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.local

import com.dawncourse.core.data.campus.*
import androidx.room.Database
import androidx.room.RoomDatabase
import com.dawncourse.core.data.local.dao.CourseDao
import com.dawncourse.core.data.local.dao.SemesterDao
import com.dawncourse.core.data.local.dao.SyncSourceBindingDao
import com.dawncourse.core.data.local.dao.TimetableProfileDao
import com.dawncourse.core.data.local.entity.CourseEntity
import com.dawncourse.core.data.local.entity.SemesterEntity
import com.dawncourse.core.data.local.entity.SyncSourceBindingEntity
import com.dawncourse.core.data.local.entity.TimetableProfileEntity

/**
 * 应用程序主数据库
 *
 * 定义了 Room 数据库的配置，包含实体列表和版本号。
 * 使用 @Database 注解声明数据库元数据。
 *
 * @property entities 数据库包含的实体表列表
 * @property version 数据库版本号，当表结构变更时需升级版本并提供迁移策略
 * @property exportSchema 是否导出 schema 文件并纳入版本控制，用于迁移验证
 */
@Database(
    entities = [
        CourseEntity::class,
        CampusGradeEntity::class,
        CampusGradeSummaryEntity::class,
        CampusAccountEntity::class,
        CampusImportEntity::class,
        SemesterEntity::class,
        TimetableProfileEntity::class,
        SyncSourceBindingEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    /**
     * 获取 CourseDao 实例
     *
     * Room 会自动实现此抽象方法。
     */
    abstract fun campusDao(): CampusDao
    abstract fun courseDao(): CourseDao
    abstract fun semesterDao(): SemesterDao
    abstract fun timetableProfileDao(): TimetableProfileDao
    abstract fun syncSourceBindingDao(): SyncSourceBindingDao
}
