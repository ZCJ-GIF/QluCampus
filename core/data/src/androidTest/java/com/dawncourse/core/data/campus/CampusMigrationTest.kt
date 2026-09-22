// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.campus

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dawncourse.core.data.local.AppDatabaseMigrations
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampusMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        "com.dawncourse.core.data.local.AppDatabase", FrameworkSQLiteOpenHelperFactory())
    private val name = "campus-migration-test"
    @After fun clean() { InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name) }
    @Test fun version7To8PreservesImportedBindingAndAllowsSameTermCopies() {
        helper.createDatabase(name, 7).apply {
            execSQL("INSERT INTO timetable_profiles VALUES (1,'test','原课表',1,0,0,0)")
            execSQL("INSERT INTO semesters VALUES (1,1,'秋季',100,20)")
            execSQL("INSERT INTO campus_accounts VALUES ('account',1)")
            execSQL("INSERT INTO campus_imports VALUES ('account',2026,1,1,1,99)")
            execSQL("INSERT INTO campus_grades VALUES ('account',2026,1,'preserved-payload',88)")
            close()
        }
        helper.runMigrationsAndValidate(name, 8, true, AppDatabaseMigrations.MIGRATION_7_8).apply {
            query("SELECT importedAt FROM campus_imports WHERE semesterId=1").use { assertTrue(it.moveToFirst()); assertEquals(99L,it.getLong(0)) }
            query("SELECT payload FROM campus_grades").use { assertTrue(it.moveToFirst()); assertEquals("preserved-payload",it.getString(0)) }
            execSQL("INSERT INTO semesters VALUES (2,1,'副本',200,20)")
            execSQL("INSERT INTO campus_imports VALUES ('account',2026,1,1,2,101)")
            query("SELECT count(*) FROM campus_imports").use { it.moveToFirst(); assertEquals(2,it.getInt(0)) }
            // MigrationTestHelper opens a raw SQLite helper; Room enables this pragma in production.
            execSQL("PRAGMA foreign_keys=ON")
            execSQL("DELETE FROM semesters WHERE id=2")
            query("SELECT count(*) FROM campus_imports").use { it.moveToFirst(); assertEquals(1,it.getInt(0)) }
            close()
        }
    }
    @Test fun version6To7PreservesCoursesAndIsolatesAccountAndTerm() {
        helper.createDatabase(name, 6).apply {
            execSQL("INSERT INTO timetable_profiles (id, uuid, name, activeSemesterId, lastUsedAt, sortOrder, archived) VALUES (1, 'test', '原课表', NULL, 0, 0, 0)")
            execSQL("INSERT INTO semesters (id, profileId, name, startDate, weekCount) VALUES (1,1,'原学期',0,20)")
            execSQL("INSERT INTO courses (id,semesterId,name,teacher,location,dayOfWeek,startSection,duration,startWeek,endWeek,weekType,color,isModified,note,originId) VALUES (1,1,'保留课程','','',1,1,2,1,16,0,'',0,'',1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 7, true, AppDatabaseMigrations.MIGRATION_6_7).apply {
            query("SELECT name FROM courses WHERE id=1").use { assertTrue(it.moveToFirst()); assertEquals("保留课程", it.getString(0)) }
            execSQL("INSERT INTO campus_grades VALUES ('account-a',2025,1,'A1',10)")
            execSQL("INSERT INTO campus_grades VALUES ('account-a',2025,2,'A2',11)")
            execSQL("INSERT INTO campus_grades VALUES ('account-b',2025,1,'B1',12)")
            execSQL("INSERT OR REPLACE INTO campus_grades VALUES ('account-a',2025,1,'new-A1',13)")
            query("SELECT payload FROM campus_grades WHERE accountId='account-b'").use { assertTrue(it.moveToFirst()); assertEquals("B1",it.getString(0)) }
            query("SELECT count(*) FROM campus_grades").use { it.moveToFirst(); assertEquals(3,it.getInt(0)) }
            close()
        }
    }
}
