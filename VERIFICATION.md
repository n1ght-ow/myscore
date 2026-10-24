# MyScore 0.3.0 验证记录

日期：2026-10-24，北京时间。

## 交付

- APK：`artifacts/MyScore-0.3.0.apk`，371,068 字节；SHA-256：`8ADC3DFACF016EF7224BB56148598E8D2576AF85A45C37C0222B150D4CE5AFA2`。
- `apksigner verify` 通过；Android debug 签名。包名 `cn.myscore.app`，versionCode 3、versionName 0.3.0，minSdk 28、targetSdk 35；已装 0.1.0 / 0.2.0 可覆盖升级。
- 本次发布已提交 main 并发布 GitHub Release `v0.3.0`；APK 作为 release asset 上传。

## 0.3.0 本次变化

- 日历改为单场订阅：比赛卡右侧「订阅」/「已订阅」药丸，写入系统日历并提前 **5 分钟**提醒；不再按赛事项目批量导入。
- 订阅状态写入本地链接；重复订阅不新增事件，改期更新原事件，取消只删除自己的事件；来源取消删除，延期/时间待定撤销旧提醒。
- 日历选择器过滤不可写/不可见日历，优先本机日历；把 `calendar_displayname_xiaomi` 这类资源键显示为「小米日历」，数字账户改为「小米账户/本机」，不再直接展示原始键值。
- 首选 `METHOD_ALERT` 提醒，日历不接受时回退 `METHOD_DEFAULT`，仍保持 5 分钟。
- 设置页重做为单场订阅：选择日历、已订阅列表、检查已订阅比赛、打开系统日历与后台说明；移除项目级开关、提醒时间和赛事多选。
- 所有二级菜单改为统一底部 sheet：日历选择、已订阅列表、外观、来源状态、订阅确认、同步结果；不再使用平台 AlertDialog。
- 启动改为异步读缓存 + 按来源增量渲染：先出标题和缓存，某个来源完成就显示该来源，不再等全部来源；网络超时缩短为 6s/8s，并发提高到 6 个来源。
- 版本 0.3.0，header 显示 0.3.0。

## 构建与确定性测试

- JDK 17，Gradle 8.14，Android Gradle Plugin 8.11.1，compile/target SDK 35。
- `:app:testDebugUnitTest`：33 项通过，0 失败。
- `:app:lintDebug`：通过，0 errors、8 warnings（不声称零警告）。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest` 通过；构建日志 `artifacts/build-0.3.0.log`。

## 启动测量

Android 15 / API 35 x86_64 Google APIs 模拟器，Pixel 7 配置，1080×2400、420dpi：

- 冷启动（`pm clear` 后无缓存，两次测量）：Activity TotalTime 1516 / 2006ms；首个来源 1015 / 1501ms；全部来源 3281 / 4283ms。
- 有缓存进程重启：Activity TotalTime 1956ms；首个来源 318ms；全部来源 1463ms。
- 首屏先渲染缓存；来源完成一个追加一个，不等待全部来源。网络波动会影响批次完成时间。原始记录：`artifacts/startup-0.3.0.txt`。

## 实际 Android 运行

- Android 15 / API 35 x86_64 Google APIs 模拟器，Pixel 7 配置，1080 × 2400、420 dpi；不是 K80 Pro 真机。
- **206 项原生检查通过**（`artifacts/android-tests-030-full.txt`）。覆盖九来源真实详情、F1 三栏/车手 4 列/成绩 Q1–Q3、无横向表格提示、足球阵容中文位置、页面切换与系统返回。
- 日历检查（测试临时本地日历）：单场订阅只创建一个事件；提醒为 5 分钟；重复订阅不新增；改期更新原事件并保留 5 分钟；来源暂时不可用保留事件；延期删除提醒、恢复后重新写入 5 分钟；未订阅比赛不自动导入；取消删除自己的事件；陈旧链接不会删除个人事件。
- 独立 CalendarProvider 查询也看到 `minutes=5, method=1` 的提醒记录；记录见 `artifacts/calendar-reminders-0.3.0.txt`。
- 主截图 24 张：`.impeccable/review/v3-030/`，首页 + 九类详情，浅/深成对。
- 补充截图：`.impeccable/review/v3-030/manual/`：浅/深首页单场订阅药丸、已订阅状态、日历选择 sheet、外观 sheet、设置页、订阅列表与取消确认。

## 数据源、网络与边界

- 模拟器未设置全局 `http_proxy`；九个来源在实际应用进程内读取成功。
- 体育接入 ESPN；CS2 接入 5EPlay；LOL 接入腾讯英雄联盟赛事官网及腾竞小局数据。无需账号或 API Key，不硬编码凭据。
- HLTV / SofaScore / BO3.gg 未作为默认来源。
- 数据限制：ESPN 事件正文保留来源英文；F1 无逐圈事件流/遥测；LOL 仅已结束小局；未发布阵容允许缺失；缺失字段显示未知或「—」，不补零、不推断。

## 真机反馈与未完成验收

- 用户提供的 Redmi K80 Pro 0.2.0 截图显示：日历选择出现 `calendar_displayname_xiaomi · 1506093879`，外观菜单是平台 AlertDialog。0.3.0 已针对这两点改为可读日历名与统一 sheet，但**尚未在该真机上复测**。
- 仍需 K80 Pro 验证：小米/澎湃 OS 的日历通知与后台限制、移动运营商网络、实际日程与悬浮提醒、关闭代理后的取数。
- 日历选择器中的“钱包提醒/Weibo”等第三方日历仍会列出（可写账户），只是本地/小米日历排前面；不做强制过滤以免误删用户可用日历。
- 单场订阅的日历事件保留历史；比赛结束后不删除事件，只撤销提醒。取消订阅删除事件。

## 0.2.0 历史记录

日期：2026-10-24，北京时间。

### 交付

- APK：`artifacts/MyScore-0.2.0.apk`，336,993 字节；SHA-256：`2DBBCDCA31382C6B0DDB9D47ADED50B71785B455F9FEDD0B6C48158EB1F3935F`。
- 白色/石墨单色界面、真实队标、完整 DetailActivity、F1 三栏、移动端数据表；33 JVM / 204 Android checks。

### 验证要点

- 首轮 finish review 判 fix，三项 material（宽表隐藏列、足球位置半翻译、设置/赛程/错误/大字号缺证据）修复后复评 ship；R1–R3 polish（F1 首列、筛选箭头、时间文案）修复后复评 ship。
- 24 张主截图 `.impeccable/review/v3-final/` 与 15 张补充截图 `.impeccable/review/v3-supplement/`。

## 0.1.0 历史记录

- JDK 17，Gradle 8.14，AGP 8.11.1，compile/target SDK 35；20 项 JVM 单元测试；49 项原生检查。
- APK `artifacts/MyScore-0.1.0.apk`，189535 字节；SHA-256 `5CDC094739F8BA1414D10656A0CE270CD59862B7E84B0E008969196E19C82105`。
- 初版绿色世界，已被用户明确拒绝并由 0.2.0 替换。

不把模拟器中的一次访问成功等同于全国、所有网络和所有时间均可用。
