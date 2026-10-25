# MyScore 0.7.0 LoL 比赛详情与刷新验证

日期：2026-10-25，北京时间。发布版本为 0.7.0；README 的安装入口与 GitHub Release 使用同一已验证 APK，0.6.0 可通过应用内更新覆盖升级。

## 交付与实现

- APK：`artifacts/MyScore-0.7.0.apk`，1,081,944 字节；SHA-256：`93F4DEFB17F744155D74DB049EADD092780365C751FF4FF446B3005644376669`。
- 包名 `cn.myscore.app`，versionCode 9、versionName 0.7.0；minSdk 28、targetSdk 35。沿用旧版 debug 签名，无新增第三方依赖；可直接覆盖安装。
- LoL 详情改为总览 / 比赛 / 统计三栏，紧凑系列赛头与独立小局击杀比分；可按局切换。双方按真实战队 ID 和位置对照，展示英雄图、等级、K/D/A、补刀、经济和召唤师技能，统计页显示伤害、视野、装备图及名称。
- 目标区按双方方向对照经济、推塔、小龙、大龙、先锋、巢虫、远古龙、厄塔汗。经济缩写为 k，长按提示及辅助功能保留原始数值。未知数据显示 —，未知蓝方不标成红方；缺少选手时明确说明。
- 接受来源的进行中小局快照和已结束小局；不以历史阵容补全，不外推比赛时钟。临时失联保留本场上次小局及原获取时间，并显示缓存说明；赛程发布时间与获取时间分别保留。
- 单日前台列表约每 10 秒检查，体育与电竞不再使用不同的 15/60 秒等待；多数未结束详情约 10 秒、F1 全量详情约 15 秒。扣除请求耗时；来源先返回先显示，不等待全部完成，滚动中延后重绘。失败来源独立退避，最长约两分钟；后台页面停止轮询，已结束详情长缓存。
- 列表缓存比检查周期提前 0.5 秒失效，避免工作线程的毫秒级偏移使刚好 10 秒的请求继续命中旧缓存、跳过一次更新。历史 ESPN 日期仍可使用较长缓存。

## 验证

- 62 项单元测试通过，含小局/战队身份、位置配对、进行中无赢家、缺失值与真实零、图像地址、缓存时间保留、重复局号拒绝及刷新/退避边界。
- `:app:lintDebug` 通过：0 errors、20 warnings，未隐藏警告；APK 与 Android 测试包构建通过。日志：`artifacts/build-0.7.0.log`。
- 最终 APK 在 API 35 / Android 15 模拟器通过 104 项 Android 运行检查，含九类来源真实连接，以及真实 C9–KC 系列赛 13608 的五局、英雄图解码、按局切换、状态重建、日历与原生导航。日志：`artifacts/android-tests-070.txt`。
- 同一最终 APK 在 1.3 倍系统字号下通过 95 项运行检查，含浅/深主题、完整阵容上下部分及出装；结束后字号恢复为 1.0。日志：`artifacts/android-tests-070-large.txt`。
- 两轮界面检查后，独立定点审查给出 `ship`，范围为未知方位、缺失统计空状态、实际装备与大字号阵容取景、相关设计记录的修正；不代表真机或实时丰富数据验收。截图：`.impeccable/review/lol070/final-normal/`、`final-large/`。最后仅调整列表缓存失效余量，未再修改界面。
- 签名证书 SHA-256 与 0.6.0 相同：`e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`。
- 前台检查节奏及缓存获取时间采样保存在 `artifacts/refresh-070.txt`、`artifacts/refresh-cache-070.txt`；这些是本地单次观察，不代表上游每 10 秒发布新比分。
- 本次前台采样的四轮请求起点间隔约 10 秒；英超缓存的相邻获取时间差为 9,998 / 10,000 毫秒，LoL 为 9,998 / 9,998 毫秒。CS2 的三次样本首次跨度约 20 秒、之后约 10 秒，不宣称每个来源每轮都成功。

## 数据与设备边界

