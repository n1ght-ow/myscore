---
name: MyScore
description: 白色与石墨色的原生 Android 比分节目单，真实队标与来源详情的单色界面
colors:
  primary-light: "#191A1D"
  primary-dark: "#F5F5F6"
  on-primary-light: "#FFFFFF"
  on-primary-dark: "#18191B"
  background-light: "#FFFFFF"
  background-dark: "#18191B"
  surface-light: "#F6F6F7"
  surface-dark: "#222427"
  text-light: "#191A1D"
  text-dark: "#F5F5F6"
  muted-light: "#656870"
  muted-dark: "#B0B2B8"
  tonal-light: "#EBECEF"
  tonal-dark: "#34363A"
  line-light: "#E6E7EA"
  line-dark: "#383A3F"
  error-light: "#A8322D"
  error-dark: "#FFC3BF"
  crest-backing: "#FFFFFF"
typography:
  headline:
    fontFamily: "sans-serif-medium"
    fontSize: "24sp"
    fontFeature: "tnum"
  score:
    fontFamily: "sans-serif-medium"
    fontSize: "28sp"
    fontFeature: "tnum"
  score-row:
    fontFamily: "sans-serif-medium"
    fontSize: "20sp"
    fontFeature: "tnum"
  title:
    fontFamily: "sans-serif-medium"
    fontSize: "22sp"
    fontFeature: "tnum"
  section:
    fontFamily: "sans-serif-medium"
    fontSize: "16sp"
    fontFeature: "tnum"
  team:
    fontFamily: "sans-serif-medium"
    fontSize: "15sp"
    fontFeature: "tnum"
  body:
    fontFamily: "sans-serif"
    fontSize: "14sp"
    fontFeature: "tnum"
  action:
    fontFamily: "sans-serif-medium"
    fontSize: "14sp"
    fontFeature: "tnum"
  supporting:
    fontFamily: "sans-serif"
    fontSize: "13sp"
    fontFeature: "tnum"
  metadata:
    fontFamily: "sans-serif"
    fontSize: "12sp"
    fontFeature: "tnum"
  timestamp:
    fontFamily: "sans-serif"
    fontSize: "11sp"
    fontFeature: "tnum"
rounded:
  crest: "8dp"
  table: "14dp"
  card: "12dp"
  date: "12dp"
  container: "14dp"
  detail-score: "16dp"
  sheet: "22dp"
  pill: "24dp"
spacing:
  small: "8dp"
  row: "10dp"
  medium: "12dp"
  field: "16dp"
  page: "20dp"
  section: "22dp"
  spacious: "28dp"
