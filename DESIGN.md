---
name: MyScore
description: 夜场比赛日：深蓝黑底、冰蓝状态与稳定比分轴的原生 Android 应用
colors:
  background-dark: "#0C1017"
  surface-dark: "#151C27"
  tonal-dark: "#202E40"
  text-dark: "#F2F5FA"
  muted-dark: "#9BAAC0"
  primary-dark: "#8BC8FF"
  on-primary-dark: "#10243A"
  secondary-dark: "#B5ACEF"
  line-dark: "#2A3547"
  error-dark: "#FFB4A9"
  background-light: "#F3F6FB"
  surface-light: "#FFFFFF"
  tonal-light: "#DFEAF7"
  text-light: "#182437"
  muted-light: "#52627A"
  primary-light: "#235E99"
  on-primary-light: "#FFFFFF"
  secondary-light: "#65548E"
  line-light: "#D6DFEB"
  error-light: "#A6342C"
  crest-backing: "transparent"
typography:
  headline:
    fontFamily: "sans-serif-medium"
    fontSize: "28sp"
    fontFeature: "tnum"
  score:
    fontFamily: "sans-serif-medium"
    fontSize: "36sp"
    fontFeature: "tnum"
  score-row:
    fontFamily: "sans-serif-medium"
    fontSize: "24sp"
    fontFeature: "tnum"
  sheet-title:
    fontFamily: "sans-serif-medium"
    fontSize: "22sp"
    fontFeature: "tnum"
  section:
    fontFamily: "sans-serif-medium"
    fontSize: "16sp"
    fontFeature: "tnum"
  body:
    fontFamily: "sans-serif"
    fontSize: "14sp"
    fontFeature: "tnum"
  supporting:
    fontFamily: "sans-serif"
    fontSize: "13sp"
    fontFeature: "tnum"
  caption:
    fontFamily: "sans-serif"
    fontSize: "12sp"
    fontFeature: "tnum"
  label:
    fontFamily: "sans-serif"
    fontSize: "11sp"
    fontFeature: "tnum"
rounded:
  card: "16dp"
  control: "12dp"
  sheet: "28dp"
  live-label: "6dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  content: "16dp"
  page-title: "20dp"
  section: "24dp"
  spacious: "32dp"
components:
  button-primary:
    backgroundColor: "{colors.primary-dark}"
    textColor: "{colors.on-primary-dark}"
    rounded: "{rounded.control}"
    padding: "14dp 16dp"
  button-secondary:
    backgroundColor: "{colors.tonal-dark}"
    textColor: "{colors.primary-dark}"
    rounded: "{rounded.control}"
    padding: "14dp 16dp"
  chip-selected:
    backgroundColor: "{colors.primary-dark}"
    textColor: "{colors.on-primary-dark}"
    rounded: "{rounded.control}"
    padding: "10dp 16dp"
  chip:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.muted-dark}"
    rounded: "{rounded.control}"
    padding: "10dp 16dp"
  match-card:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.text-dark}"
    rounded: "{rounded.card}"
    padding: "14dp 16dp 10dp"
  navigation-selected:
    backgroundColor: "{colors.tonal-dark}"
    textColor: "{colors.primary-dark}"
    rounded: "{rounded.card}"
  data-table:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.text-dark}"
    rounded: "{rounded.card}"
  sheet:
    backgroundColor: "{colors.surface-dark}"
    textColor: "{colors.text-dark}"
    rounded: "{rounded.sheet}"
    padding: "8dp 24dp 24dp"
---

# Design System: MyScore

## Overview

**Creative North Star: "夜场比赛日"**

深蓝黑底承载真实比赛，冷白队名与稳定的比分轴建立阅读顺序，冰蓝强调进行中、当前选择和主要操作。详情用冰蓝与淡紫对照双方；设置、更新和底部 sheet 延续同一套字体、图标与表面层级。

这是原生 Android Java Views 的全应用重设计，替代旧单色、禁止强调色和禁止描边约束。新安装默认深色，已有浅色、深色或跟随系统设置继续生效；Manifest 默认使用 AppThemeDark。

**Key Characteristics:**

- 深色优先，浅色具有完整对应角色。
- 固定比分轴、真实队标、清楚的来源与数据时间。
- 细描边与明暗表面分层，圆角统一，触摸反馈使用原生 ripple。
- 保留回收列表及原地更新，信息变化不打断阅读。

当前 0.10.1 已通过 91 项 JUnit、Android lint、APK 与 Android 测试包编译。API 35 模拟器常规字号和 360dp / 1.3 倍字号各通过 439 项运行检查，320dp / 2 倍字号通过 176 项基础检查；布局矩阵覆盖 320/360dp、1.0/1.3/2.0 字号及浅深主题。文字保留字体上下留白及回退字体行距，比赛卡、日期与球队筛选允许完整换行；统计表按实际内容测量并同步左右行高。验收修复列表等待布局时错误重绑旧子视图的崩溃；比分先确定字号再测量高度。本版截图见 `artifacts/review-0.10.1-normal/`、`artifacts/review-0.10.1-360dp-large/` 及 `VERIFICATION.md`；这些是模拟器证据，K80 Pro / HyperOS 仍待真机验收。

