// QluCampus iOS, GPL-3.0. Only the selected timetable is shared with the widget.
import SwiftUI
import WidgetKit
import CampusCore

struct TodayEntry: TimelineEntry { var date: Date; var courses: [Course]; var name: String }
struct TodayProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayEntry { TodayEntry(date: Date(), courses: [Course(name: "今日课程", location: "教学楼 207")], name: "齐鲁课表") }
    func getSnapshot(in context: Context, completion: @escaping (TodayEntry) -> Void) { completion(entry(Date())) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayEntry>) -> Void) {
        let now = Date(), tomorrow = Dates.calendar.startOfDay(for: Dates.add(now, days: 1))
        completion(Timeline(entries: [entry(now), entry(tomorrow)], policy: .after(tomorrow.addingTimeInterval(60))))
    }
    func entry(_ date: Date) -> TodayEntry {
        guard let dir = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.qlucampus.ios"), let data = try? Data(contentsOf: dir.appendingPathComponent("widget.json")), let table = try? JSONDecoder().decode(Timetable.self, from: data), let monday = Dates.parse(table.firstMonday) else { return TodayEntry(date: date, courses: [], name: "请先在应用选择课表") }
        let week = Dates.week(date, firstMonday: monday)
        return TodayEntry(date: date, courses: table.courses.filter { $0.day == Dates.day(date) && $0.weeks.contains(week) }.sorted { $0.start < $1.start }, name: table.name)
    }
}
struct TodayWidgetView: View {
    var entry: TodayEntry
    @Environment(\.widgetFamily) var family
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack { Text("今日课表").bold(); Spacer(); Text(entry.date, style: .date).font(.caption2) }
            if entry.courses.isEmpty { Text("今天没有课程").font(.headline); Text(entry.name).font(.caption) }
            ForEach(entry.courses.prefix(family == .systemLarge ? 6 : 3)) { c in HStack(alignment: .top) { Text(c.start <= School.sectionTimes.count ? String(School.sectionTimes[c.start - 1].prefix(5)) : "第\(c.start)节").font(.caption.monospacedDigit()); VStack(alignment: .leading) { Text(c.name).font(.caption.bold()).lineLimit(1); Text(c.location).font(.caption2).lineLimit(1) } } }
            if entry.courses.count > (family == .systemLarge ? 6 : 3) { Text("更多课程请打开应用").font(.caption2) }
            Spacer(minLength: 0)
        }.padding().widgetBackground()
    }
}
private extension View {
    @ViewBuilder func widgetBackground() -> some View {
        if #available(iOS 17.0, *) { containerBackground(for: .widget) { Color(red: 0.89, green: 0.93, blue: 0.97) } }
        else { background(Color(red: 0.89, green: 0.93, blue: 0.97)) }
    }
}
@main struct QluTodayWidget: Widget {
    let kind = "QluTodayWidget"
    var body: some WidgetConfiguration { StaticConfiguration(kind: kind, provider: TodayProvider()) { TodayWidgetView(entry: $0) }.configurationDisplayName("今日课表").description("查看所选课表今天的课程与地点").supportedFamilies([.systemSmall, .systemMedium, .systemLarge]) }
}
