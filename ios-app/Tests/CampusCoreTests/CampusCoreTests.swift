import XCTest
@testable import CampusCore

final class CampusCoreTests: XCTestCase {
    func testSchoolTermMapping() throws {
        XCTAssertEqual(Term(year: 2026, semester: 1).code, "3")
        XCTAssertEqual(Term(year: 2026, semester: 2).code, "12")
        XCTAssertThrowsError(try Term(year: 1999, semester: 1).validate())
    }
    func testOddEvenAndDiscreteWeeks() throws {
        XCTAssertEqual(try SchoolParser.numbers("1-8(单),10,12-16(双)", max: 60), [1,3,5,7,10,12,14,16])
        XCTAssertEqual(try SchoolParser.numbers("1－2节，5～6节", max: 30), [1,2,5,6])
        for bad in ["", "0-3", "4-2", "61", "1-8(单双)", "1-2?", "1,"] { XCTAssertThrowsError(try SchoolParser.numbers(bad, max: 60), bad) }
    }
    func testTimetableDedupAndGaps() throws {
        let row: [String: Any] = ["kcmc":"化学", "xm":"教师", "cdmc":"北楼207", "xqj":"2", "jcs":"1-2,5-6", "zcd":"2-8(双)"]
        let courses = try SchoolParser.timetable(JSONSerialization.data(withJSONObject: ["kbList":[row,row]]))
        XCTAssertEqual(courses.count, 2); XCTAssertEqual(courses[0].weeks, [2,4,6,8]); XCTAssertEqual(courses[1].start, 5)
        XCTAssertThrowsError(try SchoolParser.timetable(Data("{\"kbList\":[]}".utf8)))
        XCTAssertThrowsError(try SchoolParser.timetable(Data("<input type='password'>登录".utf8)))
        XCTAssertThrowsError(try SchoolParser.timetable(Data("{\"other\":[]}".utf8)))
    }
    func testDateTimezoneMondayAndNegativeWeek() throws {
        let first = try XCTUnwrap(Dates.parse("2026-09-07"))
        XCTAssertEqual(Dates.day(first), 1)
        XCTAssertEqual(Dates.week(try XCTUnwrap(Dates.parse("2026-09-22")), firstMonday: first), 3)
        XCTAssertEqual(Dates.week(Dates.add(first, days: -1), firstMonday: first), 0)
        XCTAssertNil(Dates.parse("2026-02-30"))
    }
    func testGradeOrderingIdentityAmbiguityAndGpa() {
        let details = [GradeDetail(name:"A", code:"01", credits:"2", score:"70"), GradeDetail(name:"B", code:"02", credits:"3", score:"80")]
        let points = [GradePoint(name:"B", code:"02", point:"3.5"), GradePoint(name:"A", code:"01", point:"2.0")]
        let s = GradeSnapshot(owner:"one", term:.current, detailed:false, details:details, points:points)
        let matched = Grades.match(s)
        XCTAssertEqual(matched.courses[0].point?.point, "2.0")
        XCTAssertEqual(Grades.gpa(matched.courses).value, Decimal(string:"2.9"))
        XCTAssertEqual(Grades.gpa([matched.courses[0]]).value, 2)
        var ambiguous = s; ambiguous.details[1].name = "A"; ambiguous.points = [GradePoint(name:"A", point:"4")]
        let result = Grades.match(ambiguous)
        XCTAssertTrue(result.courses.allSatisfy { $0.point == nil }); XCTAssertEqual(result.unmatched.count, 1)
        XCTAssertNil(Grades.gpa(result.courses).value); XCTAssertEqual(Grades.gpa(result.courses).excluded, 2)
        for bad in ["NaN", "Infinity", "未提供", "3x", "-1"] { XCTAssertNil(Grades.decimal(bad)) }
    }
    func testGradeLockRemovesComponentsAndKeepsTotals() {
        let s = GradeSnapshot(owner:"one", term:.current, detailed:true, details:[GradeDetail(name:"A", credits:"2", component:"平时", score:"85"), GradeDetail(name:"A", credits:"2", component:"总评", score:"62")], points:[])
        let hidden = s.visible(unlocked:false)
        XCTAssertEqual(hidden.details.count, 1); XCTAssertEqual(hidden.details[0].score, "62"); XCTAssertFalse(hidden.detailed)
        XCTAssertEqual(s.visible(unlocked:true).details.count, 2)
    }
    func testXlsxRoundTripChineseDecimalsAndFormulaText() throws {
        let rows = [["课程名称","成绩","绩点"],["化学 & 实验","60.00","3.50"],["=SUM(A1)","未提供","0"]]
        let data = try Workbook.write([("成绩明细",rows),("绩点",[["课程","点数"]])])
        XCTAssertTrue(data.starts(with:[0x50,0x4b])); XCTAssertEqual(try Workbook.read(data), rows)
        XCTAssertThrowsError(try Workbook.read(Data("<html>error</html>".utf8)))
        XCTAssertThrowsError(try Workbook.read(Data([0x50,0x4b,0,0])))
        XCTAssertThrowsError(try SchoolParser.gradeDetails([["新字段"]], detailed:false))
    }
    func testClassroomBitMasks() throws {
        let query = RoomQuery(term:Term(year:2026,semester:1),firstMonday:"2026-09-07",date:"2026-09-22",start:9,end:10,campus:"1")
        let f = Dictionary(uniqueKeysWithValues:try SchoolParser.roomFields(query,section:10,page:1))
        XCTAssertEqual(f["zcd"],"4"); XCTAssertEqual(f["jcd"],"512"); XCTAssertEqual(f["xqj"],"2"); XCTAssertEqual(f["xqm"],"3")
        var invalid = query; invalid.firstMonday = "2026-09-08"; XCTAssertThrowsError(try invalid.week())
    }
    func testClassroomTableFormAndPageErrors() throws {
        let html = """
        <h1>空闲教室</h1><input name="xnm"><input name="xqm">
        <select id="xqh_id"><option value="1" selected>菏泽</option></select>
        <select id="dm_cx"><option value="2026-3" selected>2026</option></select>
        <table><tr id="selectTR_ZC"><th class="selectTH" value="1">1</th></tr>
        <tr id="selectTR_XQJ"><th class="selectTH" value="1">1</th></tr><tr id="selectTR_JC"></tr></table>
        """
        let options = try SchoolParser.roomOptions(html)
        XCTAssertTrue(options.recognized); XCTAssertEqual(options.selectedCampus,"1"); XCTAssertEqual(options.term,Term(year:2026,semester:1))
        let page = try SchoolParser.roomPage(Data("{\"totalPage\":1,\"items\":[{\"cd_id\":\"id1\",\"cdmc\":\"207\"}]}".utf8))
        XCTAssertEqual(page.0[0].id,"id1")
        for json in ["{}","{\"items\":[]}","{\"items\":[],\"totalPage\":51}","{\"items\":[{\"cdmc\":\"207\"}],\"totalPage\":1}"] { XCTAssertThrowsError(try SchoolParser.roomPage(Data(json.utf8))) }
    }
}
