// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dawncourse.core.data.local.AppDatabase
import com.dawncourse.core.data.local.entity.CourseEntity
import com.dawncourse.core.data.local.entity.toDomain
import com.dawncourse.core.data.local.entity.SemesterEntity
import com.dawncourse.core.data.local.entity.TimetableProfileEntity
import com.dawncourse.core.domain.model.NewSemesterSpec
import com.dawncourse.core.domain.model.ImportCommitRequest
import com.dawncourse.core.domain.model.ImportCommitResult
import com.dawncourse.core.domain.model.ImportDestination
import com.dawncourse.core.domain.model.ProfileCreationRequest
import com.dawncourse.core.domain.model.ProfileMutationResult
import com.dawncourse.core.domain.model.SyncCredentials
import com.dawncourse.core.domain.model.SyncProviderType
import com.dawncourse.core.domain.repository.CredentialsRepository
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 关键事务结论使用真实 Room，而不是 JVM fake DAO。 */
@RunWith(AndroidJUnit4::class)
class ProfileSelectionCoordinatorInstrumentedTest {
    private val mutationGate = OperationalDataMutationGate()
    private lateinit var database: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var preferencesFile: File
    private lateinit var activeStore: ActiveProfileSelectionStore
    private lateinit var failingDataStore: WriteThenThrowDataStore
    private lateinit var coordinator: ProfileSelectionCoordinator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferencesFile = File(context.cacheDir, "profile-selection-${UUID.randomUUID()}.preferences_pb")
        val delegate = PreferenceDataStoreFactory.create(scope = scope) { preferencesFile }
        failingDataStore = WriteThenThrowDataStore(delegate)
        activeStore = ActiveProfileSelectionStore(failingDataStore)
        coordinator = ProfileSelectionCoordinator(
            database = database,
            profileDao = database.timetableProfileDao(),
            semesterDao = database.semesterDao(),
            courseDao = database.courseDao(),
            bindingDao = database.syncSourceBindingDao(),
            activeSelectionStore = activeStore,
            legacySemesterSelectionStore = SemesterSelectionStore(failingDataStore),
            credentialsRepository = FakeCredentialsRepository(),
            mutationGate = mutationGate,
        )
    }

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
        preferencesFile.delete()
    }

    @Test
    fun invalidSelectionSelfHealsAndDeletionUsesStableNextFallback() = runBlocking {
        val first = insertProfile("A", 0)
        val second = insertProfile("B", 1)
        val third = insertProfile("C", 2)
        activeStore.selectProfile(999L)

        assertEquals(first, coordinator.observeActiveContext().firstValue()?.profile?.id)
        coordinator.switch(second)
        val result = coordinator.delete(second)

        assertTrue(result is ProfileMutationResult.Success)
        assertEquals(third, (result as ProfileMutationResult.Success).activeContext.profile.id)
        assertEquals(third, activeStore.activeProfileId.firstValue())
    }

    @Test
    fun createSemesterIsAtomicAndCrossProfileActivationIsRejected() = runBlocking {
        val first = insertProfile("A", 0)
        val second = insertProfile("B", 1)
        activeStore.selectProfile(first)
        val created = coordinator.createSemester(first, NewSemesterSpec("2026 秋", 1L, 20))
        val semesterId = (created as ProfileMutationResult.Success).activeContext.semester?.id

        assertEquals(semesterId, database.timetableProfileDao().getProfileById(first)?.activeSemesterId)
        val rejected = coordinator.setActiveSemester(second, semesterId)
        assertTrue(rejected is ProfileMutationResult.Rejected)
        assertEquals(null, database.timetableProfileDao().getProfileById(second)?.activeSemesterId)
    }

    @Test
    fun cloneRemapsOnlyOriginsThatExistInsideSourceProfile() = runBlocking {
        val source = insertProfile("来源", 0)
        val sourceSemester = insertSemester(source)
        database.timetableProfileDao().updateActiveSemesterId(source, sourceSemester)
        val base = database.courseDao().insertCourse(course(sourceSemester, originId = 0L, modified = false))
        database.courseDao().insertCourse(course(sourceSemester, originId = base, modified = true))

        database.campusDao().saveImport(com.dawncourse.core.data.campus.CampusImportEntity("clone-account", 2026, 1, source, sourceSemester, 123))
        val cloned = coordinator.create(ProfileCreationRequest.Clone("副本", source)) as ProfileMutationResult.Success
        val clonedCourses = database.courseDao().getCoursesByProfileOnce(cloned.activeContext.profile.id)
        val clonedBase = clonedCourses.single { !it.isModified }
        val clonedFragment = clonedCourses.single { it.isModified }
        assertEquals(clonedBase.id, clonedFragment.originId)
        val sourceBinding = requireNotNull(database.campusDao().importBySemester(sourceSemester))
        val cloneBinding = requireNotNull(database.campusDao().importBySemester(clonedBase.semesterId))
        assertEquals(sourceBinding.copy(profileId = cloned.activeContext.profile.id, semesterId = clonedBase.semesterId), cloneBinding)
    }

    @Test
    fun cloneRejectsDanglingOriginWithoutCreatingPartialProfile() = runBlocking {
        val source = insertProfile("来源", 0)
        val sourceSemester = insertSemester(source)
        database.timetableProfileDao().updateActiveSemesterId(source, sourceSemester)
        database.courseDao().insertCourse(course(sourceSemester, originId = 777L, modified = true))

        val before = database.timetableProfileDao().countProfiles()
        val rejected = coordinator.create(ProfileCreationRequest.Clone("非法副本", source))
        assertTrue(rejected is ProfileMutationResult.Rejected)
        assertEquals(before, database.timetableProfileDao().countProfiles())
    }

    @Test
    fun cloneRejectsOriginFromAnotherProfileWithoutCreatingPartialProfile() = runBlocking {
        val source = insertProfile("来源", 0)
        val foreign = insertProfile("其他", 1)
        val sourceSemester = insertSemester(source)
        val foreignSemester = insertSemester(foreign)
        database.timetableProfileDao().updateActiveSemesterId(source, sourceSemester)

        val foreignCourse = database.courseDao().insertCourse(course(foreignSemester, originId = 0L, modified = false))
        database.courseDao().insertCourse(course(sourceSemester, originId = foreignCourse, modified = true))
        val before = database.timetableProfileDao().countProfiles()
        val rejected = coordinator.create(ProfileCreationRequest.Clone("非法副本", source))
        assertTrue(rejected is ProfileMutationResult.Rejected)
        assertEquals(before, database.timetableProfileDao().countProfiles())
    }

    @Test
    fun dataStoreWriteThenThrowRestoresExactSelectionBeforeRejectingSwitchAndDelete() = runBlocking {
        val first = insertProfile("A", 0)
        val second = insertProfile("B", 1)
        activeStore.selectProfile(first)

        failingDataStore.failNextWrite = IOException("after write")
        assertTrue(coordinator.switch(second) is ProfileMutationResult.Rejected)
        assertEquals(first, activeStore.rawActiveProfileId.first())

        failingDataStore.failNextWrite = IOException("after write")
        assertTrue(coordinator.delete(first) is ProfileMutationResult.Rejected)
        assertEquals(first, activeStore.rawActiveProfileId.first())
        assertEquals(2, database.timetableProfileDao().countProfiles())
    }

    @Test
    fun dataStoreWriteThenCancelRestoresSelectionBeforePropagatingCancellation() = runBlocking {
        val first = insertProfile("A", 0)
        val second = insertProfile("B", 1)
        activeStore.selectProfile(first)
        failingDataStore.failNextWrite = CancellationException("after write")
        var cancellationObserved = false

        try {
            coordinator.switch(second)
        } catch (_: CancellationException) {
            cancellationObserved = true
        }

        assertTrue(cancellationObserved)
        assertEquals(first, activeStore.rawActiveProfileId.first())
        assertEquals(0L, database.timetableProfileDao().getProfileById(second)?.lastUsedAt)
    }

    @Test
    fun capturedBindingCommitsAfterUiSwitchAndProviderRebindInvalidatesOldTask() = runBlocking {
        val first = insertProfile("A", 0)
        val second = insertProfile("B", 1)
        val firstSemester = insertSemester(first)
        database.timetableProfileDao().updateActiveSemesterId(first, firstSemester)
        activeStore.selectProfile(first)
        val oldBinding = requireNotNull(
            coordinator.ensureSourceBindingIfStillActive(first, firstSemester, SyncProviderType.WAKEUP),
        )

        coordinator.switch(second)
        assertTrue(
            coordinator.replaceCoursesForCapturedBinding(
                profileId = first,
                semesterId = firstSemester,
                provider = SyncProviderType.WAKEUP,
                sourceBindingId = oldBinding,
                courses = emptyList(),
            ),
        )
        assertEquals(second, activeStore.activeProfileId.first())

        coordinator.switch(first)
        val rebind = coordinator.saveCredentialsAndRebindIfActive(
            first,
            SyncCredentials(
                provider = SyncProviderType.ZF,
                type = com.dawncourse.core.domain.model.SyncCredentialType.PASSWORD,
                username = "user",
                secret = "secret",
                endpointUrl = "https://example.test",
            ),
        )
        assertTrue(rebind is com.dawncourse.core.domain.repository.CredentialBindingMutationResult.Success)
        val newBinding = database.syncSourceBindingDao().getBySemesterOnce(firstSemester)
        assertTrue(newBinding != null && newBinding.sourceBindingId != oldBinding)
        assertTrue(
            !coordinator.replaceCoursesForCapturedBinding(
                profileId = first,
                semesterId = firstSemester,
                provider = SyncProviderType.WAKEUP,
                sourceBindingId = oldBinding,
                courses = emptyList(),
            ),
        )

        val firstZfBinding = requireNotNull(newBinding).sourceBindingId
        val sameProviderRebind = coordinator.saveCredentialsAndRebindIfActive(
            first,
            SyncCredentials(
                provider = SyncProviderType.ZF,
                type = com.dawncourse.core.domain.model.SyncCredentialType.PASSWORD,
                username = "other-user",
                secret = "other-secret",
                endpointUrl = "https://example.test",
            ),
        )
        assertTrue(sameProviderRebind is com.dawncourse.core.domain.repository.CredentialBindingMutationResult.Success)
        val secondZfBinding = requireNotNull(database.syncSourceBindingDao().getBySemesterOnce(firstSemester))
        assertTrue(secondZfBinding.sourceBindingId != firstZfBinding)
        assertTrue(
            !coordinator.replaceCoursesForCapturedBinding(
                profileId = first,
                semesterId = firstSemester,
                provider = SyncProviderType.ZF,
                sourceBindingId = firstZfBinding,
                courses = emptyList(),
            ),
        )
    }

    @Test
    fun activeActionGateRejectsOldProfileAfterSwitch() = runBlocking {
        val first = insertProfile("A", 0)
        val second = insertProfile("B", 1)
        val semester = insertSemester(first)
        database.timetableProfileDao().updateActiveSemesterId(first, semester)
        activeStore.selectProfile(first)
        var calls = 0

        coordinator.executeActiveAction(first, semester) { calls++ }
        coordinator.switch(second)
        val rejected = coordinator.executeActiveAction(first, semester) { calls++ }

        assertEquals(1, calls)
        assertEquals(null, rejected)
    }

    @Test
    fun activeActionGateHoldsSelectionLockUntilSystemActionFinishes() = runBlocking {
        val first = insertProfile("A", 0)
        val second = insertProfile("B", 1)
        val semester = insertSemester(first)
        database.timetableProfileDao().updateActiveSemesterId(first, semester)
        activeStore.selectProfile(first)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val action = async(Dispatchers.Default) {
            coordinator.executeActiveAction(first, semester) {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val switch = async(Dispatchers.Default) { coordinator.switch(second) }

        delay(50)
        assertFalse(switch.isCompleted)
        release.complete(Unit)
        action.await()
        assertTrue(switch.await() is ProfileMutationResult.Success)
        assertEquals(second, activeStore.activeProfileId.first())
    }

    @Test
    fun automaticImportCommitsCapturedProfileWithoutChangingCurrentSelection() = runBlocking {
        val first = insertProfile("A", 0)
        val second = insertProfile("B", 1)
        val semester = insertSemester(first)
        database.timetableProfileDao().updateActiveSemesterId(first, semester)
        activeStore.selectProfile(first)
        val bindingId = requireNotNull(
            coordinator.ensureSourceBindingIfStillActive(first, semester, SyncProviderType.WAKEUP),
        )
        coordinator.switch(second)
        val repository = importCommitRepository()

        val result = repository.commit(
            ImportCommitRequest(
                destination = ImportDestination.OverwriteSemester(first, semester),
                semester = NewSemesterSpec("学期", 1L, 20),
                courses = listOf(course(semester, originId = 0L, modified = false).toDomain()),
                expectedSourceBindingId = bindingId,
            ),
        )

        assertTrue(result is ImportCommitResult.Success)
        assertEquals(second, activeStore.activeProfileId.first())
        assertEquals(1, database.courseDao().getCoursesBySemesterOnce(semester).size)
    }

    @Test
    fun importSelectionWriteThenThrowRestoresSelectionAndRoomPreimage() = runBlocking {
        val original = insertProfile("A", 0)
        activeStore.selectProfile(original)
        val before = database.timetableProfileDao().countProfiles()
        failingDataStore.failNextWrite = IOException("after write")

        val result = importCommitRepository().commit(
            ImportCommitRequest(
                destination = ImportDestination.NewProfile,
                newProfileName = "临时课表",
                semester = NewSemesterSpec("学期", 1L, 20),
                courses = emptyList(),
            ),
        )

        assertTrue(result is ImportCommitResult.Rejected)
        assertEquals(original, activeStore.rawActiveProfileId.first())
        assertEquals(before, database.timetableProfileDao().countProfiles())
    }

    private suspend fun insertProfile(name: String, order: Int): Long = database.timetableProfileDao().insert(
        TimetableProfileEntity(uuid = UUID.randomUUID().toString(), name = name, sortOrder = order),
    )

    @Test
    fun schoolImportBindingIsAtomicAndRepeatedImportReplacesSameSemester() = runBlocking {
        val profile = insertProfile("学校测试", 0)
        activeStore.selectProfile(profile)
        database.campusDao().saveAccount(com.dawncourse.core.data.campus.CampusAccountEntity("test-account", profile))
        val stamp = com.dawncourse.core.domain.model.SchoolImportStamp("test-account",
            com.dawncourse.core.domain.model.AcademicTerm(2025, 1), 10L)
        val request = ImportCommitRequest(ImportDestination.NewSemester(profile), NewSemesterSpec("2025 第一学期", 1L, 20),
            listOf(course(0, 0, false).toDomain()), schoolImport = stamp)
        val repository = importCommitRepository()
        val first = repository.commit(request) as ImportCommitResult.Success
        val semester = requireNotNull(first.activeContext.semester).id
        assertEquals(semester, database.campusDao().imported("test-account", 2025, 1)?.semesterId)
        // A stale preview cannot create another semester even if retried after process restart.
        assertTrue(repository.commit(request) is ImportCommitResult.Rejected)
        val overwrite = request.copy(destination = ImportDestination.OverwriteSemester(profile, semester), schoolImport = stamp.copy(importedAt = 20L))
        assertTrue(repository.commit(overwrite) is ImportCommitResult.Success)
        assertEquals(1, database.semesterDao().countByProfile(profile))
        assertEquals(1, database.courseDao().getCoursesBySemesterOnce(semester).size)
        assertEquals(20L, database.campusDao().imported("test-account", 2025, 1)?.importedAt)
        failingDataStore.failNextWrite = IOException("selection failed after write")
        val failed = overwrite.copy(courses = emptyList(), schoolImport = stamp.copy(importedAt = 30L))
        assertTrue(repository.commit(failed) is ImportCommitResult.Rejected)
        assertEquals(20L, database.campusDao().imported("test-account", 2025, 1)?.importedAt)
        assertEquals(1, database.courseDao().getCoursesBySemesterOnce(semester).size)
    }

    @Test
    fun schoolCopiesAndRefreshRemainIndependentAndRejectStaleDates() = runBlocking {
        val profile = insertProfile("账号课表", 0)
        activeStore.selectProfile(profile)
        database.campusDao().saveAccount(com.dawncourse.core.data.campus.CampusAccountEntity("account", profile))
        val term = com.dawncourse.core.domain.model.AcademicTerm(2026, 1)
        val stamp = com.dawncourse.core.domain.model.SchoolImportStamp("account", term, 10)
        val repo = importCommitRepository()
        val request = ImportCommitRequest(ImportDestination.NewProfile, NewSemesterSpec("秋季", 100, 20),
            listOf(course(0, 0, false).toDomain()), newProfileName = "课表甲", schoolImport = stamp)
        val a = repo.commit(request) as ImportCommitResult.Success
        val b = repo.commit(request.copy(newProfileName = "课表乙")) as ImportCommitResult.Success
        val aid = requireNotNull(a.activeContext.semester).id
        val bid = requireNotNull(b.activeContext.semester).id
        assertTrue(aid != bid)
        val target = database.campusDao().observeImports("account").first().first { it.semesterId == aid }
        val update = request.copy(destination = ImportDestination.OverwriteSemester(a.activeContext.profile.id, aid),
            schoolImport = stamp.copy(importedAt = 20), schoolTarget = target, preserveSelection = true,
            courses = listOf(course(0, 0, false).toDomain().copy(name = "甲的新课程")))
        // 删除最初账号占位课表后，其他独立学校课表仍可刷新。
        database.timetableProfileDao().deleteById(profile)
        assertTrue(repo.commit(update) is ImportCommitResult.Success)
        assertEquals(b.activeContext.profile.id, activeStore.activeProfileId.first())
        assertEquals("课程", database.courseDao().getCoursesBySemesterOnce(bid).single().name)
        assertEquals("甲的新课程", database.courseDao().getCoursesBySemesterOnce(aid).single().name)
        assertTrue(repo.commit(update) is ImportCommitResult.Rejected)
        val freshTarget = database.campusDao().observeImports("account").first().first { it.semesterId == aid }
        val semester = requireNotNull(database.semesterDao().getSemesterById(aid))
        database.semesterDao().updateSemester(semester.copy(startDate = 200))
        assertTrue(repo.commit(update.copy(schoolTarget = freshTarget)) is ImportCommitResult.Rejected)
        assertEquals(200L, database.semesterDao().getSemesterById(aid)?.startDate)
        assertEquals(10L, database.campusDao().importBySemester(bid)?.importedAt)
    }

    private suspend fun insertSemester(profileId: Long): Long = database.semesterDao().insertSemester(
        SemesterEntity(profileId = profileId, name = "学期", startDate = 1L, weekCount = 20),
    )

    @Test
    fun schoolAccountSwitchInvalidatesLateWritesAndFailedRefreshPreservesCache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("qlu_session", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val dao = database.campusDao()
        val sessions = com.dawncourse.core.data.campus.QluSessionRepository(context, dao,
            TimetableProfileRepositoryImpl(coordinator), mutationGate)
        suspend fun login(account: String) {
            sessions.beginLogin()
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    android.webkit.CookieManager.getInstance().setCookie("https://jw.qlu.edu.cn/jwglxt/",
                        "JSESSIONID=synthetic-test-only; Path=/jwglxt/; Secure") { continuation.resume(Unit) }
                }
            }
            sessions.completeLogin("https://jw.qlu.edu.cn/jwglxt/xtgl/index_initMenu.html", account)
        }
        try {
            login("test-account-a")
            val ticket = sessions.ticket("test-account-a")
            val profileA = requireNotNull(dao.account("test-account-a")).profileId
            val term = com.dawncourse.core.domain.model.AcademicTerm(2025, 1)
            val snapshot = com.dawncourse.core.domain.model.GradeSnapshot("test-account-a", term, 10,
                listOf(com.dawncourse.core.domain.model.GradeDetail("缓存课程", component = "总评", score = "89.50"),
                    com.dawncourse.core.domain.model.GradeDetail("缓存课程", component = "平时成绩", score = "92.25")), emptyList())
            dao.saveGrades(com.dawncourse.core.data.campus.CampusGradeEntity("test-account-a", 2025, 1,
                com.google.gson.Gson().toJson(snapshot), 10))
            login("test-account-b")
            val profileB = requireNotNull(dao.account("test-account-b")).profileId
            assertTrue(profileA != profileB)
            assertEquals(profileB, activeStore.activeProfileId.first())
            assertEquals(null, dao.observeGrades("test-account-b", 2025, 1).first())
            var lateWriteExecuted = false
            val failure = runCatching { sessions.commit(ticket) { lateWriteExecuted = true } }.exceptionOrNull()
            assertTrue(failure is com.dawncourse.core.domain.model.SchoolLoginRequired)
            assertFalse(lateWriteExecuted)
            login("test-account-a")
            assertEquals(profileA, activeStore.activeProfileId.first())
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    android.webkit.CookieManager.getInstance().removeAllCookies { continuation.resume(Unit) }
                }
            }
            val gradeAccess = object : com.dawncourse.core.domain.repository.GradeAccessRepository {
                    override val unlocked = kotlinx.coroutines.flow.MutableStateFlow(true)
                    override suspend fun unlock(password: String) = true
                    override suspend fun lock() { unlocked.value = false }
                }
            val repository = com.dawncourse.core.data.campus.QluGradeRepository(dao,
                com.dawncourse.core.data.campus.QluSchoolApi(sessions), sessions, mutationGate, gradeAccess)
            assertTrue(runCatching { repository.refresh("test-account-a", term) }.exceptionOrNull() is
                com.dawncourse.core.domain.model.SchoolLoginRequired)
            assertEquals(snapshot, repository.observe("test-account-a", term).first())
            val detailedExport = com.dawncourse.core.data.campus.XlsxTableCodec.read(repository.exportXlsx(snapshot)).flatten()
            assertTrue(detailedExport.contains("92.25"))
            gradeAccess.lock()
            val lockedSnapshot = requireNotNull(repository.observe("test-account-a", term).first())
            assertTrue(lockedSnapshot.details.none { it.component == "平时成绩" || it.score == "92.25" })
            // 即使调用者持有锁定前的快照，导出时也必须重新检查访问状态。
            val lockedExport = com.dawncourse.core.data.campus.XlsxTableCodec.read(repository.exportXlsx(snapshot)).flatten()
            assertFalse(lockedExport.contains("92.25"))
            assertTrue(lockedExport.contains("89.50"))
            assertEquals(2, com.google.gson.Gson().fromJson(requireNotNull(dao.observeGrades("test-account-a", 2025, 1).first()).payload,
                com.dawncourse.core.domain.model.GradeSnapshot::class.java).details.size)
            sessions.logout()
            assertEquals(null, sessions.account.value)
            assertTrue(activeStore.activeProfileId.first() !in listOf(profileA, profileB))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    private fun course(semesterId: Long, originId: Long, modified: Boolean) = CourseEntity(
        semesterId = semesterId, name = "课程", teacher = "", location = "", dayOfWeek = 1,
        startSection = 1, duration = 2, startWeek = 1, endWeek = 16, weekType = 0,
        color = "#000000", isModified = modified, originId = originId,
    )

    private suspend fun <T> Flow<T>.firstValue(): T = first()

    private fun importCommitRepository() = ImportCommitRepositoryImpl(
        database = database,
        profileDao = database.timetableProfileDao(),
        semesterDao = database.semesterDao(),
        courseDao = database.courseDao(),
        bindingDao = database.syncSourceBindingDao(),
        activeSelectionStore = activeStore,
        profileSelectionCoordinator = coordinator,
        mutationGate = mutationGate,
    )

    private class FakeCredentialsRepository : CredentialsRepository {
        override suspend fun getCredentials(profileId: Long): SyncCredentials? = null
        override suspend fun saveCredentials(profileId: Long, credentials: SyncCredentials) = Unit
        override suspend fun copyCredentials(sourceProfileId: Long, targetProfileId: Long) = Unit
        override suspend fun clearCredentials(profileId: Long) = Unit
        override fun observeBoundProvider(profileId: Long): Flow<SyncProviderType?> = flowOf(null)
    }

    /** 模拟 DataStore 已提交 updateData 后才向调用方抛错。 */
    private class WriteThenThrowDataStore(
        private val delegate: DataStore<Preferences>,
    ) : DataStore<Preferences> {
        var failNextWrite: Throwable? = null

        override val data: Flow<Preferences> = delegate.data

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = delegate.updateData(transform)
            failNextWrite?.let { failure ->
                failNextWrite = null
                throw failure
            }
            return updated
        }
    }
}
