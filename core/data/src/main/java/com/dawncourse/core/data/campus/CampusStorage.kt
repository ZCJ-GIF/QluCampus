// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.campus

import androidx.room.*
import com.dawncourse.core.data.local.entity.SemesterEntity
import com.dawncourse.core.data.local.entity.TimetableProfileEntity
import kotlinx.coroutines.flow.Flow
import com.dawncourse.core.domain.model.SchoolImportTarget

@Entity(tableName = "campus_grades", primaryKeys = ["accountId", "academicYear", "semester"])
data class CampusGradeEntity(val accountId: String, val academicYear: Int, val semester: Int, val payload: String, val fetchedAt: Long)

@Entity(tableName = "campus_grade_summaries", primaryKeys = ["accountId", "academicYear", "semester"])
data class CampusGradeSummaryEntity(val accountId: String, val academicYear: Int, val semester: Int, val payload: String, val fetchedAt: Long)

@Entity(tableName = "campus_accounts", foreignKeys = [ForeignKey(entity = TimetableProfileEntity::class,
    parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)], indices = [Index("profileId")])
data class CampusAccountEntity(@PrimaryKey val accountId: String, val profileId: Long)

@Entity(tableName = "campus_imports", primaryKeys = ["semesterId"],
    foreignKeys = [ForeignKey(entity = SemesterEntity::class, parentColumns = ["id"], childColumns = ["semesterId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("semesterId")])
data class CampusImportEntity(val accountId: String, val academicYear: Int, val semester: Int, val profileId: Long, val semesterId: Long, val importedAt: Long)

@Dao
interface CampusDao {
    @Query("SELECT * FROM campus_grade_summaries WHERE accountId=:account") fun observeSummaries(account: String): Flow<List<CampusGradeSummaryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSummary(entity: CampusGradeSummaryEntity)
    @Query("SELECT * FROM campus_accounts WHERE accountId=:account") suspend fun account(account: String): CampusAccountEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAccount(account: CampusAccountEntity)
    @Query("SELECT * FROM campus_grades WHERE accountId=:account AND academicYear=:year AND semester=:term")
    fun observeGrades(account: String, year: Int, term: Int): Flow<CampusGradeEntity?>
    @Query("SELECT * FROM campus_grades WHERE accountId=:account ORDER BY academicYear DESC, semester DESC")
    fun observeAllGrades(account: String): Flow<List<CampusGradeEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveGrades(entity: CampusGradeEntity)
    @Query("SELECT * FROM campus_imports WHERE accountId=:account AND academicYear=:year AND semester=:term ORDER BY importedAt DESC LIMIT 1")
    suspend fun imported(account: String, year: Int, term: Int): CampusImportEntity?
    @Query("SELECT * FROM campus_imports WHERE accountId=:account ORDER BY importedAt DESC LIMIT 1")
    suspend fun latestImport(account: String): CampusImportEntity?
    @Query("SELECT * FROM campus_imports WHERE semesterId=:id") suspend fun importBySemester(id: Long): CampusImportEntity?
    @Query("DELETE FROM campus_imports WHERE semesterId=:id") suspend fun deleteImportBySemester(id: Long)
    @Query("SELECT i.*, p.name AS profileName, s.name AS semesterName, s.startDate, s.weekCount FROM campus_imports i JOIN semesters s ON s.id=i.semesterId JOIN timetable_profiles p ON p.id=i.profileId WHERE i.accountId=:account ORDER BY i.importedAt DESC")
    fun observeImports(account: String): Flow<List<SchoolImportTarget>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveImport(entity: CampusImportEntity)
    @Query("DELETE FROM campus_imports WHERE accountId=:account AND academicYear=:year AND semester=:term")
    suspend fun deleteImport(account: String, year: Int, term: Int)
}