0.10.2 统一取消队标底板并按图案边界和密度调整视觉大小；5EPlay 合并图按主题选取单个版本。新增 26 项队标像素与组件检查，最终包在常规字号、360dp / 1.3 倍字号各通过 465 项 Android 运行检查；真实页面截图见 `artifacts/review-0.10.2-final/`、`artifacts/review-0.10.2-final-360dp-large/`，其余验证边界见 `VERIFICATION.md`。

1.1 新增球队赛程页：56dp 真实队标、22sp 队名和 12sp 赛事副标题；来源与获取时间使用 11sp muted；「全部 / 接下来 / 已结束」筛选使用 48dp chip 并放在横向滚动容器中，放大字体时不裁切。比赛卡沿用 16dp 卡片与 1dp 描边，首行 11sp 日期 / 状态，次行 24dp 对手队标、15sp 对手名、场地或赛事阶段与 20sp 本队在前比分；胜 / 平 / 负使用 primary / muted / error，CS2 与 LOL 不标主客场。比分卡与详情比分条的队标成为独立 48dp / 56dp 触摸目标，使用同一 ripple 与浅深主题角色。106 项 JUnit、56 项球队赛程运行检查通过；533 项检查在常规字号和 360dp / 1.3 字号各通过一次，320dp / 2 倍字号 279 项通过，截图见 `artifacts/review-team-1.1*/`。

## Colors

深蓝黑表面、冷白正文、冰蓝操作与淡紫对照形成清晰层级。数值以 frontmatter 和 Ui.java 为准。

### Primary

- **冰蓝**：primary 用于进行中比分、选中标签、操作、排序与进度；填色按钮使用 on-primary 深色文字。

### Secondary

- **对照淡紫**：secondary 用于详情双方统计的另一侧，保留左右位置、队名与数值辅助识别。

### Neutral

- **夜场底色 / 深蓝表面 / 蓝灰选中面**：background、surface、tonal 逐层区分页面、卡片与局部状态。
- **冷白 / 次级蓝灰**：text 承载正文，muted 承载来源、时间、表头和说明。
- **蓝灰线**：line 用于 1dp 卡片描边与分隔；真实队标直接透明展示，不加白色底板。
- **柔红**：error 用于失败、取消与过期记录说明。

**The Theme Roles Rule.** 全部页面按 Ui 角色整体切换主题。队标保留来源颜色、内部白色细节和透明区域；只有几乎纯黑或纯白的单色标记在低对比主题中使用正文色墨迹，不加背景。

5EPlay 已确认的 `merge_` 队标图包含横排的浅深版本，按主题选取一个正方形版本后再居中缩放，不把它作为一张宽队标显示。普通横长队标保留完整比例。

**The Visible State Rule.** 强调色必须配合状态文字、选中语义或明确位置；缺失数据使用「暂无」或「—」，不补零。

## Typography

使用平台 sans-serif / sans-serif-medium，不引入自定义字体；数字统一启用 tabular figures（tnum）。尺寸使用 sp，常规字阶为 11–36sp。

- **Headline 28sp**：首页、设置和更新品牌标题；首页标题字距 -0.025em。
- **Score 36sp**：详情主比分使用共用 ScoreText，按可用宽度在测量高度前选择 22–36sp 字号；无比分的 VS 最大 24sp。LOL 小局使用 22–24sp。若 22sp 完整横排仍放不下，改为主客上下两行，保留可读下限与完整无障碍比分描述。
- **Score row 24sp**：首页右侧对齐的比分。
- **Sheet title 22sp**：统一二级菜单标题；20sp 用于空状态和更新状态标题。
- **Section 15–18sp**：数据分组、比赛信息、F1 标题。
- **Body / action 14sp**：正文、按钮；13sp 用于筛选和表格数值。
- **Caption / label 12 / 11sp**：队名辅助、数据时间、表头和来源。

## Layout

手机纵向单列，遵守系统安全区。列表和详情正文左右留白 16dp，首页标题 20dp，sheet 与更新正文通常 24dp。内部以 4 / 8 / 12 / 16 / 24dp 节奏组织，不固定比赛卡高度。

首页由标题、横向赛事筛选、前一天 / 日期 / 后一天、按状态分组的比赛组成，底部仅「比赛 / 设置」。标题层上滑跟随收起，下滑立即出现。切换赛事复用 ScoreList、ScoreAdapter 和底部导航；结构相同时只更新可见行的文本。

统计表固定身份列，数值列横向滚动；表头至少 48dp、数据行至少 56dp，并随系统字体比例增高。每行取姓名、辅助信息与所有数值格实测高度的最大值，完整换行并保持左右对齐；表头预留排序箭头空间。完整来源字段仍可在姓名 sheet 查看。

