# MyScore 3.0.1 品牌标志裁切与毛刺修复

日期：2026-10-26，北京时间。用户反馈小米桌面 S 被放大裁切，标题与桌面边缘不顺滑。版本 `3.0.1`，versionCode `25`。

## 发现与修复

- 此前直接使用的分割 PNG 为 238 × 225，中央斜缝存在不透明黑像素，水平边缘有不规则透明像素。BitmapDrawable 统一着色后会把黑缝一并填白，不能作为干净的单色母版。
- 此前自适应前景为固定 52 × 49dp 的 LayerDrawable，缺少完整 108dp 内在画布。用户截图确实显示了过度放大；本轮未取得 HyperOS 桌面内部的归一化行为证据，不将某条系统实现细节视为已证实原因。
- 按原 S 造型重建两条平滑贝塞尔路径，保存可编辑的 `docs/assets/brand-s.svg`。标题与更新页改用同源 `VectorDrawable`；移除分割 PNG。自适应前景为 108 × 108dp 矢量画布，S 居中保留安全区；兼容图标按同一矢量重新导出五种密度。

## 验证与边界

- 3.0.0 上新增回归检查失败：品牌标志仍为分割 PNG，日志 `artifacts/brand-regression-before.txt`。修复后通过 **280 项原生运行检查**，包括 15 项新增标志检查：斜缝着色后透明、20 / 24 / 48px 抗锯齿、108dp 内在画布、108 / 216 / 432px 的可见范围、居中和 66dp 安全圆。日志 `artifacts/android-tests-3.0.1.txt`。
- **118 项 JUnit**，0 failures / errors；Lint 0 errors / 21 warnings；构建通过。模拟器实看首页矢量 S 和桌面主题图标，截图 `artifacts/brand-vector-fix/`。本轮未连接到小米真机，未声称完成 HyperOS 桌面验收；也未重做赛事联网与全部屏幕尺寸验收。
- `artifacts/MyScore-3.0.1.apk`：895,973 字节；SHA-256 `2e88373f4283ec70a88fbfc672064d64fb27a59b582968237ba0e2c754dcfa81`；校验文件 `artifacts/SHA256SUMS-3.0.1.txt`。包名与签名沿用既有版本。
- 发布入口：[v3.0.1](https://github.com/n1ght-ow/myscore/releases/tag/v3.0.1)。

# MyScore 3.0.0 版本调整

日期：2026-10-26，北京时间。按用户要求将当前版本改为 `3.0.0`，versionCode 由 23 递增至 `24`；发布标签 `v3.0.0`。沿用 2.3 的品牌和功能图标实现。

- 最终构建通过 `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --offline --console=plain`：118 项 JUnit，0 failures / errors；Lint 0 errors / 21 warnings。日志 `artifacts/build-3.0.0.log`。
- 最终 APK 覆盖安装至 Android 15 / API 35 模拟器，通过 265 项运行检查，含版本显示与更新安装校验；日志 `artifacts/android-tests-3.0.0.txt`。本轮未重新执行真机、全部屏幕尺寸或可选联网赛事验收。
- `apksigner verify --print-certs` 通过，沿用既有 debug 证书；aapt 确认包名 `cn.myscore.app`、版本 `3.0.0 / 24`、minSdk 28 / targetSdk 35。
- 附件 `artifacts/MyScore-3.0.0.apk`：933,210 字节；SHA-256 `77e81a3abdf85ce701677827449ec1462b84d1b4afe6966e63aa973bac57ef42`；校验文件 `artifacts/SHA256SUMS-3.0.0.txt`。
- 发布入口：[v3.0.0](https://github.com/n1ght-ow/myscore/releases/tag/v3.0.0)。应用内更新支持三段版本号，附件按 `MyScore-3.0.0.apk` 命名。

# MyScore 2.3 统一品牌与功能图标

日期：2026-10-26，北京时间。版本 `2.3`（versionCode `23`），标签 `v2.3`。

- 桌面、首页及更新页共用用户选定的 S 设计，应用内透明标志随主题着色；Android 自适应图标和单色图标使用同一前景。
- 设置页 8 项与底栏 2 项使用重新设计的原生矢量路径：日历接收、订阅勾选、同步核对、日历跳转、外观、升级、数据连接、帮助、比赛和设置。统一 24 单位画布与 1.7 单位圆角描边，设置项显式绑定图标。未引入生产依赖。
- 最终 2.3 构建通过 `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --offline --console=plain`：118 项 JUnit，0 failures / errors；Lint 0 errors / 21 warnings。构建日志 `artifacts/build-2.3.log`。
- 最终 2.3 APK 在 Android 15 / API 35 模拟器通过 265 项运行检查，日志 `artifacts/android-tests-2.3.txt`。图标实测深浅主题效果见 `artifacts/functional-icon-review-2026-10-26/`；该图标截图来自同一实现的 2.2.1 预览包。未重做真机、全部屏幕尺寸或可选联网赛事验收。
- 发布附件 `artifacts/MyScore-2.3.apk`：933,194 字节；SHA-256 `162c2f5bd49cbfa6e975edefd8fab6cb910c626e1c4e2e6d04c332541db4d04b`。校验附件 `artifacts/SHA256SUMS-2.3.txt`。
- `apksigner verify --print-certs` 通过，沿用既有 debug 证书，SHA-256 `e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`。包名 `cn.myscore.app`，minSdk 28 / targetSdk 35；aapt 确认版本 2.3 / 23，可按相同签名覆盖升级。
- 发布入口：[v2.3](https://github.com/n1ght-ow/myscore/releases/tag/v2.3)。发布附件采用应用内更新要求的 `MyScore-2.3.apk` 命名；APK 与构建产物、截图和测试日志不进入源码提交。

# MyScore 10 个功能图标重设计（本地预览包）

日期：2026-10-26，北京时间。根据用户截图重画设置页 8 个图标与底栏 2 个图标。统一为原生 Path 矢量、24 单位画布、1.7 单位圆角描边；每项使用明确的功能符号。品牌 S 与桌面图标沿用此前的用户素材，版本仍为 2.2.1 / versionCode 22。

- 最终构建、118 项 JUnit、Lint 通过（0 errors / 21 项原有 warnings）；最终 APK 在 Android 15 / API 35 模拟器通过 265 项既有运行检查。未启用可选的来源 / 详情联网检查。
- 核对深浅主题下的设置图标与底栏选中状态；保持原有文字、触摸区域和交互。没有引入依赖。
- 本地包：`artifacts/MyScore-icons-redesigned.apk`；SHA-256 `3331d904fac4b56c5eaeeff5835ec916dcdc7f3c744542ac92774f6316eacea6`。未发布，未做本轮真机验收。

# MyScore 统一 S 图标接入（本地预览包）

日期：2026-10-26，北京时间。按用户提供的图标套件替换桌面图标、首页 / 更新页品牌 S，并将显示器、人物、星标分别用于比赛、设置和已订阅比赛。素材映射及 PNG / 矢量边界见 `docs/icon-provenance.md`。保留版本 2.2.1 / versionCode 22，未发布新版本。

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 通过；118 项 JUnit，0 失败 / 0 错误。Lint 为 0 errors / 21 warnings，仍有项目原有的本地化等警告。
- 最终 APK 在 Android 15 / API 35 模拟器 `Codex_MyScore_API35`（emulator-5554，1080 × 2400）通过 265 项现有运行检查，包含自适应图标和 Android 13 单色资源检查；本轮未启用可选的真实来源 / 详情 / 球队联网检查。
- 人工核对深浅主题首页、设置页星标与人物图标、桌面主题图标；未在本轮验证 Redmi K80 Pro 的桌面遮罩。桌面主题图标的外圈和配色由系统控制。
- 本地预览包：`artifacts/MyScore-icons-preview.apk`，SHA-256 `63de9c6b25a60ac35198755b894589ac7e77f785591809c515751ee2d46fd3af`。这是图标预览构建，不替换已有正式发布附件。

# MyScore 2.2.1 应用内下载修复

日期：2026-10-26，北京时间。versionName `2.2.1`，versionCode `22`，标签 `v2.2.1`。

## 问题与修复

- 用户报告更新下载长期停在 0% / 等待网络。REDMI K80 Pro 真机诊断中，应用网络直接读取已发布的 2.2 APK 约 1.7 秒完成、SHA-256 匹配；同一地址交给系统下载器后显示 0 字节，状态查询未正常返回。此前界面将暂停原因合并为等待提示，缺少可靠的恢复方式。本次未将具体系统内部或运营商原因视为已证实结论。
- 新下载改用应用自己的 HTTPS 连接和 `dataSync` 前台服务，离开页面后继续。优先使用本仓库官方附件 API，失败时切换官方发布下载地址，最多三次尝试；连接 / 读取 / 整体时限均受限。旧系统下载任务只在独立线程尝试清理，不再查询它的状态或阻塞更新页。
- 只接受本仓库的发布与附件 API 地址，跳转限制为 GitHub 官方 HTTPS 主机；限制文件大小并验证 SHA-256，失败或取消时移除部分文件。安装前继续验证包名、更高版本号和现有签名。
- 文件存于应用内部 `files/updates/`。私有 ContentProvider 只为已校验的当前 APK 提供只读安装 URI，拒绝未校验文件、写入和其他路径。无新增第三方依赖。

## 已执行验证

- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --offline --console=plain` 通过：**118 项 JUnit**，0 failures / errors；Lint **0 errors / 27 warnings**。新增传输回归覆盖官方跳转、旧元数据兼容、恶意地址 / HTTP 降级拒绝、重定向循环、错误响应、超长 / 截断 / 篡改数据和取消清理。日志 `artifacts/build-update-fix-2.2.1.log`。
- 最终 APK 在 Android 15 / API 35 模拟器通过 **265 项运行检查**，含私有安装 Provider、旧系统任务立即返回可重试错误、取消任务的迟到回调不能覆盖新任务，以及既有日历 / 页面 / 更新回归。日志 `artifacts/android-tests-update-2.2.1.txt`。
- 连接的 REDMI K80 Pro 实测系统为 **Android 17 / API 37**。覆盖安装最终修复版后，原生前台服务从 GitHub 获取实际已发布的 2.2 APK（1,421,442 字节），退出应用页面后继续；最终测试约 **1,321ms** 完成，SHA-256 与发布元数据一致。另一次正常传输约 2,123ms；一次真实网络连接超时能结束等待并报告可重试错误。以上为当次设备 / 网络采样，不保证所有网络下载速度。
- 真机 **10 项下载与安装文件访问检查**通过：后台服务启动、离开应用后完成、长度与 SHA-256、重新创建 repository 后恢复任务、实际包名及签名、阻止同版 / 降级安装、只读安装 URI 可读、拒绝写入、拒绝越界路径和拒绝未校验文件。日志 `artifacts/update-network-phone-final.txt`。
- 真机测试使用真实公开 2.2 附件，测试结束恢复更新 preferences 并删除测试 APK；临时系统下载诊断任务已移除。未清除应用数据；检查确认外观仍为浅色、原日历仍被选择、原有 11 场订阅记录保留。
- `apksigner verify --print-certs` 通过；证书 SHA-256 `e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`，沿用既有签名；手机包管理器确认 `versionName=2.2.1 / versionCode=22`。

## 交付与边界

- `artifacts/MyScore-2.2.1.apk`：1,815,590 字节；SHA-256 `4fb7c2c203644aaef7944007af73b5166618b2604dc63aa58f7f9dc5304a6f5c`。校验文件 `artifacts/SHA256SUMS-2.2.1.txt`。
- 发布入口：[v2.2.1](https://github.com/n1ght-ow/myscore/releases/tag/v2.2.1)。手机安装修复版使用 USB 覆盖安装，安装的 APK 与上述本地交付包 SHA-256 完全一致。本轮下载测试使用真实已发布的 2.2 附件并确认拒绝降级，未在手机上通过系统安装器完成“下载更高正式版本 → 安装”的闭环。
- 本轮没有重跑全部联网赛事详情和屏幕尺寸矩阵，未验证全部手机系统和运营商组合。网络不可达时仍可能失败，但更新页会结束等待并允许重试。

# MyScore 2.2 已结束比赛自动移除订阅

日期：2026-10-26，北京时间。versionName `2.2`，versionCode `21`，标签 `v2.2`。

## 本次变更

- 修复同步逻辑将进行中和已结束放在同一分支、只删除提醒却始终保留订阅记录的问题。来源确认已结束后，清除订阅关联、保存的比赛记录与指纹；保留系统日历历史日程，停止后续重复查询。
- 旧版本已保存为 `final` 的订阅直接使用已确认状态清理，不依赖再次联网。来源暂不可用、延期及进行中的比赛继续保留，不按估算结束时间删除。
- 进入设置及恢复设置页会自动检查，不受最近一小时已检查的限制；完成后刷新订阅数量。检查改用已有的日历工作线程，避免与比分加载争用同一执行队列。
- 沿用原签名，无新增生产依赖。

## 已执行验证

- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --offline --console=plain` 通过：106 项 JUnit，0 failures / errors；Lint 0 errors / 25 warnings。日志 `artifacts/build-2.2.log`。
- Android 15 / API 35 模拟器 `emulator-5554`（1080×2400）通过 **260 项运行检查**。覆盖进行中订阅保留、结束后各订阅视图和持久记录清理、重复同步幂等、后台身份解析确认结束、旧版结束记录无网络清理、最近刚检查后进入设置仍自动清理并更新数量、历史日程保留、个人日历事件保护，以及既有界面 / 队标 / 更新等默认回归。日志 `artifacts/android-tests-2.2.txt`、`artifacts/android-runtime-2.2.log`。
- 最终 APK 由 Mobile Dev 覆盖安装并启动，包管理器确认 `versionName=2.2 / versionCode=21`。
- `apksigner verify --print-certs` 通过，证书 SHA-256 `e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`，与实际本地 v2.1 APK 对照一致。包名 `cn.myscore.app`，minSdk 28 / targetSdk 35，可覆盖安装。

## 交付包与边界

- `artifacts/MyScore-2.2.apk`：1,421,442 字节；SHA-256 `b6e4a801dea4fc07fc7f6c43fe3821ab70ea4d4ece65c9e665bc82899d8290e2`；校验文件 `artifacts/SHA256SUMS-2.2.txt`。
- 发布入口：[v2.2](https://github.com/n1ght-ow/myscore/releases/tag/v2.2)。APK、测试日志与本地工具启动记录保留在被忽略的 `artifacts/`，不加入 Git。
- 本轮未启用可选的 `network=true details=true lol=true` 检查，未重跑全部屏幕尺寸矩阵；Redmi K80 Pro / HyperOS、运营商网络及真实锁屏提醒仍未验收。自动清理依赖来源确认结束；联网或系统后台执行受限时会延后。

# MyScore 2.1 流畅性与稳定性

日期：2026-10-25，北京时间。versionName `2.1`，versionCode `20`，标签 `v2.1`。

## 本次变更

- 首页与球队页记录按住、拖动和惯性滚动状态，自动更新延后到手势结束；暂停球队页时移除延后的渲染任务，返回时显示最新已有结果。
- 快速切换赛事或日期会取消旧缓存 Future，已排队的过时任务不再执行读取；请求完成仍校验当前选择，避免旧结果覆盖新页面。
- 同一队标视图只挂接一个未完成请求的回调；复用已经显示的图片和比分卡点击背景，缩写不变时不重复写入文字。图片失败释放绑定状态供重试，回收视图拒绝旧请求回写。
- 球队加载期间的手动刷新合并为一次待处理请求；页面恢复时优先显示已有数据。比分列表的变更指纹包含来源球队 ID，队名与图片不变而 ID 补齐时也能更新赛程入口。
- 保留 2.0 界面与既有签名，没有新增生产依赖。

## 已执行验证

- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --offline --console=plain` 通过：106 项 JUnit，0 failures / errors；Lint 0 errors / 25 warnings。日志 `artifacts/build-2.1.log`。
- 最终 APK 在 Android 15 / API 35 模拟器 `emulator-5554`（1080×2400 / 420dpi）通过 **570 项 Android 运行检查**，启用 `network=true details=true lol=true`。覆盖九类真实数据源、详情、双主题、LoL 小局、球队赛程、队标入口、订阅及个人日历事件保护。日志 `artifacts/android-tests-2.1-final.txt`、`artifacts/android-runtime-2.1.log`。
- 新增 `StabilityChecks` 的 15 项回归：100 次连续绑定只挂接 1 个图片回调；图片成功、失败重试及回收后迟到结果；比分卡保留 RippleDrawable 且点击使用最新比赛；快速选择取消旧缓存任务；静止按住、取消手势、恢复刷新及惯性滚动状态。测试图片、比赛和阻塞任务仅用于内存中的独立测试，不写入应用数据源或持久缓存。
- 同一次模拟器检查的离屏视图微基准：1000 次比分卡复用绑定共 324ms。这只记录绑定路径耗时，没有 v2.0 同条件对照，也不代表屏幕 FPS 或真实手机性能提升百分比。
- 最终包通过 Mobile Dev 覆盖安装，包管理器确认 `versionName=2.1 / versionCode=20`。额外实际滑动确认列表推进、顶部收起和导航保持可用；球队截图保存于 `artifacts/review-2.1/`。

## 交付包与边界

- `artifacts/MyScore-2.1.apk`：1,784,810 字节，包名 `cn.myscore.app`，minSdk 28 / targetSdk 35。
- SHA-256：`c807ecfddb0ba2e508d0ce4ac915122888441c1d50981bb2e06e0fff7751f4ad`；校验文件 `artifacts/SHA256SUMS-2.1.txt`。
- `apksigner verify` 通过；证书 SHA-256 `e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`，已与 v2.0 APK 实际对照，包名一致、versionCode 递增，可覆盖安装保留设置、缓存及日历关联。
- 发布入口：[v2.1](https://github.com/n1ght-ow/myscore/releases/tag/v2.1)。APK、日志与生成截图保留在被忽略的 `artifacts/`，不加入 Git。
- Redmi K80 Pro / HyperOS、国内运营商网络、锁屏提醒及高刷新率真机表现尚未验收；本轮没有重新执行 2.0 的全部屏幕尺寸截图矩阵。

# MyScore 2.0 全界面重新设计

日期：2026-10-25，北京时间。versionName `2.0`，versionCode `19`，标签 `v2.0`。

## 本次变更

- 使用 UI UX Pro Max 的原生交互 / 字体缩放指导，以及 Taste Skill 的 True-black flat 方向（seed 778335413）。建立统一的黑白表面、Inter 字体、线框图标、冷绿色状态、20dp 卡片和黑白反相操作。
- 移除首页大标题副文案、赛事大色块和底部选中色块，改为紧凑品牌行、文字下划线赛事切换和单行日期操作。
- 比分卡、球队赛程、设置、更新、统计表及所有底部弹层共用同一个设计系统。详情比分牌随正文滚动；大字号 / 窄屏用纵向对阵，避免长英文队名被三列挤碎；横屏 / 大屏正文及弹层最大 640dp 居中。
- 保留缓存、真实来源、队标入口、单场日历订阅和覆盖更新，没有新增生产依赖。字体作为离线资产随包，许可证与来源记录位于 `app/src/main/assets/fonts/`。

## 已执行验证

- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 通过；106 项 JUnit，0 failures / errors；Lint 0 errors / 25 warnings。构建日志 `artifacts/build-2.0.log`。
- 最终 APK 在 Android 15 / API 35 模拟器 `emulator-5554` 通过 **555 项运行检查**，启用 `network=true details=true lol=true`。覆盖九类真实来源、各项目详情与统计、LoL、球队赛程、筛选、队标跳转及实际日历订阅 / 取消确认；日志 `artifacts/android-tests-2.0-final.txt`。
- 球队筛选回归改为在主线程等待列表分组完成重排后断言，并校验筛选值；避免跨线程读取尚在变化的 View 树。隔离复测 301 项及上述最终完整回归均通过。
- 常规手机 411dp / 1.0 倍字号：14051 项原生布局断言通过，深浅主题均覆盖。
- 360dp / 1.3 倍字号：9909 项原生布局断言通过，深浅主题均覆盖。
- 320dp / 2 倍字号：10155 项原生布局断言通过，深浅主题均覆盖。
- 914×411dp 横屏：9421 项原生布局断言通过，深浅主题均覆盖。
- 1066×1706dp 平板：10011 项原生布局断言通过，深浅主题均覆盖。
- 专用 `RedesignInstrumentation` 检查文字自然高度、完整行、无省略、可见行宽与详情滚动区域；保存 **182 张原生截图** 于 `artifacts/review-2.0/`。窗口实测：1080×2400 / 420dpi、945×2100 / 420dpi、840×1866 / 420dpi、2400×1080 / 420dpi、1600×2560 / 240dpi。关闭系统动画完成小屏、平板与末轮手机检查；最后恢复窗口大小、420dpi、1.0 字号和动画。
- 截图迭代修正了大头部、窄屏英文队名碎行、日期断行和浅色次级文字对比度。最后的窄屏字号分支另行复测；截图索引和拼图在同目录。原生界面没有 WebView，使用 Android 布局检查替代网页 DOM inspector。
- `artifacts/ui-contrast-2.0.json`：30 组正文颜色 / 表面组合均不低于 4.5:1，最低 **4.89:1**；主操作及状态文字同时检查。无新页面入场动画。

## 交付包

- `artifacts/MyScore-2.0.apk`：1,777,946 字节，包名 `cn.myscore.app`，最低 Android 9 / targetSdk 35。
- SHA-256：`fe51b554380a03b413f8c48330f3f394472d81c9c37c0ad532b13dcd8a12de83`；校验文件 `artifacts/SHA256SUMS-2.0.txt`。
- `apksigner verify` 通过。证书 SHA-256 `e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`，已与实际下载的 v1.2 发布 APK 对照；包名相同、versionCode 递增，可覆盖安装保留数据。继续使用既有个人版 debug 签名。
- 发布入口：[v2.0](https://github.com/n1ght-ow/myscore/releases/tag/v2.0)。发布资产与远端哈希在发布后另存 `artifacts/release-verification-2.0.json`，不把 APK 和生成截图加入 Git。

## 验证边界

未连接 Redmi K80 Pro；HyperOS 真机、运营商网络、实际刷新率、锁屏日历通知及后台调度仍需真机验收。上述检查覆盖指定窗口与真实缓存样本，不代表穷尽所有来源文字和设备组合。来源测试来自模拟器的现有网络，未对数据逻辑作更改。

---

# MyScore 1.2 球队赛程订阅

日期：2026-10-25，北京时间。版本 `1.2`（versionCode 18），发布标签 `v1.2`，附件 `MyScore-1.2.apk` 与 `SHA256SUMS-1.2.txt`。

## 本次变更

- 球队页默认显示「接下来」，顶部球队信息直接放在页面背景上。
- 球队赛程卡右下角复用比分页的透明日历图标：22dp 图形、48dp 点击区域，订阅显示勾选，不加按钮底色、订阅文字或逐卡重复的提醒说明。卡底展示北京时间和来源。
- 接入现有单场日历订阅，后台线程枚举与写入日历；首次请求日历权限，小米账户/本机优先；同一比赛记录与首页及设置共用。时间未确认、延期、已开始的比赛不提供新订阅；已订阅比赛仍可取消。
- 沿用提前 5 分钟系统日历提醒、取消确认、失败提示和既有后台改期检查，无新增生产依赖。

## 已执行验证

- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 通过；106 项 JUnit，0 failures / errors。
- Android 15 / API 35 模拟器 `emulator-5554`，1080×2400：完整 553 项运行检查通过（`network=true details=true lol=true`），覆盖真实来源、LoL、各项目详情、球队赛程、实际订阅写入、勾选状态和关闭取消确认框后保留订阅。
- 订阅操作额外断言 48dp 点击区、无可见「订阅」标签、无逐卡「提前 5 分钟提醒」说明。布局检查覆盖 320/360dp、1/1.3/2 倍字号、深浅主题及未来比赛卡。
- 新版原生截图：`artifacts/review-team-1.2/phone-dark.png`、`phone-light.png`；深色包含勾选与加号两种状态，浅色同步检查。
- 包名 `cn.myscore.app`、versionName `1.2`、versionCode 18；`apksigner verify` 通过，证书 SHA-256 `e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`，与现有 1.1 附件一致，可覆盖安装保留数据。
- APK SHA-256 `68d949c196a399d1a53a0eab490aab09c6c1bfa3c034eab2b0a5be3c7e8b4253`。

- 发布核验：功能提交 `80f95f9` 已推送 `origin/main`，`v1.2` 标签指向该提交；GitHub Release 为正式版，非 draft / prerelease，已设为 latest。APK 937,280 字节，远端资产 digest 与上述本地 SHA-256 一致；公开发布附件再次下载后哈希一致。
- 应用内更新页在已安装 1.2 的模拟器读取正式 Release，实际显示「当前版本 1.2」「当前已是最新版本」「已发布的最新版本 1.2」。
- 独立界面审查结论 `ship`：深浅主题的透明加号 / 勾选图标符合用户参考，无大按钮和重复说明；文档一致性检查确认未新增全局设计 token。

## 边界

未连接 Redmi K80 Pro；HyperOS 真机、运营商网络、锁屏日历通知与后台调度仍待验收。字体/宽度布局检查不等同于在这些物理分辨率逐场运行 UI。Mobile Dev Android 安装、读取与截图可用，侧栏面板曾返回 macOS 平台检查错误。

---

# MyScore 1.1 球队赛程

日期：2026-10-25，北京时间。版本 `1.1`（versionCode 17），发布标签 `v1.1`，APK 文件名 `MyScore-1.1.apk`。

## 新功能

- 比赛卡与详情比分条上的队标成为球队赛程入口（分别 48dp / 56dp 触摸目标）；来源缺少球队 ID 时不跳转，点击仍由比赛卡打开详情，不按队名猜测。
- 新增 `TeamActivity`：真实队标、队名和赛事；来源 / 场次 / 获取时间；「全部 / 接下来 / 已结束」筛选；每场显示北京时间日期、开赛时间、对手、场地或赛事阶段、本队在前比分和胜 / 平 / 负。列表使用回收行，结构不变时只重绑可见卡片；有进行中比赛时前台每 30 秒更新，否则不轮询。
- 数据来源：ESPN 球队 schedule（足球合并默认已结束结果与 `fixture=true` 剩余赛程；NBA / NFL 单次返回整季）；5EPlay `team/matches_v1`（按战队 ID，最近 30 场与接下来已发布比赛）；LOL Worlds 官网列表按战队 ID 筛选。F1 没有球队赛程入口。
- 新增 30 分钟磁盘缓存 `teams-v1`；失败时保留上次成功赛程，并在页面显示失败原因和原获取时间。空赛程与连接失败分开；缺失比分保持未知。CS2 / LOL 不标主客场，比分按本队在前。
- 新增 6 个生产类（`Team`、`TeamSchedule`、`TeamRepository`、`TeamActivity`、`TeamScheduleRow`、`TeamScheduleAdapter`），无新增生产依赖；`DomesticEsports.fiveMatch` 增加可关闭 Spirit 限定的重载，主列表及既有详情行为不变。

## 已执行验证

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` 全部通过，日志 `artifacts/build-1.1.log`；106 项 JUnit（新增 13 项 `TeamScheduleTest`），0 failures / errors；Lint 0 errors、25 warnings（新增 3 条为 `TeamActivity` 的 `SetTextI18n`，与既有 `UpdateActivity` 同类）。
- 开发机经本地代理探测真实接口：ESPN 球队 schedule 对 EPL、欧冠、Carabao Cup、FA Cup、NBA、NFL 返回本赛季赛程；5EPlay 战队赛程对 Spirit（`csgo_tm_7020`）与对手（如 `csgo_tm_4494`、`hltv_team_12394`）返回；Worlds 列表可按战队 ID 筛选。响应抽样在 `artifacts/probe-team-*.json`，不进入仓库。
- `:app:verifyLiveSources` 九类来源全部成功：英超 5、欧冠 0、Carabao Cup 0、FA Cup 0、NBA 7、NFL 12、F1 5、CS2 Spirit 31、Worlds 90；Java 代理参数显式传入，日志 `artifacts/live-1.1.log`，报告 `artifacts/live-source-report.json`。
- API 35 / Android 15 x86_64 模拟器（1080×2400、420dpi、1.0 字号）完整 533 项运行检查通过，启用 `network=true details=true lol=true`；日志 `artifacts/android-tests-1.1.txt`、`artifacts/android-tests-1.1-runtime.log`，无 FATAL / ANR。其中球队赛程 56 项检查覆盖真实 EPL 38 场（8 已结束 + 30 接下来）、Spirit 30 场、对手战队赛程、Worlds 筛出 10 场，包含筛选、48dp 队标触控、从赛程打开详情并返回。
- 同一 APK 在实际 945×2100（360dp）/ 1.3 倍字号下再次通过 533 项；在实际 840×1866（320dp）/ 2 倍字号下通过 279 项基础检查（启用 `network=true`，不重复运行九类详情）；日志 `artifacts/android-tests-1.1-360dp-large.txt`、`artifacts/android-tests-1.1-320dp-2x.txt`。
- 主列表队标点击由 `TeamChecks.crestShortcut` 实际执行：在真实比分页找到带「查看 X 的赛程」描述的可点击队标，点击后由 ActivityMonitor 确认进入 `TeamActivity`；详情页队标同样确认。测试结束后已恢复默认主题、分辨率和字号。
- 截图：常规字号 6 张 `artifacts/review-team-1.1/`（EPL 深 / 浅、CS2、LOL、赛程打开详情、队标直达）；360dp / 1.3 字号 6 张 `artifacts/review-team-1.1-360dp-large/`；320dp / 2 倍字号 6 张 `artifacts/review-team-1.1-320dp-2x/`。首页队标布局另见 `artifacts/main-1.1-crest.png`。
- 本地 APK：`artifacts/MyScore-1.1.apk`，1,285,980 字节；包名 `cn.myscore.app`，versionCode 17、versionName 1.1，minSdk 28 / targetSdk 35；SHA-256 `625b361cdfa45f31379bebbadb8cc1f979319be498a64ae2841d877b34d70982`；校验文件 `artifacts/SHA256SUMS-1.1.txt`。`apksigner` 验证通过，证书 SHA-256 `e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`，与 1.0 相同，可覆盖安装并保留数据。从模拟器拉回的 `artifacts/installed-1.1.apk` 哈希一致。
- 发布：提交 `1c4c932` 已推送 `origin/main`；GitHub Release [`v1.1`](https://github.com/n1ght-ow/myscore/releases/tag/v1.1) 为 latest，非 draft / prerelease，标签指向该提交。上传的 `MyScore-1.1.apk` 大小 1,285,980 字节、GitHub 资产 digest `sha256:625b361cdfa45f31379bebbadb8cc1f979319be498a64ae2841d877b34d70982`，`SHA256SUMS-1.1.txt` digest 为 `sha256:128946c1e5cfb01cf23e4ceae52dc71349583deae461adfc4eae8fb77ae851c8`；经本地代理下载公开发布资产返回 HTTP 200，APK SHA-256 与本地交付包一致。应用内更新所需的版本号、资产名、大小和 digest 均由该 release 提供。
- 应用内更新页在模拟器实际读取该 release：显示「当前版本 1.1」「当前已是最新版本」「已发布的最新版本 1.1」，截图 `artifacts/update-check-1.1.png`；证明更新器能解析并比较已发布版本，不只是本地安装包。

## 未验证边界

- 未连接 Redmi K80 Pro；HyperOS、真实移动网络、120Hz、锁屏日历通知仍待真机验收。所有来源证据来自开发机代理网络和模拟器，不代表运营商网络。
- 球队赛程的运行检查覆盖 EPL、CS2、LOL；NBA、NFL、欧冠、Carabao Cup、FA Cup 的球队 schedule 只完成接口探测，没有逐项运行 UI；它们共用同一 ESPN 解析与页面组件。
- 5EPlay 对短时间高频请求返回过「未匹配到归一化的team」；测试中对 CS2 请求做了两次退避重试，产品错误文案为「暂未返回该队赛程，请稍后刷新重试」，不把限流当作空赛程。
- 从对手赛程进入 5EPlay 比赛详情时关闭了 Spirit 限定，并按比赛 id 校验身份；本版本没有新增对手详情的逐项运行检查。
- 320dp / 2 倍字号只运行基础套件，未在该字号重复完整详情与双主题交互。

---

# MyScore 1.0 发布核验

日期：2026-10-25，北京时间。发布标签 `v1.0`，APK 文件名 `MyScore-1.0.apk`。

## 发布范围

- 汇总尚未发布的 0.10.1 稳定性 / 文字显示修复与 0.10.2 透明队标修复；下方保留这些本地版本的独立历史记录。
- 应用 versionName 为用户指定的 `1.0`，versionCode 16，包名 `cn.myscore.app`，minSdk 28 / targetSdk 35。没有新增生产依赖；沿用原有个人 debug 签名。
- 更新器现在同时识别两段和三段正式版本号，缺省 patch 按 0 比较，继续拒绝前导零、预发布和非本仓库附件；下载目录清理也支持两段版本文件名。新增两项 JUnit 覆盖版本比较、`v1.0` 附件解析与保存恢复。
- **从 0.x 首次升到 1.0 必须手动覆盖安装。** 已发布旧客户端的三段版本解析规则无法通过服务器附件绕过；1.0 起支持后续两段 / 三段更新。签名一致，无需卸载，不要求清除设置、缓存或日历关联。

## 本发布包已验证

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` 全部通过，日志 `artifacts/build-1.0.log`。93 项 JUnit，0 failures/errors；Lint 0 errors、20 warnings。
- 实际 1.0 APK 在 API 35 / Android 15 x86_64 模拟器（1080×2400、420dpi、1.0 字号）通过 465 项完整运行检查，启用 `network=true details=true lol=true`；覆盖来源、日历、设置重建、更新签名校验与版本显示、文字 / 队标布局、浅深详情与 LoL 小局。日志 `artifacts/android-tests-1.0.txt`、`artifacts/android-tests-1.0-runtime.log`。
- 同一 APK 在实际 360dp 窗口、1.3 倍系统字号下通过 202 项基础回归，本轮不再次执行全套在线详情；日志 `artifacts/android-tests-1.0-360dp-large.txt`。结束后恢复原始分辨率与 1.0 字号。
- 1.0 实际页面截图：`artifacts/review-1.0/` 40 张，`artifacts/review-lol-1.0/` 8 张；不使用设备目录内两张旧 `football-overview` 截图作为本版证据。
- 发布 APK `artifacts/MyScore-1.0.apk`，899,548 字节，SHA-256：`092068d4c26b98dee90ecbad3e2f91a7340a5fbf668604c2e95a7012f0c1c1a8`；发布校验附件 `artifacts/SHA256SUMS-1.0.txt`。
- aapt 核对包名 / SDK / 版本，apksigner 验证通过；证书 SHA-256 `e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`，与 0.10.0、0.10.1、0.10.2 相同。
- K80 Pro / HyperOS、真实运营商网络、120Hz、锁屏日历通知仍未验收；没有把此前版本的性能或压力数字计入 1.0。

---

# MyScore 0.10.2 透明队标与视觉尺寸修复（本地包）

日期：2026-10-25，北京时间。未提交、推送或发布 GitHub Release；保留 0.10.1 APK 及其历史验收记录。

## 改动与规则

- 共用 `Logos` 组件移除队标的白色底板与固定 3dp 内边距，加载失败时保留透明的文字占位。首页 28dp、详情 44dp、表格小徽标和 LoL 队标均直接显示图案；人物、英雄及装备图像沿用独立 artwork 路径。
- 在后台解码后按实际 alpha 边界去掉源图透明留白，保持宽高比例居中；按不透明覆盖率调整视觉占比，实心徽章约 86%、疏朗轮廓最多约 96%，不以拉伸或裁切主体换取大小。源图颜色、内部白色细节与透明孔洞保留；没有对来源图片做白色抠除。
- 几乎纯黑 / 纯白的单色标记只在低对比主题下适配正文墨迹色。只要存在相反的白 / 黑细节，就不整体换色，避免抹掉黑白标记内部结构。
- 实际缓存检查发现 Spirit 来源为 600×300 的黑白双版本合并图：`https://oss.5eplay.com/sport/csgo/team/merge_cgchcqhll806h1bg2lt0.png`。仅对 5EPlay 该队标命名空间下 `merge_*.png`、且为两个等大正方形的图按主题选取一半；普通横长队标不分割。选取后再执行透明边界与视觉尺寸归一化。
- 内存缓存键区分尺寸、队标 / artwork 和主题墨迹色；磁盘仍保存原始来源字节，16 MiB 上限沿用原实现。不新增生产依赖。

## 已执行验证

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` 全部通过；91 项 JUnit、0 failures/errors，Lint 0 errors / 20 warnings。日志 `artifacts/build-0.10.2.log`。
- 新增 26 项 Android `CrestChecks` 已运行通过：浅深主题的 14/28/44dp 透明槽位、透明留白不同但图案相同的尺寸一致性、居中、横竖比例、密度补偿、透明孔洞、颜色与白色细节保留、黑白标记保护、低对比单色适配、空图回退、5EPlay 合并图按主题选取及普通横长图保护。测试构造图只在内存中使用，不写入比分、队标来源或应用持久存储。
- 最终 APK 在 API 35 / Android 15 x86_64 模拟器（1080×2400、420dpi、1.0 字号）通过 465 项完整运行检查，启用 `network=true details=true lol=true`，含九类来源、浅深详情、日历、日期切换和设置重建。日志 `artifacts/android-tests-0.10.2.txt`、`artifacts/android-tests-0.10.2-runtime.log`。
- 同一最终 APK 在实际 945×2100 窗口（360dp）、1.3 倍系统字号下再次通过完整 465 项检查；日志 `artifacts/android-tests-0.10.2-360dp-large.txt` 及对应 `*-runtime.log`。40 张首页/详情截图、8 张 LoL 分局图分别见 `artifacts/review-0.10.2-final-360dp-large/`、`artifacts/review-0.10.2-lol-final-360dp-large/`；结束后恢复 1080×2400 和 1.0 字号。
- 最终包常规字号的 40 张首页/详情截图见 `artifacts/review-0.10.2-final/`，LoL 分局的 8 张图见 `artifacts/review-0.10.2-lol-final/`；已剔除设备目录中两张历史 `football-overview` 图。浅深主题首页队标裁图预览：`artifacts/logo-review/transparent-crest-preview.png`。

## 安装包与边界

- APK：`artifacts/MyScore-0.10.2.apk`，1,221,132 字节；包名 `cn.myscore.app`，versionCode 15、versionName 0.10.2，minSdk 28 / targetSdk 35。SHA-256：`76663bcf28d24fa2b3a2b81b373cea78871ab9febeb0bd1bd955960ce8f91eee`；校验文件已同步。
- apksigner 验证通过，证书 SHA-256 仍为 `e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`，可覆盖安装并保留数据。没有卸载或清空应用数据。
- 已拉回实际安装的 `base.apk`，SHA-256 与交付包一致；0.10.1 旧包及其校验文件保留。
- 以上为 API 35 模拟器证据，未连接 K80 Pro，不声称真机手感、性能或运营商网络已验收。

---

# MyScore 0.10.1 稳定性与文字显示修复（本地包）

日期：2026-10-25，北京时间。未提交、推送或发布 GitHub Release。

## 修复范围

- 共用正文与比分保留字体上下留白和 fallback line spacing；比赛卡长队名、状态、日期与球队筛选允许换行。详情表头、身份列和数值按内容测量高度，左右同一行保持对齐，取消固定行高与行数省略。日历日期格和 LoL 等级标记改为内容高度；小局结果限制在分配列宽内。
- 切换日期 / 赛事立即显示对应内存数据或读取状态，取消过期请求，通过递增版本拒绝 A → B → A 的旧回调；切回全部时合入已更新的单项目数据。保留回收列表与适用的原地更新，不重复设置未改变的比赛文字。
- 列表与详情的强制刷新在已有请求结束后继续执行；详情状态或比分变化会使旧缓存失效。失败详情在失败结束后退避，避免慢失败后立即重试。跨午夜的旧查询完成后仍能进入新日期加载。
- 相同 URL / 请求头的并发来源请求合并，仅共享进行中的读取，不额外缓存成功或失败响应；已取消的共享请求所有者不取消其他有效读取方。响应读取检查中断、总读取时限与大小上限。
- ESPN 每个来源日期单独降级；失败日期保留自己的缓存，成功日期仍可更新。全部日期失败则保留原视图缓存；空赛程和连接失败使用不同提示。部分失败写入缓存，重启后不伪装为完整成功。
- 队标请求在视图挂载前完成也可绑定，仍按 URL 校验回收视图；缩写占位使用受限自动字号。
- 设备验收复现快速 A → B → A 切换的真实崩溃：前一次 `notifyDataSetChanged` 尚未布局，适配器把旧比赛卡当成加载文字直接重绑。现在列表等待布局时交由 ListView 按视图类型回收；正常稳定列表仍可原地更新。
- 设备布局检查复现 `123–124` 比分高度不足：平台 auto-size 在布局阶段改变字号，晚于 wrap-content 高度测量。现在先按宽度选字号，再测量完整行高，保留 22sp 下限及上下两行回退。

## 本次已执行

- JDK 17 / Gradle 8.14 / AGP 8.11.1 执行 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`；设备修复后再次全部通过，日志 `artifacts/build-0.10.1-device-fix.log`。测试包修正后的单独构建日志 `artifacts/build-0.10.1-device-tests.log`；最终完整构建检查 `artifacts/build-0.10.1-final.log` 通过。
- 91 项 JUnit，0 failures/errors；新增 16 项覆盖并发合并、取消隔离、失败后重试、独立来源、跨日期部分失败 / 全失败 / 去重 / 空赛程，以及详情缓存时间、身份、状态和比分变化。
- Android Lint：0 errors、20 warnings，没有隐藏警告。
- `:app:verifyLiveSources` 开发机网络探测九类来源全部成功；日志 `artifacts/live-0.10.1.log`。本次 Android 运行套件另通过九类来源的赛程读取及已选真实比赛的详情身份、队标、统计/阵容检查；这不代表真实运营商网络可访问。
- Android `LayoutChecks` 的 108 项组合检查已实际运行：320/360dp、1.0/1.3/2.0 字号、浅深主题，覆盖长中英文、表头、五行数据、姓名与数值对齐、比赛卡状态及三位数比分。测试不将构造的极端内容写入应用存储。
- API 35 / Android 15 x86_64 模拟器 `Codex_MyScore_API35`：1080×2400、420dpi、1.0 字号，完整 439 项通过；实际窗口 945×2100（360dp）、1.3 字号，同一 APK 的完整 439 项再次通过。均启用 `network=true details=true lol=true`，包含日历、日期切换 / A → B → A、设置重建、更新校验、九类来源及浅深主题详情交互。日志 `artifacts/android-tests-0.10.1.txt`、`artifacts/android-tests-0.10.1-360dp-large.txt`，对应 `*-runtime.log` 保留具体断言。
- 实际窗口 840×1866（320dp）、2.0 字号：176 项基础运行检查通过，未再次执行全套在线详情；日志 `artifacts/android-tests-0.10.1-320dp-2x.txt`。结束后恢复原始分辨率和 1.0 字号。
- 本版截图：`artifacts/review-0.10.1-normal/`、`artifacts/review-0.10.1-360dp-large/` 各 40 张详情/首页图；LoL 分局图各 8 张位于 `artifacts/review-0.10.1-lol-normal/`、`artifacts/review-0.10.1-lol-360dp-large/`。320dp / 2 倍字号首页和日期 sheet 位于 `artifacts/review-0.10.1-320dp-2x/`。已剔除设备目录中两张旧版 `football-overview` 图，未把它们作为本版证据。
- 验收同时修正三处测试问题：加载行提交后不在 ListView 布局前查找尚未挂载的 TextView；宽度检查使用排除行尾空白的可见行宽；NBA 姓名入口按现有完整姓名查找。主线程断言现会返回测试线程，执行失败报告与 finally 清理，避免断言直接杀死进程、跳过临时日历和偏好恢复。
- 600 次随机 monkey 事件通过（seed 102、80ms throttle，禁用模拟器不支持的 flip / trackball 等事件），无输入失败、应用崩溃或 ANR；日志 `artifacts/monkey-0.10.1-final.txt`、`artifacts/crash-after-monkey-0.10.1-final.txt`、`artifacts/anr-0.10.1.txt`。前一轮 seed 101 存在模拟器 flip 注入拒绝，未作为最终无输入失败结果。
- Mobile Dev 同时尝试 CPU / RSS / Display FPS 的 30 秒采样，包含八次 650ms 首页往返滑动；原始数据 `artifacts/performance-0.10.1.json`，recording ID `6bc27833-3d86-4e3c-87ba-316f20254adb`。CPU 平均 19.48%、峰值 91.86%（100% 为一核）；RSS 首尾 163.41 → 162.24 MiB，仅是本次混合静置/交互样本，不是内存优化基准。FPS 采集失败：`Invalid Perfetto trace stream.`，没有有效 FPS / 卡顿率，不据此声称流畅度达标。

## 包与验证边界

- 本地 APK 已更新为设备修复后通过验收的包：`artifacts/MyScore-0.10.1.apk`，1,214,856 字节；包名 `cn.myscore.app`，versionCode 14、versionName 0.10.1，minSdk 28 / targetSdk 35。SHA-256：`da856030e9151a2686bd22d48e80473d330537a08c8364b538dedfa4e9fa5123`；同目录校验文件已同步。apksigner 验证通过，证书仍为 `e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`。
- 不新增生产依赖；延续原 debug 签名，供个人覆盖安装。没有卸载或清除应用数据。
- 已从模拟器拉回实际安装的 `base.apk`，SHA-256 与上述交付 APK 一致。早期断言直接终止进程遗留的测试日历已按 `myscore-functional-tests` / `LOCAL` 账户限定清理；没有删除其他日历账户。应用最终保持打开，分辨率 1080×2400、字号 1.0。
- Mobile Dev 的 Android 发现、安装、启动、UI 树与截图工具已可用；侧栏 `mobile_open_simulator` 仍返回 Apple Silicon / Xcode 要求，未据此声称侧栏画面正常。本次沿用该插件的 Android 工具和项目自身 ADB instrumentation，没有替换设备后端。
- 没有连接 Redmi K80 Pro；HyperOS、真实运营商网络、120Hz、锁屏/后台日历通知、TalkBack 仍待真机验收。构造内容的布局检查及模拟器操作不能替代真机。小于 336dp 的七列日历仍不能保证每格 48dp 宽度；320dp 的列宽约 45.7dp。

---

# MyScore 0.10.0 深色重设计验证

日期：2026-10-25，北京时间。本节只证明当前发布包的单元测试、静态检查、构建、元信息及签名，不继承旧版的设备运行验收。

## 发布包与改动

- APK：`artifacts/MyScore-0.10.0.apk`，889,484 字节；SHA-256：`d959b9d03cdd96b51bc9d38c65b4c57c6bc5f755764b405a83f99160e07f02f7`。
- 包名 `cn.myscore.app`，versionCode 13、versionName 0.10.0；minSdk 28、targetSdk 35。沿用个人 debug 签名，apksigner 验证通过；证书 SHA-256 与本地 0.9.1 包相同：`e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`。
- 深蓝黑底、冷白文字、冰蓝主操作与淡紫双方对比统一首页、详情、统计表、设置、更新页和底部弹层；新安装默认深色，保留已有外观设置和配套浅色主题。
- 比赛卡显示数据来源与更新时间；继续使用已有回收列表、缓存与原地更新机制。所有赛事 / 球队 / 小局筛选复用共享 chip。
- 主详情和 LOL 小局共用 `Ui.ScoreText`；按当前字体的 22sp 下限测量，完整横排放不下时改为上下比分，保留无障碍比分描述，避免静默裁切。相同比分不重复修改文本。
- 底部弹层统一整体滚动，最大为屏高的 75%；月份日期栏调整左右留白以适配 360dp 宽度，日期格高 48dp。
- `DESIGN.md` 与 `PRODUCT.md` 已记录新视觉语言；没有新增生产依赖。

## 本版本已执行

- JDK 17、Gradle 8.14、AGP 8.11.1 离线构建：`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` 全部通过；日志 `artifacts/build-0.10.0.log`。
- 75 项 JUnit 测试，0 failures/errors；Android Lint 0 errors、20 warnings，未隐藏警告。
- `aapt dump badging` 核对包名、版本及 SDK；`apksigner verify --verbose --print-certs` 验证发布包签名，并与 0.9.1 证书对比。
- 正文、辅助文字、选中态与主按钮的关键配色组合计算对比度均高于 4.5:1；这不等同于实际小字号视觉验收。
- 独立静态复核发现并修复窄屏比分适配缺口，后续源码复核确认修复；`git diff --check` 通过。

## 未验证边界

- 当前会话未加载 Mobile Dev 设备工具，**本版本没有模拟器 / 真机安装运行、截图、字体缩放、TalkBack 或交互验收**。Android 测试 APK 只完成编译，没有运行。不得将下方旧版本的 327 项检查、录屏、截图或性能数字转用于本包。
- 320/360dp 窗口、1.3 倍系统字号、长队名、三位数比分、两行比分切换与 sheet 手势区仍需本版本设备确认。小于 336dp 窗口不能保证七列日历每格宽达到 48dp。
- Redmi K80 Pro、HyperOS 日历 / 后台提醒、真实运营商网络、刷新率与帧率仍待真机验收。

---

# MyScore 0.9.1 切换赛事项目闪烁修复验证

日期：2026-10-25，北京时间。发布版本为 0.9.1；README 与 GitHub Release 使用同一已验证 APK，0.9.0 或以上版本可通过应用内更新覆盖升级。

## 问题与修复

- 录屏逐帧复现：在「全部 → NFL → NBA」切换时，顶部 chip 已经改变，但列表仍短暂保留旧项目内容，随后出现一帧「这个日期没有已确认的比赛」，最后才显示目标项目；整页 root、列表和导航同时重建。
- 根因是 `select()` 在读取目标项目缓存之前先执行 `snapshot = new Snapshot()`，再 `render(false)` 重建整页：空 snapshot 被当成「没有比赛」渲染，缓存回调到达后又重建一次。
- 现在切换项目/日期走 `inPlace` 路径：只重建顶部标题、筛选和日期层，保留 `ScoreList`、`ScoreAdapter` 和底部导航；列表回到顶部后等待数据。
- 已加载的全量 `allSnapshot` 用于本地即时筛选目标项目；缓存返回时直接原地换行。缓存为空时保留本地结果，不先清空。
- 缓存读取后才调用 `load()`；无数据且请求进行中时只显示 11sp「正在读取赛程…」，不再显示「这个日期没有已确认的比赛」。请求全部失败后才显示空状态和失败横幅。
- 缓存/选中数据直接调用 `updateScoreRows()`，不经过滚动延后路径；列表实例、adapter 和滚动容器保持同一对象。

## 构建与测试

- APK：`artifacts/MyScore-0.9.1.apk`，883,452 字节；SHA-256：`6581270411CE802FA136EF950DB801F876D2E47356E270DA3EA703CEBA4F0A2F`。
- 包名 `cn.myscore.app`，versionCode 12、versionName 0.9.1；minSdk 28、targetSdk 35。无新增依赖；沿用 debug 签名，证书 SHA-256 与 0.9.0 相同：`e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`。
- 75 项单元测试通过；`:app:lintDebug` 0 errors、20 warnings；构建日志 `artifacts/build-0.9.1.log`。
- 最终 APK 通过 327 项 Android 运行检查（0.9.0 的 323 项 + 4 项切换项目检查），日志 `artifacts/android-tests-091.txt`。
- 同一 APK 在 1.3 倍系统字号下同样通过 327 项检查；日志 `artifacts/android-tests-091-large-full.txt`。
- 新增检查断言切换 NFL 后 `ScoreList` 和 `ScoreAdapter` 为同一实例、空状态文本未出现、NFL chip 处于 selected；再切回「全部」继续设置页和重建测试。
- 600 次随机 monkey 事件无崩溃/ANR；日志 `artifacts/monkey-091.txt`。
- 0.9.1 录屏：`artifacts/switch-091.mp4`，6 秒/177 帧；`artifacts/switch-091-transition.png` 覆盖全部→NFL→NBA→全部，逐帧未见「这个日期没有已确认的比赛」空状态，列表只在目标数据到达时换成对应项目。
- APK 已用 apksigner 校验，证书与 0.9.0 一致。

## 边界

- 录屏和运行检查在 API 35 / Android 15 x86_64 模拟器上完成，不是 K80 Pro；真机 120 Hz 手感、触控和移动网络仍未验收。
- 本地即时筛选依赖启动后加载过的「全部」快照；若没有全量快照，切换依靠目标项目缓存，读取期间保留当前列表或显示「正在读取赛程…」，不会先显示空状态。
- 本次只修复切换项目的闪烁和错误空状态，不改变 0.9.0 的取数、日历和详情刷新策略。

---
# MyScore 0.9.0 流畅性与稳定性验证

日期：2026-10-25，北京时间。发布版本为 0.9.0；README 与 GitHub Release 使用同一已验证 APK，0.6.0 或以上版本可通过应用内更新覆盖升级。本记录只证明该 APK 在模拟器上的行为，不冒充真机结果。

## 交付与变化

- APK：`artifacts/MyScore-0.9.0.apk`，881,828 字节；SHA-256：`06E77800B95399CCA65D2C202F16BF9C836E508DED95E7CCFE4F2142E35C3A3D`。
- 包名 `cn.myscore.app`，versionCode 11、versionName 0.9.0；minSdk 28、targetSdk 35。无新增第三方依赖；沿用 debug 签名，证书 SHA-256 与 0.8.0 相同：`e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`，可覆盖安装并保留设置与日历关联。
- 比分页从 `ScrollView` 全量视图改为平台 `ListView` 回收容器，新增 `ScoreList`、`ScoreAdapter`、`ScoreRow`；只创建视口内卡片，队标、图标和文本视图随行复用。同一份缓存列表下，页面 View 数从 589 降到 143。
- 刷新不再按整页内容键重建：主线程只比较字段签名，不序列化比赛 JSON；有缓存时等全部来源返回后一次更新，无缓存时仍按来源增量显示。结构不变时直接重绑可见行文本，不调用 `notifyDataSetChanged`，因此滚动位置和分区不变。
- 页面底色改由窗口绘制一次，根容器改为透明；删除重复的全屏底色绘制。Typeface 按进程缓存；`Ui.Icon` 改为可复用；队标按显示尺寸的两倍解码（上限 512px），内存缓存按 URL + 目标尺寸分键，上限 16 MiB，并处理解码 OOM。
- 详情拆成固定框架（返回、比分条、分栏）与正文。`MatchDetail.bodySignature()` 在后台按事实、统计、表格、进程和小局计算内容指纹，排除比分、节次计时、获取时间；比分条原位更新，正文只在内容指纹变化时重建。纵滑、横滑、按压和滚动惯性期间延后重绘。
- 球员表默认先显示 16 人，`显示更多` 每次增加 24 人；排序和筛选仍作用于完整行集合。首次构建 29 人表在模拟器上从约 526 ms 降到约 169 ms（单次观察）。
- 日历 Provider 的枚举、订阅、取消改到独立后台线程；权限回调、订阅状态刷新、目标日历校正仍在主线程收尾。`Sheet.show()` 捕获 Activity 已结束时的窗口异常，避免异步回调在退出后弹窗崩溃。

## 构建与测试

- JDK 17、Gradle 8.14、AGP 8.11.1、compile/target SDK 35，离线构建；未新增生产依赖。
- `:app:testDebugUnitTest`：75 项通过，0 failures/errors。新增 3 项验证比分/计时/获取时间不使正文指纹变化、统计或表格变化会触发重建、缓存往返后指纹一致。
- `:app:lintDebug`：0 errors、20 warnings；未隐藏警告。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest` 通过。
- 最终 APK 在 API 35 / Android 15 模拟器通过 323 项 Android 运行检查，包含九类来源、浅/深主题、日历单场订阅与取消、详情分栏、NBA 筛选/排序/固定姓名列、球员表展开、布局重建与日历回归。日志：`artifacts/android-tests-090.txt`。
- 同一 APK 在 1.3 倍系统字号下再次通过 323 项检查，结束后恢复 1.0；日志：`artifacts/android-tests-090-large-full.txt`。
- 600 次随机 monkey 事件（80 ms 间隔，含滚动、点击、返回键和系统键）后未出现 `FATAL EXCEPTION`、应用 ANR 或崩溃记录；日志：`artifacts/monkey-090.txt`。
- 截图：`artifacts/review-090/`，含浅/深色首页、收起顶部、NBA 详情、16/26 人球员表、日历 sheet、大字号首页，以及刷新前后同位置对比。
- apksigner 校验通过，证书与 0.8.0 一致。

## 性能测量及边界

设备为 API 35 / Android 15 x86_64 模拟器（Codex_MyScore_API35），1080×2400、420 dpi、60 Hz，GPU 为 SwiftShader 软件 GLES；**不是 K80 Pro**。真实在线数据、进程预热和模拟器负载都会变化，以下为单次观察，不是固定负载基准。

- 同协议 650 ms 八次坐标往返滑动（启动后等待 6 秒，三次采样，gfxinfo）：
  - 0.8.0：卡顿帧 13.63% / 16.35% / 18.40%，P50 29 / 29 / 31 ms，慢 UI 线程 24 / 38 / 40，慢 draw 58 / 61 / 73。
  - 0.9.0：卡顿帧 14.29% / 15.42% / 18.73%，P50 29 / 29 / 31 ms，慢 UI 线程 27 / 26 / 35，慢 draw 58 / 61 / 77。
  - 中位数：卡顿帧 16.35% → 15.42%（约 6%），慢 UI 线程 38 → 27（约 29%），P50 与慢 draw 基本不变。SwiftShader 下 GPU 填充仍是瓶颈，单次滑动样本波动明显，不据此宣称真机达到 60/120 fps。
  - 400 ms 短滑动三次：卡顿帧 15.02% / 14.96% / 23.97%；较短的采样更容易受进程预热影响，只作为趋势记录。
  - 原始文件：`artifacts/baseline-reboot-*.txt`、`artifacts/gfxinfo-0.9.0-final2-*.txt`、`artifacts/gfxinfo-0.9.0-final2-400-*.txt`。
- 同一份缓存列表下 Views 589 → 143；PSS 在不同预热/GC 时点为 44–80 MB，0.8.0 配对样本为 104 MB，波动不足以宣称稳定内存降幅，因此只把 View 数作为可复现指标。
- 首页 10 秒刷新在 28 行、25 场数据下日志为 `relaid=false`，主线程约 3 ms；刷新前后截图 `refresh-before.png` / `refresh-after.png` 停在相同滚动位置，只有来源计时变化。早期版本在同样场景会重建整页。
- 详情直播刷新：比分/计时变化时 `detail update ms=0 rebuilt=false`；表格真正变化时约 54 ms 重建正文（模拟器）。球员表从 29 行全量创建约 526 ms 降到默认 16 行约 169 ms。
- 冷启动 `am start -W` 在 1.12–1.41 s 间波动，0.8.0 为 1.16–1.19 s；样本受 ART 编译和模拟器负载影响，本次不宣称启动提速。
- 模拟器在测量期间自行关机，ADB 重连后冷启动同一 AVD（保留应用数据）完成最终测试；该基础设施故障不作为产品崩溃记录。

## 边界

- 未连接 K80 Pro / 澎湃 OS；真机 120 Hz 手感、触控、移动网络、日历通知与后台限制仍未验收。高刷新率窗口提示不等于实测帧率。
- SwiftShader 的 GPU 时间掩盖了列表回收本应带来的部分帧率收益；本记录只报告可复现的 View 数、主线程耗时、刷新稳定性和同设备相对变化。
- 日历异步化只在模拟器 Provider 上验证；系统日历账户同步慢、MIUI 权限弹窗等真实设备行为仍需验收。
- 0.9.0 已发布 GitHub Release `v0.9.0`，README 与 Release 使用同一 `artifacts/MyScore-0.9.0.apk`；0.6.0 或以上版本可通过应用内更新覆盖升级。
- NBA 球员表默认 16 人是性能与可读性的取舍；完整的 26–29 行需要通过「显示更多」再展开一次，不把未显示行当成不存在。

---
# MyScore 0.8.0 全项目详情重设计验证

日期：2026-10-25，北京时间。发布版本为 0.8.0；README 与 GitHub Release 使用同一已验证 APK，0.6.0 或以上版本可通过应用内更新覆盖升级。

## 交付与变化

- APK：`artifacts/MyScore-0.8.0.apk`，1,133,624 字节；SHA-256：`4FB9B7DA93AB955B9C622B67CC28D7C168806BBEC66F4FAABCBB4AB464C654D5`。
- 包名 `cn.myscore.app`，versionCode 10、versionName 0.8.0；minSdk 28、targetSdk 35。无新增第三方依赖，延用原 debug 签名，可覆盖安装并保留设置与日历关联。
- 足球、NBA、NFL、F1、CS2、LoL 统一使用紧凑比分条、原生分栏、分组比赛信息、球队对比与事件进程；LoL 进一步压紧成对阵容、小局计分和目标区。浅色/石墨主题与高刷新率窗口提示延续原有实现。
- NBA 从逐人巨大字段卡改为固定姓名列的统计表：真实来源头像、球队标识、号码、位置及出场状态，双方/单队筛选，数值列升降序，时间/得分/篮板/助攻优先。横向查看投篮、三分、罚球、正负值等；姓名 sheet 保留完整身份及所有统计，不添加 SofaScore 专有评分。
- 足球以首发/替补及球队筛选对照阵容，NFL 按传球/冲球/接球等分类，F1 使用排名与成绩表，CS2 使用全场/地图分类。数值排序保留真实零值、缺失和未出场的区别。
- 裁判问题来自 ESPN 的 `position` 为对象、旧代码直接 `optString` 序列化。现在读取可读职位并翻译为裁判，姓名按职位归组；未知结构不转为 JSON 字符串显示。详情缓存升级至 `details-v4`，避免旧字段继续出现。
- 普通 NBA 四节在常规及 1.3 字号下完整显示，宽 Q 段计时仅用于 F1。表前说明实际可用的排序/横滑/姓名操作；横滑与纵滑都延后后台重绘，保留数值滚动位置。比赛进程每次加载 50 条，可继续查看全部。

## 测试与界面证据

- 72 项单元测试通过，0 failures/errors；新增 10 项验证裁判对象与字符串职位、未知结构与真实零、球员身份/头像/球队元信息、列重排、ID 筛选、缺失值排序、带符号/百分比/计时排序和缓存往返。
- 最终构建通过 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest`。Lint：0 errors、21 warnings，未隐藏警告；日志 `artifacts/build-0.8.0.log`。
- 最终 APK 在 API 35 / Android 15 模拟器通过 321 项 Android 运行检查，包含九类来源真实详情与当前列表、六类原生页面、NBA 排序/按 ID 筛选/固定姓名列、四节布局、裁判无 JSON、完整身份 sheet、横滑时延后重绘和日历回归。日志 `artifacts/android-tests-080.txt`。
- 同一最终 APK 在 1.3 倍系统字号下通过 312 项运行检查，含两主题、完整四节和身份 sheet；结束后恢复字号 1.0。日志 `artifacts/android-tests-080-large.txt`。
- 实际页面使用 ESPN、5EPlay、腾讯/腾竞返回的真实历史赛事，不向 UI 注入模拟球员或比分。横滑回归测试仅令 render key 失效以验证重绘时机，未替换来源数据。
- 原生截图位于 `.impeccable/review/detail080/final-normal/`、`final-large/`、`final-lol-normal/`、`final-lol-large/`；包含 NBA 总览/球员/筛选/球队统计/裁判/身份、足球阵容、NFL 分类、F1 车手/排位和 CS2/LoL 页面。各目录只以本轮指定文件作为验收证据，不将历史遗留文件名当作本轮新截图。
- 首轮独立审查实际检查六类项目的浅/深色及大字号样本；修正四节宽度、横滑重绘、身份字段恢复和表前提示后，定点复核为 `ship`，四项均 resolved。该结论关闭这四项发现，不冒充再次执行全表面审查。DESIGN.md 与 sidecar 已按最终实现同步。
- apksigner 验证通过，证书 SHA-256 与 0.7.0 一致：`e363b1a345efd722269f345ef515f36ad8fc02919134ad08c6d2e79899c35646`。

## 边界

- 验收为手机竖屏模拟器；未连接 K80 Pro / 澎湃 OS，未测真机 FPS、触控或系统日历通知。高刷新率提示不等于实测帧率。
- 本次重做展示与数据解析，未改变 0.7.0 的前台取数策略。实时覆盖依各来源发布时间；历史页面验收不代表持续直播数据验收。没有虚构评分、战术坐标、阵容或实时计时。
- ESPN 部分事件描述和专有字段保留来源英文。球员、队伍与游戏图像仍通过既有 HTTPS allowlist 在运行时加载；没有新增随包栅格图像。

---
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