components:
  button-primary:
    backgroundColor: "{colors.primary-light}"
    textColor: "{colors.on-primary-light}"
    typography: "{typography.action}"
    rounded: "{rounded.pill}"
    padding: "12dp 16dp"
  button-primary-dark:
    backgroundColor: "{colors.primary-dark}"
    textColor: "{colors.on-primary-dark}"
    typography: "{typography.action}"
    rounded: "{rounded.pill}"
    padding: "12dp 16dp"
  button-tonal:
    backgroundColor: "{colors.tonal-light}"
    textColor: "{colors.primary-light}"
    typography: "{typography.action}"
    rounded: "{rounded.pill}"
    padding: "12dp 16dp"
  button-tonal-dark:
    backgroundColor: "{colors.tonal-dark}"
    textColor: "{colors.primary-dark}"
    typography: "{typography.action}"
    rounded: "{rounded.pill}"
    padding: "12dp 16dp"
  filter-selected:
    backgroundColor: "{colors.primary-light}"
    textColor: "{colors.on-primary-light}"
    typography: "{typography.action}"
    rounded: "{rounded.pill}"
    padding: "0dp 14dp"
  filter-selected-dark:
    backgroundColor: "{colors.primary-dark}"
    textColor: "{colors.on-primary-dark}"
    typography: "{typography.action}"
    rounded: "{rounded.pill}"
    padding: "0dp 14dp"
  filter-idle:
    backgroundColor: "{colors.background-light}"
    textColor: "{colors.muted-light}"
    typography: "{typography.action}"
    rounded: "{rounded.pill}"
    padding: "0dp 14dp"
  filter-idle-dark:
    backgroundColor: "{colors.background-dark}"
    textColor: "{colors.muted-dark}"
    typography: "{typography.action}"
    rounded: "{rounded.pill}"
    padding: "0dp 14dp"
  date-selected:
    backgroundColor: "{colors.tonal-light}"
    textColor: "{colors.primary-light}"
    typography: "{typography.action}"
    rounded: "{rounded.date}"
  date-selected-dark:
    backgroundColor: "{colors.tonal-dark}"
    textColor: "{colors.primary-dark}"
    typography: "{typography.action}"
    rounded: "{rounded.date}"
  subscribe-icon:
    backgroundColor: "{colors.tonal-light}"
    textColor: "{colors.primary-light}"
    rounded: "{rounded.pill}"
    width: "48dp"
    height: "48dp"
  subscribe-icon-dark:
    backgroundColor: "{colors.tonal-dark}"
    textColor: "{colors.primary-dark}"
    rounded: "{rounded.pill}"
    width: "48dp"
    height: "48dp"
  subscribe-icon-selected:
    backgroundColor: "{colors.primary-light}"
    textColor: "{colors.on-primary-light}"
    rounded: "{rounded.pill}"
    width: "48dp"
    height: "48dp"
  subscribe-icon-selected-dark:
    backgroundColor: "{colors.primary-dark}"
    textColor: "{colors.on-primary-dark}"
    rounded: "{rounded.pill}"
    width: "48dp"
    height: "48dp"
  sheet:
    backgroundColor: "{colors.surface-light}"
    textColor: "{colors.text-light}"
    rounded: "{rounded.sheet}"
    padding: "20dp"
  sheet-dark:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.text-dark}"
    rounded: "{rounded.sheet}"
    padding: "20dp"
  match-card:
    backgroundColor: "{colors.surface-light}"
    textColor: "{colors.text-light}"
    rounded: "{rounded.card}"
    padding: "10dp 12dp"
  match-card-dark:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.text-dark}"
    rounded: "{rounded.card}"
    padding: "10dp 12dp"
  calendar-summary:
    backgroundColor: "{colors.tonal-light}"
    textColor: "{colors.text-light}"
    rounded: "{rounded.container}"
    padding: "16dp"
  calendar-summary-dark:
    backgroundColor: "{colors.tonal-dark}"
    textColor: "{colors.text-dark}"
    rounded: "{rounded.container}"
    padding: "16dp"
  data-table:
    backgroundColor: "{colors.surface-light}"
    textColor: "{colors.text-light}"
    rounded: "{rounded.table}"
    padding: "0dp"
  data-table-dark:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.text-dark}"
    rounded: "{rounded.table}"
    padding: "0dp"
  detail-score-header:
    backgroundColor: "{colors.surface-light}"
    textColor: "{colors.text-light}"
    rounded: "{rounded.detail-score}"
    padding: "12dp"
  detail-score-header-dark:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.text-dark}"
    rounded: "{rounded.detail-score}"
    padding: "12dp"
  navigation-selected:
    backgroundColor: "{colors.tonal-light}"
    textColor: "{colors.primary-light}"
    typography: "{typography.metadata}"
    padding: "10dp 0dp"
  navigation-selected-dark:
    backgroundColor: "{colors.tonal-dark}"
    textColor: "{colors.primary-dark}"
    typography: "{typography.metadata}"
    padding: "10dp 0dp"
  crest-frame:
    backgroundColor: "{colors.crest-backing}"
    rounded: "{rounded.crest}"
---

# Design System: MyScore

## Overview

**Creative North Star: "单色比赛节目单"**

