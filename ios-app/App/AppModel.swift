// QluCampus iOS, GPL-3.0. Local persistence contains no school password.
import SwiftUI
import CampusCore
import WebKit
import WidgetKit
import UserNotifications

struct Appearance: Codable, Equatable {
    var style = "Wake Up", font = "系统", textMode = "自动黑白", textHex = "#202124"
    var fontSize: Double = 11, showOtherWeeks = true, borders = false, adaptiveCourses = true
    var extendBackground = true, blur: Double = 0, brightness: Double = 1
    var backgroundFile: String? = nil
    var reminders = false
}
struct Database: Codable {
    var schema = 1, account: String? = nil, active: UUID? = nil
    var schedules: [Timetable] = [], grades: [GradeSnapshot] = [], roomResults: [RoomResult] = []
    var appearance = Appearance(), gradeUnlocked = false, disclaimerHidden = false
    func validate() throws {
        guard schema == 1, schedules.count <= 100, grades.count <= 400, roomResults.count <= 30,
              Set(schedules.map(\.id)).count == schedules.count else { throw CampusError.invalid("本地备份格式或数量不支持") }
        try schedules.forEach { try $0.validate() }
        for g in grades { try g.term.validate(); guard g.details.count <= 20000, g.points.count <= 20000 else { throw CampusError.invalid("成绩备份过大") } }
    }
}
struct ImportPreview: Identifiable {
    var id = UUID(), ticket: SessionTicket, table: Timetable, destination: UUID?
}
struct BrowserRoute: Identifiable { var id = UUID(), url: URL, login: Bool }
struct SharedFile: Identifiable { var id = UUID(), url: URL }

