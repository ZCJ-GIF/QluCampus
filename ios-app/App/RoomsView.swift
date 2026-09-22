// QluCampus iOS, GPL-3.0.
import SwiftUI
import CampusCore

struct RoomsView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.campusTheme) var theme
    @State private var term = Term.current, date = Date(), monday = Dates.monday(Date())
    @State private var start = 1, end = 2, campus = "", building = "", roomType = ""
    var query: RoomQuery { RoomQuery(term: term, firstMonday: Dates.string(monday), date: Dates.string(date), start: start, end: end, campus: campus, building: building, type: roomType) }
    var result: RoomResult? { model.db.roomResults.last { $0.owner == model.owner && $0.query == query } }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("空教室查询").font(theme.font(25, bold: true)).regionalText()
                Text("按学校记录查整个时段空闲的教室，不代表预约；到场以实际占用为准。").font(theme.font(12)).regionalText()
                HStack { Text(model.maskedAccount); Spacer(); Button("学校登录") { model.login() } }.regionalText()
                TermPicker(term: $term).regionalText()
                DisclosureGroup("课表首周周一：\(Dates.string(monday))") { DatePicker("首周周一", selection: $monday, displayedComponents: .date).datePickerStyle(.graphical).environment(\.timeZone, Dates.calendar.timeZone) }.regionalText()
                DatePicker("查询日期", selection: $date, displayedComponents: .date).datePickerStyle(.graphical).environment(\.timeZone, Dates.calendar.timeZone).padding(8).background(.white.opacity(0.35), in: RoundedRectangle(cornerRadius: 18))
                Stepper("从第 \(start) 节", value: $start, in: 1...16).onChange(of: start) { end = max($0, end) }.regionalText()
                Stepper("至第 \(end) 节", value: $end, in: start...16).regionalText()
                Button("加载学校查询选项") { model.loadRooms(term: term) }.buttonStyle(.borderedProminent).disabled(model.db.account == nil || model.busy)
                if let options = model.options, options.term == term {
                    Picker("校区", selection: $campus) { ForEach(options.campuses) { Text($0.label).tag($0.id) } }.onChange(of: campus) { value in building = ""; if value != options.selectedCampus { model.loadRooms(term: term, campus: value) } }
                    Picker("教学楼", selection: $building) { Text("全部").tag(""); ForEach(options.buildings) { Text($0.label).tag($0.id) } }
                    Picker("场地类别", selection: $roomType) { Text("全部").tag(""); ForEach(options.types) { Text($0.label).tag($0.id) } }
                    Button("查询整个时段空闲的教室") { model.queryRooms(query) }.buttonStyle(.borderedProminent).disabled(model.busy || campus != options.selectedCampus || Dates.day(monday) != 1)
                }
                Button("打开学校官方查询页面") { model.openSchoolPage(URL(string: School.root + School.roomPage)!) }.regionalText()
                if let result {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("\(result.query.date) · 第 \(result.query.start)–\(result.query.end) 节").font(theme.font(16, bold: true))
                        Text("查询时间 \(result.fetchedAt.formatted(date: .abbreviated, time: .shortened)) · 缓存结果").font(theme.font(11))
                        if result.rooms.isEmpty { Text("学校返回此时段没有符合条件的空教室") }
                        ForEach(result.rooms) { room in VStack(alignment: .leading) { Text(room.name).font(theme.font(17, bold: true)); Text("\(room.campus) · \(room.building) · 座位 \(room.capacity.isEmpty ? "未提供" : room.capacity)").font(theme.font(12)) }.padding(.vertical, 5) }
                    }.padding().background(.white.opacity(0.2), in: RoundedRectangle(cornerRadius: 18)).regionalText()
                }
            }.padding()
        }
        .onAppear { if let table = model.active { term = table.term; monday = Dates.parse(table.firstMonday) ?? monday } }
        .onChange(of: model.options?.selectedCampus) { value in if let value { campus = value } }
        .onChange(of: term) { _ in model.options = nil; campus = ""; building = ""; roomType = "" }
        .onChange(of: model.owner) { _ in campus = ""; building = ""; roomType = "" }
    }
}