MyScore 像一份只印黑、白与灰的比赛节目单：白色纸张承载浅色模式，石墨表面承载深色模式。赛事、对阵和比分建立阅读顺序，来源与数据时间交代记录依据。比分、选中态和主操作使用同一支"墨"，因此界面没有品牌色块，也没有装饰性插画；彩色来自来源提供的真实队标、人物与游戏图像，以及错误提示的红色。

界面沿用 Android 平台字体、原生控件和系统返回手势。视觉重点来自字号对比、对齐的比分轴、留白与表面明暗，而不是阴影、描边或渐变。旧版的绿色球场导视世界已被用户明确拒绝；本文记录从 0.2.0 起采用、在 0.3 系列加入单场订阅与统一 sheet、在 0.4.0 合并比赛/赛程并加入任意日期与紧凑可收起顶部的白/石墨单色世界。

**Key Characteristics:**
- 白色浅色模式、石墨深色模式的单色外壳，主题成对切换。
- 列表保持队名在左、比分在右；详情以双方居中比分条和紧凑比较表承载更多数据。
- 真实来源队标，白色底板保证深色队标在两种主题下都可读。
- 每张比赛卡和详情页都保留来源与数据时间。
- 比赛卡上的日历订阅图标把单场比赛写入日历，提前 5 分钟提醒。
- 详情是完整原生 Activity，按运动显示 3–4 个分栏承载阵容、统计与进程。
- 所有二级菜单使用同一套底部 sheet，不出现平台 AlertDialog。
- 平坦的自绘表面，无自定义阴影；触控目标不小于 48dp。

## Colors

调色板只有墨黑、纸白和一组中性灰；红色只用于错误。frontmatter 中 `-light` / `-dark` 是成对主题角色，不能交叉混用；`crest-backing` 在两种主题中共用。

### Primary
- **墨黑 / 雪白**（`primary-light` / `primary-dark`）：标题、比分、选中筛选、主按钮、刷新图标和详情分栏指示线。它同时承担"文字色"与"主操作色"两种语义，保证单色界面里最高对比度始终留给比分和当前状态。
- **主色上的文字**（`on-primary-*`）：只落在实心 `primary` 表面上；浅色为纸白字，深色为石墨字。
- **柔灰选中面**（`tonal-*`）：选中日期、次级按钮、日历摘要、导航选中块和数据表以外的分组容器。

### Neutral
- **页面底色**（`background-*`）：应用根背景、未选筛选和日期的底色。
- **内容表面**（`surface-*`）：比赛卡、设置卡、数据表和底部导航。
- **正文**（`text-*`）：队名、比分、统计数值与标题。
- **辅助文字**（`muted-*`）：赛事名、状态、来源、时间、说明和表头。
- **分隔线**（`line-*`）：比赛卡之间的列表边界、导航上方细线和表格行间线。
- **队标底板**（`crest-backing`）：固定白色，仅作为队标容器，不参与主题反转。

错误使用 `error-*`：连接失败横幅、旧进行中记录、详情加载失败。红色不是第二种强调色，也不用于普通进行中状态。

**The Monochrome Programme Rule.** 界面只使用黑、白与中性灰；颜色只能来自来源真实队标、人物与游戏图像和 `error-*`。不得重新引入绿色、品牌强调色或装饰渐变。

**The Theme Pair Rule.** 切换外观时整体切换主题角色；不在深色表面沿用浅色文字、队标底板以外的固定色块或浅色选中面。

## Typography

标题、正文与操作全部使用 Android `sans-serif` 家族；强调使用 `sans-serif-medium` 字面，而不是额外加粗。平台实际字形与回退由设备提供，不下载字体。`Ui.text()` 统一启用等宽数字特性 `tnum`，并关闭额外字体内边距；没有自定义行高、字距或全大写转换。

