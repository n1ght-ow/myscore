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
