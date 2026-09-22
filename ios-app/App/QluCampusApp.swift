// QluCampus iOS, GPL-3.0.
import SwiftUI
import CampusCore

@main struct QluCampusApp: App {
    @StateObject private var model = AppModel()
    var body: some Scene { WindowGroup { CampusRoot().environmentObject(model) } }
}
struct CampusRoot: View {
    @EnvironmentObject var model: AppModel
    @State private var tab = 0
    @State private var settings = false
    @State private var disclaimer = false
    @State private var hideDisclaimer = false
    @State private var image: UIImage? = nil
    private var theme: CampusTheme { CampusTheme(appearance: model.appearance, image: image) }
    var body: some View {
        ZStack {
            CampusBackground()
            VStack(spacing: 0) {
                Group {
                    switch tab {
                    case 1: GradesView()
                    case 2: RoomsView()
                    default: TimetableView(openSettings: { settings = true })
                    }
                }.frame(maxWidth: .infinity, maxHeight: .infinity)
                HStack {
                    tabButton(0, "课表", "calendar")
                    tabButton(1, "成绩", "chart.bar")
                    tabButton(2, "空教室", "door.left.hand.open")
                    Button { settings = true } label: { VStack(spacing: 4) { Image(systemName: "gearshape"); Text("设置").font(theme.font(11)) }.frame(maxWidth: .infinity).padding(.vertical, 9) }.accessibilityIdentifier("settingsTab")
                }.font(theme.font(19)).regionalText(useWallpaper: model.appearance.extendBackground).background(model.appearance.extendBackground ? .white.opacity(0.12) : theme.background[0].opacity(0.95)).clipShape(RoundedRectangle(cornerRadius: 22))
            }
            if model.busy { VStack { ProgressView().padding(); Text("正在读取学校数据…").font(.footnote) }.padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18)).allowsHitTesting(false) }
        }
        .environment(\.campusTheme, theme)
        .preferredColorScheme(model.appearance.style == "夜色" ? .dark : .light)
        .font(theme.font(15)).tint(theme.text)
        .sheet(isPresented: $settings) { SettingsView().environmentObject(model).environment(\.campusTheme, theme) }
        .sheet(item: $model.browser) { route in SchoolBrowser(route: route).environmentObject(model) }
        .sheet(item: $model.sharedFile) { ShareSheet(url: $0.url) }
        .sheet(item: $model.preview) { preview in
            NavigationStack { List {
                Section { Text(preview.table.name); Text("首周周一：\(preview.table.firstMonday)"); Text("\(preview.table.courses.count) 门课程安排") }
                ForEach(preview.table.courses) { c in VStack(alignment: .leading) { Text(c.name); Text("周\(c.day) · 第 \(c.start)–\(c.end) 节 · \(c.location)").font(.caption) } }
            }.navigationTitle("导入预览").toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { model.preview = nil } }; ToolbarItem(placement: .confirmationAction) { Button("确认保存") { model.confirmImport() } } } }
        }
        .sheet(isPresented: $disclaimer) {
            VStack(alignment: .leading, spacing: 22) {
                Text("使用说明与免责声明").font(.title2.bold())
                Text("齐鲁课表为非官方个人工具。课表、成绩和教室信息以学校教务系统及实际安排为准；空教室查询不代表预约。学校账号、Cookie 和成绩仅用于学校查询与本机保存，默认不保存密码。")
                Text("作者微信：a3130149711\n遇到 Bug 请联系作者。\n基于 Dawn Course，原作者 HF-CYGG；源码遵循 GPL-3.0。")
                Toggle("不再显示", isOn: $hideDisclaimer)
                Button("开始使用") { try? model.commit { $0.disclaimerHidden = hideDisclaimer }; disclaimer = false }.buttonStyle(.borderedProminent)
            }.padding(28).interactiveDismissDisabled()
        }
        .alert("齐鲁课表", isPresented: Binding(get: { model.message != nil && !settings }, set: { if !$0 { model.message = nil } })) { Button("知道了") { model.message = nil } } message: { Text(model.message ?? "") }
        .task { disclaimer = !model.db.disclaimerHidden; loadImage(); model.scheduleReminders() }
        .onChange(of: model.appearance.backgroundFile) { _ in loadImage() }
    }
    private func tabButton(_ number: Int, _ title: String, _ icon: String) -> some View {
        Button { tab = number } label: { VStack(spacing: 4) { Image(systemName: icon); Text(title).font(theme.font(11, bold: tab == number)) }.frame(maxWidth: .infinity).padding(.vertical, 9).background(tab == number ? .white.opacity(0.18) : .clear, in: Capsule()) }.accessibilityIdentifier("tab\(number)")
    }
    private func loadImage() { image = model.appearance.backgroundFile.flatMap { UIImage(contentsOfFile: model.directory.appendingPathComponent($0).path) } }
}
struct ShareSheet: UIViewControllerRepresentable {
    var url: URL
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: [url], applicationActivities: nil) }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

struct TermPicker: View {
    @Binding var term: Term
    var body: some View { HStack { Picker("学年", selection: $term.year) { ForEach(2000...2100, id: \.self) { Text("\($0)–\($0 + 1)").tag($0) } }; Picker("学期", selection: $term.semester) { Text("第 1 学期").tag(1); Text("第 2 学期").tag(2) } }.pickerStyle(.menu) }
}