### Hierarchy
- **Headline**（22sp medium）：首页"我的比赛"和设置页"设置"。
- **Score**（28sp medium）：详情紧凑比分条的比分；没有比分时用 24sp 的 "VS" 占位。
- **Score row**（20sp medium）：列表比赛卡右侧的比分列，保持数字末端对齐。
- **Title**（22sp medium）：保留标题角色；F1 详情赛事标题、详情空状态和日历摘要标题使用 18sp 的局部字号。
- **Section**（16sp medium）：详情分组标题、设置分组标题；列表分区标题用 14sp medium。
- **Team**（15sp medium）：比赛卡队名；详情比分条队名为 12sp medium。
- **Body / Action**（14sp regular / medium）：说明正文使用 regular；按钮、日期和筛选使用 medium。
- **Supporting**（13sp regular）：设置说明、详情正文和表格数值；详情分组标题为 15sp medium，身份栏姓名为 12sp medium、副行为 11sp。
- **Metadata**（12sp regular）：赛事名、状态、卡底来源、表头副行。
- **Timestamp**（11sp regular）：数据获取时间、比赛卡底部来源、详情比分条开赛时间、表头和表格操作提示。11sp 是本系统的最小字号，不用于正文。

**The Live Type Rule.** 文字使用 sp 并随系统字号缩放；容器由内容决定高度。大字号下只能换行或滚动，不能裁切、缩小字号或把比分挤出屏幕。

## Layout

原生竖向结构依次是：顶部标题、赛事筛选、日期条（仅比赛页）、可滚动正文和底部导航。比赛与赛程合并为一个页面，不再有独立赛程入口。首页正文水平边距为 16dp，详情页同为 16dp；首页正文上方 4dp、下方 24dp，详情页上方 12dp、下方 24dp。状态栏、导航栏和显示缺口通过窗口 insets 留空；应用自绘内容不进入系统栏区域。

顶部标题行使用 20dp 左、12dp 上、12dp 右、6dp 下的内边距：首页为 22sp「MyScore」，不再显示「北京时间」提示；设置页保留 11sp 版本说明。刷新按钮是 48dp 方形触控区。赛事筛选是横向滚动行，chip 最小宽 56dp、最小高 48dp，水平内边距 14dp、间距 8dp；右侧固定 48dp 的 chevron 触控区（描述"更多赛事筛选"），独立占位、不覆盖 chip，内容不溢出或滚到末尾时只隐藏箭头并保留占位；选中的 chip 会自动滚入可视区，避免被右缘裁成半圆。

日期条是 48dp 高的单行：左侧前一天 48dp、右侧后一天 48dp，中间是可点开的日期 pill（`tonal` 底、15sp medium、左右 6dp 间距）；不是今天的日期在右侧追加"今天"文字按钮。点日期 pill 打开自绘月份 sheet：月份标题加前后月 48dp 箭头、周一到周日表头、5–6 行日期格（42dp 高，选中为 `primary` 圆、今天为 `tonal` 圆），取消使用 tonal 全宽按钮。可任意前后翻月和选日，不限制前后一天或三天。

标题、筛选和日期组成一个覆盖在列表上的顶部区域。列表顶部留出与顶部区域等高的内边距；上滑时顶部区域随滚动增量向上移出（跟手消失），下滑时立即回到 0（不带滑动动画，瞬间出现）。顶部区域使用不透明页面底色，收起后列表内容占据整屏；切换标签或日期时顶部区域回到展开状态。

首页在比分列表前不再插入说明行；只有正在更新时才出现 11sp 的"正在更新比赛…"。有缓存时先稳定显示缓存，来源全部返回后只重绘一次；无缓存时才按来源逐步显示。

比赛卡占满正文宽度，以内容决定高度，卡片间距 6dp。卡内是：赛事与状态行、两行队名比分（F1 为标题加前二名）、底部比赛时间。队标 25dp，与队名间隔 10dp；队名取得剩余宽度，比分最小宽 32dp、右对齐并保留 12dp 起始间隔。所有比赛卡在时间右侧保留 48×48dp 的日历订阅触控区：各状态均无常驻圆底，以加号/勾选和主色/辅助色区分。列表按状态分区，分区标题前 18dp、后 10dp。

