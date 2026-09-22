// QluCampus iOS, GPL-3.0.
import SwiftUI
import CampusCore

struct TimetableView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.campusTheme) var theme
    var openSettings: () -> Void
    @State private var week = 1
    @State private var importing = false
    @State private var editingTable = false
    @State private var managing = false
    @State private var editingCourse: Course? = nil
    var body: some View {
        GeometryReader { geo in
            VStack(spacing: 10) {
                header
                if let table = model.active, let first = Dates.parse(table.firstMonday) {
                    weekHeader(first: first)
                    let rowHeight = max(56, (geo.size.height - 120) / 10)
                    ScrollView(.vertical) {
                        HStack(alignment: .top, spacing: 2) {
                            VStack(spacing: 0) {
                                ForEach(1...max(10, table.courses.map(\.end).max() ?? 10), id: \.self) { section in
                                    VStack(spacing: 4) { Text("\(section)").font(theme.font(16)); if section <= School.sectionTimes.count { let parts = School.sectionTimes[section - 1].components(separatedBy: "–"); Text(parts.joined(separator: "\n")).font(theme.font(8)).lineSpacing(2) } }
                                    .frame(width: 30, height: rowHeight, alignment: .top).regionalText().accessibilityIdentifier("section\(section)")
                                }
                            }
                            ForEach(1...7, id: \.self) { day in
                                dayColumn(table, day: day, rowHeight: rowHeight)
                            }
                        }.padding(.bottom, 12)
                    }.simultaneousGesture(DragGesture(minimumDistance: 30).onEnded { value in if abs(value.translation.width) > abs(value.translation.height) * 1.5 { week = min(60, max(1, week + (value.translation.width < 0 ? 1 : -1))) } })
                } else {
                    Spacer()
                    Image(systemName: "calendar.badge.plus").font(.system(size: 45))
                    Text("添加你的第一张课表").font(theme.font(20, bold: true))
                    Text("登录学校导入，也可以手动添加课程").font(theme.font(13))
                    Button("学校登录") { model.login() }.buttonStyle(.borderedProminent)
                    Button("新建本地课表") { editingTable = true }.buttonStyle(.bordered)
                    Spacer()
                }
            }
        }.padding(.horizontal, 4)
        .sheet(isPresented: $importing) { ImportForm().environmentObject(model) }
        .sheet(isPresented: $editingTable) { TableEditor(table: nil).environmentObject(model) }
        .sheet(item: $editingCourse) { CourseEditor(course: $0).environmentObject(model) }
        .sheet(isPresented: $managing) { TableManager().environmentObject(model) }
        .onAppear { jumpToday() }.onChange(of: model.active?.id) { _ in jumpToday() }
    }
    var header: some View {
        HStack(alignment: .center, spacing: 0) {
            VStack(alignment: .leading, spacing: 3) {
                Text(Dates.string(Date()).replacingOccurrences(of: "-", with: "/")).font(theme.font(25, bold: true)).lineLimit(1).minimumScaleFactor(0.7)
                HStack(spacing: 3) {
                    Button { week = max(1, week - 1) } label: { Image(systemName: "chevron.left").font(.caption) }
                    Button { jumpToday() } label: { Text("第 \(week) 周").font(theme.font(16)) }
                    Button { week = min(60, week + 1) } label: { Image(systemName: "chevron.right").font(.caption) }
                    if let table = model.active, let first = Dates.parse(table.firstMonday) { let current = Dates.week(Date(), firstMonday: first); if current != week { Text("当前第\(current)周").font(theme.font(11)).lineLimit(1) } }
                }
            }.frame(maxWidth: .infinity, alignment: .leading)
            Button { if model.active == nil { editingTable = true } else { editingCourse = Course(name: "") } } label: { Image(systemName: "plus") }.accessibilityLabel("添加课程").accessibilityIdentifier("addCourse")
                .frame(width: 40, height: 44)
            Button { importing = true } label: { Image(systemName: "arrow.down.to.line") }.accessibilityLabel("导入课表").frame(width: 40, height: 44)
            Button { if let table = model.active, let date = Dates.parse(table.firstMonday) { model.importTable(term: table.term, firstMonday: date, name: table.name, target: table, immediate: true) } } label: { Image(systemName: "arrow.clockwise") }.disabled(model.busy || model.active?.owner == "guest" || model.active == nil).accessibilityLabel("刷新课表").frame(width: 38, height: 44)
            Menu {
                Button("多课表管理") { managing = true }
                Button("字体、背景与设置") { openSettings() }
                Button(model.appearance.showOtherWeeks ? "隐藏非本周课程" : "显示非本周课程") { model.changeAppearance { $0.showOtherWeeks.toggle() } }
                Button("导出本机备份") { model.exportBackup() }
            } label: { Image(systemName: "ellipsis").rotationEffect(.degrees(90)).frame(width: 28, height: 44) }
        }.padding(.horizontal, 10).padding(.top, 10).font(theme.font(22, bold: true)).regionalText()
    }
    func weekHeader(first: Date) -> some View {
        HStack(spacing: 2) {
            Text("\(Dates.calendar.component(.month, from: Dates.add(first, days: (week - 1) * 7)))\n月").font(theme.font(14)).frame(width: 30)
            ForEach(1...7, id: \.self) { day in
                let date = Dates.add(first, days: (week - 1) * 7 + day - 1)
                VStack(spacing: 6) { Text(["一", "二", "三", "四", "五", "六", "日"][day - 1]).font(theme.font(15)); Text("\(Dates.calendar.component(.month, from: date))/\(Dates.calendar.component(.day, from: date))").font(theme.font(11)) }.frame(maxWidth: .infinity).fontWeight(Dates.string(date) == Dates.string(Date()) ? .bold : .regular)
            }
        }.padding(.vertical, 7).regionalText()
    }
    func dayColumn(_ table: Timetable, day: Int, rowHeight: CGFloat) -> some View {
        let courses = table.courses.filter { $0.day == day && ($0.weeks.contains(week) || model.appearance.showOtherWeeks) }.sorted { ($0.weeks.contains(week) ? 1 : 0) < ($1.weeks.contains(week) ? 1 : 0) }
        return ZStack(alignment: .top) {
            Color.clear
            ForEach(courses) { course in
                let color = theme.courseColor(course, active: course.weeks.contains(week))
                Button { editingCourse = course } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(course.name).font(theme.font(model.appearance.fontSize)).fixedSize(horizontal: false, vertical: true)
                        Text("@" + (course.location.isEmpty ? "地点未提供" : course.location)).font(theme.font(model.appearance.fontSize)).fixedSize(horizontal: false, vertical: true)
                        if !course.teacher.isEmpty { Text(course.teacher).font(theme.font(max(8, model.appearance.fontSize - 1))).lineLimit(2) }
                        Spacer(minLength: 0)
                    }.padding(3).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    .foregroundColor(theme.courseText(color)).background(color.opacity(model.appearance.adaptiveCourses ? 0.97 : 0.85))
                    .clipShape(RoundedRectangle(cornerRadius: 5))
                    .overlay(RoundedRectangle(cornerRadius: 5).stroke(.white.opacity(model.appearance.borders ? 0.8 : 0), lineWidth: 1.2))
                }.buttonStyle(.plain).frame(height: rowHeight * CGFloat(course.end - course.start + 1) - 3).clipped().offset(y: rowHeight * CGFloat(course.start - 1))
                .accessibilityLabel("\(course.name)，\(course.location)，第\(course.start)至\(course.end)节")
            }
        }.frame(maxWidth: .infinity).frame(height: rowHeight * CGFloat(max(10, table.courses.map(\.end).max() ?? 10)))
    }
    func jumpToday() { if let table = model.active, let first = Dates.parse(table.firstMonday) { week = min(60, max(1, Dates.week(Date(), firstMonday: first))) } }
}