**The Compact Calendar Rule.** 月份日历按宽度收缩左右 padding：360dp 宽时为每侧 12dp，保留七列各 48dp；屏宽低于 336dp 时，日期格宽度仍可能小于 48dp，不承诺所有宽度均满足该目标。

## Elevation & Depth

以 background / surface / tonal 明暗层与 1dp line 描边建立层次，没有自定义阴影或渐变。sheet 使用原生窗口遮罩（dim 0.64），进入 190ms、退出 160ms；首页标题随滚动直接位移，不增加装饰动画。

## Shapes

卡片、数据表和底部导航选中面为 16dp；按钮、筛选、日期与图标控件为 12dp；sheet 仅顶部两角为 28dp。进行中小标签为 6dp，其他局部徽标按内容尺寸处理。

## Components

### Buttons & chips

主按钮为 primary / on-primary；次按钮为 tonal / primary；筛选未选为 surface / muted、选中为 primary / on-primary。按钮与常规筛选最小高度 48dp，使用原生 ripple、焦点和可访问选中描述。

### Match cards

16dp 圆角、1dp 描边，内边距为左右 16dp、顶部 14dp、底部 10dp，卡间 10dp。真实队标在左、队名居中展开、24sp 比分右对齐。队标使用 28dp 透明槽位，按图案实际 alpha 边界去除源图空白、保持比例居中；实心徽章占槽位约 86%，疏朗轮廓最多约 96%，统一视觉重量。详情沿用 44dp 槽位，表格与 LoL 队标共用同一规则；人物、英雄和装备图像保留原有独立展示方式。进行中使用冰蓝数值和状态标签；底部用分隔线区分北京时间、实际来源 / 更新时间与 48dp 日历操作。

队标同时是球队赛程入口：比分卡内使用 48dp 槽位，详情比分条使用 56dp 槽位，均保留原队标的视觉尺寸与位置；点击水波纹为透明圆形，内容描述为「查看 X 的赛程」。来源未提供球队标识时不设置点击和描述，点击仍由比赛卡打开详情。

### Team schedule

从队标进入的球队页沿用 16dp 卡片、1dp 描边和 48dp 筛选。顶部 56dp 真实队标与 22sp 队名，下面 12sp 赛事副标题；来源行使用 11sp muted。比赛卡首行为 11sp 日期与状态，次行为 24dp 对手队标、15sp 队名、场地 / 赛事阶段与 20sp 本队在前比分；胜 / 平 / 负使用 primary / muted / error。CS2 与 LOL 不显示主客场。筛选行包在横向滚动容器内，放大字体时不裁切；列表沿用回收行，只重绑可见卡片。 默认进入「接下来」。球队信息区直接落在页面背景上；比赛卡底部以细分隔线区分北京时间 / 来源与右侧 22dp 日历图标。日历操作复用比分页的透明圆形 ripple、48dp 触摸区与加号 / 勾选状态，不加按钮底色、订阅文字或逐卡提醒说明。

### Detail & comparisons

比分条采用 44dp 队标和居中主比分；窄宽度由共用 ScoreText 切为上下比分；活动分栏使用 primary 文字与 24 × 3dp 下划线。双方技术统计采用 primary / secondary 数字及对比条，排序列用 tonal 背景和 primary 数字。足球、NBA、NFL、F1、CS2 保留各自字段结构，LOL 保留真实英雄、技能与装备图像；全部筛选复用 Ui.chip。

### Settings & update

设置按订阅、偏好与帮助分组，76dp 起的图标行放入统一描边卡片。更新页复用返回键、64dp 图标底座、28sp 品牌标题、4dp 进度条与按钮角色；状态和操作均有文字说明。

### Bottom sheets

日历、外观、来源、确认、结果和完整身份使用同一套 sheet。外层统一可滚动，最大为屏高的 75%，标题、正文、选项与按钮随内容一起滚动；选项行最小 56dp，选中为 tonal / primary 与 20dp 勾选。长正文使用 13sp，通常左右 24dp；日历 padding 按 Layout 规则调整。

## Do's and Don'ts

### Do:

- **Do** 复用 Ui 的配色、字体、描边、ripple 与圆角角色。
- **Do** 保留真实来源、北京时间、更新时间和明确的未知状态。
- **Do** 保持比分列与身份列稳定，并在更新时复用已有列表和视图。
- **Do** 让可点击队标拥有独立的 48dp / 56dp 触摸目标与「查看 X 的赛程」无障碍描述，未知球队标识不加点击。
- **Do** 同时维护浅色与深色，检查字体放大、三位数比分及长内容滚动。

### Don't:

- **Don't** 恢复旧单色或禁止强调色 / 描边的约束。
- **Don't** 用颜色单独传达比赛状态，或虚构比分、阵容与统计。
- **Don't** 用装饰阴影、渐变、伪造队标或不同样式的二级对话框打破现有语言。
- **Don't** 把构建通过、旧版截图或源码检查写成本版视觉与真机验收。
