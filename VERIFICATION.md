# MyScore 0.2.0 验证记录

日期：2026-10-24，北京时间。

## 交付

- APK：`artifacts/MyScore-0.2.0.apk`，336,993 字节；SHA-256：`2DBBCDCA31382C6B0DDB9D47ADED50B71785B455F9FEDD0B6C48158EB1F3935F`。
- `apksigner verify` 通过；Android debug 签名。包名 `cn.myscore.app`，versionCode 2、versionName 0.2.0，minSdk 28、targetSdk 35；已装 0.1.0 可直接覆盖升级。
- 未提交、未推送 Git。旧绿色设计文档存档在 `.impeccable/archive/DESIGN-0.1.0-green.md`；当前 `DESIGN.md` 与 `.impeccable/design.json` 已按白色/石墨世界重写。

## 0.2.0 本次变化

- 白色浅色、石墨深色单色外壳；真实来源队标保留原色，白色底板保证深色队标可读。
- 紧凑首页比分列表，点入完整原生 DetailActivity；系统返回回列表。
- 详情按运动分栏：足球/NBA/NFL 四栏，CS2/LOL 四栏（小局），F1 三栏（总览、车手、成绩）。
- 数据表改为手机形态：3–5 列按内容自适应宽度，6 列以上改为逐人/逐车手字段块，所有列默认可见，不再横向滚动。
- 足球位置统一中文；F1 车手栏含名次、车队、车号，成绩含圈数、成绩/用时、积分与 Q1–Q3。0–0 统计行保留中性轨道。
- 首页筛选行右侧增加独立的 48dp chevron 触控区与选中项自动滚入；移除首屏说明行；比赛卡来源时间与详情注脚统一为 11sp，10sp 不再用于任何文本。
- 说明性/状态文案统一：卡片与详情状态使用“更新”，hero 使用“比分更新于 HH:mm:ss · 北京时间”。

## 构建与确定性测试

