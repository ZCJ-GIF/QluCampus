// QluCampus iOS, GPL-3.0. Keep parameter names aligned with Android QluSchoolApi.
import Foundation
import SwiftSoup

public enum SchoolParser {
    public static func rejectLogin(_ text: String) throws {
        if text.contains("统一身份认证") || text.range(of: "<input[^>]*type\\s*=\\s*['\"]?password", options: [.regularExpression, .caseInsensitive]) != nil {
            throw CampusError.login
        }
    }
    public static func object(_ data: Data) throws -> [String: Any] {
        guard data.count <= 8 * 1024 * 1024 else { throw CampusError.invalid("学校响应过大") }
        try rejectLogin(String(decoding: data.prefix(4096), as: UTF8.self))
        guard let value = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw CampusError.invalid("学校返回的数据格式已变化，原数据已保留") }; return value
    }
    static func value(_ row: [String: Any], _ key: String) -> String {
        guard let v = row[key], !(v is NSNull) else { return "" }; return String(describing: v).trimmingCharacters(in: .whitespacesAndNewlines)
    }
    public static func numbers(_ raw: String, max: Int) throws -> [Int] {
        let normalized = raw.replacingOccurrences(of: "[，、]", with: ",", options: .regularExpression).replacingOccurrences(of: "[－—～~]", with: "-", options: .regularExpression)
        var result = Set<Int>()
        for part in normalized.components(separatedBy: ",") {
            let odd = part.contains("单"), even = part.contains("双")
            let text = part.replacingOccurrences(of: "[周节\\s()（）单双]", with: "", options: .regularExpression)
            guard !(odd && even), text.range(of: "^\\d+(?:-\\d+)?$", options: .regularExpression) != nil else { throw CampusError.invalid("无法识别周次或节次：\(raw)") }
            let ends = text.components(separatedBy: "-").compactMap(Int.init)
            guard let first = ends.first, let last = ends.last, first >= 1, last >= first, last <= max else { throw CampusError.invalid("周次或节次超出范围") }
            result.formUnion((first...last).filter { (!odd || $0 % 2 == 1) && (!even || $0 % 2 == 0) })
        }
        guard !result.isEmpty else { throw CampusError.invalid("学校未返回有效周次或节次") }; return result.sorted()
    }
    public static func timetable(_ data: Data) throws -> [Course] {
        let root = try object(data)
        guard let list = root["kbList"] as? [[String: Any]], list.count <= 2000 else { throw CampusError.invalid("没有识别到学校课表结构，原课表未改动") }
        var result: [Course] = []
        var seen = Set<String>()
        for row in list {
            guard let day = Int(value(row, "xqj")) else { throw CampusError.invalid("课程星期无法识别") }
            let rawSections = value(row, "jc").isEmpty ? value(row, "jcs") : value(row, "jc")
            let sections = try numbers(rawSections, max: 30), weeks = try numbers(value(row, "zcd"), max: 60)
            var runs: [[Int]] = []
            for section in sections {
                if let last = runs.last?.last, last + 1 == section { runs[runs.count - 1].append(section) } else { runs.append([section]) }
            }
            for run in runs {
                let course = Course(name: value(row, "kcmc"), teacher: value(row, "xm"), location: value(row, "cdmc"), day: day, start: run[0], end: run.last!, weeks: weeks)
                try course.validate()
                let key = [course.name, course.teacher, course.location, String(day), String(course.start), String(course.end), weeks.map(String.init).joined(separator: ",")].joined(separator: "\u{1f}")
                if seen.insert(key).inserted { result.append(course) }
            }
        }
        guard !result.isEmpty else { throw CampusError.invalid("该学期未返回课程，原课表已保留") }; return result
    }
    public static func gradeDetails(_ rows: [[String]], detailed: Bool) throws -> [GradeDetail] {
        let required = detailed ? ["课程名称", "成绩", "成绩分项"] : ["课程名称", "成绩", "学分"]
        let table = try Table(rows, required: required)
        return try table.rows.map { row in
            let name = table.get(row, "课程名称")
            guard !name.isEmpty else { throw CampusError.invalid("成绩记录缺少课程名称") }
            return GradeDetail(name: name, code: table.get(row, "课程代码"), className: table.get(row, "教学班"), credits: table.get(row, "学分"), component: detailed ? table.get(row, "成绩分项") : "总评", score: table.get(row, "成绩"))
        }
    }
    public static func gradePoints(_ rows: [[String]]) throws -> [GradePoint] {
        let table = try Table(rows, required: ["课程名称", "绩点"])
        return try table.rows.map { row in
            let name = table.get(row, "课程名称"); guard !name.isEmpty else { throw CampusError.invalid("绩点记录缺少课程名称") }
            return GradePoint(name: name, code: table.get(row, "课程代码"), className: table.get(row, "教学班"), point: table.get(row, "绩点"), weightedPoint: table.get(row, "学分绩点"))
        }
    }
    public static func roomOptions(_ html: String) throws -> RoomOptions {
        try rejectLogin(html)
        let doc = try SwiftSoup.parse(html)
        let text = try doc.text()
        guard text.contains("空教室") || text.contains("空闲教室") else { throw CampusError.invalid("未识别到学校空教室页面") }
        func opts(_ id: String) throws -> [SchoolOption] {
            var seen = Set<String>()
            return try doc.select("select[id='\(id)'] option, select[name='\(id)'] option").array().compactMap {
                let id = try $0.attr("value"); guard !id.isEmpty, seen.insert(id).inserted else { return nil }; return SchoolOption(id: id, label: try $0.text())
            }
        }
        func selected(_ id: String) throws -> String {
            let e = try doc.select("select#\(id) option[selected]").first() ?? doc.select("select#\(id) option").first(); return try e?.attr("value") ?? ""
        }
        func has(_ name: String) throws -> Bool { try !doc.select("input[name='\(name)'],select[name='\(name)'],input[id='\(name)'],select[id='\(name)']").isEmpty() }
        let legacy = try ["xnm", "xqm", "xqh_id", "jcd", "zcd", "xqj"].allSatisfy { try has($0) }
        let table = try ["xnm", "xqm", "xqh_id", "dm_cx"].allSatisfy { try has($0) } && !doc.select("#selectTR_ZC th.selectTH[value]").isEmpty() && !doc.select("#selectTR_XQJ th.selectTH[value]").isEmpty() && !doc.select("tr#selectTR_JC").isEmpty()
        let parts = try selected("dm_cx").components(separatedBy: "-")
        let term: Term? = parts.count == 2 && Int(parts[0]) != nil && ["3", "12"].contains(parts[1]) ? Term(year: Int(parts[0])!, semester: parts[1] == "3" ? 1 : 2) : nil
        return try RoomOptions(campuses: opts("xqh_id"), buildings: opts("lh"), types: opts("cdlb_id"), sections: [], selectedCampus: selected("xqh_id"), term: term, recognized: legacy || table)
    }
    public static func campusDetails(_ data: Data) throws -> ([SchoolOption], [Int]) {
        let root = try object(data)
        guard let buildings = root["lhList"] as? [[String: Any]], let sections = root["jcList"] as? [[String: Any]] else { throw CampusError.invalid("无法读取校区教学楼和节次") }
        let parsed = try buildings.map { row -> SchoolOption in
            let id = value(row, "JXLDM"), label = value(row, "JXLMC")
            guard !id.isEmpty, !label.isEmpty else { throw CampusError.invalid("教学楼格式已变化") }; return SchoolOption(id: id, label: label)
        }
        let numbers = sections.compactMap { Int(value($0, "JCMC")) }
        guard numbers.count == sections.count, !numbers.isEmpty, numbers.allSatisfy({ (1...16).contains($0) }) else { throw CampusError.invalid("学校未提供有效节次") }
        return (Array(Set(parsed)).sorted { $0.id < $1.id }, Array(Set(numbers)).sorted())
    }
    public static func roomPage(_ data: Data) throws -> ([Room], Int) {
        let root = try object(data)
        guard let items = root["items"] as? [[String: Any]], let pages = Int(value(root, "totalPage")), (0...50).contains(pages), items.count <= 100 else { throw CampusError.invalid("学校未返回完整教室分页结构") }
        let rooms = try items.map { row -> Room in
            let id = value(row, "cd_id"), name = value(row, "cdmc")
            guard !id.isEmpty, !name.isEmpty else { throw CampusError.invalid("教室缺少标识，无法判断连续节次是否空闲") }
            return Room(id: id, name: name, campus: value(row, "xqmc"), building: value(row, "jxlmc"), capacity: value(row, "zws"))
        }
        return (rooms, pages)
    }
    public static func roomFields(_ q: RoomQuery, section: Int, page: Int) throws -> [(String, String)] {
        let week = try q.week()
        guard (q.start...q.end).contains(section), (1...50).contains(page), let date = Dates.parse(q.date) else { throw CampusError.invalid("教室查询条件无效") }
        return [("xnm", String(q.term.year)), ("xqm", q.term.code), ("xqh_id", q.campus), ("lh", q.building), ("cdlb_id", q.type), ("cdejlb_id", ""), ("qszws", ""), ("jszws", ""), ("cdmc", ""), ("cdjylx", ""), ("zcd", String(UInt64(1) << (week - 1))), ("xqj", String(Dates.day(date))), ("jcd", String(UInt64(1) << (section - 1))), ("jyfs", "0"), ("queryModel.currentPage", String(page)), ("queryModel.showCount", "100"), ("queryModel.sortName", "cdbh"), ("queryModel.sortOrder", "asc")]
    }
}

