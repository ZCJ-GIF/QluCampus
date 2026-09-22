# iOS 验证记录

版本：0.1.0 (1)，独立 Bundle ID com.qlucampus.ios。2026-09-22 新增原生 SwiftUI 客户端，Android 发布源不变。

当前提交待云端 macOS 完成编译与测试，尚不能声明 IPA 可安装或真实学校联调通过。

工作流包含：纯业务 Swift tests、iPhone 真机架构无签名 Release 编译、WidgetKit 模拟器编译、原生存储与账号隔离测试、iPhone 模拟器界面测试。

真实学校账号、aTrust、设备通知送达和签名后的桌面组件需要 iPhone 实测；后台 CI 不包含学校密码或会话。