@MainActor final class AppModel: ObservableObject {
    @Published private(set) var db = Database()
    @Published var message: String? = nil
    @Published var busy = false
    @Published var browser: BrowserRoute? = nil
    @Published var preview: ImportPreview? = nil
    @Published var sharedFile: SharedFile? = nil
    @Published var options: RoomOptions? = nil
    @Published var revision = UUID()
    let api = SchoolAPI()
    let directory: URL
    var appearance: Appearance { db.appearance }
    var owner: String { db.account ?? "guest" }
    var schedules: [Timetable] { db.schedules.filter { $0.owner == owner } }
    var active: Timetable? { schedules.first { $0.id == db.active } ?? schedules.first }
    var maskedAccount: String { db.account.map { "••••" + $0.suffix(4) } ?? "未登录" }
    var snapshots: [GradeSnapshot] { db.grades.filter { $0.owner == owner }.map { $0.visible(unlocked: db.gradeUnlocked) } }
    init(directory: URL? = nil) {
        self.directory = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("QluCampus", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: self.directory, withIntermediateDirectories: true)
            let url = self.directory.appendingPathComponent("campus-v1.json")
            if FileManager.default.fileExists(atPath: url.path) { let stored = try JSONDecoder().decode(Database.self, from: Data(contentsOf: url)); try stored.validate(); db = stored }
        } catch { message = "本地数据读取失败，原文件已保留：\(error.localizedDescription)" }
        if ProcessInfo.processInfo.arguments.contains("--uitest") { seedTestData() }
    }
    func commit(_ edit: (inout Database) throws -> Void) throws {
        var candidate = db; try edit(&candidate); try candidate.validate()
        try JSONEncoder().encode(candidate).write(to: directory.appendingPathComponent("campus-v1.json"), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        db = candidate
        updateWidget()
    }
    func changeAppearance(_ update: (inout Appearance) -> Void) {
        do { try commit { update(&$0.appearance) } } catch { message = error.localizedDescription }
    }
    func ticket() throws -> SessionTicket {
        guard let account = db.account else { throw CampusError.login }; return SessionTicket(account: account, revision: revision)
    }
    func check(_ ticket: SessionTicket) throws { guard db.account == ticket.account, revision == ticket.revision else { throw CampusError.changedAccount } }
    func perform(_ action: @escaping @MainActor () async throws -> Void) {
        guard !busy else { return }; busy = true
        Task { defer { busy = false }; do { try await action() } catch {
            message = (error as? CampusError)?.localizedDescription ?? "操作失败，旧数据已保留。请先连接手机 aTrust 后重试。\n\(error.localizedDescription)"
        } }
    }
    func login() { browser = BrowserRoute(url: School.login, login: true) }
    func acceptLogin(url: URL, student: String) throws {
        guard url.scheme == "https", url.host == "jw.qlu.edu.cn", url.path.hasPrefix("/jwglxt/"), !url.path.lowercased().contains("login"), student.range(of: "^[A-Za-z0-9_-]{5,32}$", options: .regularExpression) != nil else { throw CampusError.invalid("请完成统一认证并进入教务主页，再点击完成登录") }
        revision = UUID(); options = nil; preview = nil
        try commit { $0.account = student; $0.active = $0.schedules.first { $0.owner == student }?.id; $0.gradeUnlocked = false }
        browser = nil
    }
    func logout() {
        guard !busy else { return }
        perform {
            self.revision = UUID(); self.options = nil; self.preview = nil
            try self.commit { $0.account = nil; $0.active = nil; $0.gradeUnlocked = false }
            await withCheckedContinuation { continuation in
                WKWebsiteDataStore.default().removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince: .distantPast) { continuation.resume() }
            }
        }
    }
    func select(_ id: UUID) { do { revision = UUID(); options = nil; try commit { $0.active = id }; scheduleReminders() } catch { message = error.localizedDescription } }
    func saveTable(_ table: Timetable) throws {
        guard table.owner == owner else { throw CampusError.changedAccount }; try table.validate()
        try commit { state in if let i = state.schedules.firstIndex(where: { $0.id == table.id }) { state.schedules[i] = table } else { state.schedules.append(table) }; state.active = table.id }
        scheduleReminders()
    }
    func removeTable(_ id: UUID) {
        do { revision = UUID(); try commit { $0.schedules.removeAll { $0.id == id && $0.owner == owner }; if $0.active == id { $0.active = nil } }; scheduleReminders() } catch { message = error.localizedDescription }
    }
    func importTable(term: Term, firstMonday: Date, name: String, target: Timetable? = nil, immediate: Bool = false) {
        perform {
            let ticket = try self.ticket(); try term.validate()
            guard Dates.day(firstMonday) == 1 else { throw CampusError.invalid("请选择首周周一") }
            if let target { guard target.owner == ticket.account, target.term == term else { throw CampusError.changedAccount } }
            let data = try await self.api.timetable(term, ticket: ticket, check: self.check)
            let courses = try SchoolParser.timetable(data); try self.check(ticket)
            var table = target ?? Timetable(owner: ticket.account, name: name, term: term, firstMonday: Dates.string(firstMonday))
            table.courses = courses; table.updatedAt = Date()
            if immediate { try self.saveTable(table); self.message = "课表已按原首周周一刷新" }
            else { self.preview = ImportPreview(ticket: ticket, table: table, destination: target?.id) }
        }
    }
    func confirmImport() {
        guard let preview else { return }
        do { try check(preview.ticket); try saveTable(preview.table); self.preview = nil; message = "课表已保存" } catch { message = error.localizedDescription }
    }
    func refreshGrades(_ term: Term) {
        perform {
            let ticket = try self.ticket(), unlocked = self.db.gradeUnlocked
            let snapshot = try await self.api.grades(term, detailed: unlocked, ticket: ticket, check: self.check)
            try self.check(ticket); guard self.db.gradeUnlocked == unlocked else { throw CampusError.invalid("成绩入口状态已改变，未保存本次查询") }
            try self.commit { $0.grades.removeAll { $0.owner == ticket.account && $0.term == term }; $0.grades.append(snapshot) }
            self.message = "成绩已保存到本机"
        }
    }
    func unlock(_ password: String) { do { guard password == "070528" else { throw CampusError.invalid("密码不正确") }; try commit { $0.gradeUnlocked = true }; message = "平时成绩已开启，可在设置中重新锁定" } catch { message = error.localizedDescription } }
    func lock() { do { try commit { $0.gradeUnlocked = false } } catch { message = error.localizedDescription } }
    func loadRooms(term: Term, campus: String = "") {
        perform { let ticket = try self.ticket(); let loaded = try await self.api.roomOptions(term: term, campus: campus, ticket: ticket, check: self.check); try self.check(ticket); self.options = loaded }
    }
    func queryRooms(_ query: RoomQuery) {
        perform {
            let ticket = try self.ticket()
            let result = try await self.api.rooms(query, ticket: ticket, check: self.check); try self.check(ticket)
            try self.commit { $0.roomResults.removeAll { $0.owner == ticket.account && $0.query == query }; $0.roomResults.append(result); if $0.roomResults.count > 30 { $0.roomResults.removeFirst($0.roomResults.count - 30) } }
        }
    }
    func exportGrades(_ snapshot: GradeSnapshot) {
        do { guard snapshot.owner == owner else { throw CampusError.changedAccount }; try share(Workbook.export(snapshot.visible(unlocked: db.gradeUnlocked)), name: "成绩-\(snapshot.term.id).xlsx") } catch { message = error.localizedDescription }
    }
    func share(_ data: Data, name: String) throws {
        let url = directory.appendingPathComponent(name); try data.write(to: url, options: [.atomic, .completeFileProtection]); sharedFile = SharedFile(url: url)
    }
    func exportBackup() {
        do {
            var backup = Database(); backup.schedules = schedules; backup.grades = snapshots; backup.appearance = appearance; backup.appearance.backgroundFile = nil
            try share(JSONEncoder().encode(backup), name: "齐鲁课表-iOS备份.json")
        } catch { message = error.localizedDescription }
    }
    func importBackup(_ url: URL) {
        do {
            let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
            guard (try url.resourceValues(forKeys: [.fileSizeKey])).fileSize ?? Int.max <= 16 * 1024 * 1024 else { throw CampusError.invalid("备份文件过大") }
            let backup = try JSONDecoder().decode(Database.self, from: Data(contentsOf: url)); try backup.validate()
            // Restoring never changes the authenticated identity or grants detailed-grade access.
            let owned = backup.schedules.filter { $0.owner == owner || $0.owner == "guest" }
            guard !owned.isEmpty || backup.grades.contains(where: { $0.owner == owner }) else { throw CampusError.invalid("请登录备份所属账号后恢复") }
            try commit { state in
                for var table in owned { table.id = UUID(); table.owner = owner; state.schedules.append(table) }
                for g in backup.grades where g.owner == owner { if !state.grades.contains(where: { $0.owner == g.owner && $0.term == g.term }) { state.grades.append(g.visible(unlocked: state.gradeUnlocked)) } }
            }; message = "备份已追加恢复，原课表已保留"
        } catch { message = error.localizedDescription }
    }
    func setBackground(_ data: Data) {
        guard data.count < 20 * 1024 * 1024, let image = UIImage(data: data) else { message = "无法读取图片"; return }
        let scale = min(1, 1600 / max(image.size.width, image.size.height)), size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: size)
        guard let jpeg = renderer.image(actions: { _ in image.draw(in: CGRect(origin: .zero, size: size)) }).jpegData(compressionQuality: 0.88) else { return }
        do {
            let filename = "background-\(UUID().uuidString).jpg", old = appearance.backgroundFile
            try jpeg.write(to: directory.appendingPathComponent(filename), options: [.atomic, .completeFileProtection])
            try commit { $0.appearance.backgroundFile = filename }
            if let old, old.hasPrefix("background-"), !old.contains("/") { try? FileManager.default.removeItem(at: directory.appendingPathComponent(old)) }
        } catch { message = error.localizedDescription }
    }
    func scheduleReminders() {
        let center = UNUserNotificationCenter.current(); center.removeAllPendingNotificationRequests()
        guard appearance.reminders, let table = active, let monday = Dates.parse(table.firstMonday) else { return }
        Task {
            guard (try? await center.requestAuthorization(options: [.alert, .sound])) == true else { message = "请在系统设置中允许课程通知"; return }
            var events: [(Date, Course)] = []
            for c in table.courses where c.start <= School.sectionTimes.count {
                let time = School.sectionTimes[c.start - 1].prefix(5).split(separator: ":").compactMap { Int($0) }
                for week in c.weeks {
                    let day = Dates.add(monday, days: (week - 1) * 7 + c.day - 1)
                    guard let start = Dates.calendar.date(bySettingHour: time[0], minute: time[1], second: 0, of: day) else { continue }
                    let reminder = start.addingTimeInterval(-600); if reminder > Date() { events.append((reminder, c)) }
                }
            }
            for (date, c) in events.sorted(by: { $0.0 < $1.0 }).prefix(60) {
                let content = UNMutableNotificationContent(); content.title = c.name; content.body = "10 分钟后上课 · \(c.location)"; content.sound = .default
                var components = Dates.calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date); components.timeZone = Dates.calendar.timeZone
                try? await center.add(UNNotificationRequest(identifier: "\(c.id)-\(date.timeIntervalSince1970)", content: content, trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false)))
            }
        }
    }
    func updateWidget() {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "CampusAppGroup") as? String, !group.isEmpty, let dir = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) else { return }
        if let active, let data = try? JSONEncoder().encode(active) { try? data.write(to: dir.appendingPathComponent("widget.json"), options: .atomic) }
        else { try? FileManager.default.removeItem(at: dir.appendingPathComponent("widget.json")) }
        WidgetCenter.shared.reloadAllTimelines()
    }
    private func seedTestData() {
        let monday = Dates.monday(Date())
        var table = Timetable(owner: "guest", name: "界面测试课表", term: .current, firstMonday: Dates.string(monday))
        table.courses = [Course(name: "计算机在化学中的应用", location: "菏泽校区 菏泽北楼303", day: 1), Course(name: "无机合成化学", location: "菏泽北楼207", day: 3, start: 3, end: 4), Course(name: "危险化学品安全管理", location: "菏泽南楼104", day: 2, start: 9, end: 10)]
        db = Database(); db.schedules = [table]; db.active = table.id; db.disclaimerHidden = true
    }
}