详情页顶部为 48dp 返回与刷新按钮、16sp 居中赛事名；其下是 16dp 圆角的紧凑比分条：两侧 32dp 队标及 12sp 队名，中间 28sp 比分、12sp 状态与 11sp 开赛时间。LoL 系列比分与正文的小局击杀、来源比赛用时分开。比分条下方是等宽原生分栏：足球/NBA/NFL 为"总览、阵容/球员、统计、进程"，CS2 为"总览、选手、统计、小局"，LoL 为"总览、比赛、统计"，F1 为"总览、车手、成绩"三栏。每项最小高 48dp，选中项在文字下显示 2dp、28dp 宽指示线；来源与获取时间放在分栏下正文首行。

详情数据表使用 14dp 圆角表面，固定左侧身份栏，右侧统计列横向滚动；表头 48dp、数据行 56dp，随大字号增加高度与列宽。球员页在表格前显示类别、双方/单队筛选、人数与操作提示；横向操作计入刷新延后判断，避免手势中替换表格。正常字号的 NBA 四节分表完整容纳 Q1–Q4；其余密集统计保留横向浏览，不再变成逐人字段卡。

底部导航两等分，23dp 图标、图文间距 5dp、上下内边距各 10dp，顶部 1dp 分隔线；当前项使用整格 `tonal` 背景，不是浮动圆角胶囊。所有触控目标最小 48dp。应用只实现手机竖屏；没有平板分栏或网页断点。

**The Fixed Score Column Rule.** 列表的队名/车手在左、比分/名次在右，比分列右对齐；详情使用双方围绕比分的紧凑对阵条，统计表保持身份栏固定与数值列对齐。

**The Comparable Row Rule.** 密集详情保持一人一行、身份栏固定、统计列横向可达；表前说明可用的排序与横向操作，点姓名打开完整身份和全部来源字段。不得用逐人字段卡取代横向比较。

**The Moving Header Rule.** 标题、筛选和日期是一层覆盖在列表上的 app bar；上滑时按滚动增量跟随移出，下滑时立即回到 0，不添加回弹动画或第二层浮动标题。

**The One List Rule.** 比赛与赛程合并为一个比赛页；不再提供内容重复的赛程入口，日期切换在同一页完成。

**The Any-Date Rule.** 日期使用单行日期条和自绘月份 sheet，可任意前后翻月选日；不把日期限制为昨天/今天/明天，也不使用平台日期对话框。

**The Single-Match Rule.** 日历只按单场比赛订阅；不在设置里按赛事项目批量导入。每场订阅写入一个事件，开赛前 5 分钟提醒，取消时只移除该事件。

**The One Sheet Rule.** 应用内所有二级菜单——日历、订阅、外观、来源、确认、结果——使用同一套底部 sheet；不得混用平台 AlertDialog 或系统列表对话框。

**The Source Lane Rule.** 比赛卡底部和详情页顶部始终保留来源与数据时间；缓存、旧进行中或失败状态必须用文字说明，不能用空白、颜色或省略来代替。

## Elevation & Depth

应用自绘的卡片、按钮、导航、订阅图标和表格没有阴影、描边或 elevation。深度只来自三种表面：页面底色、`surface` 内容表面、`tonal` 选中/摘要表面，以及 1dp 的 `line` 分隔线。应用内的二级菜单是自绘 sheet，不使用平台 `AlertDialog`；系统权限对话框和跳转到系统日历的界面仍由 Android 决定，不从截图反推为应用 token。

**The Flat Surface Rule.** 不新增装饰性阴影、渐变、玻璃或描边。需要分层时先换表面色，再考虑间距，最后才考虑分隔线。

## Shapes

比赛卡与日期 pill 使用 12dp 圆角；设置卡、空状态容器、详情面板和数据表使用 14dp，详情比分条使用 16dp；按钮、筛选 chip 和图标触控区使用全圆 24dp；队标底板使用 8dp；统计比例条使用 2dp 圆角。底部导航的选中块保持直角，延续两等分的连续结构。内容表面没有边框，`line` 只用于实际的列表边界、表格行和导航上缘。

