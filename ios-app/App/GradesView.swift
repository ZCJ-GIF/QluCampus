// QluCampus iOS, GPL-3.0.
import SwiftUI
import CampusCore

struct GradesView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.campusTheme) var theme
    @State private var term = Term.current
    @State private var showGpa = false
    var snapshot: GradeSnapshot? { model.snapshots.first { $0.term == term } }
    var body: some View {
        VStack(spacing: 10) {
            HStack { Text(model.db.gradeUnlocked ? "成绩与分项" : "成绩查询").font(theme.font(25, bold: true)); Spacer(); Button("GPA") { showGpa = true }.accessibilityIdentifier("openGpa") }.padding(.horizontal)
            TermPicker(term: $term).padding(.horizontal).regionalText()
            HStack {
                Button(model.db.account == nil ? "学校登录" : "切换 / 重新登录") { model.login() }
                Spacer()
                Button("刷新") { model.refreshGrades(term) }.disabled(model.busy || model.db.account == nil)
                if let snapshot { Button { model.exportGrades(snapshot) } label: { Image(systemName: "square.and.arrow.up") }.accessibilityLabel("导出 Excel") }
            }.padding(.horizontal).regionalText()
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    Text("账号：\(model.maskedAccount) · 校外需连接 aTrust").font(theme.font(12)).regionalText()
                    if let snapshot {
                        Text("最后更新：\(snapshot.fetchedAt.formatted(date: .abbreviated, time: .shortened))").font(theme.font(12)).regionalText()
                        let matched = Grades.match(snapshot)
                        if matched.courses.isEmpty { Text("该学期学校未返回成绩记录").regionalText() }
                        ForEach(matched.courses) { course in gradeCard(course) }
                        if !matched.unmatched.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("未能唯一匹配的绩点").font(theme.font(16, bold: true))
                                Text("重名或标识有歧义，以下记录独立显示，不按行号合并。").font(theme.font(12))
                                ForEach(Array(matched.unmatched.enumerated()), id: \.offset) { item in Text("\(item.element.name)：\(provided(item.element.point)) · 学分绩点 \(provided(item.element.weightedPoint))").font(theme.font(14)) }
                            }.padding().background(.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 16)).regionalText()
                        }
                    } else { Text("此学期暂无本机缓存。登录学校后点击刷新。").padding(.vertical, 30).regionalText() }
                }.padding()
            }
        }.padding(.top, 14).sheet(isPresented: $showGpa) { GpaView().environmentObject(model) }
    }
    func gradeCard(_ course: MatchedGrade) -> some View {
        let score = Grades.decimal(course.total)
        let semantic: Color = score.map { $0 >= 60 ? .green : .red } ?? theme.text
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) { Text(course.name).font(theme.font(17, bold: true)); Spacer(); Text(score.map { $0 >= 60 ? "合格" : "挂科" } ?? "未判定").font(theme.font(12)).foregroundColor(score == nil ? nil : semantic) }
            HStack { Text("总评 \(provided(course.total))").foregroundColor(score == nil ? nil : semantic); Spacer(); Text("学分 \(provided(course.details.first?.credits ?? ""))") }
            Text("绩点 \(provided(course.point?.point ?? "")) · 学分绩点 \(provided(course.point?.weightedPoint ?? ""))").font(theme.font(13))
            if model.db.gradeUnlocked {
                ForEach(Array(course.details.enumerated()), id: \.offset) { item in HStack { Text(provided(item.element.component)); Spacer(); Text(provided(item.element.score)) }.font(theme.font(13)) }
            }
        }.padding(15).regionalText().background(score == nil ? .white.opacity(0.12) : semantic.opacity(0.11), in: RoundedRectangle(cornerRadius: 16))
    }
    func provided(_ text: String) -> String { text.isEmpty ? "未提供" : text }
}

struct GpaView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) var dismiss
    @State private var chosen = Set<String>()
    var rows: [(String, String, MatchedGrade)] { model.snapshots.sorted { $0.term.id < $1.term.id }.flatMap { snapshot in Grades.match(snapshot).courses.map { (snapshot.term.id + "|" + $0.id, snapshot.term.label, $0) } } }
    var body: some View {
        NavigationStack { List {
            Section {
                let result = Grades.gpa(rows.filter { chosen.contains($0.0) }.map { $0.2 })
                Text("GPA：\(Grades.format(result.value))").font(.title.bold()).accessibilityIdentifier("gpaResult")
                Text("已选 \(chosen.count) 门 · 计入学分 \(Grades.format(result.credits))")
                if result.excluded > 0 { Text("其中 \(result.excluded) 门数据不完整，未计入结果").foregroundStyle(.orange) }
                Text("Σ（课程学分 × 学校绩点）÷ Σ学分。缺少学分、绩点或匹配有歧义的课程不可选，不从总评推算。").font(.footnote)
                HStack { Button("全选有效课程") { chosen = Set(rows.filter { Grades.inputs($0.2) != nil }.map { $0.0 }) }; Spacer(); Button("清空") { chosen = [] } }.buttonStyle(.borderless)
            }
            ForEach(rows, id: \.0) { row in
                Button { if chosen.contains(row.0) { chosen.remove(row.0) } else { chosen.insert(row.0) } } label: {
                    HStack {
                        Image(systemName: chosen.contains(row.0) ? "checkmark.circle.fill" : "circle")
                        VStack(alignment: .leading, spacing: 5) { Text(row.2.name); Text(row.1).font(.caption); if let input = Grades.inputs(row.2) { Text("学分 \(Grades.format(input.0)) · 绩点 \(Grades.format(input.1))").font(.caption) } else { Text("学分 / 绩点缺失或匹配不唯一").font(.caption).foregroundStyle(.secondary) } }
                    }
                }.disabled(Grades.inputs(row.2) == nil)
            }
        }.navigationTitle("手选课程计算 GPA").toolbar { Button("完成") { dismiss() } } }
        .onChange(of: model.owner) { _ in chosen = [] }
    }
}
