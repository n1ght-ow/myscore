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
    fontSize: "36sp"
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
  table: "10dp"
  card: "12dp"
  date: "12dp"
  container: "14dp"
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
  subscribe-pill:
    backgroundColor: "{colors.primary-light}"
    textColor: "{colors.on-primary-light}"
    typography: "{typography.metadata}"
    rounded: "{rounded.pill}"
    padding: "0dp 12dp"
  subscribe-pill-dark:
    backgroundColor: "{colors.primary-dark}"
    textColor: "{colors.on-primary-dark}"
    typography: "{typography.metadata}"
    rounded: "{rounded.pill}"
    padding: "0dp 12dp"
  subscribe-pill-selected:
    backgroundColor: "{colors.tonal-light}"
    textColor: "{colors.primary-light}"
    typography: "{typography.metadata}"
    rounded: "{rounded.pill}"
    padding: "0dp 12dp"
  subscribe-pill-selected-dark:
    backgroundColor: "{colors.tonal-dark}"
    textColor: "{colors.primary-dark}"
    typography: "{typography.metadata}"
    rounded: "{rounded.pill}"
    padding: "0dp 12dp"
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
    padding: "12dp"
  data-table-dark:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.text-dark}"
    rounded: "{rounded.table}"
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

MyScore 像一份只印黑、白与灰的比赛节目单：白色纸张承载浅色模式，石墨表面承载深色模式。信息按节目单的栏位排列——赛事与状态在上，队名与比分在中，来源和数据时间压在下缘。比分、选中态和主操作使用同一支"墨"，因此界面没有品牌色块，也没有装饰性插画；唯一的彩色来自来源提供的真实队标，以及错误提示的红色。

界面沿用 Android 平台字体、原生控件和系统返回手势。视觉重点来自字号对比、对齐的比分轴、留白与表面明暗，而不是阴影、描边或渐变。旧版的绿色球场导视世界已被用户明确拒绝；本文记录从 0.2.0 起采用、在 0.3.0 加入单场订阅与统一 sheet 的白/石墨单色世界。

**Key Characteristics:**
- 白色浅色模式、石墨深色模式的单色外壳，主题成对切换。
- 固定的"队名在左、比分在右"比分轴，列表与详情一致。
- 真实来源队标，白色底板保证深色队标在两种主题下都可读。
- 每张比赛卡和详情页都保留来源与数据时间。
- 比赛卡上的订阅药丸把单场比赛写入日历，提前 5 分钟提醒。
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

**The Monochrome Programme Rule.** 界面只使用黑、白与中性灰；颜色只能来自真实队标和 `error-*`。不得重新引入绿色、品牌强调色或装饰渐变。

**The Theme Pair Rule.** 切换外观时整体切换主题角色；不在深色表面沿用浅色文字、队标底板以外的固定色块或浅色选中面。

## Typography

标题、正文与操作全部使用 Android `sans-serif` 家族；强调使用 `sans-serif-medium` 字面，而不是额外加粗。平台实际字形与回退由设备提供，不下载字体。`Ui.text()` 统一启用等宽数字特性 `tnum`，并关闭额外字体内边距；没有自定义行高、字距或全大写转换。

### Hierarchy
- **Headline**（24sp medium）：首页"我的比赛"、赛程页"未来赛程"、设置页"设置"。
- **Score**（36sp medium）：详情页 hero 的比分；没有比分时用 25sp 的 "VS" 占位。
- **Score row**（20sp medium）：列表比赛卡右侧的比分列，保持数字末端对齐。
- **Title**（22sp medium）：详情页 F1 赛事标题；日历摘要标题和空状态标题使用 18sp 的局部字号。
- **Section**（16sp medium）：详情分组标题、设置分组标题；列表分区标题用 14sp medium。
- **Team**（15sp medium）：比赛卡队名；详情 hero 队名为 13sp medium。
- **Body / Action**（14sp regular / medium）：说明正文使用 regular；按钮、日期和筛选使用 medium。
- **Supporting**（13sp regular）：设置说明、详情提示、表格单元格。
- **Metadata**（12sp regular）：赛事名、状态、卡底来源、表头副行。
- **Timestamp**（11sp regular）：数据获取时间、比赛卡底部来源、详情 hero 时间注脚和表头。11sp 是本系统的最小字号，不用于正文。

**The Live Type Rule.** 文字使用 sp 并随系统字号缩放；容器由内容决定高度。大字号下只能换行或滚动，不能裁切、缩小字号或把比分挤出屏幕。

