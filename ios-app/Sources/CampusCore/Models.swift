// QluCampus iOS, GPL-3.0.
import Foundation

public enum CampusError: LocalizedError, Equatable {
    case login, invalid(String), changedAccount
    public var errorDescription: String? {
        switch self {
        case .login: return "学校登录已失效，请重新登录。"
        case .invalid(let message): return message
        case .changedAccount: return "账号或课表已切换，本次结果未保存。"
        }
    }
}

public struct Term: Codable, Hashable, Identifiable {
    public var year: Int
    public var semester: Int
    public init(year: Int, semester: Int) { self.year = year; self.semester = semester }
    public var id: String { "\(year)-\(semester)" }
    public var code: String { semester == 1 ? "3" : "12" }
    public var label: String { "\(year)–\(year + 1) · 第 \(semester) 学期" }
    public func validate() throws {
        guard (2000...2100).contains(year), (1...2).contains(semester) else { throw CampusError.invalid("请选择有效学年和学期") }
    }
    public static var current: Term {
        let c = Dates.calendar.dateComponents([.year, .month], from: Date())
        return Term(year: (c.year ?? 2026) - ((c.month ?? 9) < 8 ? 1 : 0), semester: (2...7).contains(c.month ?? 9) ? 2 : 1)
    }
}

public enum Dates {
    // Academic dates follow the school's timezone, even when the phone travels.
    public static var calendar: Calendar {
        var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "Asia/Shanghai")!; c.firstWeekday = 2; return c
    }
    public static func string(_ date: Date) -> String {
        let f = DateFormatter(); f.calendar = calendar; f.timeZone = calendar.timeZone; f.dateFormat = "yyyy-MM-dd"; return f.string(from: date)
    }
    public static func parse(_ value: String) -> Date? {
        let f = DateFormatter(); f.calendar = calendar; f.timeZone = calendar.timeZone; f.dateFormat = "yyyy-MM-dd"; f.isLenient = false
        guard let date = f.date(from: value), string(date) == value else { return nil }; return date
    }
    public static func day(_ date: Date) -> Int { (calendar.component(.weekday, from: date) + 5) % 7 + 1 }
    public static func add(_ date: Date, days: Int) -> Date { calendar.date(byAdding: .day, value: days, to: date)! }
    public static func monday(_ date: Date) -> Date { add(calendar.startOfDay(for: date), days: 1 - day(date)) }
    public static func week(_ date: Date, firstMonday: Date) -> Int {
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: firstMonday), to: calendar.startOfDay(for: date)).day ?? 0
        return Int(floor(Double(days) / 7)) + 1
    }
}

public struct Course: Codable, Hashable, Identifiable {
    public var id: UUID = UUID()
    public var name: String
    public var teacher: String
    public var location: String
    public var day: Int
    public var start: Int
    public var end: Int
    public var weeks: [Int]
    public init(name: String, teacher: String = "", location: String = "", day: Int = 1, start: Int = 1, end: Int = 2, weeks: [Int] = Array(1...20)) {
        self.name = name; self.teacher = teacher; self.location = location; self.day = day; self.start = start; self.end = end; self.weeks = weeks
    }
    public func validate() throws {
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, (1...7).contains(day), (1...30).contains(start), (start...30).contains(end), !weeks.isEmpty, weeks.allSatisfy({ (1...60).contains($0) }) else { throw CampusError.invalid("课程名称、星期、节次或周次无效") }
    }
}

public struct Timetable: Codable, Identifiable {
    public var id = UUID()
    public var owner: String
    public var name: String
    public var term: Term
    public var firstMonday: String
    public var courses: [Course]
    public var updatedAt: Date?
    public init(owner: String, name: String, term: Term, firstMonday: String, courses: [Course] = []) {
        self.owner = owner; self.name = name; self.term = term; self.firstMonday = firstMonday; self.courses = courses
    }
    public func validate() throws {
        try term.validate()
        guard let monday = Dates.parse(firstMonday), Dates.day(monday) == 1, !name.isEmpty, courses.count <= 2000 else { throw CampusError.invalid("请选择首周周一并填写课表名称") }
        try courses.forEach { try $0.validate() }
    }
}

