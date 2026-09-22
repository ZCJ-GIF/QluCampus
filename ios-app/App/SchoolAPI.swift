// QluCampus iOS, GPL-3.0. One WKWebView cookie store shared by all school features.
import Foundation
import WebKit
import CampusCore

struct SessionTicket: Equatable { var account: String, revision: UUID }
private final class NoRedirect: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}

@MainActor final class SchoolAPI {
    private let delegate = NoRedirect()
    private lazy var session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil; config.httpShouldSetCookies = false; config.timeoutIntervalForRequest = 20; config.timeoutIntervalForResource = 45
        return URLSession(configuration: config, delegate: delegate, delegateQueue: nil)
    }()
    func request(_ path: String, fields: [(String, String)]? = nil, referer: String = "", ticket: SessionTicket, check: (SessionTicket) throws -> Void) async throws -> Data {
        try check(ticket)
        guard !path.contains(":"), !path.hasPrefix("/"), !path.contains(".."), let url = URL(string: School.root + path), url.host == "jw.qlu.edu.cn" else { throw CampusError.invalid("学校请求地址无效") }
        let cookies = await withCheckedContinuation { continuation in WKWebsiteDataStore.default().httpCookieStore.getAllCookies { continuation.resume(returning: $0) } }
        try check(ticket)
        let applicable = cookies.filter { c in
            let domain = c.domain.hasPrefix(".") ? String(c.domain.dropFirst()) : c.domain
            return (url.host == domain || url.host!.hasSuffix("." + domain)) && url.path.hasPrefix(c.path) && (c.expiresDate == nil || c.expiresDate! > Date())
        }
        guard !applicable.isEmpty else { throw CampusError.login }
        var request = URLRequest(url: url); request.httpShouldHandleCookies = false
        request.allHTTPHeaderFields = HTTPCookie.requestHeaderFields(with: applicable)
        request.setValue(School.root + referer, forHTTPHeaderField: "Referer")
        if let fields {
            request.httpMethod = "POST"; request.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")
            request.setValue("application/x-www-form-urlencoded; charset=UTF-8", forHTTPHeaderField: "Content-Type")
            let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~")
            request.httpBody = fields.map { ($0.0.addingPercentEncoding(withAllowedCharacters: allowed) ?? "") + "=" + ($0.1.addingPercentEncoding(withAllowedCharacters: allowed) ?? "") }.joined(separator: "&").data(using: .utf8)
        }
        let (bytes, response) = try await session.bytes(for: request)
        try check(ticket)
        guard let http = response as? HTTPURLResponse else { throw CampusError.invalid("学校响应无效") }
        if (300...399).contains(http.statusCode) || http.statusCode == 401 { throw CampusError.login }
        guard http.statusCode != 403 else { throw CampusError.invalid("当前学校账号无权使用该功能") }
        guard (200...299).contains(http.statusCode), response.expectedContentLength <= 8 * 1024 * 1024 else { throw CampusError.invalid("学校响应异常（\(http.statusCode)）") }
        var data = Data()
        for try await byte in bytes { if data.count >= 8 * 1024 * 1024 { throw CampusError.invalid("学校响应过大") }; data.append(byte) }
        try check(ticket)
        if let headers = http.allHeaderFields as? [String: String] {
            for cookie in HTTPCookie.cookies(withResponseHeaderFields: headers, for: url) {
                try check(ticket)
                await withCheckedContinuation { continuation in WKWebsiteDataStore.default().httpCookieStore.setCookie(cookie) { continuation.resume() } }
            }
        }
        try check(ticket)
        try SchoolParser.rejectLogin(String(decoding: data.prefix(4096), as: UTF8.self))
        return data
    }
    func timetable(_ term: Term, ticket: SessionTicket, check: (SessionTicket) throws -> Void) async throws -> Data {
        try await request("kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151", fields: [("xnm", String(term.year)), ("xqm", term.code), ("kzlx", "ck")], referer: "kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151&layout=default", ticket: ticket, check: check)
    }
    func grades(_ term: Term, detailed: Bool, ticket: SessionTicket, check: (SessionTicket) throws -> Void) async throws -> GradeSnapshot {
        try term.validate()
        let common = [("gnmkdmKey", "N305005"), ("xnm", String(term.year)), ("xqm", term.code), ("exportModel.exportWjgs", "xls"), ("fileName", "成绩单")]
        let columns = detailed ? ["kcmc@课程名称", "kch@课程代码", "jxbmc@教学班", "xf@学分", "xmcj@成绩", "xmblmc@成绩分项"] : ["kcmc@课程名称@120", "kch@课程代码@80", "jxbmc@教学班@80", "xf@学分@50", "cj@成绩@50", "jd@绩点@50", "xfjd@学分绩点@80"]
        let path = detailed ? "cjcx/cjcx_dcXsKccjList.html?gnmkdm=N305005&layout=default" : "cjcx/cjcx_dcListByXs.html"
        let data = try await request(path, fields: common + [("dcclbh", detailed ? "JW_N305005_XS" : "JW_N305005_XSCXCJ")] + columns.map { ("exportModel.selectCol", $0) }, referer: School.gradePage, ticket: ticket, check: check)
        let rows = try Workbook.read(data)
        let details = try SchoolParser.gradeDetails(rows, detailed: detailed)
        let pointRows: [[String]]
        if detailed {
            let points = try await request("cjcx/cjcx_dcListByXs.html", fields: common + [("dcclbh", "JW_N305005_XSCXCJ"), ("queryModel.sortOrder", "asc")] + ["kcmc@课程名称@120", "jd@绩点@50", "xfjd@学分绩点@80"].map { ("exportModel.selectCol", $0) }, referer: School.gradePage, ticket: ticket, check: check)
            pointRows = try Workbook.read(points)
        } else { pointRows = rows }
        return GradeSnapshot(owner: ticket.account, term: term, detailed: detailed, details: details, points: try SchoolParser.gradePoints(pointRows))
    }
    func roomOptions(term: Term, campus: String, ticket: SessionTicket, check: (SessionTicket) throws -> Void) async throws -> RoomOptions {
        let data = try await request(School.roomPage, ticket: ticket, check: check)
        var options = try SchoolParser.roomOptions(String(decoding: data, as: UTF8.self))
        guard options.recognized else { throw CampusError.invalid("学校页面结构暂不支持原生筛选，请使用官方查询页面") }
        let selected = campus.isEmpty ? options.selectedCampus : campus
        guard options.campuses.contains(where: { $0.id == selected }) else { throw CampusError.invalid("请选择学校提供的校区") }
        let escaped = selected.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? ""
        let details = try await request("cdjy/cdjy_cxXqjc.html?gnmkdm=N2155&xqh_id=\(escaped)&xnm=\(term.year)&xqm=\(term.code)", ticket: ticket, check: check)
        let parsed = try SchoolParser.campusDetails(details)
        options.buildings = parsed.0; options.sections = parsed.1; options.selectedCampus = selected; options.term = term
        return options
    }
    func rooms(_ query: RoomQuery, ticket: SessionTicket, check: (SessionTicket) throws -> Void) async throws -> RoomResult {
        _ = try query.week()
        let options = try await roomOptions(term: query.term, campus: query.campus, ticket: ticket, check: check)
        guard (query.building.isEmpty || options.buildings.contains(where: { $0.id == query.building })), (query.type.isEmpty || options.types.contains(where: { $0.id == query.type })), (query.start...query.end).allSatisfy({ options.sections.contains($0) }) else { throw CampusError.invalid("筛选条件已失效，请重新加载学校选项") }
        var intersection: [String: Room]? = nil
        for section in query.start...query.end {
            var page = 1, pages = 1, current: [String: Room] = [:]
            repeat {
                let data = try await request("cdjy/cdjy_cxKxcdlb.html?doType=query&gnmkdm=N2155", fields: SchoolParser.roomFields(query, section: section, page: page), referer: School.roomPage, ticket: ticket, check: check)
                let parsed = try SchoolParser.roomPage(data); pages = parsed.1
                for room in parsed.0 { current[room.id] = room }; page += 1
            } while page <= pages
            intersection = intersection.map { $0.filter { current[$0.key] != nil } } ?? current
        }
        try check(ticket)
        return RoomResult(owner: ticket.account, query: query, rooms: (intersection ?? [:]).values.sorted { ($0.campus, $0.building, $0.name) < ($1.campus, $1.building, $1.name) })
    }
}
