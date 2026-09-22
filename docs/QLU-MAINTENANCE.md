# 齐鲁课表维护说明

本项目是基于 HF-CYGG/Dawn-Course 的非官方修改版，保留 GPL-3.0、上游作者署名及源代码。独立应用 ID 为 `com.qlucampus.app`，当前版本为 `0.2.10`（versionCode 12）。本项目不代表学校官方。

## 0.2.10 字体与自适应文字

`AppFontStyle` 增加 BOLD，`getTypography` 对全部 15 个 Material 角色应用字体；课程名称的拟合测量和局部日期/导航样式保留加粗选择。SYSTEM/SERIF/MONOSPACE 使用 Android 原生字体族，不下载或打包额外字体；中文回退字形由 ROM 提供。

`CampusAppearance.textColorMode` 为 STYLE（旧用户默认）、AUTO_BW、CUSTOM；`customTextColor` 使用不含透明度的 #RRGGBB。DataStore 解析异常模式回退 STYLE、异常颜色回退 #202124；备份门禁拒绝非法枚举和颜色后才允许写入。保留模式与自定义色，切换自动黑白不丢失手选颜色。

`CampusText` 集中管理颜色解析、黑白对比与区域采样。`CampusBackdrop` 仅在 AUTO_BW 模式生成每张原图/模糊图的 32×32 颜色缓存，IO 执行；`rememberBackdropText` 使用布局位置、原图裁剪/填充、亮度及区域遮罩取得代表背景，按对比选择黑白。位置变更时读取缓存，不在 draw 中分配位图或更新状态。顶部和底部可使用不同前景，时间轴逐节判断。混合照片的细节仍可通过栏位遮罩改善。

`CourseAppearance` 在自动模式选择黑/白，并用已有 safeOpacity 保护照片混合像素的对比；不要求课程配色自适应开启。自定义使用所选文字颜色，不擅自替换成黑白。`CampusTextColors` 和 `CampusPanelCard` 只重设普通 onSurface/onBackground 文本；按钮、错误、成绩合格/挂科继续保留语义色。字体控制面板使用可读实色底，即使用户选择白字仍能恢复默认。

## 0.2.9 Wake Up 与背景可见性

`CampusStyle.WAKE_UP` 增加粉彩色板，`CampusBackdrop` 在没有自定义图片时绘制浅蓝灰（或深色）渐变；优先使用用户图片。Wake Up 自动分配课程使用白字，自适应开启时按对比度加深色板，关闭时保留原色。保存过的课程颜色、高对比度及非本周灰色继续优先。

`CampusAppearance.courseBorders` 默认为 false，两个课表布局只按此字段决定描边，和自适应无关联。`barWallpaperBlur` 默认 false，`barWallpaperOpacity` 默认 .18f；DataStore 和备份均支持，旧备份补充默认值。备份门禁检查新浮点字段有限性。

`wallpaperSurfacePolicy` 将 COURSE 与 HEADER/NAVIGATION/PANEL 分开。栏位/面板只绘制对齐的原图或模糊缓存，再绘制一次用户设定的遮罩；不能重复应用根背景 transparency，也不能用课程 safeOpacity 强制提高整栏遮罩。0% 显示原图，100% 纯色，复杂背景的文字可通过用户调高遮罩改善。课程仍独立使用原有可读性保护。背景解码和模糊保持缓存，绘制时不生成位图。

## 0.2.8 配色与背景基础

`CampusAppearance` 持有风格、四个背景区域开关及课程自适应开关，`SettingsRepositoryImpl` 使用独立 DataStore 键读写；缺失/未知的风格名回退为 CLASSIC。UI 通过既有 SettingsViewModel 保存，Course/Room 数据不发生变化。

`core:ui/theme/CampusStyles` 集中维护四套色板及浅色/深色语义色。选择明确风格时优先使用其配色；CLASSIC 沿用原有系统/壁纸动态取色配置。课程自定义颜色仍优先，只有自动分配颜色采用所选风格。

`CampusBackdrop` 只解码并缓存背景一次，`glassSurface` 按 HEADER/NAVIGATION/COURSE/PANEL 采样同一背景的位置；关闭对应区域或删除壁纸时使用主题底色。局部毛玻璃开关只决定采样原图还是模糊缓存。已有高对比度、非本周灰色、成绩合格/挂科等语义实色优先保留。

