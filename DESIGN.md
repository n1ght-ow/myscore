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
  micro:
    fontFamily: "sans-serif"
    fontSize: "10sp"
    fontFeature: "tnum"
rounded:
  crest: "8dp"
  table: "10dp"
  card: "12dp"
  date: "12dp"
  container: "14dp"
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

界面沿用 Android 平台字体、原生控件和系统返回手势。视觉重点来自字号对比、对齐的比分轴、留白与表面明暗，而不是阴影、描边或渐变。旧版的绿色球场导视世界已被用户明确拒绝，本文记录的是 0.2.0 实际采用的白/石墨单色世界。

**Key Characteristics:**
- 白色浅色模式、石墨深色模式的单色外壳，主题成对切换。
- 固定的"队名在左、比分在右"比分轴，列表与详情一致。
- 真实来源队标，白色底板保证深色队标在两种主题下都可读。
- 每张比赛卡和详情页都保留来源与数据时间。
- 详情是完整原生 Activity，四个分栏承载阵容、统计与进程。
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
- **Timestamp**（11sp regular）：数据获取时间、详情顶部时间、表头。
- **Micro**（10sp regular）：仅比赛卡底部的来源与数据时间、详情 hero 的时间注脚。

**The Live Type Rule.** 文字使用 sp 并随系统字号缩放；容器由内容决定高度。大字号下只能换行或滚动，不能裁切、缩小字号或把比分挤出屏幕。

## Layout

原生竖向结构依次是：顶部标题、赛事筛选、日期选择（仅比赛页）、可滚动正文和底部导航。首页正文水平边距为 16dp，详情页为 20dp；首页正文上方 4dp、下方 24dp，详情页上方 16dp、下方 28dp。状态栏、导航栏和显示缺口通过窗口 insets 留空；应用自绘内容不进入系统栏区域。

顶部标题行使用 20dp 左、18dp 上、12dp 右/下的内边距，标题与 12sp 副标题成组，刷新按钮是 48dp 方形触控区。赛事筛选是横向滚动行，chip 最小宽 56dp、最小高 48dp，水平内边距 14dp、间距 8dp；不为了塞满屏幕而缩小触控区。日期是三等分横行，每项最小高 48dp、间距 8dp。

比赛卡占满正文宽度，以内容决定高度，卡片间距 6dp。卡内是：赛事与状态行、两行队名比分（F1 为标题加前二名）、底部来源时间行。队标 25dp，与队名间隔 10dp；队名取得剩余宽度，比分最小宽 32dp、右对齐并保留 12dp 起始间隔。列表按状态分区，分区标题前 18dp、后 10dp。

详情页顶部为 48dp 返回与刷新按钮、16sp 居中赛事名；其下是 hero：状态行、两行队标/队名/36sp 比分、10sp 时间注脚。hero 下方是四个等宽分栏（总览、阵容/车手/选手/球员、统计、进程/小局），每项最小高 48dp，选中项在文字下显示 2dp、28dp 宽指示线。正文数据表使用 10dp 圆角表面、12dp 行内边距；列数超过三列时横向滚动，并显示"左右滑动查看完整数据"的 10sp 提示。小于等于三列的表格按 2:1:1 比例分配宽度。

底部导航三等分，23dp 图标、图文间距 5dp、上下内边距各 10dp，顶部 1dp 分隔线；当前项使用整格 `tonal` 背景，不是浮动圆角胶囊。所有触控目标最小 48dp。应用只实现手机竖屏；没有平板分栏或网页断点。

**The Fixed Score Column Rule.** 从列表到详情的比分轴不变：队名/车手在左，比分/名次在右，比分列右对齐。新增字段只能进入既有栏位，不能打散这条轴。

**The Source Lane Rule.** 比赛卡底部和详情页顶部始终保留来源与数据时间；缓存、旧进行中或失败状态必须用文字说明，不能用空白、颜色或省略来代替。

## Elevation & Depth

应用自绘的卡片、按钮、导航和表格没有阴影、描边或 elevation。深度只来自三种表面：页面底色、`surface` 内容表面、`tonal` 选中/摘要表面，以及 1dp 的 `line` 分隔线。系统 `AlertDialog`、权限界面和原生 `Switch` 的阴影与形状由 Android 主题决定，不从截图反推为应用 token。

**The Flat Surface Rule.** 不新增装饰性阴影、渐变、玻璃或描边。需要分层时先换表面色，再考虑间距，最后才考虑分隔线。

## Shapes

