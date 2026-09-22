// QluCampus iOS, GPL-3.0. Credentials remain inside the school's WKWebView.
import SwiftUI
import WebKit
import CampusCore

struct SchoolBrowser: View {
    @EnvironmentObject var model: AppModel
    let route: BrowserRoute
    @State private var web = WKWebView(frame: .zero, configuration: WKWebViewConfiguration())
    @State private var failure: String? = nil
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let failure { Text(failure).font(.footnote).foregroundStyle(.red).padding() }
                Text("校外先连接手机 aTrust。账号密码只在学校页面输入。").font(.caption).padding(8)
                WebContainer(web: web, url: route.url, failure: $failure)
            }
            .navigationTitle(route.login ? "学校统一认证" : "学校官方页面")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("关闭") { model.browser = nil } }
                ToolbarItemGroup(placement: .bottomBar) {
                    Button("返回") { if web.canGoBack { web.goBack() } }
                    Button("重新加载") { web.reload() }
                    if route.login { Button("完成登录") { finish() }.accessibilityIdentifier("finishLogin") }
                }
            }
        }
    }
    func finish() {
        guard let url = web.url, url.host == "jw.qlu.edu.cn", url.path.hasPrefix("/jwglxt/"), !url.path.lowercased().contains("login") else { failure = "请先完成统一认证，进入教务系统主页"; return }
        let script = """
        (function(){
          if(document.querySelector('input[type=password]')) return '';
          for(const s of ['#sessionUserKey','#xh','#xh_id','input[name="xh"]','input[name="xh_id"]']) {
            const e=document.querySelector(s); if(e){const v=(e.value||e.textContent||'').trim();if(/^[A-Za-z0-9_-]{5,32}$/.test(v))return v;}
          }
          return '';
        })()
        """
        web.evaluateJavaScript(script) { result, error in
            guard let student = result as? String, !student.isEmpty, error == nil else { failure = "未识别到学号，请进入个人信息或个人课表页面后重试"; return }
            Task { @MainActor in do { try model.acceptLogin(url: url, student: student) } catch { failure = error.localizedDescription } }
        }
    }
}
private struct WebContainer: UIViewRepresentable {
    let web: WKWebView, url: URL
    @Binding var failure: String?
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    func makeUIView(context: Context) -> WKWebView { web.navigationDelegate = context.coordinator; web.allowsBackForwardNavigationGestures = true; web.load(URLRequest(url: url)); return web }
    func updateUIView(_ view: WKWebView, context: Context) {}
    final class Coordinator: NSObject, WKNavigationDelegate {
        let parent: WebContainer
        init(_ parent: WebContainer) { self.parent = parent }
        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = action.request.url else { decisionHandler(.cancel); return }
            let host = url.host ?? ""
            let allowed = url.scheme == "https" && (host == "qlu.edu.cn" || host.hasSuffix(".qlu.edu.cn"))
            if !allowed { parent.failure = "已停止非学校 HTTPS 跳转。如认证地址变化，请更新学校适配。" }
            decisionHandler(allowed ? .allow : .cancel)
        }
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { if (error as NSError).code != NSURLErrorCancelled { parent.failure = "页面无法访问，请先连接 aTrust，再点重新加载" } }
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { parent.failure = nil }
    }
}