## Components

### Buttons

主按钮使用 `primary` 底与 `on-primary` 字，次按钮使用 `tonal` 底与 `primary` 字。两者都是 `action` 字体、48dp 最小高、12dp 上下与 16dp 左右内边距、24dp 全圆角。日历容器内的按钮随父级拉满宽度。交互使用原生 `RippleDrawable`，波纹为当前 `primary` 的 RGB 加 alpha `0x25`。控件可聚焦；没有自定义焦点环、悬停态或自定时长动画。

### Chips

赛事筛选 chip 选中时是实心 `primary` 与 `on-primary` 字；未选时直接使用页面底色与 `muted` 字。两者同为 14sp medium、48dp 高、24dp 圆角。选中状态同时写入原生 selected 与中文可访问描述（"已选中" / "筛选赛事"）。筛选行右侧固定 48dp 的 chevron 触控区（描述"更多赛事筛选"），独立占位、不覆盖 chip，滚到末尾时只隐藏箭头并保留占位；选中 chip 自动滚入可视区。日期使用 `tonal` 选中面而非实心主色。

### Cards / Containers

比赛卡由赛事/状态、两行队名比分、底部来源时间与订阅按钮组成，使用 `surface`、12dp 圆角、10dp 垂直与 12dp 水平内边距，无描边无阴影。整卡可点击进入详情，波纹覆盖整卡；订阅按钮独立消费点击，不打开详情。进行中状态不会仅靠颜色表达：状态文字保留，数据超过两分钟时追加"缓存"文字，旧进行中记录追加 error 色的"上次记录为进行中，当前状态待刷新"。

设置卡使用 `surface` 与 14dp 圆角，内部承载日历选择、已订阅列表、检查按钮和说明。详情面板和数据表放在 14dp 圆角的 `surface` 中，行间使用 `line`；比较面板使用 14dp 水平、12dp 垂直内边距，左右数值夹住居中指标名，比例条只显示可比较的来源数值。

### Subscription Icon

比赛卡底部右侧保留 48×48dp 的透明触控区，图标为 22dp 日历线框，不显示外围圆底。未订阅但可订阅时为 `primary` 日历加号；已订阅时为 `primary` 日历勾选；不可订阅时为 `muted` 日历加号。触控时仍有短暂波纹反馈。图标本身只表达状态，文字只存在于可访问描述；点击未订阅先检查权限与目标日历，点击已订阅先弹出统一确认 sheet，点击不可订阅用 sheet 说明原因。

### Detail Header

详情比分条在两侧放置队标与可换行队名，中间垂直排列比分、状态和开赛时间；左右外边距 16dp、内部 12dp。F1 用 18sp 赛事标题和 12sp 状态/开赛时间替代对阵。没有比分时显示 "VS"，不伪造 0:0。返回与刷新按钮固定 48dp 触控区，系统返回手势与按钮行为一致。

LoL 系列赛头沿用统一比分条；正文小局面板使用 30dp 队标、24sp 击杀比分与 14sp 来源比赛用时。正文状态说明显示小局获取与赛程发布时间；小局未提供时显示赛程获取时间并明确说明。「来源与更新时间」sheet 保留来源、获取时间、赛程发布时间及可用的小局获取时间，时间显示月日与时分秒。不得把这些时间与小局来源比赛用时混为一项，也不按本地时间推算进行中计时。

### Tabs

详情分栏等宽，14sp medium 文字，最小高 48dp；选中项文字用 `text` 并在下方绘制 2dp `text` 指示线（宽 28dp），未选项文字用 `muted` 且指示线取页面底色。足球/NBA/NFL 为总览、阵容/球员、统计、进程；CS2 的进度分栏显示"小局"；LoL 为总览、比赛、统计三栏，「比赛」与「统计」均可按小局切换，「总览」的小局结果可直接打开对应比赛；F1 只有总览、车手、成绩三栏，不显示空的进程栏。图标不使用 Unicode 字符代替。