## Layout

原生竖向结构依次是：顶部标题、赛事筛选、日期选择（仅比赛页）、可滚动正文和底部导航。首页正文水平边距为 16dp，详情页为 20dp；首页正文上方 4dp、下方 24dp，详情页上方 16dp、下方 28dp。状态栏、导航栏和显示缺口通过窗口 insets 留空；应用自绘内容不进入系统栏区域。

顶部标题行使用 20dp 左、18dp 上、12dp 右/下的内边距，标题与 12sp 副标题成组，刷新按钮是 48dp 方形触控区。赛事筛选是横向滚动行，chip 最小宽 56dp、最小高 48dp，水平内边距 14dp、间距 8dp；右侧固定 48dp 的 chevron 触控区（描述"更多赛事筛选"），独立占位、不覆盖 chip，内容不溢出或滚到末尾时只隐藏箭头并保留占位；选中的 chip 会自动滚入可视区，避免被右缘裁成半圆。不为了塞满屏幕而缩小触控区。日期是三等分横行，每项最小高 48dp、间距 8dp。

首页在比分列表前不再插入说明行；只有正在更新时才出现 11sp 的"正在更新比赛…"，空闲首屏直接进入分区和卡片。赛程页保留"未来 7 天 · 赛程可能改期"提示。

比赛卡占满正文宽度，以内容决定高度，卡片间距 6dp。卡内是：赛事与状态行、两行队名比分（F1 为标题加前二名）、底部来源时间行。队标 25dp，与队名间隔 10dp；队名取得剩余宽度，比分最小宽 32dp、右对齐并保留 12dp 起始间隔。可订阅的未开始比赛在来源行右侧显示 64×48dp 的订阅药丸，其余状态不显示；已订阅的比赛即使后来开始或结束仍保留药丸，便于取消。列表按状态分区，分区标题前 18dp、后 10dp。

详情页顶部为 48dp 返回与刷新按钮、16sp 居中赛事名；其下是 hero：状态行、两行队标/队名/36sp 比分、11sp 时间注脚。hero 下方是等宽分栏：足球/NBA/NFL 为"总览、阵容/球员、统计、进程"，CS2/LOL 为"总览、选手/阵容、统计、小局"，F1 为"总览、车手、成绩"三栏。每项最小高 48dp，选中项在文字下显示 2dp、28dp 宽指示线。

正文数据表使用 10dp 圆角表面、12dp 行内边距。列数为 3–5 列时，按每列内容估算宽度并缩放到可用宽度，所有列默认完整显示，不横向滚动；6 列以上时，改为每行一段的字段块：首列作为 14sp 标题，其余列按两列网格显示 11sp 列名和 13sp 值，所有字段默认可见。F1 车手表为 4 列（车手、名次、车队、车号），成绩为 9 列字段块。表格不再显示"左右滑动查看完整数据"提示。

底部导航三等分，23dp 图标、图文间距 5dp、上下内边距各 10dp，顶部 1dp 分隔线；当前项使用整格 `tonal` 背景，不是浮动圆角胶囊。所有触控目标最小 48dp。应用只实现手机竖屏；没有平板分栏或网页断点。

**The Fixed Score Column Rule.** 从列表到详情的比分轴不变：队名/车手在左，比分/名次在右，比分列右对齐。新增字段只能进入既有栏位，不能打散这条轴。

**The Complete Row Rule.** 数据表的每一列都必须在默认视口内可见；列多时改为字段块，不以横向滚动或"左右滑动"提示隐藏关键数据。

**The Single-Match Rule.** 日历只按单场比赛订阅；不在设置里按赛事项目批量导入。每场订阅写入一个事件，开赛前 5 分钟提醒，取消时只移除该事件。

**The One Sheet Rule.** 应用内所有二级菜单——日历、订阅、外观、来源、确认、结果——使用同一套底部 sheet；不得混用平台 AlertDialog 或系统列表对话框。

**The Source Lane Rule.** 比赛卡底部和详情页顶部始终保留来源与数据时间；缓存、旧进行中或失败状态必须用文字说明，不能用空白、颜色或省略来代替。

## Elevation & Depth

应用自绘的卡片、按钮、导航、订阅药丸和表格没有阴影、描边或 elevation。深度只来自三种表面：页面底色、`surface` 内容表面、`tonal` 选中/摘要表面，以及 1dp 的 `line` 分隔线。应用内的二级菜单是自绘 sheet，不使用平台 `AlertDialog`；系统权限对话框和跳转到系统日历的界面仍由 Android 决定，不从截图反推为应用 token。