- 开发时官网将 DK–T1（13635）赛程标为进行中，但详细接口未返回该场小局载荷。真实丰富页面用已结束系列 13608 验证；进行中快照解析由单元测试覆盖，不能宣称已取得与 SofaScore 一样的实时数据覆盖。
- 比赛计时、比分与统计的时效仍受 ESPN、5EPlay、英雄联盟官网 / 腾竞实际发布时间限制。缩短请求间隔不等于消除来源延迟，缺失字段不会补零或按本地时间推进。
- 腾讯英雄 / 装备 / 技能图使用运行时官网静态资源；已验证 Rumble、3157、SummonerFlash 的 HTTPS PNG 响应，并在 Android 解码真实英雄图。没有增加随 APK 分发的栅格图像。
- 本机投屏服务曾反复崩溃并导致模拟器系统服务无响应；停止临时投屏进程、重新连接 ADB，并保留数据冷启动同一 AVD 后完成最终测试。未卸载或清除应用数据。该故障不作为产品崩溃记录。
- K80 Pro / 澎湃 OS、移动运营商网络及真机帧率仍未验收。没有将前台数据检查频率当作屏幕渲染帧率。

---
# MyScore 0.6.0 应用内更新验证

日期：2026-10-25，北京时间。

## 交付与实现

- 本地 APK：`artifacts/MyScore-0.6.0.apk`，1,037,744 字节；SHA-256：`DF6ED1DCD65456F0C980F26081C02C4B86CD15747F628DFDE6595138395DC810`。
- 包名 `cn.myscore.app`，versionCode 8、versionName 0.6.0，minSdk 28、targetSdk 35；继续使用原 debug 签名。没有新增第三方依赖。
- 设置增加「检查更新」，独立原生页面显示当前/最新版本、更新摘要、系统下载进度、取消下载及安装操作，沿用黑白主题、高刷新率窗口提示和系统返回。
- 读取固定仓库 GitHub Releases 最新正式版，严格匹配版本与 APK URL；不接受草稿、预发布、非本仓库地址、缺失 SHA-256、异常大小或错误版本的资产。
- Android DownloadManager 管理后台下载；任务 ID 与已验证的发布元数据保存在应用专属 preferences，重入页面可恢复。安装包位于应用专属下载目录，不请求共享存储权限。
- 安装前校验长度、SHA-256、包名、versionName、更高 versionCode 和实际 APK 签名；校验失败清理任务。使用系统下载 Content URI 和临时读取授权交给系统安装器，不使用 file URI 或自定义导出文件服务。
- 初次安装需要用户授予 MyScore「安装未知应用」权限，之后仍由系统确认覆盖安装；旧版需先手动覆盖安装一次带更新入口的版本。

## 构建与测试