- JDK 17，Gradle 8.14，Android Gradle Plugin 8.11.1，compile/target SDK 35。
- `:app:testDebugUnitTest`：33 项通过，0 失败。
- `:app:lintDebug`：通过，0 errors、8 warnings（未声称零警告；警告为备份属性、目标 SDK、依赖版本、绘制分配、自定义 View 构造器等既有提示）。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest` 通过；构建日志 `artifacts/build-0.2.0-final.log`。
- 本轮 Gradle wrapper 的 `gradle-8.14-bin` 下载在当前开发网络下超时，改用本机已安装的 Gradle 8.14 发行版并以离线依赖缓存构建；未修改项目依赖或包管理器。

## 实际 Android 运行

- Android 15 / API 35 x86_64 Google APIs 模拟器，Pixel 7 配置，1080 × 2400、420 dpi；不是 K80 Pro 真机。
- 204 项原生检查通过（`artifacts/android-details-tests-v3.txt`）。覆盖九个来源的真实详情读取、F1 三栏、车手 4 列、成绩 Q1–Q3 默认可见、无“左右滑动”表格提示、足球阵容中文位置、日历去重/改期/取消/个人事件保护、页面切换与系统返回。
- 主截图 24 张：`.impeccable/review/v3-final/`，首页 + 九类赛事详情，浅色/深色成对（Aston Villa 与 Manchester City 队标、英超/欧冠/Carabao/FA Cup、NBA、NFL、F1、CS2 Spirit、LOL Worlds）。
- 补充状态 15 张：`.impeccable/review/v3-supplement/`，包含设置与赛程双主题、空状态双主题、飞行模式错误态与恢复、1.3× 字号首页/F1 三栏/赛程/设置、深色 1.3× 空状态；全部 22:17–22:20 重拍，晚于最终 APK（22:15:47）。
- 1.3× 字号下 F1 车手表（车手、名次、车队、车号）与成绩字段块均完整可见，未出现横向裁切；设置/赛程正文可纵向滚动到底部。
- 错误态：飞行模式刷新后显示“9 个赛事来源未更新 · 查看连接状态”横幅，保留缓存比分及来源时间；恢复网络刷新后横幅消失、时间更新。未加载任何模拟比赛。
- 实测详情 ID：英超 `401879256`、欧冠 `401915395`、Carabao Cup `401859837`、FA Cup `401867655`、NBA `401909848`、NFL `401872996`、F1 `600057427` 的排位场次 `401839031`、Spirit `csgo_mc_2398756`、Worlds `13608`。详情覆盖摘要：`artifacts/device-v2/detail-coverage-*.txt`。

## 数据源、网络与边界

- 模拟器未设置全局 `http_proxy`；九个来源在实际应用进程内读取成功。本轮未单独重跑 `:app:verifyLiveSources`，设备内读取和 204 项检查覆盖同一批来源。
- 体育接入 ESPN 公开比分、summary 与 F1 场次统计；CS2 接入 5EPlay；LOL 接入腾讯英雄联盟赛事官网及腾竞小局数据。均无需账号或 API Key，不硬编码凭据，不展示赔率。
- HLTV / SofaScore / BO3.gg 未作为默认来源；此前无代理请求出现 403。
- 数据限制：ESPN 事件正文保留来源英文；F1 不提供逐圈事件流/遥测；LOL 仅展示已结束小局；未发布阵容允许缺失；缺失字段显示未知或“—”，不补零、不推断。

## 独立视觉复核

- 首轮 finish review（2026-10-24）：disposition = **fix**。material 项为：①详情宽表默认隐藏大部分列、横滚会滚走首列；②足球阵容位置半翻译且长值折行破坏行网格；③赛程/设置、错误/缓存、1.3× 字号没有截图证据。另有 5 项 polish（F1 三栏、筛选可发现性、时间文案与 10sp、0–0 统计条、平台组件说明）。
- 修复批次：宽表改字段块、窄表按内容自适应；位置标签补全中文；补齐设置/赛程/空态/错误态/1.3× 截图；F1 改为三栏并补名次；筛选行增加渐隐箭头；统一“更新/比分”文案与 11sp 下限；0–0 行保留中性轨道；DESIGN.md 明示沿用平台 M1 控件、不引入 Material Components。
- verdict pass（2026-10-24）：三处 material 全部 **resolved**，disposition = **ship**；该结论只覆盖 material 1–3 与 `v3-final` / `v3-supplement` 所列证据，不覆盖整个表面或 K80 Pro。reviewer 同时标记三个 polish 回归：R1 F1 车手首列右对齐并留下空洞；R2 筛选“›”紧贴/压住 CS2；R3 “比分 HH:mm:ss”读成比分本身。
- 已在最终 APK 修复 R1–R3：F1 车手改为“车手、名次、车队、车号”且名字左对齐；筛选箭头独立 48dp 占位并与滚动区留 8dp，选中 chip 自动滚入可视区；hero 改为“比分更新于 HH:mm:ss · 北京时间”。重新构建、204 项检查通过、24 + 15 张截图重截。
- R1–R3 复评（2026-10-24）：全部 **resolved**，disposition = **ship**；范围仅 R1–R3 与所列证据。48dp 箭头位让 1.0× 首屏显示 5 个筛选 chip，CS2/LOL 需通过箭头滑动到达，这是以明确提示换密度的已接受取舍。
- 证据闭合：`v3-final` 24 张为 22:16:51，`v3-supplement` 15 张为 22:17–22:20，全部晚于最终 APK（22:15:47）；先前 3 张深色补充图早于 APK 的问题已通过重截关闭。

## 仍需 K80 Pro 验收

- 用户所在地 Wi-Fi 与移动运营商网络、关闭手机代理后的取数。
- 手机日历权限、目标日历、实际日程显示与日历悬浮通知。
- 澎湃 OS 锁屏、后台省电、重启后的周期日历同步；JobScheduler 允许延后。
- F1 正赛、NBA/NFL 进行中的持续源更新；接口结构检查不能代替整场观察。

不把模拟器中的一次访问成功等同于全国、所有网络和所有时间均可用。

## 0.1.0 历史记录

日期：2026-10-24，北京时间。

## 构建和确定性测试

- JDK 17，Gradle 8.14，Android Gradle Plugin 8.11.1，compile/target SDK 35。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:app:testDebugUnitTest`、`:app:lintDebug` 通过。
- 20 项 JVM 单元测试，0 失败：北京时间日期边界、未知时间、状态优先级、Spirit 精确筛选、Worlds 排除其他赛事、主客顺序、未知比分、F1 场次、腾讯赛程时间、5EPlay 历史比分坐标、完成地图过滤。
- Lint 无错误；仍有开发工具版本、兼容性提示和自定义图标等警告，不声称零警告。
- APK 使用 Android debug 签名，可直接个人安装；`apksigner verify` 验证通过。未提交、未推送 Git。
- 交付：`artifacts/MyScore-0.1.0.apk`，189535 字节；SHA-256：`5CDC094739F8BA1414D10656A0CE270CD59862B7E84B0E008969196E19C82105`。