### Tables

表头为 11sp `muted` medium，数值为 13sp `text`；当前排序列表头使用 `tonal` 和方向图标。只有含可解析数值的统计列可点击升降序，姓名与文本列不排序；NBA 默认按得分降序，未知值和未出场球员排后。身份栏一般取可用宽度约 42%（130–180dp），显示 30dp 来源人物图及可用的 14dp 队标角标、12sp 姓名与 11sp 球衣号/位置/首发信息。姓名可缩写或省略；点击后以统一 sheet 展示完整姓名、球队、球衣号、位置、首发/未出场及该行全部来源字段。

足球按来源阵容/技术类别、NFL 按传球/冲球等项目、CS2 按全场/地图筛选；F1 用固定车手身份栏和名次/车队/车号/场次成绩列，排位赛保留 Q1–Q3，宽列横向可达。球队分节、地图、场次总览沿用相同表格语法。空表不补零、不显示占位行，只显示明确的"暂无"说明。比赛信息采用左标签、右多行值（1:2）布局，裁判/官员按可读角色归组，每个姓名独立一行，绝不显示序列化 JSON。

LoL 使用专用小局视图：「比赛」先显示小局选择、独立的击杀比分和来源比赛用时，再以每行四项的紧凑网格对照双方经济与目标；经济缩写为 k，完整数值保留在可访问描述和 tooltip。阵容按上单、打野、中单、下路、辅助配对，角色居中，两侧镜像排列 13sp 选手名、36dp 英雄图和等级角标、K/D/A、补刀、经济与 20dp 可用技能图。「统计」按本局选手显示伤害、视野、28dp 装备图标与完整装备名。队伍按真实 ID 匹配，不以蓝红方顺序替代对阵方向；缺失字段显示 —，未发布的小局或阵容明确说明，不填入历史阵容。

人物与队标使用现有 allowlist 内的 ESPN / 5EPlay / 腾讯官方来源 URL；LoL 英雄、装备与技能图由 `https://game.gtimg.cn/images/lol/act/img/{champion,item,spell}/{source-id}.png` 在运行时加载。所有图像保留来源身份，不新增随包栅格资源或生成替代图像。

### Navigation

0.5.0 精修：导航与顶部在自动更新中保持原有 View；无内容变化时只更新来源时间。滚动中延后数据视图替换，保留手势连续性。设置页使用两组 68dp 以上的原生操作行（单场订阅、偏好与帮助），标题 15sp、说明 12sp、末尾统一 chevron；长篇使用说明移入「使用帮助」。列表状态分组已说明的普通状态不再逐卡重复，来源与带具体日期的数据时间保留在两行注脚。详情移除与来源注脚重复的顶部时间说明。

后续用户标注调整：首页标题改为「MyScore」，删除旁边的时区提示；所有比赛卡脚注仅显示 12sp 比赛时间（未确认时显示「时间待定」），来源与数据更新时间可在详情和连接状态中查看。独立的过期进行中警告保留。

底部入口为"比赛""设置"，使用代码绘制的 1.8dp 线性图标和 12sp 标签。当前项使用整格 `tonal` 背景、`primary` 图标与 medium 文字；其他项使用 `surface` 和 `muted`。图标本身不进入可访问树，入口容器提供中文名称和"已选中"描述。

### Sheets

所有二级菜单统一使用底部 sheet：`surface` 背景、顶部 22dp 圆角、20dp 水平内边距、顶部 38×4dp 拖动条、20sp medium 标题、13sp 说明、56dp 选项行（15sp 标题 + 12sp 说明 + 右侧 20dp 勾选）与 48dp 按钮。sheet 从底部滑入并带 46% 遮罩；列表可滚动，最高约屏幕 75%。用于日历选择、已订阅列表、外观、来源状态、订阅确认、日期和同步结果。日历选项按小米账户、本机、其他可写账户排序，`calendar_displayname_xiaomi` 等系统资源键显示为可读名称。取消使用 `tonal` 全宽按钮；确认使用 `primary`/`tonal` 双按钮。不使用平台 `AlertDialog`、单选列表或多选对话框。