private struct Table {
    var header: [String], rows: [[String]]
    init(_ rows: [[String]], required: [String]) throws {
        guard let index = rows.firstIndex(where: { Set(required).isSubset(of: Set($0)) }) else { throw CampusError.invalid("学校成绩表字段已变化，旧成绩已保留") }
        header = rows[index]; self.rows = Array(rows.dropFirst(index + 1)).filter { $0.contains { !$0.isEmpty } }
    }
    func get(_ row: [String], _ name: String) -> String { guard let index = header.firstIndex(of: name), index < row.count else { return "" }; return row[index] }
}

public enum Grades {
    public static func match(_ snapshot: GradeSnapshot) -> (courses: [MatchedGrade], unmatched: [GradePoint]) {
        let groups = Dictionary(grouping: snapshot.details, by: \.key).sorted { $0.key < $1.key }.map(\.value)
        func matches(_ rows: [GradeDetail], _ p: GradePoint) -> Bool {
            let d = rows[0]
            let same = !d.code.isEmpty && !p.code.isEmpty ? d.code == p.code : d.name == p.name
            return same && (p.className.isEmpty || d.className == p.className)
        }
        var used = Set<Int>()
        let courses = groups.map { rows -> MatchedGrade in
            let candidates = snapshot.points.indices.filter { matches(rows, snapshot.points[$0]) }
            if candidates.count == 1, let i = candidates.first, groups.filter({ matches($0, snapshot.points[i]) }).count == 1 {
                used.insert(i); return MatchedGrade(details: rows, point: snapshot.points[i])
            }
            return MatchedGrade(details: rows, point: nil)
        }
        return (courses, snapshot.points.enumerated().filter { !used.contains($0.offset) }.map(\.element))
    }
    public static func decimal(_ text: String) -> Decimal? {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard t.count <= 38, t.range(of: "^\\d+(?:\\.\\d+)?$", options: .regularExpression) != nil else { return nil }; return Decimal(string: t, locale: Locale(identifier: "en_US_POSIX"))
    }
    public static func inputs(_ c: MatchedGrade) -> (Decimal, Decimal)? {
        let values = Set(c.details.map { $0.credits })
        guard values.count == 1, let raw = values.first, let credits = decimal(raw), credits > 0, let point = decimal(c.point?.point ?? ""), point >= 0 else { return nil }; return (credits, point)
    }
    public static func gpa(_ courses: [MatchedGrade]) -> (value: Decimal?, credits: Decimal, excluded: Int) {
        var credits = Decimal.zero, weighted = Decimal.zero, excluded = 0
        for c in courses { if let (xf, jd) = inputs(c) { credits += xf; weighted += xf * jd } else { excluded += 1 } }
        return (credits > 0 ? weighted / credits : nil, credits, excluded)
    }
    public static func format(_ number: Decimal?, digits: Int = 3) -> String {
        guard var n = number else { return "未提供" }; var rounded = Decimal(); NSDecimalRound(&rounded, &n, digits, .plain); return NSDecimalNumber(decimal: rounded).stringValue
    }
}