## 实际 Android 运行

Android 15 / API 35 x86_64 Google APIs 模拟器，Pixel 7 配置，1080 × 2400、420 dpi；WHPX 加速。不是 K80 Pro 真机。

原生自定义 Instrumentation 执行 49 项检查，通过。覆盖页面切换、筛选操作、实际 Calendar Provider 事件插入、重复去重、改期更新、提醒提前量、延期取消旧提醒、恢复提醒、取消事件、外部个人事件保护，以及九个来源的设备内读取。独立复核后补充了“明天比赛改到十天后并离开列表”的回归，验证旧日程按身份更新、不重复新增；身份复核失败时保留旧日程并报告失败，不把缺失当取消。

测试临时创建独立本地日历，结束前清理并恢复原设置。运行器拒绝在真实手机执行，不会把测试比赛混入交付应用。

## 网络证据

初轮开发环境带有 HTTP_PROXY / HTTPS_PROXY / ALL_PROXY。随后关闭自建模拟器，并以清空这三个环境变量的进程重新启动；Android 全局 http_proxy 为 null。清除该测试实例的应用数据后重新验证。

- 无显式 Java 代理的独立实时检查：9 / 9 来源成功。
- 无代理模拟器中的实际应用网络调用：英超、欧冠、Carabao Cup、FA Cup、NBA、NFL、F1、5EPlay Spirit、腾讯 Worlds 全部通过。无比赛的日期允许返回 0 场，不伪造记录。
- 当前截图使用实际来源数据，包括英超正在进行的比赛、Spirit 系列赛与已结束地图比分、Worlds 赛果及未来赛程。
- 初版 BO3.gg、SofaScore、HLTV 曾返回 403，已从默认数据通路移除。BO3.gg 解析代码保留为备用研究，不被默认请求。

实时检查报告：`artifacts/live-source-report.json`；原生检查日志：`artifacts/android-functional-tests.txt`。它们是本次开发证据，不随 APK 作为比赛数据分发。

## 界面检查

原生模拟器截图存于 `.impeccable/review/`：`phone.png`、`schedule.png`、`settings.png`、`phone-dark-large.png`。涵盖系统浅色、深色、字体倍率 1.3、状态栏/手势导航栏边界。截图未使用浏览器模拟原生应用。

独立原生界面复核确认截图有效；提出的七天窗口之外改期问题已修复，修复项复核为 resolved / ship，四张重截截图未出现可见回归。该结论仅覆盖所评修复项与原生参考范围，没有外部视觉样板评分，也不是 K80 Pro 硬件验收。
