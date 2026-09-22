# 齐鲁课表 iOS 个人版

原生 SwiftUI / WKWebView / WidgetKit 客户端，iOS 16 及以上。独立版本 `0.1.0 (1)`，Bundle ID `com.qlucampus.ios`。Android 仍为 0.2.10；Android 数据库、签名、版本更新源不变。

## 功能

- 周课表：日期同行按钮、1–10 节及超出部分纵向滚动、左右滑动换周、非本周灰色/隐藏、课程和地点、手动编辑、多个课表。
- 首周周一在应用内日历选择；学校导入先预览，刷新按原学期和首周日期覆盖对应课表。
- 学校原生网页统一认证，共用 WKWebView Cookie；不保存密码，不向作者服务发送学校数据。打开学校网页时失效旧请求，点击“完成并返回”后从学校页面重新绑定学号。
- 成绩：学期筛选、分项、总评合格/挂科红绿标识、学分和绩点、缺失显示未提供、重名歧义独立展示；按勾选课程计算加权 GPA。锁定时查询总评接口、导出只含可见数据。
- 隐藏平时成绩入口：设置页长按版本号，输入既有密码；保持开启，直到手动锁定或切换账号。它是显示开关，不是成绩文件加密密码。
- 空教室：加载学校校区/教学楼/场地类型，按首周日期换算教学周；逐节次完整分页后取教室标识交集。只显示与当前条件一致的历史结果，并注明查询时间。
- XLSX 成绩导出为“成绩明细”和“绩点”两张表，可保存到“文件”或分享。按真实 ZIP/OOXML 格式读取学校响应，拒绝登录页及异常格式。
- 本机离线保存；按账号隔离；原子写入；查询失败不覆盖；iOS JSON 备份追加恢复。
- Wake Up 等六套风格、自定义背景和毛玻璃、导航延伸、课程边线开关、字号、系统/衬线/等宽/加粗、颜色选择及黑白自适应。
- 原生本地上课通知：提前 10 分钟，预排最近 60 次；再次打开软件时补充。作息沿用 Android 设置的五大节、晚间连续 18:25–19:55。
- 提供 WidgetKit 今日课程扩展；需启用 App Group 的签名构建。默认个人独立包不包含此扩展。

## Windows 上的工作方式

源码可以在 Windows 编辑；iOS SDK 编译、单元测试和 iPhone 模拟器在 GitHub Actions 的 macOS 环境运行，不占用本机前台。工作流见 `../.github/workflows/ios-ci.yml`。构建产物中的 `unsigned.ipa` 是未签名真机包，必须用你自己的 Apple 签名后才能安装，不能直接点击 IPA 安装。无需为了开发此个人版先上架 App Store。

## 本机 Mac 构建（有 Mac 时）

```sh
cd ios-app
brew install xcodegen
swift test
xcodegen generate
open QluCampus.xcodeproj
```

Xcode 选择 QluCampus scheme 和你的签名 Team，连接 iPhone 后 Run。生成器的配置是 `project.yml`；Swift package 依赖固定为 ZIPFoundation 0.9.20、SwiftSoup 2.8.8。

需要桌面组件时用 `xcodegen generate --spec project-widget.yml`，在 App 和 TodayWidget 两个 target 配置同一已注册的 App Group。若更改 Group ID，同步更新两份配置与 Widget 中的 ID；独立个人包不需要 App Group。

## 架构与维护

- `Sources/CampusCore`：不依赖 UIKit 的模型、学校字段/周次/教室解析、GPA 和 XLSX；可用 `swift test` 检查。
- `App/SchoolAPI.swift`：固定学校主机、Cookie 同步、禁止跟随 API 重定向、有界响应与请求参数。`SchoolBrowser.swift` 只负责学校 HTTPS 页面和认证完成。
- `App/AppModel.swift`：账号/请求修订检查、读写事务、离线缓存、导入预览、备份及平台通知。
- 其余 `App/*View.swift`：原生界面；学校请求和 Excel 不在界面直接处理。
- `Widget`：只读取当前课表的 App Group 副本，不共享成绩或学校会话。
- 默认时区为学校的 Asia/Shanghai，避免手机时区变化造成周次和节次偏移。
- 缓存 `Application Support/QluCampus/campus-v1.json` 含 schema 1；更改结构应做迁移，勿通过卸载升级。包名和签名保持一致，递增 iOS 版本。

## 验证范围与平台差异

请以 `../docs/QLU-IOS-VALIDATION.md` 的实际构建记录为准。模拟器使用虚构课表，不包含任何真实账号。学校真实登录、VPN、课表/成绩/空教室返回仍须 iPhone 上实测。

iOS 不能使用 Android APK 自更新；本版不接 Android version.json，也不会下载 APK。aTrust 需先由用户连接，未凭空指定未经证实的 iOS URL Scheme或声明能检查其他 App 的运行状态。个人独立包没有桌面扩展；带组件的源代码可在具备 App Group 签名条件时构建。iOS 备份格式与 Android 现有数据库不是同一种格式，两个客户端可各自在学校重新导入；不把任意 Android 备份当 iOS 备份解析。

保留 GPL-3.0，原 Android 基础 Dawn Course 作者 HF-CYGG；齐鲁适配维护作者微信 a3130149711。完整许可随源码和应用资源附带。