- `:app:testDebugUnitTest`：45 项通过，新增 12 项覆盖版本数值比较、源地址限制、摘要要求、大小限制、持久化元数据再验证、真实字节哈希及文件篡改。
- `:app:lintDebug`：通过，0 errors、20 warnings，未隐藏警告。新警告涉及后台持久化 commit 与既有中文界面风格的 setText 字符串；其他为原工具链/图标/配置提示。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest` 通过。构建日志：`artifacts/build-0.6.0.log`。
- 最终 0.6.0 APK：58 项 Android 界面/日历检查通过，含实际签名 APK 升级判断、同版本/错误包/缺失签名拒绝、更新权限声明和原生更新页。日志：`artifacts/android-tests-060.txt`。
- 修正 UI 测试对异步重绘的跨线程读取，重建后的可见标题在主线程检查；保留原有导航恢复断言。

## 真实覆盖安装链路

使用 `artifacts/update-qa-bootstrap/` 内隔离构建的低版本验证包（0.5.0 / versionCode 6，含同一更新实现），由应用读取真实发布接口，下载正式 Release 的 0.5.1 APK（776,452 字节），通过应用的哈希/包名/版本/签名校验，走权限引导并打开系统的 Update 确认框。点击系统 Update 后，设备包版本变为 0.5.1 / versionCode 7，系统报告安装成功。

- 没有卸载、清除应用数据或使用 adb 安装代替这次应用内升级。adb 仅用于安装验证包、恢复最终 0.6.0 及读取验证结果。
- 升级前后，日历关联的 3 个逻辑记录（事件 ID、fingerprint、match record）逐项相同；目标日历 ID/名称、主题也保持相同。原始 XML 序列化有变化，未以原始文件哈希代替逻辑值检查。
- 本机模拟器的网络曾被系统标记为部分连通，DownloadManager 等待 VALIDATED 网络。仅在测试环境暂时关闭 captive portal 检测并重连网络后完成真实下载；结束后恢复 captive_portal_mode 未设置、Wi-Fi/移动数据为原状态、安装应用 AppOp 为 default。未将此环境调整写入产品代码。
- 验证包没有用于交付。测试结束后已恢复最终 0.6.0 APK。
- 截图：`.impeccable/review/updater/`，包含准备安装、权限引导及系统覆盖安装确认；发布摘要在正式页面只呈现更新条目，完整技术说明通过发布页查看。

## 边界

- 这是 API 35 Android 模拟器的验证；K80 Pro / 澎湃 OS 权限页、移动运营商网络和系统下载策略仍待真机验收。
- 更新依赖 GitHub 可访问和系统下载服务可用；网络限制时保留重试/取消和发布页入口。首次旧版不能自行获得尚未安装的更新功能，仍需手动覆盖升级一次。

---
# MyScore 0.5.1 验证记录

日期：2026-10-25，北京时间。

## 交付与变化

- APK：`artifacts/MyScore-0.5.1.apk`，776,452 字节；SHA-256：`DB810D0F0929C2FB14FD62B9A2846056A295D87B1F640F381B1380E3DE315231`。
- 包名 `cn.myscore.app`，versionCode 7、versionName 0.5.1，minSdk 28、targetSdk 35。
- apksigner 验证通过，签名证书与 0.5.0 相同，可覆盖升级，保留原设置和日历关联。延续个人使用的 debug 签名版本。
- 首页标题改为 MyScore，删除标题旁的「北京时间」提示。
- 所有比赛卡底部只保留开赛时间；时间未确认时显示「时间待定」。来源与更新时间仍可在详情和连接状态查看，独立的过期进行中警告保留。
- 订阅按钮改为无圆底的日历线框，加号/勾选居中，48dp 点击区域与状态描述保持可用。
- 同步更新界面标题断言、设计说明和 README 下载链接。

## 验证

- JDK 17 / Gradle 8.14 / Android Gradle Plugin 8.11.1 / SDK 35；无新增依赖。
- `:app:testDebugUnitTest`：33 项通过。
- `:app:lintDebug`：通过，0 errors、14 warnings，均记录在报告中。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest` 通过；日志 `artifacts/build-0.5.1.log`。
- 最终 0.5.1 APK 在 API 35 模拟器覆盖安装后，50 项 Android 界面/日历运行检查通过，含图标、自适应刷新率请求、视图复用、导航重建和日历事件保护。日志 `artifacts/android-tests-051.txt`。
- 本次界面调整的浅/深色截图与人工核对见 `.impeccable/review/ui-refinement/`；此前的九来源实时/详情全面验证见下方 0.5.0 历史记录。本次 50 项运行检查不包含可选实时网络和详情检查。
- 未连接 K80 Pro，不把模拟器验证视为真机高帧率或小米日历通知验收。

---
# MyScore 0.5.0 验证记录

日期：2026-10-25，北京时间。

## 交付

- APK：`artifacts/MyScore-0.5.0.apk`，973,908 字节；SHA-256：`5F8E3EEC843B6D4BCE92C55975201D09C7FDAA1AFC655D8F0A6A7D7F69DFE3C5`。
- 包名 `cn.myscore.app`，versionCode 6、versionName 0.5.0，minSdk 28、targetSdk 35。
- Android debug 签名。apksigner 验证通过，证书 SHA-256 与 0.4.0 完全相同，可覆盖升级，不需要卸载。

## 本次变化

