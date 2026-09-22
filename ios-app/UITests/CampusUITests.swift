import XCTest

final class CampusUITests: XCTestCase {
    func testTimetableSettingsAndManualCourse() throws {
        let app = XCUIApplication(); app.launchArguments = ["--uitest"]; app.launch()
        XCTAssertTrue(app.buttons["addCourse"].waitForExistence(timeout: 15))
        app.buttons["addCourse"].tap()
        let name = app.textFields["courseName"]; XCTAssertTrue(name.waitForExistence(timeout: 5)); name.tap(); name.typeText("iOS course")
        let location = app.textFields["courseLocation"]; location.tap(); location.typeText("Building 207")
        app.buttons["saveCourse"].tap()
        XCTAssertTrue(app.buttons["addCourse"].waitForExistence(timeout: 5))
        app.buttons["settingsTab"].tap()
        let font = app.buttons["fontPicker"]
        for _ in 0..<5 { if font.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(font.exists)
        font.tap(); app.buttons["加粗"].tap()
        app.navigationBars.buttons["完成"].tap()
        app.buttons["tab1"].tap(); XCTAssertTrue(app.buttons["openGpa"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["合格"].exists); XCTAssertTrue(app.staticTexts["挂科"].exists)
        XCTAssertFalse(app.staticTexts["平时"].exists)
        app.buttons["openGpa"].tap(); XCTAssertTrue(app.staticTexts["gpaResult"].waitForExistence(timeout: 5))
        app.buttons["全选有效课程"].tap()
        XCTAssertEqual(app.staticTexts["gpaResult"].label, "GPA：2.333")
        let attachment = XCTAttachment(screenshot: app.screenshot()); attachment.name = "iOS GPA"; attachment.lifetime = .keepAlways; add(attachment)
        app.navigationBars.buttons["完成"].tap()
        app.buttons["tab2"].tap(); XCTAssertTrue(app.staticTexts["空教室查询"].waitForExistence(timeout: 5))
        app.buttons["tab0"].tap()
        let timetable = XCTAttachment(screenshot: app.screenshot()); timetable.name = "iOS timetable"; timetable.lifetime = .keepAlways; add(timetable)
    }
}
