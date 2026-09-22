// QluCampus iOS, GPL-3.0. OOXML only; never treat HTML/error pages as empty grades.
import Foundation
import ZIPFoundation

public enum Workbook {
    public static func read(_ data: Data) throws -> [[String]] {
        guard data.count <= 8 * 1024 * 1024, data.starts(with: [0x50, 0x4b]) else {
            try SchoolParser.rejectLogin(String(decoding: data.prefix(4096), as: UTF8.self))
            throw CampusError.invalid("学校没有返回 XLSX 文件，旧成绩已保留")
        }
        let archive = try Archive(data: data, accessMode: .read)
        var files: [String: Data] = [:], size = 0
        for entry in archive {
            guard files.count < 512, files[entry.path] == nil, entry.uncompressedSize <= 32 * 1024 * 1024 else { throw CampusError.invalid("表格压缩结构超出限制") }
            var body = Data()
            _ = try archive.extract(entry) { chunk in
                size += chunk.count
                guard size <= 32 * 1024 * 1024 else { throw CampusError.invalid("表格解压后过大") }; body.append(chunk)
            }
            files[entry.path] = body
        }
        func xml(_ path: String) throws -> Node {
            guard let file = files[path] else { throw CampusError.invalid("XLSX 缺少必要内容") }; return try XMLTree.parse(file)
        }
        let shared = try files["xl/sharedStrings.xml"].map { try XMLTree.parse($0).all("si").map { $0.all("t").map(\.text).joined() } } ?? []
        guard let sheet = try xml("xl/workbook.xml").all("sheet").first, let rid = sheet.attrs["r:id"], let rel = try xml("xl/_rels/workbook.xml.rels").all("Relationship").first(where: { $0.attrs["Id"] == rid && $0.attrs["TargetMode"] != "External" }), let target = rel.attrs["Target"] else { throw CampusError.invalid("工作表关系无效") }
        let path = target.hasPrefix("/") ? String(target.dropFirst()) : "xl/" + target
        guard path.hasPrefix("xl/"), !path.contains(".."), !path.contains("\\") else { throw CampusError.invalid("工作表路径无效") }
        let rows = try xml(path).all("row")
        guard rows.count <= 20000 else { throw CampusError.invalid("成绩行数过多") }
        return try rows.map { row in
            var result: [String] = []
            for (position, cell) in row.all("c").enumerated() {
                let letters = (cell.attrs["r"] ?? "").prefix { $0.isASCII && $0.isLetter }.uppercased()
                guard letters.count <= 2 else { throw CampusError.invalid("表格列标识超出范围") }
                let index = letters.isEmpty ? position : letters.utf8.reduce(0) { $0 * 26 + Int($1) - 64 } - 1
                guard (0..<80).contains(index) else { throw CampusError.invalid("表格列数过多") }
                while result.count <= index { result.append("") }
                let v = cell.all("v").first?.text ?? ""
                if cell.attrs["t"] == "s" {
                    guard let i = Int(v), shared.indices.contains(i) else { throw CampusError.invalid("表格字符串索引无效") }; result[index] = shared[i]
                } else if cell.attrs["t"] == "inlineStr" { result[index] = cell.all("t").map(\.text).joined() }
                else { result[index] = v }
                result[index] = result[index].trimmingCharacters(in: .whitespacesAndNewlines)
            }
            return result
        }.filter { $0.contains { !$0.isEmpty } }
    }
    public static func export(_ snapshot: GradeSnapshot) throws -> Data {
        let detail = [["课程名称", "课程代码", "教学班", "学分", "成绩分项", "成绩"]] + snapshot.details.map { [$0.name, $0.code, $0.className, $0.credits, $0.component, $0.score] }
        let points = [["课程名称", "课程代码", "教学班", "绩点", "学分绩点"]] + snapshot.points.map { [$0.name, $0.code, $0.className, $0.point, $0.weightedPoint] }
        return try write([("成绩明细", detail), ("绩点", points)])
    }
    public static func write(_ sheets: [(String, [[String]])]) throws -> Data {
        guard !sheets.isEmpty, sheets.count <= 10 else { throw CampusError.invalid("工作表数量无效") }
        let a = try Archive(accessMode: .create)
        func put(_ path: String, _ text: String) throws {
            let bytes = Data(text.utf8)
            try a.addEntry(with: path, type: .file, uncompressedSize: Int64(bytes.count), compressionMethod: .deflate) { position, size in bytes.subdata(in: Int(position)..<(Int(position) + size)) }
        }
        let overrides = sheets.indices.map { "<Override PartName=\"/xl/worksheets/sheet\($0 + 1).xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }.joined()
        try put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\(overrides)</Types>")
        try put("_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
        let names = sheets.enumerated().map { "<sheet name=\"\(escape($0.element.0))\" sheetId=\"\($0.offset + 1)\" r:id=\"rId\($0.offset + 1)\"/>" }.joined()
        try put("xl/workbook.xml", "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>\(names)</sheets></workbook>")
        let rels = sheets.indices.map { "<Relationship Id=\"rId\($0 + 1)\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet\($0 + 1).xml\"/>" }.joined()
        try put("xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\(rels)</Relationships>")
        for (index, sheet) in sheets.enumerated() {
            guard sheet.1.count <= 20000, sheet.1.allSatisfy({ $0.count <= 80 }) else { throw CampusError.invalid("表格大小超出范围") }
            let rows = sheet.1.enumerated().map { row in
                "<row r=\"\(row.offset + 1)\">" + row.element.enumerated().map { cell in
                    // Inline strings keep identifiers, decimal spellings and formula-like text literal.
                    "<c r=\"\(column(cell.offset))\(row.offset + 1)\" t=\"inlineStr\"><is><t xml:space=\"preserve\">\(escape(cell.element))</t></is></c>"
                }.joined() + "</row>"
            }.joined()
            try put("xl/worksheets/sheet\(index + 1).xml", "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>\(rows)</sheetData></worksheet>")
        }
        guard let result = a.data else { throw CampusError.invalid("无法生成 Excel") }; return result
    }
    private static func column(_ index: Int) -> String {
        var n = index + 1, out = ""
        while n > 0 { n -= 1; out = String(UnicodeScalar(65 + n % 26)!) + out; n /= 26 }; return out
    }
    private static func escape(_ s: String) -> String {
        s.filter { !$0.unicodeScalars.contains { $0.value < 32 && ![9, 10, 13].contains($0.value) } }.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;").replacingOccurrences(of: "\"", with: "&quot;")
    }
}

private final class Node {
    var name: String, attrs: [String: String], text = "", children: [Node] = []
    init(_ name: String, _ attrs: [String: String]) { self.name = name.components(separatedBy: ":").last!; self.attrs = attrs }
    func all(_ name: String) -> [Node] { (self.name == name ? [self] : []) + children.flatMap { $0.all(name) } }
}
private final class XMLTree: NSObject, XMLParserDelegate {
    var root = Node("root", [:]), stack: [Node] = [], failure = false, nodes = 0
    static func parse(_ data: Data) throws -> Node {
        let raw = String(decoding: data, as: UTF8.self).uppercased()
        guard !raw.contains("<!DOCTYPE"), !raw.contains("<!ENTITY") else { throw CampusError.invalid("不支持的 XML 声明") }
        let delegate = XMLTree(); delegate.stack = [delegate.root]
        let parser = XMLParser(data: data); parser.shouldResolveExternalEntities = false; parser.delegate = delegate
        guard parser.parse(), !delegate.failure else { throw CampusError.invalid("XLSX XML 内容损坏") }; return delegate.root
    }
    func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName qName: String?, attributes: [String: String]) {
        nodes += 1
        if stack.count > 32 || nodes > 500000 { failure = true; parser.abortParsing(); return }
        let n = Node(name, attributes); stack.last?.children.append(n); stack.append(n)
    }
    func parser(_ parser: XMLParser, foundCharacters string: String) { stack.last?.text += string }
    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) { if stack.count > 1 { stack.removeLast() } }
}