- 折页 S 图标进入 APK：满版生产 artwork、自适应图标、Android 13 单色主题图标及五档密度兼容资源。aapt2 确认各密度的 application-icon 指向自适应 XML；运行检查通过 PackageManager 读取实际安装图标。
- 设置页改成两组原生操作行；长说明收进使用帮助。普通状态不再与分组标题逐卡重复；保留来源和完整日期时间。详情删除重复的顶部时间说明。
- 主页面、详情和底部 sheet 请求当前分辨率下受支持的最高窗口刷新率；不指定分辨率模式，不添加定时重绘。
- 日期与详情缓存后台读取；自动更新保留主页面的顶部、筛选和滚动容器；未变化的比赛复用卡片，仅更新数据时间。列表滚动中推迟视图替换，详情未变化时也复用视图。
- 删除没有上限的队标会话缓存，改用 24 MiB LRU；相同 URL 的请求合并，回调弱引用视图；长边采样上限 256px。手动刷新仍重试失败队标。
- 修复设置页在 Activity 重建后跳回比赛的问题；销毁时取消定时回调与工作线程；异常来源保留缓存并标明失败，详情缓存的未来时间不再被误当成新鲜数据。

## 构建与测试

- JDK 17，Gradle 8.14，Android Gradle Plugin 8.11.1，SDK 35。使用本机已缓存的同版本 Gradle 离线构建，无新增生产依赖。
- `:app:testDebugUnitTest`：33 项通过。
- `:app:lintDebug`：通过，0 errors、14 warnings。包含工具链更新提示、既有配置提示，以及满版兼容图标/自适应资源版本限定的设计提示；没有将警告隐藏为通过。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest`：通过。日志：`artifacts/build-0.5.0.log`。
- 最终 Android runtime：226 项通过。结果记录在 `artifacts/android-tests-050-full.txt`，包含九来源、两主题、原生日历、图标类型/单色图标、最高刷新率请求、视图复用与重建后保留设置页的检查。
- 原生截图：`.impeccable/review/v5-050/`，含浅/深色首页与详情、设置页及 1.3 倍字体。
- 图标来源与导出方式见 `docs/icon-provenance.md`。

## 性能测量及边界

设备是 API 35 / Android 15 x86_64 模拟器（Codex_MyScore_API35），不是 K80 Pro。同一设备、同日全部赛事、8 次相同坐标往返滑动（每次 650ms），执行前重置 gfxinfo。真实在线数据有变化，进程预热时长不同，因此以下只是单次观察，不是严格固定负载基准或真机性能承诺。

- 0.4.0：235 帧，Android gfxinfo 卡顿帧 158（67.23%），P50 44ms / P95 85ms / P99 117ms。
- 0.5.0：252 帧，卡顿帧 136（53.97%），P50 36ms / P95 69ms / P99 97ms。
- 原始渲染统计：`artifacts/gfxinfo-0.4.0.txt`、`artifacts/gfxinfo-0.5.0.txt`。这是 Android app gfxinfo 指标，不是 SurfaceFlinger 或 Android Vitals 指标。
- 两版另各采集 20 秒 CPU / RSS / Display FPS。CPU 平均约 20.73% / 19.68%（100% 为一个核心）；RSS 进程预热不同，不据此宣称内存降幅。两个 Display FPS 采集均报 `Invalid Perfetto trace stream`，没有有效 FPS 或逐帧统计，不推算或宣称达到 120fps。
- 录制 ID：0.4.0 `192fb93d-d788-4044-a33e-0b205a4c9789`；0.5.0 `8a5b511d-7095-4609-8913-c3b243c0edf3`。
- 最高刷新率仅为 Android 窗口提示，系统可能因用户设置、省电、温控或厂商策略忽略它。参考 https://developer.android.com/reference/android/view/WindowManager.LayoutParams#preferredRefreshRate 。
- K80 Pro 真机帧率、澎湃 OS 日历提醒、移动运营商网络和长期运行仍未验收。

---
# MyScore 0.4.0 验证记录

日期：2026-10-24，北京时间。

## 交付

- APK：`artifacts/MyScore-0.4.0.apk`，391,468 字节；SHA-256：`EA55E8F782FEDCCF3123806F276B43659D96ECCB80EA0CECBE9F85BA2015AAA6`。
- Android debug 签名；包名 `cn.myscore.app`，versionCode 5、versionName 0.4.0，minSdk 28、targetSdk 35；已装 0.3.x 可直接覆盖升级。
- 本次发布已提交 main 并发布 GitHub Release `v0.4.0`；APK 作为 release asset 上传。

## 0.4.0 本次变化

- **合并比赛与赛程**：只剩「比赛 / 设置」两个底部入口；日期切换和比分在同一页，移除重复的赛程页与日历摘要。
- **任意日期**：日期条支持前一天/后一天；点日期打开自绘月份 sheet，可前后翻月并选择任意一天，不再限制昨天/今天/明天。月份 sheet 使用统一底部 sheet 语言，选中日为实心圆、今天为 tonal 圆。
- **订阅直写小米日历**：不再在订阅时弹日历选择。首次只请求系统日历权限，然后自动优先选择小米账户日历（没有则本机日历）；设置页仍可手动更换。启动时在后台静默确定默认日历，不阻塞缓存首屏。
- **加载稳定性**：有缓存时先显示缓存列表，9 个来源完成前不再逐批重建列表，全部返回后只重绘一次；无缓存时才按来源逐步显示。列表重绘仍保留滚动位置和顶部收起状态。
- **速度**：NBA/NFL/足球/F1/CS2/LOL 网络线程从 6 提到 10；队标线程从 3 提到 8，连接/读取超时降到 6s/8s；缓存读取排在默认日历查询之前。队标仍在首次下载时可能显示缩写。
- 设置页不再显示无意义的刷新按钮；底部导航两等分。

## 构建与确定性测试

- JDK 17，Gradle 8.14，Android Gradle Plugin 8.11.1，compile/target SDK 35。
- `:app:testDebugUnitTest`：33 项通过，0 失败。
- `:app:lintDebug`：通过，0 errors、8 warnings。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest` 通过；日志 `artifacts/build-0.4.0.log`。
- Android runtime：**204 项通过**（`artifacts/android-tests-040-full.txt`）；测试覆盖九来源详情、单场订阅、5 分钟提醒、改期/取消/延期/个人事件保护、两入口导航和默认可写日历。