public struct GradeDetail: Codable, Hashable {
    public var name: String, code: String, className: String, credits: String, component: String, score: String
    public init(name: String, code: String = "", className: String = "", credits: String = "", component: String = "总评", score: String = "") {
        self.name = name; self.code = code; self.className = className; self.credits = credits; self.component = component; self.score = score
    }
    public var key: String { [code, className, name].joined(separator: "\u{1f}") }
}
public struct GradePoint: Codable, Hashable {
    public var name: String, code: String, className: String, point: String, weightedPoint: String
    public init(name: String, code: String = "", className: String = "", point: String = "", weightedPoint: String = "") {
        self.name = name; self.code = code; self.className = className; self.point = point; self.weightedPoint = weightedPoint
    }
}
public struct GradeSnapshot: Codable {
    public var owner: String, term: Term, fetchedAt: Date, detailed: Bool
    public var details: [GradeDetail], points: [GradePoint]
    public init(owner: String, term: Term, detailed: Bool, details: [GradeDetail], points: [GradePoint], fetchedAt: Date = Date()) {
        self.owner = owner; self.term = term; self.detailed = detailed; self.details = details; self.points = points; self.fetchedAt = fetchedAt
    }
    public func visible(unlocked: Bool) -> GradeSnapshot {
        guard !unlocked else { return self }
        var result = self; result.detailed = false
        result.details = Dictionary(grouping: details, by: \.key).values.map { rows in
            var first = rows[0]
            let totals = rows.filter { $0.component.contains("总评") || $0.component == "成绩" }
            first.component = "总评"; first.score = totals.count == 1 ? totals[0].score : ""
            let credits = rows.compactMap { Grades.decimal($0.credits) }
            first.credits = credits.count == rows.count && Set(credits).count == 1 ? first.credits : ""; return first
        }.sorted { $0.key < $1.key }
        return result
    }
}
public struct MatchedGrade: Identifiable {
    public var details: [GradeDetail], point: GradePoint?
    public var id: String { details[0].key }
    public var name: String { details[0].name }
    public var total: String {
        let totals = details.filter { $0.component.contains("总评") || $0.component == "成绩" }
        return totals.count == 1 ? totals[0].score : ""
    }
}
public struct SchoolOption: Codable, Hashable, Identifiable { public var id: String, label: String }
public struct Room: Codable, Hashable, Identifiable { public var id: String, name: String, campus: String, building: String, capacity: String }
public struct RoomOptions {
    public var campuses: [SchoolOption], buildings: [SchoolOption], types: [SchoolOption], sections: [Int]
    public var selectedCampus: String
    public var term: Term?
    public var recognized: Bool
}
public struct RoomQuery: Codable, Equatable {
    public var term: Term, firstMonday: String, date: String, start: Int, end: Int, campus: String, building: String, type: String
    public init(term: Term, firstMonday: String, date: String, start: Int, end: Int, campus: String, building: String = "", type: String = "") {
        self.term = term; self.firstMonday = firstMonday; self.date = date; self.start = start; self.end = end; self.campus = campus; self.building = building; self.type = type
    }
    public func week() throws -> Int {
        try term.validate()
        guard let first = Dates.parse(firstMonday), let day = Dates.parse(date), Dates.day(first) == 1, (1...16).contains(start), (start...16).contains(end) else { throw CampusError.invalid("请选择首周周一及有效的日期、节次") }
        let week = Dates.week(day, firstMonday: first)
        guard (1...53).contains(week) else { throw CampusError.invalid("查询日期超出课表学期") }; return week
    }
}
public struct RoomResult: Codable { public var owner: String, query: RoomQuery, rooms: [Room], fetchedAt: Date
    public init(owner: String, query: RoomQuery, rooms: [Room], fetchedAt: Date = Date()) { self.owner = owner; self.query = query; self.rooms = rooms; self.fetchedAt = fetchedAt }
}

public enum School {
    public static let root = "https://jw.qlu.edu.cn/jwglxt/"
    public static let login = URL(string: "https://jw.qlu.edu.cn/sso/ddlogin")!
    public static let roomPage = "cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155&layout=default"
    public static let gradePage = "cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default"
    public static let sectionTimes = ["08:30–09:15", "09:20–10:05", "10:20–11:05", "11:10–11:55", "14:00–14:45", "14:50–15:35", "15:50–16:35", "16:40–17:25", "18:25–19:10", "19:10–19:55"]
}
