// QluCampus iOS, GPL-3.0.
import SwiftUI
import CampusCore

struct CampusTheme {
    var appearance: Appearance, image: UIImage?
    static let styles = ["Wake Up", "云雾蓝", "鼠尾草", "奶油杏", "雾紫", "夜色"]
    var background: [Color] {
        switch appearance.style {
        case "鼠尾草": return [Color(hex: "E7EEE5"), Color(hex: "C6D6C9")]
        case "奶油杏": return [Color(hex: "FBF1DF"), Color(hex: "E8D9C4")]
        case "雾紫": return [Color(hex: "F0EBF5"), Color(hex: "D3CADF")]
        case "夜色": return [Color(hex: "192230"), Color(hex: "293F4E")]
        default: return [Color(hex: "E5E5F2"), Color(hex: "BDD0E0")]
        }
    }
    var text: Color { appearance.textMode == "自定义" ? Color(hex: appearance.textHex) : (appearance.style == "夜色" ? .white : Color(hex: "202124")) }
    func font(_ size: Double, bold: Bool = false) -> Font {
        .system(size: size, weight: (bold || appearance.font == "加粗") ? .bold : .regular, design: appearance.font == "衬线" ? .serif : appearance.font == "等宽" ? .monospaced : .default)
    }
    func courseColor(_ course: Course, active: Bool) -> Color {
        guard active else { return Color(hex: appearance.style == "夜色" ? "656B73" : "B0B3BA") }
        let colors: [String]
        if appearance.highContrast {
            let colors = ["165A9C", "8B2450", "246341", "704090", "985024", "315C68"]
            let hash = course.name.utf8.reduce(UInt64(2166136261)) { ($0 ^ UInt64($1)) &* 16777619 }
            return Color(hex: colors[Int(hash % UInt64(colors.count))])
        }
        switch appearance.style {
        case "鼠尾草": colors = ["81AA97", "9AAD79", "B1A57E", "829CAF", "AA8E9B"]
        case "奶油杏": colors = ["D7A87A", "CCA4A3", "9DAE88", "96AEC0", "BDA1BD"]
        case "雾紫": colors = ["B194C9", "CBA0B6", "939DC8", "92B5B0", "CBB08D"]
        default: colors = ["E97D9F", "84ADF0", "E8B768", "679DCA", "B29FE5", "E6927D"]
        }
        let hash = course.name.utf8.reduce(UInt64(2166136261)) { ($0 ^ UInt64($1)) &* 16777619 }
        return Color(hex: colors[Int(hash % UInt64(colors.count))])
    }
    func courseText(_ color: Color) -> Color {
        if appearance.textMode == "自定义" { return Color(hex: appearance.textHex) }
        if appearance.textMode == "跟随风格" && !appearance.adaptiveCourses { return .white }
        return UIColor(color).luminance > 0.179 ? .black : .white
    }
    func regionalColor(_ frame: CGRect) -> Color {
        guard appearance.textMode == "自动黑白", let image, let cg = image.cgImage else { return text }
        let screen = UIScreen.main.bounds
        let scale = max(screen.width / CGFloat(cg.width), screen.height / CGFloat(cg.height))
        let left = (CGFloat(cg.width) * scale - screen.width) / 2, top = (CGFloat(cg.height) * scale - screen.height) / 2
        var sum: CGFloat = 0, count: CGFloat = 0
        for y in [0.25, 0.5, 0.75] { for x in [0.25, 0.5, 0.75] {
            let px = (frame.minX + frame.width * x + left) / scale, py = (frame.minY + frame.height * y + top) / scale
            if let crop = cg.cropping(to: CGRect(x: max(0, min(CGFloat(cg.width - 1), px)), y: max(0, min(CGFloat(cg.height - 1), py)), width: 1, height: 1)) {
                var bytes: [UInt8] = [0, 0, 0, 255]
                if let context = CGContext(data: &bytes, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) {
                    context.draw(crop, in: CGRect(x: 0, y: 0, width: 1, height: 1))
                    let color = UIColor(red: CGFloat(bytes[0]) / 255 * appearance.brightness, green: CGFloat(bytes[1]) / 255 * appearance.brightness, blue: CGFloat(bytes[2]) / 255 * appearance.brightness, alpha: 1)
                    sum += color.luminance; count += 1
                }
            }
        } }
        return count > 0 && sum / count <= 0.179 ? .white : .black
    }
}
private struct ThemeKey: EnvironmentKey { static let defaultValue = CampusTheme(appearance: Appearance(), image: nil) }
extension EnvironmentValues { var campusTheme: CampusTheme { get { self[ThemeKey.self] } set { self[ThemeKey.self] = newValue } } }
extension Color {
    init(hex: String) { let text = hex.replacingOccurrences(of: "#", with: ""); let value = UInt32(text, radix: 16) ?? 0x202124; self.init(red: Double(value >> 16 & 255) / 255, green: Double(value >> 8 & 255) / 255, blue: Double(value & 255) / 255) }
}
extension UIColor {
    var luminance: CGFloat {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0; getRed(&r, green: &g, blue: &b, alpha: &a)
        func linear(_ c: CGFloat) -> CGFloat { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }
    var hex: String { var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0; getRed(&r, green: &g, blue: &b, alpha: &a); return String(format: "#%02X%02X%02X", Int(r * 255), Int(g * 255), Int(b * 255)) }
}
struct RegionalText: ViewModifier {
    @Environment(\.campusTheme) var theme
    @State private var frame = CGRect.zero
    var useWallpaper = true
    func body(content: Content) -> some View {
        content.foregroundColor(useWallpaper ? theme.regionalColor(frame) : theme.text).background(GeometryReader { geo in
            Color.clear.onAppear { frame = geo.frame(in: .global) }.onChange(of: geo.frame(in: .global)) { frame = $0 }
        })
    }
}
extension View { func regionalText(useWallpaper: Bool = true) -> some View { modifier(RegionalText(useWallpaper: useWallpaper)) } }

struct CampusBackground: View {
    @Environment(\.campusTheme) var theme
    var body: some View {
        GeometryReader { geo in
            ZStack {
                LinearGradient(colors: theme.background, startPoint: .topLeading, endPoint: .bottomTrailing)
                if let image = theme.image { Image(uiImage: image).resizable().scaledToFill().frame(width: geo.size.width, height: geo.size.height).clipped().blur(radius: theme.appearance.blur); Color.black.opacity(1 - theme.appearance.brightness) }
            }
        }.ignoresSafeArea()
    }
}
