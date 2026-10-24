# MyScore 安卓个人版

一个面向 Redmi K80 Pro 的原生 Android 比分与赛程应用。中文界面，北京时间，无广告，无登录，不依赖 Google 服务，不使用厂商推送 SDK。

## 安装

1. 将 `artifacts/MyScore-0.2.0.apk` 传到手机，在文件管理器中打开安装。按系统提示允许本次安装来源。已装 0.1.0 可以直接覆盖升级，保留日历关联。如需自行构建，产物在 `app/build/outputs/apk/debug/app-debug.apk`。
2. 打开应用，在「比赛」页切换足球、NBA、NFL、F1、CS2 或 LOL。赛事来源需要联网，首次加载可能较慢。
3. 在「设置 → 自动同步至日历」允许日历权限，并选择手机上可写入的日历。
4. 在「选择同步赛事」勾选需要写入日历的赛事，在「提前提醒」中设置时间，点「现在同步日历」验证一次。
5. 在系统日历中确认日程已出现，并允许日历发送通知和悬浮提醒。日历事件一旦写入，提醒由系统日历负责。

最低支持 Android 9。当前 APK 是供个人安装验证的 debug 签名版本；升级时保持包名和签名一致。卸载会清除应用设置及比分缓存，已写入系统日历的事件由日历保留。不要在需要保留现有赛事日程关联的情况下先卸载再安装。

## 覆盖与行为

- 足球：英超、欧冠、Carabao Cup、FA Cup。
- NBA、NFL：当前比分、节次与来源计时。
- F1：按练习、排位、冲刺、正赛拆分场次；详情分「总览、车手、成绩」三栏，车手含名次、车队、车号，成绩含圈数、用时、积分与 Q1–Q3。
- CS2：仅 Spirit 主队，使用 5EPlay 战队 ID `csgo_tm_7020`，不匹配 Spirit Academy。
- LOL：仅 Worlds / World Championship，排除其他联赛及 Esports World Cup。
- 电竞展示系列赛比分及来源提供的已结束小局信息；没有提供的地图赢家或回合比分不会推断。
- 前台体育至少间隔 15 秒取数，电竞至少 60 秒；赛程视图缓存 5 分钟。比分和计时均来自来源，不在手机上自行推进比赛钟。
- 昨天/今天/明天按 Asia/Shanghai 划分。凌晨的美洲比赛会合并前一个来源日期后再次按北京时间筛选。
- 请求失败时显示错误和真实缓存的时间；不会加载模拟比赛。

## 队标与比赛详情

浅色模式以白色为主，深色模式以石墨深灰为主，可跟随系统或在设置切换。首页保留紧凑的队标／队名／比分列表，点击比赛进入完整详情页，系统返回键回到列表。

- 足球四项赛事：球场、阵型、首发与替补、换人状态、双方技术统计、进球/红黄牌/换人等关键事件。
- NBA：分节比分、球队统计、球员时间/得分/篮板/助攻等完整数据表、逐回合进程。
- NFL：分节比分、球队统计、传球/冲球/接球/防守等球员数据、得分进程。
- F1：总览、车手、成绩三栏；车手含名次、车队、车号，成绩含完成圈数、成绩/用时、积分及来源提供的 Q1/Q2/Q3，另有比赛周末赛程。当前不提供逐圈事件流或车载遥测。
- CS2 Spirit 主队：赛制、地图比分、攻守半场比分，全场及各图的 K/D/A、ADR、KAST、Rating。地图比分仅展示已结束地图。
- LOL Worlds：赛制与阶段、已结束小局胜方及用时、红蓝方阵容、英雄、K/D/A、补刀、伤害、经济、团队资源和出装。只展示已结束小局，不用历史队员推算本场未发布阵容。

上述信息以每场来源实际提供为准：未开赛阵容可能尚未发布，较低覆盖的比赛可能缺少统计。没有提供的字段显示暂无或 `—`，不补零，不用示例数据。三到五列的数据表按屏宽适配，列更多时改为逐人/逐车手字段块，所有列默认可见。ESPN 的部分事件描述和专有字段保留来源英文。