`CourseAppearance.rememberCourseSurface` 为两种周课表布局共用的展示规则。自适应结合低分辨率壁纸主色温和调色，再选黑/白前景；`ReadableColors.safeOpacity` 对最暗/最亮背景及跨越文字亮度的区间提高遮罩下限，避免复杂照片局部导致课程名/地点难读。只调整显示，不覆盖用户存储颜色。前景位于遮罩之上，不参与模糊；关闭自适应才完全使用用户指定透明度。背景取色和模糊在 IO 线程，绘制路径不解码图片或重新生成模糊。

## 构建

0.2.4 显示规则：`CourseColorUtils.getTimetableColor` 按正在查看的教学周决定灰色，仅供周课表两种卡片使用；非本周卡片不混入壁纸颜色。隐藏非本周的原设置与单双周判定不变。`GradesScreen.GradeScore` 仅对数字总评按 60 分界线着色并标注合格/挂科，卡片背景对应淡绿/淡红，普通成绩与分项页共用；非数字和缺失值不推算，GPA/导出/数据库不受影响。

默认作息集中在领域模型 `QluSectionTimes`：1–2 节 08:30–10:05，3–4 节 10:20–11:55，5–6 节 14:00–15:35，7–8 节 15:50–17:25，9–10 节 18:25–19:55。每节 45 分钟，白天中间休息 5 分钟，晚课中间不休息。仓库仅在未保存作息时采用默认；已有自定义时间不会覆盖。节次设置的“恢复默认作息（五大节）”显式应用这组时间。第十一节以后没有默认时间，需自行设置，避免为提醒及日历导出编造时间。

本机工程位于 `E:\Android\QluCampus`，SDK、JDK、Gradle 在 `E:\Android` 下，缓存使用 `E:\Android\cache`。执行：

```powershell
.\build-local.ps1 -Tasks ':app:assembleDebug'
.\build-local.ps1 -Tasks ':core:domain:testDebugUnitTest',':core:data:testDebugUnitTest',':feature:grades:testDebugUnitTest'
```

JDK 21、AGP 9.3.1、Gradle 9.5.0、compileSdk 37、minSdk 26。构建脚本读取本机系统代理，不修改系统设置。Gradle 镜像下载包应核对官方 SHA-256；本次 Gradle 9.5.0 为 `553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746`。

首次在其他电脑构建时自行安装 SDK，并在未提交的 `local.properties` 设置 `sdk.dir`。原仓库 AGENTS.md 引用的 `.trae/rules` 没有随仓库发布，本次遵循已提供的 AGENTS.md 和仓库实际分层。

本机构建命令使用固定 E 盘目录；在其他电脑复现时准备同样目录，或调整 `build-local.ps1` 的路径。SDK 包为 `platforms/android-37.0`、`build-tools/36.0.0`、`platform-tools`（新版 SDK CLI 的路径写法；旧 sdkmanager 使用分号分隔）。Gradle Wrapper 及版本目录已经包含在源码中。源码包不含 JDK/SDK、依赖缓存或密钥，首次构建需要下载依赖。

其他电脑生成自己的测试密钥时，先创建源码目录的同级 `keys` 文件夹，再使用 JDK 的 keytool；不得覆盖本机已有密钥：

```powershell
keytool -genkeypair -keystore ..\keys\qlucampus-debug.keystore -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=QluCampus Debug,O=QluCampus,C=CN"
```

其他电脑新生成的密钥不能覆盖安装本次交付的 APK；在本机后续升级时必须继续使用原密钥。公开分发前请确定正式签名方案。

## 学校适配与功能边界