## 启动与渲染测量

设备：Android 15 / API 35 x86_64 模拟器，Pixel 7 配置；**不是 K80 Pro**。详见 `artifacts/perf-0.4.0.txt`。

- Activity 冷启动 TotalTime：1647 ms / 1889 ms（两次，缓存保留）。
- 有缓存启动：首帧骨架 → 缓存列表 → 来源 `first 467 ms`、`all 552 ms` → **一次**最终列表重绘；没有 9 次逐来源重建。
- 冷启动/无缓存时仍按来源增量显示；首次安装后第一次打开会走这条路径。

## 证据

- 主截图 24 张：`.impeccable/review/v4-040/`。
- 补充截图 6 张：`.impeccable/review/v4-040/manual/`：首页、顶部收起、日期 sheet、任意日期、订阅直写、设置。
- 真机反馈的两张截图已对应到：合并入口、任意日期、直写小米日历、减少列表重绘和队标并发下载。

## 仍未完成

- **未在 K80 Pro 真机复测**：小米/澎湃 OS 的日历权限、通知、后台限制、实际悬浮提醒；移动运营商网络；高刷新率下顶部收起/展开手感；队标首次下载速度。
- 队标首次下载仍可能先显示缩写；会话/磁盘缓存命中后不再闪回。Coventry/Fulham 等来源未提供队标 URL 的场次会一直显示缩写。
- 任意日期只改变查询日；部分赛事源对远期日期没有赛程，会显示“这个日期没有已确认的比赛”，这不是网络失败。
- 若系统没有可写日历，订阅会提示先启用本地/小米日历；不会偷偷写入第三方账户。

## 0.3.1 历史记录

- 卡片日历图标订阅、可收起顶部、队标会话缓存；204→此前 206 项检查（含赛程页检查，0.4.0 已合并删除）。
- APK `artifacts/MyScore-0.3.1.apk`，379,008 字节；SHA-256 `39782ABD598A5D45B7253F4DC9914C283E36DB976453A185026510D8D2DCFCC5`。
- GitHub Release `v0.3.1`。

## 0.3.0 / 0.2.0 / 0.1.0 历史

- 0.3.0：单场订阅、5 分钟提醒、统一底部 sheet、异步缓存与来源增量；Release `v0.3.0`。
- 0.2.0：白/石墨单色、真实队标、完整详情、F1 三栏；Release `v0.2.0`。
- 0.1.0：绿色初版，已被用户拒绝。

不把模拟器成功等同于所有真机、网络和时段可用。