比赛卡与日期选中块使用 12dp 圆角；设置卡、日历摘要和空状态容器使用 14dp；数据表容器使用 10dp；按钮、筛选 chip 和图标触控区使用全圆 24dp；队标底板使用 8dp；统计比例条使用 2dp 圆角。底部导航的选中块保持直角，延续三等分的连续结构。内容表面没有边框，`line` 只用于实际的列表边界、表格行和导航上缘。

## Components

### Buttons

主按钮使用 `primary` 底与 `on-primary` 字，次按钮使用 `tonal` 底与 `primary` 字。两者都是 `action` 字体、48dp 最小高、12dp 上下与 16dp 左右内边距、24dp 全圆角。日历容器内的按钮随父级拉满宽度。交互使用原生 `RippleDrawable`，波纹为当前 `primary` 的 RGB 加 alpha `0x25`。控件可聚焦；没有自定义焦点环、悬停态或自定时长动画。

### Chips

赛事筛选 chip 选中时是实心 `primary` 与 `on-primary` 字；未选时直接使用页面底色与 `muted` 字。两者同为 14sp medium、48dp 高、24dp 圆角。选中状态同时写入原生 selected 与中文可访问描述（"已选中" / "筛选赛事"）。日期使用 `tonal` 选中面而非实心主色。

### Cards / Containers

比赛卡由赛事/状态、两行队名比分、底部来源时间组成，使用 `surface`、12dp 圆角、10dp 垂直与 12dp 水平内边距，无描边无阴影。整卡可点击进入详情，波纹覆盖整卡。进行中状态不会仅靠颜色表达：状态文字保留，数据超过两分钟时追加"缓存"文字，旧进行中记录追加 error 色的"上次记录为进行中，当前状态待刷新"。

设置卡使用 `surface` 与 14dp 圆角，内部承载原生 Switch、三个次按钮和一个主按钮。赛程页日历摘要使用 `tonal` 与 14dp 圆角，承载 18sp 标题、13sp 说明和主操作。数据表放在 10dp 圆角的 `surface` 中，行间使用 `line`。

### Detail Header

详情 hero 居中排列：12sp 状态行、44dp 队标、13sp 队名、36sp 比分；F1 以 22sp 赛事标题替代队标对阵。没有比分时显示 25sp "VS"，不伪造 0:0。hero 下方 10sp 时间注脚标明"北京时间"和比分时间。返回按钮固定 48dp 触控区，系统返回手势与按钮行为一致。

### Tabs

详情四个分栏等宽，14sp medium 文字，最小高 48dp；选中项文字用 `text` 并在下方绘制 2dp `text` 指示线（宽 28dp），未选项文字用 `muted` 且指示线取页面底色。分栏名称随运动变化：阵容、车手、选手、球员；进度分栏在 CS2/LOL 显示"小局"。图标不使用 Unicode 字符代替。

### Tables

表头为 11sp `muted` medium，单元格为 13sp `text`（第一列 medium）。行内边距 12dp，列宽固定：首列 140dp，队名/成绩列 120dp，英雄列 100dp，其余 64dp；四列及以上横向滚动。装备列采用 2 列卡片式布局，物品名在上、装备文本在下。空表不补零、不显示占位行，只显示明确的"暂无"说明。

### Navigation

底部入口为"比赛""赛程""设置"，使用代码绘制的 1.8dp 线性图标和 12sp 标签。当前项使用整格 `tonal` 背景、`primary` 图标与 medium 文字；其他项使用 `surface` 和 `muted`。图标本身不进入可访问树，入口容器提供中文名称和"已选中"描述。原生 `Switch`、单选、多选和对话框保留 Android 行为与可访问性。

### Empty / Error States

空状态先给出 20sp 标题（"这个日期没有已确认的比赛"或"暂无本场…数据"），再给出 14sp 说明，最后是"重新获取"次按钮。错误横幅使用 `error` 文字与 `surface` 背景，点击打开来源连接状态；详情加载失败时保留缓存并在顶部显示失败原因。所有状态都明确区分"没有比赛""来源未提供""连接失败"。

## Do's and Don'ts

### Do:
- **Do** 用 `primary` / `background` 的对比承载比分和当前选中态，保持单色。
- **Do** 保持队名在左、比分在右、来源时间在卡底或详情顶部的阅读顺序。
- **Do** 为列表和详情使用同一比分轴、同一队标尺寸体系和同一来源时间格式。
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
- **Don't** 固定比赛卡高度、缩小触控目标或把 10sp 用于正文和可操作文字。
- **Don't** 在深色表面沿用浅色选中面，或把 `crest-backing` 当作可主题化的表面。
- **Don't** 添加当前原生实现不存在的平板分栏、网页断点、悬停动画或自定义字体。