struct TableEditor: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    var table: Timetable?
    @State private var name = "我的课表"
    @State private var term = Term.current
    @State private var date = Dates.monday(Date())
    var body: some View {
        NavigationStack { Form {
            TextField("课表名称", text: $name)
            TermPicker(term: $term)
            Section("在日历中选择首周周一") { DatePicker("首周周一", selection: $date, displayedComponents: .date).datePickerStyle(.graphical).environment(\.timeZone, Dates.calendar.timeZone); if Dates.day(date) != 1 { Text("所选日期不是周一").foregroundStyle(.red) } }
        }.navigationTitle(table == nil ? "新建课表" : "课表设置").toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) { Button("保存") {
                do { var updated = table ?? Timetable(owner: model.owner, name: name, term: term, firstMonday: Dates.string(date)); updated.name = name; updated.term = term; updated.firstMonday = Dates.string(date); try model.saveTable(updated); dismiss() } catch { model.message = error.localizedDescription }
            }.disabled(Dates.day(date) != 1 || name.trimmingCharacters(in: .whitespaces).isEmpty) }
        }}.onAppear { if let table { name = table.name; term = table.term; date = Dates.parse(table.firstMonday) ?? date } }
    }
}
struct TableManager: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    @State private var edit: Timetable? = nil
    @State private var new = false
    var body: some View {
        NavigationStack { List {
            ForEach(model.schedules) { table in
                HStack { Button { model.select(table.id); dismiss() } label: { VStack(alignment: .leading) { Text(table.name); Text(table.term.label).font(.caption) } }; Spacer(); Button("编辑") { edit = table } }
            }.onDelete { indices in for i in indices { model.removeTable(model.schedules[i].id) } }
            Button("新建课表") { new = true }
        }.navigationTitle("多课表").toolbar { Button("完成") { dismiss() } } }
        .sheet(item: $edit) { TableEditor(table: $0).environmentObject(model) }
        .sheet(isPresented: $new) { TableEditor(table: nil).environmentObject(model) }
    }
}
struct CourseEditor: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    @State var course: Course
    @State private var weeks = "1-20"
    @State private var error = ""
    var body: some View {
        NavigationStack { Form {
            TextField("课程名称", text: $course.name).accessibilityIdentifier("courseName")
            TextField("上课地点", text: $course.location).accessibilityIdentifier("courseLocation")
            TextField("教师", text: $course.teacher)
            Picker("星期", selection: $course.day) { ForEach(1...7, id: \.self) { Text("周\($0)").tag($0) } }
            Stepper("从第 \(course.start) 节", value: $course.start, in: 1...30).onChange(of: course.start) { course.end = max($0, course.end) }
            Stepper("至第 \(course.end) 节", value: $course.end, in: course.start...30)
            TextField("周次，如 1-16(单),18", text: $weeks)
            Text("支持 1-16、1-16(单)、2-16(双) 和逗号分隔的离散周。").font(.caption)
            if !error.isEmpty { Text(error).foregroundStyle(.red) }
            if model.active?.courses.contains(where: { $0.id == course.id }) == true { Button("删除课程", role: .destructive) { guard var table = model.active else { return }; table.courses.removeAll { $0.id == course.id }; do { try model.saveTable(table); dismiss() } catch { self.error = error.localizedDescription } } }
        }.navigationTitle("课程详情").toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) { Button("保存") { save() }.accessibilityIdentifier("saveCourse") }
        }}.onAppear { weeks = course.weeks.map(String.init).joined(separator: ",") }
    }
    func save() {
        do { course.weeks = try SchoolParser.numbers(weeks, max: 60); try course.validate(); guard var table = model.active else { return }; table.courses.removeAll { $0.id == course.id }; table.courses.append(course); try model.saveTable(table); dismiss() } catch { self.error = error.localizedDescription }
    }
}
struct ImportForm: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    @State private var term = Term.current
    @State private var monday = Dates.monday(Date())
    @State private var name = "学校课表"
    var body: some View {
        NavigationStack { Form {
            Section {
                Text("学校账号：\(model.maskedAccount)")
                Text("校外先连接手机 aTrust，然后返回导入。").font(.footnote)
                if model.db.account == nil {
                    Button("前往学校登录") { dismiss(); DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { model.login() } }
                }
            }
            TermPicker(term: $term); TextField("课表名称", text: $name)
            Section("首周周一") { DatePicker("选择日期", selection: $monday, displayedComponents: .date).datePickerStyle(.graphical).environment(\.timeZone, Dates.calendar.timeZone); if Dates.day(monday) != 1 { Text("请选择周一").foregroundStyle(.red) } }
            Button("读取导入预览") { dismiss(); DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { model.importTable(term: term, firstMonday: monday, name: name) } }.disabled(model.db.account == nil || model.busy || Dates.day(monday) != 1 || name.isEmpty)
        }.navigationTitle("导入学校课表").toolbar { Button("取消") { dismiss() } } }
    }
}
