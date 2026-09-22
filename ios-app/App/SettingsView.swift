// QluCampus iOS, GPL-3.0.
import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import CampusCore

struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    @Environment(\.campusTheme) var theme
    @State private var photo: PhotosPickerItem? = nil
    @State private var importFile = false
    @State private var unlocking = false
    @State private var password = ""
    @State private var colorHex = ""
    @State private var confirmLogout = false
    func binding<T>(_ key: WritableKeyPath<Appearance, T>) -> Binding<T> { Binding(get: { model.appearance[keyPath: key] }, set: { value in model.changeAppearance { $0[keyPath: key] = value } }) }
    var body: some View {
        NavigationStack {
            ZStack {
                if model.appearance.extendBackground { CampusBackground() }
                Form {
                    Section("学校账号") {
                        Text("当前：\(model.maskedAccount)")
                        Button("学校统一认证登录") { dismiss(); DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { model.login() } }
                        if model.db.account != nil { Button("退出并切换账号", role: .destructive) { confirmLogout = true } }
                        Text("校外导入课表、查成绩或空教室前，请先连接 iPhone 上的 aTrust，再返回查询。").font(.footnote)
                    }.listRowBackground(rowBackground)
                    Section("风格与背景") {
                        Picker("风格", selection: binding(\.style)) { ForEach(CampusTheme.styles, id: \.self) { Text($0) } }
                        PhotosPicker(selection: $photo, matching: .images) { Label("选择自定义背景", systemImage: "photo") }
                        Button("恢复风格背景") { model.changeAppearance { $0.backgroundFile = nil } }
                        Toggle("背景延伸到导航栏和其他页面", isOn: binding(\.extendBackground))
                        Text("毛玻璃强度 \(Int(model.appearance.blur))")
                        Slider(value: binding(\.blur), in: 0...24)
                        Text("背景亮度 \(Int(model.appearance.brightness * 100))%")
                        Slider(value: binding(\.brightness), in: 0.1...1)
                        Toggle("课程颜色适应背景", isOn: binding(\.adaptiveCourses))
                        Toggle("课程高对比度", isOn: binding(\.highContrast))
                        Toggle("显示课程边缘线", isOn: binding(\.borders))
                        Toggle("显示非本周课程（灰色）", isOn: binding(\.showOtherWeeks))
                    }.listRowBackground(rowBackground)
                    Section("字体与文字颜色") {
                        Picker("字体样式", selection: binding(\.font)) { ForEach(["系统", "衬线", "等宽", "加粗"], id: \.self) { Text($0) } }.accessibilityIdentifier("fontPicker")
                        Text("课程文字大小：\(Int(model.appearance.fontSize))")
                        Slider(value: binding(\.fontSize), in: 8...18, step: 1)
                        Picker("文字颜色", selection: binding(\.textMode)) { ForEach(["跟随风格", "自动黑白", "自定义"], id: \.self) { Text($0) } }.accessibilityIdentifier("colorModePicker")
                        if model.appearance.textMode == "自定义" {
                            ColorPicker("选择颜色", selection: Binding(get: { Color(hex: model.appearance.textHex) }, set: { color in model.changeAppearance { $0.textHex = UIColor(color).hex }; colorHex = model.appearance.textHex }), supportsOpacity: false)
                            HStack { TextField("#RRGGBB", text: $colorHex).textInputAutocapitalization(.characters).autocorrectionDisabled(); Button("应用") { model.changeAppearance { $0.textHex = colorHex.uppercased() } }.disabled(colorHex.range(of: "^#[0-9a-fA-F]{6}$", options: .regularExpression) == nil) }
                        }
                        Text("自动黑白按背景明暗调整日期、时间轴和导航文字；成绩合格 / 挂科保留红绿标记。").font(.footnote)
                        Button("恢复默认字体与颜色") { model.changeAppearance { $0.font = "系统"; $0.textMode = "自动黑白"; $0.textHex = "#202124"; $0.fontSize = 11 } }
                    }
                    Section("提醒与桌面组件") {
                        Toggle("提前 10 分钟提醒上课", isOn: binding(\.reminders)).onChange(of: model.appearance.reminders) { _ in model.scheduleReminders() }
                        Text("iOS 最多预排最近 60 次课程提醒；打开软件会重新补充。桌面组件需使用带 App Group 签名的组件构建，在桌面长按 → 添加小组件 → 齐鲁课表。").font(.footnote)
                    }.listRowBackground(rowBackground)
                    Section("本地备份") {
                        Button("导出 iOS 备份") { model.exportBackup() }
                        Button("导入 iOS 备份") { importFile = true }
                        Text("备份包含当前账号课表和可见成绩，不包含密码、Cookie 或解锁状态。导入时追加课表，保留原数据。").font(.footnote)
                    }.listRowBackground(rowBackground)
                    if model.db.gradeUnlocked { Section { Button("锁定并隐藏平时成绩") { model.lock() } } }
                    Section("关于") {
                        Text("齐鲁课表 iOS 0.1.0（个人预览版）").onLongPressGesture(minimumDuration: 1.2) { password = ""; unlocking = true }.accessibilityIdentifier("iosVersion")
                        Text("作者微信：a3130149711\n遇到 Bug 请联系作者")
                        Link("项目源码与发布记录", destination: URL(string: "https://github.com/ZCJ-GIF/QluCampus")!)
                        Text("非官方工具，课表、成绩和教室安排以学校为准。基于 Dawn Course（HF-CYGG），沿用 GPL-3.0。第三方：SwiftSoup / ZIPFoundation（MIT）。")
                        Button("下次打开显示免责声明") { try? model.commit { $0.disclaimerHidden = false }; model.message = "下次启动时显示" }
                        Text("iOS 版暂不从 Android 更新源下载安装；更新使用同一 iOS Bundle ID 和签名覆盖安装。").font(.footnote)
                    }.listRowBackground(rowBackground)
                }.scrollContentBackground(model.appearance.extendBackground ? .hidden : .visible)
            }.navigationTitle("设置").toolbar { Button("完成") { dismiss() } }
        }
        .onAppear { colorHex = model.appearance.textHex }
        .onChange(of: photo) { selected in Task { if let data = try? await selected?.loadTransferable(type: Data.self) { model.setBackground(data) } } }
        .fileImporter(isPresented: $importFile, allowedContentTypes: [.json]) { result in if case .success(let url) = result { model.importBackup(url) } }
        .alert("隐藏入口", isPresented: $unlocking) { SecureField("输入密码", text: $password); Button("开启") { model.unlock(password); password = "" }; Button("取消", role: .cancel) { password = "" } } message: { Text("开启后保持显示，直到在设置中锁定。此开关控制界面显示，不代替设备安全锁。") }
        .confirmationDialog("退出学校账号？本机历史数据按账号保留。", isPresented: $confirmLogout, titleVisibility: .visible) { Button("退出账号", role: .destructive) { model.logout() } }
    }
    var rowBackground: Color { model.appearance.extendBackground && model.appearance.backgroundFile != nil ? .white.opacity(0.18) : Color(uiColor: .secondarySystemGroupedBackground) }
}