`feature:grades/ATrustQueryGate` 负责用户主动操作时的外部应用跳转，`ATrustPreferences` 独立保存自动打开开关，默认开启。成绩、空教室和学校课表导入共用设置；读取本机记录不触发。外部客户端是深信服官方包 `com.sangfor.atrust`，包名依据 [官方应用页](https://play.google.com/store/apps/details?id=com.sangfor.atrust)，通过 Android `<queries><package>` 声明可见性，使用系统启动 Intent，不传入凭据或学校查询参数。

0.2.7 通过领域接口 `SchoolNetworkRepository` 与 data 层 `QluSchoolNetworkRepository`，先读取当前网络的 VPN transport；已有 VPN 时直接继续。无 VPN 时匿名 HEAD 探测教务首页（总超时 3 秒，不带学校 Cookie、不跟随跳转、保持 TLS 校验），收到学校响应也直接继续；不可达才尝试打开 aTrust。探测可取消，离开前台不延迟弹出。只申请普通 ACCESS_NETWORK_STATE 权限，不读取使用记录、无障碍信息或完整应用列表。每次重新检测，不永久缓存“已连接”。

跳转协调器只在用户请求后等待一次离开前台与恢复事件，恢复前清空待执行动作，防止重复查询和循环弹出。取消、离页、账号/学期变化或页面重建会丢弃动作；旋转或系统回收后需重新点查询。返回应用不代表 VPN 已连接；登录、隧道和学校资源权限均由 aTrust 自己管理。未安装/被系统拦截时提供“直接继续”和取消；直接继续仍会经过原有会话与接口校验。校区切换引发的选项联动沿用当前连接，不重复跳转；主课表工具栏的直接刷新暂沿用原逻辑。

普通 Android 应用无法可靠读取其他应用进程或确定 VPN 所有者，所以本功能不声称检测到 aTrust 本身已启动/已认证。已有其他 VPN（如 Clash）也会跳过自动弹出；此时若学校访问失败，可点“手动打开 aTrust”。网络可达、返回 aTrust 和 VPN transport 均不代表学校登录有效，查询仍走原有认证与响应校验。不得忽略 TLS 或伪造学校会话。不实现外部 VPN 的自动连接或长期保活。参考 [Android NetworkCapabilities.getOwnerUid](https://developer.android.com/reference/android/net/NetworkCapabilities#getOwnerUid())。

- Domain：`SchoolSessionRepository`、`GradeRepository`、`SchoolTimetableRepository` 和纯数据模型；UI 不直接访问 DAO、Cookie 或 Excel。
- Data：`core/data/.../campus` 集中保存齐鲁工大接口、会话处理、表格解析和成绩缓存。身份从学校已登录页面读取，不读取密码，也不保存密码。
- Feature：`feature:grades` 提供成绩、课表预览、登录页面；`feature:import` 通过 Domain 的 `SchoolHtmlParser` 接口复用内置正方 HTML 解析器。
- 成绩参考用户本机 Python 程序的两个导出接口和参数；未复制配置文件、学号、密码或实际成绩。成绩明细、绩点分别请求，两者都校验成功后才替换该账号该学期的快照。
- 第一学期 `xqm=3`、第二学期 `xqm=12`。`xnm` 使用学年的起始年份。
- 绩点导出接口目前已知列没有课程代码；首版使用唯一课程名匹配。有同名课程或冲突标识时不强行配对，单独展示绩点。以后若真实返回课程代码，解析器已预留该字段。
- 已确认的本地历史导出格式是 OOXML/XLSX，不以接口 `xls` 参数判断格式。旧二进制 XLS、HTML 错误页和未知表头会报错并保留缓存。
- 课表 JSON 入口采用新正方 `kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151`；需要学校实测确认。内置正方 HTML 解析可处理返回的实际课表 HTML；不把登录页或没有课表的页面视为成功。
- 默认手动刷新，不后台查询学校。联网失败仍可看上次缓存。第一周日期必须由用户在日历中选择教学周周一，避免猜测校历。

## 账号与存储

本机开发模拟器安装在 `E:\Android\sdk\emulator`，Android 15 镜像在 SDK 的 `system-images` 下，AVD 和缓存均在 `E:\Android\cache`。使用 `start-emulator.ps1 -WaitForBoot` 后台启动。AEHD 驱动以按需服务 `aehd` 从 `E:\Android\sdk\extras\google\Android_Emulator_Hypervisor_Driver\aehd.Sys` 加载；重启后脚本会尝试启动服务，需要管理员权限。

Room v6→v7 新增 `campus_accounts`、`campus_grades`、`campus_imports`；v7→v8 将导入绑定主键改为 semesterId，支持同账号同学期多份课表，并新增 `campus_grade_summaries` 保存总评，保留原详细缓存。沿用上游 SQLCipher 数据库。成绩按账号、学年、学期隔离；账号对应独立课表 Profile，退出或重新登录先切回本地课表。刷新票据在提交前重新校验，阻止退出后迟到的请求覆盖数据。

再次导入可以新建独立课表，或明确选择相同账号/学期的既有目标。直接刷新使用该课表保存的账号、学期和首周周一，覆盖该目标的课程（包括手动改动），保留名称与日期。目标已变化时要求重新刷新，其他课表不改动。手动课表管理仍可显式查看其他本机 Profile，不等同于后台切换学校身份。

学校导入绑定与课程由 `ImportCommitRepositoryImpl` 在同一 Room 事务写入；活动课表选择持久化失败时同时回滚。每次新建是独立课表；旧版按账号学期创建的重试仍会被拒绝，覆盖请求校验绑定时间及日期/名称/周数以拒绝陈旧预览。新增数据库写入也遵守基础项目的 `OperationalDataMutationGate`，避免绕过恢复隔离。

原项目“备份与还原”只备份课表，成绩通过独立 Excel 导出保存。不要将其描述为包含成绩和学校登录状态的全量备份。

## 网络、升级与许可

上游云端诊断/LLM/远程脚本和版本服务器均关闭；所有教务调用直接访问学校。学校登录只允许学校 HTTPS 域名，不忽略证书错误。VPN 由用户负责，应用不包含 VPN 功能。

学校适配规则随 APK 更新；发布新版本时递增 versionCode/versionName，保留 `com.qlucampus.app` 和同一签名。当前交付是测试 APK，稳定测试签名位于 `E:\Android\keys\qlucampus-debug.keystore`，不得上传。后续对现有安装的覆盖升级必须继续使用该密钥；如果改用正式发布签名，需要另行迁移，不能直接覆盖当前安装。独立线上更新源配置在 `config/qlu-update.properties`，不会安装上游 Dawn Course 的 APK。

## 版本检查与发布

`feature:update` 复用上游下载和系统安装流程，`UpdatePreferencesRepository` 独立保存来源、自动检查开关和上次检查时间。启动后检查至多每 6 小时一次；关闭应用时不安排后台定时任务。自动检查失败静默，手动检查显示失败原因；学校缓存不受影响。当前版本首次手动安装后，后续版本才可通过应用内更新。

默认来源由 `config/qlu-update.properties` 的 `metadataUrl` 编入 APK，也可用 Gradle 参数 `-Pqlu.updateUrl=https://.../version.json` 覆盖。设置页允许填写作者 HTTPS 地址或 GitHub 仓库链接（解析为 main 分支 version.json），留空停用；没有向上游节点降级。更新网络客户端不使用学校 Cookie 或登录仓库。

0.2.7 默认地址为 `https://raw.githubusercontent.com/ZCJ-GIF/QluCampus/main/version.json`，仓库及安装包位于 [ZCJ-GIF/QluCampus](https://github.com/ZCJ-GIF/QluCampus)。旧版未内置地址，需手动覆盖安装一次。若曾在设置中显式留空停用，需要重新填入仓库链接；不会覆盖用户主动停用的选择。公开仓库暂关闭 GitHub Actions，避免直接运行继承的上游工作流；本版使用已记录的本机构建和后台验证。

发布步骤：

1. 修改 app 中 versionCode/versionName，更新版本说明；保持现有包名和签名，执行构建及相关测试。
2. 准备版本说明文本，运行 `python scripts/prepare_qlu_update.py --repo OWNER/QluCampus --notes-file release-notes.md --out version.json`。脚本用 aapt 校验真实 APK 标识与源码版本一致，再写入 APK SHA-256；不会自行上传。
3. 运行 `python scripts/validate_qlu_artifacts.py` 及 `python scripts/package_qlu_release.py`，核对 APK、对应源码 ZIP、LICENSE 和校验文件。所有报告使用合成数据。
4. 推送审查后的源码提交，建立 `v版本号` GitHub Release，上传 `QluCampus-版本号-debug.apk`、对应源码 ZIP、LICENSE 与校验值；确认资产可下载后，再把 version.json 发布到 main。避免先发布指向不存在 APK 的更新元数据。
5. 对公开元数据及 APK 重新核对版本、SHA-256 与签名。GitHub 网络不可用时会显示检查失败，用户可改用作者提供的独立 HTTPS 源；不使用未知镜像。

元数据包含 applicationId、versionCode、versionName、downloadUrl、sha256、title、updateContent、date、type 和 forceUpdate（本版始终不强制）。下载仅允许 HTTPS，限制大小，在私有缓存完成并检查 APK 内实际包名/版本/签名；用户取消会删除未完成下载。未知来源权限由系统授予，安装仍需系统确认。

公开分发修改后的 APK 时一并提供对应完整源码、GPL-3.0 许可、上游链接、修改记录和构建方法。构建缓存、密钥、用户数据、真实学校响应不得加入源码包。

## 联调

真实学校登录、课表和成绩一致性目前标记为“学校联调待验证”。由用户连接校园网/VPN 并在应用学校页面自行登录；若无法识别学号，先打开个人信息或个人课表页，再点完成登录。需要修复时只采集脱敏结构，不保存密码和 Cookie 到诊断日志。

本机已验证 Clash 开启时，通过单次请求绕过 HTTP 代理即可使用已有 aTrust 路由访问教务登录页；没有更改 Clash 配置。SSO 页面在指定 IPv4 后也可访问。Android 模拟器匿名 HTTPS 访问教务登录页已通过。手机端需自行具备校园网络/VPN，这与电脑 aTrust 连接不是同一个登录状态。登录页及官方查询页的“统一认证”使用学校首页实际跳转的 `https://jw.qlu.edu.cn/sso/ddlogin`；官方页认证完成后点“返回查询”。本轮按用户要求在 Windows 系统代理绕过列表中增加 `qlu.edu.cn;*.qlu.edu.cn`，保留其他代理设置，使学校流量使用已有 aTrust 路由；备份位于 E:\Android\logs\school-proxy-backup-20260921-133102.json。

验收：登录一次后分别导入课表和查询成绩；核对至少一门含多个成绩分项的课程、一门绩点记录以及单双周课；断网后重启仍能查看；再次导入不累加；切换账号不展示另一账号成绩；Excel 含两个工作表且能由 Excel/WPS 打开。


## 0.2.0 维护入口

- `SchoolTimetableControls` 只提供课表动作与切换弹窗，`TimetableTopBar` 在日期同行显示操作按钮；不再占用独立顶部行，也不把管理和刷新放进日期菜单。
- `CampusBottomNavigation` 使用 60dp 内容高度及系统导航区留白，课表页为 76% 不透明度的圆角覆盖；其他表单仍避让底栏。`MainActivity` 将实际底栏高度通过 `bottomOverlayPadding` 传给课表，标准行高减去一半遮挡量以露出第十节标签，滚动内容末尾预留整个底栏高度，Snackbar 同样避让；不依赖固定设备像素。
- `CompleteTimetableLayout` 与 `CompactCourseCard` 使用可用高度分配十节，默认首屏显示第 9、10 节；第 11 节以后可滚动。名称 10–13sp、最多四行，优先留出地点，教师信息在详情。放大字号时行高同步增加，允许滚动；极长内容可点击详情。`campus_fit_timetable` 默认 true，关闭恢复完整信息布局。
- `CampusBackdrop` 从私有文件读取壁纸与低分辨率 CPU 模糊缓存，仅在卡片/导航区域采样，文字不参与模糊。全局字体通过 Compose Density 应用；小组件需显式使用相同倍率。
- `CourseColorUtils.highContrastPalette` 从整个活动学期稳定分配配色，应用与组件共用。高对比度仍使用 0.2.0 的实色卡片与黑白文字，普通模式保留普通底色与毛玻璃。12 个色值循环使用，课程数超过色值数时可能复用颜色。
- `CalculateSelectedGpa` 使用 BigDecimal，Σ(学分×绩点)/Σ学分，四位 HALF_UP；不把百分制成绩换算成绩点。不同账号不可混选；重修课程是否重复计入由使用者选择，不冒充官方 GPA。
- `QluClassroomRepository` 集中维护学校空教室页面、选项和 POST 参数。先检查学校页面结构；没有所需字段时不发送猜测请求。原生查询以逐节结果交集确保所选全部节次空闲；未知分页、登录页或错误页不当成空列表。
- 空教室页面 `cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155&layout=default` 已由用户登录后实测。真实筛选使用 `#selectTR_ZC th.selectTH[value]`、`#selectTR_XQJ` 与动态 `tr#selectTR_JC`，不能要求 jcd/zcd/xqj 输入框。`cdjy/cdjy_cxXqjc.html` 按 xnm/xqm/xqh_id 返回 lhList（JXLDM/JXLMC）和 jcList（JCMC）。类型来自 cdlb_id；周次/节次是 2^(n-1)，星期是 1–7，jyfs=0，分页按 cdbh 升序。已核对学校 kxcdlb.js；源码不包含学校脚本或真实响应。官网样例返回 47 条，Android 原生端仍待实际对照。
- `GradeAccessRepositoryImpl` 保存本机功能开关；`GradeAccessPassword` 比对摘要，不在运行日志记录输入。固定密码只是入口锁，不是加密密钥或学校访问权限，也无法防止有源码的人修改应用。
- 隐藏入口为设置底部版本文字连续点击五次（相邻点击不超过 5 秒）。解锁后主导航出现“平时成绩”，退出/重启仍开启；设置中点“锁定并隐藏平时成绩”恢复隐藏。正常总评/GPA 不需要解锁。
- `QluGradeRepository` 锁定时只读取总评导出，详细缓存经过 `GradeVisibility.summary`；导出时再次检查开关。解锁时两个原始成绩接口均通过校验才保存详细快照。若总评接口当前返回不包含必要的成绩/学分列，会报适配错误并保留缓存，需在联调后更新字段。
- `WelcomeNotice` 不代表学校官方说明，包含软件用途、数据以学校为准、网络依赖与作者微信 a3130149711。勾选后不再自动弹出，设置中仍能查看。

## 桌面组件添加兼容

- 组件仍是 Android 原生 AppWidget，Provider 保持 `com.dawncourse.feature.widget.DawnWidgetReceiver`，避免升级破坏已有绑定。标准 `previewLayout` 用静态 RemoteViews；较旧系统保留 `previewImage`。默认 4×2，最小高度与调整高度为 110dp。
- `WidgetPinControls` 始终提供添加状态与手动帮助；`requestPinAppWidget` 返回 true 只代表请求流程可用，绝不直接显示成功。不可变、一次性的显式 PendingIntent 回调到非导出的 `WidgetPinConfirmationReceiver`；匹配本次随机 token 后才确认成功，重试取消旧 PendingIntent，旧回调不确认新请求。
- `WidgetRegistrationCheck` 只读查询本应用组件声明、启用、系统已登记的 Provider、添加支持与实例数。系统登记为“是”并不能保证第三方桌面列出它；为“否”需进一步检查安装/组件状态。不要清理用户桌面数据或重置其布局来尝试修复。
- Redmi K80 / 澎湃 3.0.307.0 的实际列表缺失尚待设备检测。本轮电脑未连接该手机，不能用 Android 15 Pixel Launcher 的成功代替该机型验收。
- 小米官方说明原生安卓组件无需通过小米小部件审核；不要盲目加 `miuiWidget` 或调用需上架审核的商店详情接口。参考：[小米原生组件说明](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1588)、[小米添加路径](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1664)、[Android 添加回调语义](https://developer.android.com/reference/android/appwidget/AppWidgetManager#requestPinAppWidget(android.content.ComponentName,%20android.os.Bundle,%20android.app.PendingIntent))。

## 后台验收复现

使用专用 AVD `QluCampusTest35`（Android 15，port 5562）运行，启动参数必须含 `-no-window -no-audio`。不要对用户正在使用的模拟器进行自动测试。测试使用合成账号和课程，会修改专用测试应用数据。

```powershell
$env:ANDROID_SERIAL = 'emulator-5562'
.\build-local.ps1 -Tasks ':app:assembleDebug',':core:domain:testDebugUnitTest',':core:data:testDebugUnitTest',':feature:grades:testDebugUnitTest',':feature:timetable:testDebugUnitTest',':feature:widget:testDebugUnitTest',':feature:settings:testDebugUnitTest'
# 在下列占位参数填写本机设定的功能解锁码；测试组件不会写入正式 APK。
.\build-local.ps1 -Tasks ':core:data:connectedDebugAndroidTest',':app:connectedDebugAndroidTest','-Pandroid.testInstrumentationRunnerArguments.gradeUnlockCode=<本机解锁码>'
python scripts/validate_qlu_artifacts.py
python scripts/package_qlu_release.py
```

电脑开发期间 `E:\Android\tools\qlu-keepalive.ps1` 每 5 分钟匿名访问教务首页，维持 aTrust 链路；仅限开发辅助，不打包进手机应用。正式应用仍手动刷新，不后台访问学校。