### Platform Controls

软件更新入口位于「设置 → 检查更新」，说明显示当前版本。独立原生更新页沿用黑白主题与系统返回：依次为版本、检查/下载/校验状态、主操作、简短更新内容和发布页入口。下载进度只更新文本与进度条，不重建滚动容器；下载中可取消。长发布说明的技术校验信息留在发布页，应用提取更新条目。安装权限引导使用统一 sheet；授权设置和覆盖安装确认由系统界面负责。

系统日历权限对话框、跳转到系统日历以及系统通知设置不属于应用自绘范围；应用在首次订阅时直接请求系统日历权限，其余说明与结果使用 sheet。应用沿用 `Theme.Material.Light.NoActionBar` / `Theme.Material.NoActionBar`（M1）而不引入 Material Components 依赖，这是有意的依赖与包体积约束；业务菜单不再暴露 M1 的 `AlertDialog` 外观。

### Empty / Error States

空状态先给出 20sp 标题（"这个日期没有已确认的比赛"或"暂无本场…数据"），再给出 14sp 说明，最后是"重新获取"次按钮。错误横幅使用 `error` 文字与 `surface` 背景，点击打开来源连接状态；详情加载失败时保留缓存并在顶部显示失败原因。所有状态都明确区分"没有比赛""来源未提供""连接失败"。

## Do's and Don'ts

### Do:
- **Do** 用 `primary` / `background` 的对比承载比分和当前选中态，保持单色。
- **Do** 保持列表队名在左、比分在右；详情以双方居中比分条和分栏下来源时间建立阅读顺序。
- **Do** 让详情身份栏固定、统计横向可达，表前明确操作，点击姓名查看完整来源字段。
- **Do** 仅对数值列排序，保留未知/未出场状态；按运动提供球队、类别、地图或小局筛选。
- **Do** 用 48dp 日历图标管理单场比赛：加号订阅、勾选已订阅、透明底表示暂不可订阅。
- **Do** 所有二级菜单使用同一套底部 sheet；选项行提供 15sp 标题、12sp 说明和 20dp 勾选。
- **Do** 显示来源真实队标；下载失败时退回首字母缩写并保留队名，刷新可重试。
- **Do** 为所有触控操作保留至少 48dp 高度，并让筛选横向滚动、正文纵向滚动。
- **Do** 在缓存、失败、未知时间和大字号下保留明确文字与完整布局。
- **Do** 同时维护 light / dark 主题角色，使用 dp 排布、sp 文字并保留系统字号缩放。

### Don't:
- **Don't** 重新引入绿色或其他品牌强调色；颜色只属于来源真实队标、人物、游戏图像和错误提示。
- **Don't** 为卡片、按钮或表格添加装饰阴影、描边、渐变或图片背景。
- **Don't** 用颜色单独表示进行中、缓存或失败；必须保留状态文字和来源时间。
- **Don't** 生成、绘制或替换队标，也不要用缩写冒充真实队标。
- **Don't** 虚构比分、阵容、统计或补零；缺失字段显示未知或明确的"暂无"。
- **Don't** 固定比赛卡高度、缩小触控目标或把 10sp 用于任何文本；系统最小字号是 11sp。
- **Don't** 把密集比较表改成逐人长卡、让身份栏随数值滚走，或把缩写当成唯一可用姓名。
- **Don't** 按赛事项目批量导入日历，或在比赛卡之外暗地里创建日程。
- **Don't** 用“订阅/已订阅”文字药丸代替日历图标；卡片入口只用图标和可访问描述。
- **Don't** 使用平台 AlertDialog、系统单选/多选列表，或让二级菜单与应用主题脱节。
- **Don't** 在深色表面沿用浅色选中面，或把 `crest-backing` 当作可主题化的表面。
- **Don't** 添加当前原生实现不存在的平板分栏、网页断点、悬停动画或自定义字体。
