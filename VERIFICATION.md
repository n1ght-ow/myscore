# MyScore 0.2.0 验证记录

日期：2026-10-24，北京时间。

## 0.2.0 本次变化与验证

- 白色 / 深灰主题，真实队标，紧凑首页，完整原生 DetailActivity，按运动分栏展示来源详情。无新增第三方生产依赖。
- 33 项 JVM 单元测试通过。新增真实响应裁剪 fixture 验证：足球首发和替补、NBA 主客身份对齐、分节与球员数据、NFL 传冲接球和得分、CS2 地图坐标和 Rating、Worlds 红蓝方与比赛身份、仅已结束小局、F1 精确场次和缺失统计、缓存往返、脚本仅解析数据不执行。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:app:lintDebug` 通过。Lint 无错误，不声称零警告。
- 第一轮原生检查 189 项通过，字号 1.3；修正长标签、统计排序和出装排布后，最后一轮 190 项通过，字号 1.0。最后一轮含实测 Aston Villa 队标下载并解码、九类赛事详情真实读取、全部详情分栏的浅深色交互、日历去重/改期/取消/个人事件保护及九来源比分读取。
- 实测详情：英超 `401879256`、欧冠 `401915395`、Carabao Cup `401859837`、FA Cup `401867655`、NBA `401909848`、NFL `401872996`、F1 大奖赛 `600057427` 的排位场次 `401839031`、Spirit `csgo_mc_2398756`、Worlds `13608`。实测样本覆盖了这些适配器，不保证所有历史场次字段齐全。
- Android 模拟器全局 http_proxy 为 null。前一次以清空代理变量启动的同一模拟器继续使用；本轮未更改用户代理配置。K80 Pro 运营商网络未实测。
- 原生截图 24 张：`.impeccable/review/v2-final/`，首页、足球总览/阵容/统计/进程，NBA/NFL 球员，F1 车手/成绩，CS2 选手，LOL 阵容/团队统计；均有浅深色版本。`.impeccable/review/v2-round1/review-v2/` 保存第一轮大字号证据。
- 日志：`artifacts/android-details-tests-final.txt`；按来源覆盖摘要 `artifacts/device-v2/detail-coverage-*.txt`。截图和研究响应不随 APK 分发，测试 fixture 只在 JVM 测试 classpath。
- 数据限制：ESPN 事件正文保留来源英文；F1 不提供逐圈事件流/遥测；LOL 小局仅展示已结束局；未发布阵容、连接失败与真实零值明确区分。队标下载失败使用队名缩写并支持刷新重试。
- 独立原生界面复核和新设计系统文档正在收尾；下文保留上一版历史证据，不能把旧版的 ship 结论用于本版。

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

## 仍需 K80 Pro 验收

- 用户所在地 Wi-Fi 和移动运营商网络，关闭手机代理后取数。
- 手机日历权限、选择目标日历、实际日程显示和日历悬浮通知。
- 澎湃 OS 锁屏、后台省电、重启后的周期日历同步。JobScheduler 允许延期，不承诺精确到前一天的某一分钟。
- F1 正赛、NBA/NFL 正在进行时的持续源更新；本次接口结构检查不能代替所有赛事状态的整场观察。

不把模拟器中的一次访问成功等同于全国、所有网络和所有时间均可用。