**The Flat Surface Rule.** 不新增装饰性阴影、渐变、玻璃或描边。需要分层时先换表面色，再考虑间距，最后才考虑分隔线。

## Shapes

比赛卡与日期选中块使用 12dp 圆角；设置卡、日历摘要和空状态容器使用 14dp；数据表容器使用 10dp；按钮、筛选 chip 和图标触控区使用全圆 24dp；队标底板使用 8dp；统计比例条使用 2dp 圆角。底部导航的选中块保持直角，延续三等分的连续结构。内容表面没有边框，`line` 只用于实际的列表边界、表格行和导航上缘。

## Components

### Buttons

主按钮使用 `primary` 底与 `on-primary` 字，次按钮使用 `tonal` 底与 `primary` 字。两者都是 `action` 字体、48dp 最小高、12dp 上下与 16dp 左右内边距、24dp 全圆角。日历容器内的按钮随父级拉满宽度。交互使用原生 `RippleDrawable`，波纹为当前 `primary` 的 RGB 加 alpha `0x25`。控件可聚焦；没有自定义焦点环、悬停态或自定时长动画。

### Chips

赛事筛选 chip 选中时是实心 `primary` 与 `on-primary` 字；未选时直接使用页面底色与 `muted` 字。两者同为 14sp medium、48dp 高、24dp 圆角。选中状态同时写入原生 selected 与中文可访问描述（"已选中" / "筛选赛事"）。筛选行右侧固定 48dp 的 chevron 触控区（描述"更多赛事筛选"），独立占位、不覆盖 chip，滚到末尾时只隐藏箭头并保留占位；选中 chip 自动滚入可视区。日期使用 `tonal` 选中面而非实心主色。

### Cards / Containers

比赛卡由赛事/状态、两行队名比分、底部来源时间与订阅按钮组成，使用 `surface`、12dp 圆角、10dp 垂直与 12dp 水平内边距，无描边无阴影。整卡可点击进入详情，波纹覆盖整卡；订阅按钮独立消费点击，不打开详情。进行中状态不会仅靠颜色表达：状态文字保留，数据超过两分钟时追加"缓存"文字，旧进行中记录追加 error 色的"上次记录为进行中，当前状态待刷新"。

设置卡使用 `surface` 与 14dp 圆角，内部承载日历选择、已订阅列表、检查按钮和说明。赛程页日历摘要使用 `tonal` 与 14dp 圆角，承载 18sp 标题、13sp 说明和"管理日历"主操作。数据表放在 10dp 圆角的 `surface` 中，行间使用 `line`。

### Subscription Pill

比赛卡底部的订阅按钮是 64×48dp、24dp 圆角的药丸，文字 12sp medium：未订阅为 `primary` 实心与 `on-primary` 字，已订阅为 `tonal` 底与 `primary` 字。按钮文案分别是"订阅"/"已订阅"，可访问描述补充比赛名和"提前 5 分钟提醒"。点击"订阅"先检查权限与目标日历；点击"已订阅"先弹出统一确认 sheet。

### Detail Header

详情 hero 居中排列：12sp 状态行、44dp 队标、13sp 队名、36sp 比分；F1 以 22sp 赛事标题替代队标对阵。没有比分时显示 25sp "VS"，不伪造 0:0。hero 下方 11sp 时间注脚写明"比分更新于 HH:mm:ss · 北京时间"。返回按钮固定 48dp 触控区，系统返回手势与按钮行为一致。

### Tabs

详情分栏等宽，14sp medium 文字，最小高 48dp；选中项文字用 `text` 并在下方绘制 2dp `text` 指示线（宽 28dp），未选项文字用 `muted` 且指示线取页面底色。足球/NBA/NFL 为总览、阵容/球员、统计、进程；CS2/LOL 的进度分栏显示"小局"；F1 只有总览、车手、成绩三栏，不显示空的进程栏。图标不使用 Unicode 字符代替。

### Tables

表头为 11sp `muted` medium，单元格为 13sp `text`（第一列 medium），行内边距 12dp。3–5 列表格按每列最长内容估算宽度（每列最小 32dp）并缩放到可用宽度，所有列默认完整显示，不横向滚动。6 列以上时，每行改为字段块：首列 14sp 标题，其余列以两列网格显示 11sp 列名和 13sp 值。F1 车手表为 4 列（车手、名次、车队、车号），成绩为 9 列字段块；足球阵容为 3 列表格且位置统一为中文；LOL 团队数据 6 列使用字段块。装备列采用卡片式布局，物品名在上、装备文本在下。空表不补零、不显示占位行，只显示明确的"暂无"说明。