队标使用 ESPN、5EPlay、英雄联盟官网提供的真实图片；下载失败时退回队名缩写，刷新可重试。图片和详情均有本地缓存。F1 是车手排名赛事，不在比赛首页虚构两队队标。

## 日历规则

日历同步默认关闭。开启并选择目标日历后，系统后台任务约每小时检查一次，导入北京时间明天的已确认赛程，也补导当天尚未开始的比赛。默认开赛前 15 分钟提醒。

只维护带有 MyScore 包名、比赛唯一标记和本机事件关联的日程；不修改其他个人事件。同一比赛重复同步不新增，改期更新原日程，来源明确取消时删除原日程，延期/时间待定时撤销旧提醒。已导入但离开七天列表的未来比赛会按保存的来源身份单独复核，避免改到更远日期后遗留旧提醒。身份复核失败时保留原日程并计入失败数；未在列表中出现不等于取消。日历中结束时间为估算，说明中会标注。

后台同步由 Android JobScheduler 执行并在重启后保留，可能被省电策略或断网延后；打开应用时补同步。关闭自动同步后已导入的日程仍保留。更换目标日历或同步赛事范围后，需要同步成功才能迁移或撤销对应的未来日程。

## 数据源及网络边界

当前体育接入 ESPN 公开网站比分、summary 和 F1 场次统计接口，CS2 接入 5EPlay 公开赛程和比赛详情，LOL 接入腾讯英雄联盟赛事官网赛程及官网使用的腾竞小局详情。它们不是本项目签约的数据服务，可能有延迟、字段变化或访问限制。无需用户账号或 API Key；LOL 网页的公开客户端配置从官网在运行时读取，仅保存在内存，不把凭据硬编码到 APK，不展示接口中附带的赔率。LOL 同时显示源数据自带的发布时间，详情另显示获取时间，避免把下载时间当成比分更新时间。

HLTV / SofaScore / BO3.gg 在未配置代理的开发环境请求中出现过 403，不作为默认来源。5EPlay 当前全局列表最多检查前 100 场，结合 Spirit 主队的未来/历史接口获取赛程；遇到分页限制时保留未知范围。开发环境访问成功不能代表所有中国内地移动运营商、地区、时段均稳定可用；K80 Pro 真机和移动网络验收仍需进行。

## 开发与验证

使用 JDK 17 和 Android SDK 35，配置 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT`。没有第三方生产依赖；JUnit 和 org.json 仅用于本地测试。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。可显式运行实时接口检查：

```powershell
.\gradlew.bat :app:verifyLiveSources
```

实时检查单独记录结果，不与确定性的单元测试混在一起。如构建工具需要网络代理，使用 Java/Gradle 的代理参数；不要把代理密码或密钥写入项目。

原生集成测试使用一个临时本地日历验证去重、改期、取消、延期后恢复、提醒设置和个人事件保护，并检查页面切换。测试运行器限制在 Android 模拟器上执行，结束后清理临时日历并恢复设置。

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell pm grant cn.myscore.app android.permission.READ_CALENDAR
adb -s emulator-5554 shell pm grant cn.myscore.app android.permission.WRITE_CALENDAR
adb -s emulator-5554 shell am instrument -w cn.myscore.app.test/cn.myscore.app.SmokeInstrumentation
```

增加 `-e network true -e details true` 可在模拟器中检查九类真实详情、队标与双主题分栏；测试使用已知真实比赛 ID，不在界面注入模拟数据。截图写入测试设备应用外部文件目录的 `review-v2/`。默认单元测试不请求网络。

验收证据与未验证事项见 `VERIFICATION.md`。源码仓库为 [n1ght-ow/myscore](https://github.com/n1ght-ow/myscore)（公开）。仓库不含 `artifacts/`、构建产物、设计工具缓存和原始接口抓包，克隆后需按上文自行构建 APK。
