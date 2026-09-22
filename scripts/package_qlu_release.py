"""Package the current QluCampus test build and corresponding GPL source on E:.
QluCampus modification, 2026-09-21, GPL-3.0.
Run only after building and reviewing the validation record.
"""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import zipfile
import re

ROOT = Path(__file__).resolve().parents[1]
VERSION = re.search(r'versionName = "([0-9.]+)"', (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")).group(1)
OUT = ROOT.parent / "releases" / VERSION
OUT.mkdir(parents=True, exist_ok=True)

files = subprocess.check_output(
    ["git", "ls-files", "-c", "-o", "--exclude-standard", "-z"], cwd=ROOT
).decode("utf-8").split("\0")
files = sorted({p for p in files if p and (ROOT / p).is_file()})
for name in files:
    parts = Path(name).parts
    assert not ({".git", ".gradle", "build", "node_modules", "keys"} & set(parts)), name
    assert not name.endswith((".keystore", ".jks", ".apk")) and Path(name).name != "local.properties", name
assert all(name in files for name in (
    "LICENSE", "docs/QLU-MAINTENANCE.md", "docs/QLU-VALIDATION.md",
    "gradle/wrapper/gradle-wrapper.jar", "core/data/schemas/com.dawncourse.core.data.local.AppDatabase/8.json",
    "feature/grades/build.gradle.kts",
))
source_zip = OUT / f"QluCampus-{VERSION}-source.zip"
with zipfile.ZipFile(source_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
    for name in files:
        archive.write(ROOT / name, "QluCampus/" + name)
with zipfile.ZipFile(source_zip) as archive:
    assert archive.testzip() is None
    assert len(archive.namelist()) == len(files)

copies = {
    "app/build/outputs/apk/debug/app-debug.apk": f"QluCampus-{VERSION}-debug.apk",
    "LICENSE": "LICENSE",
    "docs/QLU-MAINTENANCE.md": "QLU-MAINTENANCE.md",
    "docs/QLU-VALIDATION.md": "QLU-VALIDATION.md",
    "docs/QLU-CHANGELOG.md": "QLU-CHANGELOG.md",
    "build/reports/qlu-host-validation.json": "reports/qlu-host-validation.json",
    "feature/grades/build/reports/lint-results-debug.txt": "reports/grades-lint.txt",
    "core/data/build/reports/qlu-sample.xlsx": "reports/synthetic-excel-sample.xlsx",
    "core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk": "tests/data-debug-androidTest.apk",
    "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk": "tests/app-debug-androidTest.apk",
}
for source, target in copies.items():
    destination = OUT / target
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / source, destination)
for module in ("core/domain", "core/data", "core/ui", "feature/grades", "feature/timetable", "feature/widget", "feature/settings", "feature/update"):
    destination = OUT / "reports" / module.replace("/", "-")
    destination.mkdir(parents=True, exist_ok=True)
    for path in (ROOT / module / "build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
        shutil.copy2(path, destination / path.name)
for module in ("core/data", "app"):
    destination = OUT / "reports" / (module.replace("/", "-") + "-android")
    destination.mkdir(parents=True, exist_ok=True)
    for path in (ROOT / module / "build/outputs/androidTest-results/connected").rglob("TEST-*.xml"):
        shutil.copy2(path, destination / path.name)
tag = VERSION.replace(".", "")
release_logs = ("build-020-final-check.log", "android-020-final.log", "build-020-widget-final-online.log", "lint-020-app.log", "apk-020-verification.txt") if VERSION == "0.2.0" else (f"ui-{tag}-layout.log", f"apk-{tag}-verification.txt")
if VERSION == "0.2.3":
    release_logs = ("ui-023-widget.log", "apk-023-verification.txt")
if VERSION == "0.2.4":
    release_logs = ("ui-024-colors.log", "ui-024-colors-final.log", "ui-024-final.log", "ui-024-delivery.log", "024-time-validation.txt", "apk-024-verification.txt")
if VERSION == "0.2.5":
    release_logs = ("build-025-update-pass.log", "ui-025-updates.log", "apk-025-verification.txt")
if VERSION == "0.2.6":
    release_logs = ("build-026-vpn.log", "ui-026-vpn.log", "build-026-final.log", "026-atrust-handoff.txt", "apk-026-verification.txt")
if VERSION == "0.2.7":
    release_logs = ("build-027-network.log", "ui-027-network.log", "build-027-release.log", "027-vpn-handoff.txt", "apk-027-verification.txt")
if VERSION == "0.2.8":
    release_logs = ("build-028-styles.log", "test-028-styles.log", "ui-028-styles.log", "apk-028-verification.txt")
if VERSION == "0.2.9":
    release_logs = ("build-029-wakeup.log", "ui-029-wakeup.log", "apk-029-verification.txt")
for name in release_logs:
    shutil.copy2(ROOT.parent / "logs" / name, OUT / "reports" / name)
small_screen_log = ROOT.parent / "logs" / f"ui-{tag}-small-screen.log"
if small_screen_log.is_file():
    shutil.copy2(small_screen_log, OUT / "reports" / small_screen_log.name)
for path in (ROOT.parent / "logs").glob(f"{tag}-*-ui.xml"):
    shutil.copy2(path, OUT / "reports" / path.name)
for path in (ROOT.parent / "logs").glob(VERSION.replace(".", "") + "-*-final.png"):
    shutil.copy2(path, OUT / "reports" / path.name)
for module in ("app", "core/ui", "feature/settings", "feature/timetable", "feature/widget", "feature/update"):
    shutil.copy2(ROOT / module / "build/reports/lint-results-debug.txt", OUT / "reports" / (module.replace("/", "-") + "-lint.txt"))

(OUT / "先读我.md").write_text(f"""# 齐鲁课表 {VERSION} 测试版

安装文件：`QluCampus-{VERSION}-debug.apk`，适用于 Android 8.0 及以上。
对应完整源码：`QluCampus-{VERSION}-source.zip`。可直接覆盖本机交付的旧版，无需卸载。

## 使用

1. 将主 APK 传到手机，允许此次文件来源安装后安装；tests 文件夹是开发测试组件，不是日常使用入口。
2. 在手机上连通校园网络/VPN，打开「成绩 → 登录学校」，通过学校原网页自行登录，进入教务系统后点「完成登录」。
3. 选择学年和学期，点击「刷新成绩」。课表页点击导入，预览后在日历中选择第 1 教学周的周一日期，选择新建或覆盖目标后保存。
4. 「导出 Excel」通过系统文件选择器保存两个工作表；离线显示上次成功刷新的记录。
5. 日期同一行的图层、刷新、文件夹图标可直接切换、刷新、管理课表；「刷新课表」沿用已保存的首周周一并覆盖该目标课程，含手动改动，不影响其他课表。
6. 「设置 → 清晰度与桌面小组件」调整标准周课表布局、字号、高对比度、毛玻璃并添加今日课表；「外观与视觉」选择本地壁纸；「显示与布局 → 隐藏非本周课程」只显示所查看周次的课程。标准布局首屏露出第 9、10 节，卡片优先显示课程名称和地点。底部为半透明圆角导航，在课表区域向上滑动可看全被遮住的末节和第 11 节以后的课程。
7. 「成绩 → 选择课程计算 GPA」勾选课程；按学分加权，不补算缺失绩点。筛选其他缓存学期时保留已选项。
8. 平时成绩默认隐藏。设置底部连续点击版本号五次，输入你设定的密码后显示主导航入口。解锁会保留到手动点击「锁定并隐藏平时成绩」；锁定后总评和 GPA 仍可用，Excel 不包含平时分项。
9. 启动说明可勾选「下次不再显示」。遇到问题联系作者微信 a3130149711；设置中可再次查看免责声明。
10. 空教室页可直接点击「在应用内打开学校查询」，在学校原网页选择条件。原生筛选先加载学校查询选项，再选择具体校区、教学楼和场地类别；学校官网已查到结果，Android 原生端仍需实际对照。页面未适配不代表没有空教室。
11. 桌面组件点击添加后会显示请求状态，收到系统成功回调才显示已添加。小米/Redmi/POCO 可从「桌面双指捏合 → 添加小部件 → 搜索 → 安卓小部件」寻找「齐鲁课表·今日课表」。若进入列表仍找不到，在设置点击「检测桌面组件」，反馈组件启用、系统登记和添加支持三项结果；Redmi K80 澎湃 3.0.307.0 仍需实体手机验证。
12. 非本周课程默认显示灰色；开启隐藏后仍不显示。成绩数字总评大于等于 60 为绿色“合格”加淡绿背景，小于 60 为红色“挂科”加淡红背景，缺失或文字成绩保留普通样式。
13. 默认五大节为 08:30–10:05、10:20–11:55、14:00–15:35、15:50–17:25、18:25–19:55，每小节 45 分钟，白天小节间休息 5 分钟，晚上不休息。以前手动设过作息的，可在「设置 → 节次时间设置 → 恢复默认作息（五大节）」应用；默认未设定第十一节之后的时间。
14. 已启用 https://github.com/ZCJ-GIF/QluCampus 的独立发布源。先手动覆盖安装本版，之后打开应用会检查新版本（最多每 6 小时一次）；「设置 → 检查更新」可手动检查、关闭自动检查或修改发布源。曾显式清空来源的用户需要填入此仓库链接。下载完成校验后由系统确认安装，不会静默安装。
15. 成绩、空教室和学校课表导入默认开启「查询前打开 aTrust」，先检测当前 VPN 或学校网址是否可访问，已有连接则直接继续；否则打开 aTrust，返回后继续一次。Android 无法可靠识别 VPN 所有者，其他 VPN 也会跳过；可点击「手动打开 aTrust」。未安装/无法打开时可取消或直接继续；开关可关闭。查看缓存、导出、GPA 和确认保存不触发。aTrust 的登录和 VPN 连接仍需本人完成，网络检测不代表学校认证有效。
16. 「设置 → 配色风格与背景」选择云雾蓝、鼠尾草、奶油杏、雾紫或原有配色；可独立将图片延伸到顶栏日期栏、底部导航、课程卡片和设置/查询面板。课程颜色适应背景默认开启，按背景调色并保护文字与地点的对比度；高对比度和非本周灰色规则保留。风格只改变显示，手工保存的课程原色不被改写。
17. 新增 Wake Up 风格：浅蓝灰渐变、粉蓝杏白字课程卡片。自适应开启时加深配色以保护白字，关闭后保留粉彩原色。课程边缘线默认关闭，在「配色风格与背景」独立开启。栏位与面板默认显示背景原图，可另开毛玻璃，并用 0–100% 遮罩调整图片清晰度；默认 18%，不再与根背景重复叠加。课程卡片的模糊和覆盖层另行调整。

本次构建、单元与 Android 15 无窗口测试、桌面组件、签名检查、独立 XLSX 读取及迁移结果见 `QLU-VALIDATION.md`。报告和截图使用合成数据；真实账号下已验证空教室官方网页查询；Android 原生课表、总评、平时成绩及空教室结果仍需在 VPN 可用时登录核验。不能把“学校联调待验证”理解为已通过学校接口验收。

默认不保存学校密码；学校会话仅保存在本机。接口未返回的数据不自行计算。遇到学校登录识别问题，可进入个人信息/个人课表页再完成登录。

## 源码与升级

基于 HF-CYGG/Dawn-Course（https://github.com/HF-CYGG/Dawn-Course），保留 GPL-3.0、原作者署名及原有课表功能。独立包名 com.qlucampus.app。
修改日期 2026-09-21，详情见 `QLU-CHANGELOG.md`；维护和构建见 `QLU-MAINTENANCE.md`。
公开转发 APK 时同时提供对应源码、LICENSE 和修改说明。

本机稳定测试签名保存在 E:\\Android\\keys\\qlucampus-debug.keystore，源码包不含密钥。以后覆盖升级需要相同包名和签名、递增版本号；正式发布签名必须在分发前确定并备份。
文件 SHA-256 校验值见 `SHA256SUMS.txt`。
""", encoding="utf-8")
checksums = []
for path in sorted(OUT.rglob("*")):
    if path.is_file() and path.name != "SHA256SUMS.txt":
        checksums.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(OUT).as_posix()}")
(OUT / "SHA256SUMS.txt").write_text("\n".join(checksums) + "\n", encoding="utf-8")
print(json.dumps(dict(output=str(OUT), source_files=len(files), source_bytes=source_zip.stat().st_size,
                      apk_bytes=(OUT / f"QluCampus-{VERSION}-debug.apk").stat().st_size), ensure_ascii=True))