### Navigation

底部入口为"比赛""赛程""设置"，使用代码绘制的 1.8dp 线性图标和 12sp 标签。当前项使用整格 `tonal` 背景、`primary` 图标与 medium 文字；其他项使用 `surface` 和 `muted`。图标本身不进入可访问树，入口容器提供中文名称和"已选中"描述。

### Sheets

所有二级菜单统一使用底部 sheet：`surface` 背景、顶部 22dp 圆角、20dp 水平内边距、顶部 38×4dp 拖动条、20sp medium 标题、13sp 说明、56dp 选项行（15sp 标题 + 12sp 说明 + 右侧 20dp 勾选）与 48dp 按钮。sheet 从底部滑入并带 46% 遮罩；列表可滚动，最高约屏幕 75%。用于日历选择、已订阅列表、外观、来源状态、订阅确认和同步结果。日历选项按本机、小米、其他可写账户排序，`calendar_displayname_xiaomi` 等系统资源键显示为可读名称。取消使用 `tonal` 全宽按钮；确认使用 `primary`/`tonal` 双按钮。不使用平台 `AlertDialog`、单选列表或多选对话框。

### Platform Controls

系统日历权限对话框、跳转到系统日历以及系统通知设置不属于应用自绘范围；应用在首次订阅时直接请求系统日历权限，其余说明与结果使用 sheet。应用沿用 `Theme.Material.Light.NoActionBar` / `Theme.Material.NoActionBar`（M1）而不引入 Material Components 依赖，这是有意的依赖与包体积约束；业务菜单不再暴露 M1 的 `AlertDialog` 外观。

### Empty / Error States

空状态先给出 20sp 标题（"这个日期没有已确认的比赛"或"暂无本场…数据"），再给出 14sp 说明，最后是"重新获取"次按钮。错误横幅使用 `error` 文字与 `surface` 背景，点击打开来源连接状态；详情加载失败时保留缓存并在顶部显示失败原因。所有状态都明确区分"没有比赛""来源未提供""连接失败"。

## Do's and Don'ts

### Do:
- **Do** 用 `primary` / `background` 的对比承载比分和当前选中态，保持单色。
- **Do** 保持队名在左、比分在右、来源时间在卡底或详情顶部的阅读顺序。
- **Do** 为列表和详情使用同一比分轴、同一队标尺寸体系和同一来源时间格式。
- **Do** 让数据表的所有列默认可见：3–5 列按内容适配宽度，6 列以上改用字段块。
- **Do** 用 64×48dp 的订阅药丸管理单场比赛，文案在“订阅”和“已订阅”之间切换。
- **Do** 所有二级菜单使用同一套底部 sheet；选项行提供 15sp 标题、12sp 说明和 20dp 勾选。
- **Do** 显示来源真实队标；下载失败时退回首字母缩写并保留队名，刷新可重试。
- **Do** 为所有触控操作保留至少 48dp 高度，并让筛选横向滚动、正文纵向滚动。
- **Do** 在缓存、失败、未知时间和大字号下保留明确文字与完整布局。
- **Do** 同时维护 light / dark 主题角色，使用 dp 排布、sp 文字并保留系统字号缩放。

### Don't:
- **Don't** 重新引入绿色或其他品牌强调色；颜色只属于真实队标和错误提示。
- **Don't** 为卡片、按钮或表格添加装饰阴影、描边、渐变或图片背景。
- **Don't** 用颜色单独表示进行中、缓存或失败；必须保留状态文字和来源时间。
- **Don't** 生成、绘制或替换队标，也不要用缩写冒充真实队标。
- **Don't** 虚构比分、阵容、统计或补零；缺失字段显示未知或明确的"暂无"。
- **Don't** 固定比赛卡高度、缩小触控目标或把 10sp 用于任何文本；系统最小字号是 11sp。
- **Don't** 使用横向滚动表格、让关键列默认落在屏外，或用"左右滑动"提示代替可见数据。
- **Don't** 按赛事项目批量导入日历，或在比赛卡之外暗地里创建日程。
- **Don't** 使用平台 AlertDialog、系统单选/多选列表，或让二级菜单与应用主题脱节。
- **Don't** 在深色表面沿用浅色选中面，或把 `crest-backing` 当作可主题化的表面。
- **Don't** 添加当前原生实现不存在的平板分栏、网页断点、悬停动画或自定义字体。
