import XCTest
import CampusCore
@testable import QluCampus

@MainActor final class StorageTests: XCTestCase {
    func testAccountSwitchInvalidatesPendingRequestAndIsolatesOfflineData() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at:folder) }
        let model = AppModel(directory:folder)
        try model.acceptLogin(url:URL(string:School.root + "xtgl/index_initMenu.html")!,student:"student001")
        let ticket = try model.ticket()
        let table = Timetable(owner:"student001",name:"账号一",term:.current,firstMonday:"2026-09-07",courses:[Course(name:"化学")])
        try model.saveTable(table)
        try model.acceptLogin(url:URL(string:School.root + "xtgl/index_initMenu.html")!,student:"student002")
        XCTAssertThrowsError(try model.check(ticket)); XCTAssertTrue(model.schedules.isEmpty)
        XCTAssertThrowsError(try model.saveTable(table))
        try model.acceptLogin(url:URL(string:School.root + "xtgl/index_initMenu.html")!,student:"student001")
        XCTAssertEqual(model.schedules.count,1)
        XCTAssertEqual(AppModel(directory:folder).schedules.first?.courses.first?.name,"化学")
        model.unlock("070528")
        model.openSchoolPage(School.login, login:true)
        XCTAssertThrowsError(try model.ticket())
        try model.acceptLogin(url:URL(string:School.root + "xtgl/index_initMenu.html")!,student:"student002")
        XCTAssertFalse(model.db.gradeUnlocked)
        try model.acceptLogin(url:URL(string:School.root + "xtgl/index_initMenu.html")!,student:"student001")
        XCTAssertTrue(model.db.gradeUnlocked)
        model.lock(); XCTAssertFalse(AppModel(directory:folder).db.gradeUnlocked)
    }
    func testFailedWriteValidationKeepsSavedTimetableAndBackupHidesDetails() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at:folder) }
        let model = AppModel(directory:folder)
        try model.saveTable(Timetable(owner:"guest",name:"原课表",term:.current,firstMonday:"2026-09-07",courses:[Course(name:"化学")]))
        XCTAssertThrowsError(try model.saveTable(Timetable(owner:"guest",name:"坏数据",term:.current,firstMonday:"bad")))
        XCTAssertEqual(AppModel(directory:folder).schedules.count,1)
        try model.commit { $0.grades = [GradeSnapshot(owner:"guest",term:.current,detailed:true,details:[GradeDetail(name:"化学",component:"平时",score:"85"),GradeDetail(name:"化学",component:"总评",score:"65")],points:[])] }
        model.exportBackup()
        let file = try XCTUnwrap(model.sharedFile?.url)
        let restored = try JSONDecoder().decode(Database.self,from:Data(contentsOf:file))
        XCTAssertEqual(restored.grades.first?.details.count,1); XCTAssertFalse(restored.gradeUnlocked); XCTAssertNil(restored.account)
    }
}
