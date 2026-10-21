# 02 · 设计系统定义（Starveil 曜）

> **本文是 Starveil 全部 UI 的唯一真理源。** 写 UI 时有疑问先查本文；本文与代码冲突时，以本文为准并同步修改代码。
> 命名规则：Compose 引用名 = Token 名（如 `StarveilColors.accentA`）；**禁止在 Screen 里写裸色值或裸字号**，`Color(0xFF…)` 只允许出现在 `core/ui/theme/Palette.kt`。
> 历史：本文此前描述的是 HUD 青蓝辉光配色（2026-10 之前），已被「曜」深空曜黑单主题取代；v1「浅色奢华」与 v2「双生主题」两版均已作废，不再有第二套色板。

---

## 1. 设计语言陈述

**一句话**：纯黑 OLED 底、唯一青紫渐变发光圆环、近黑玻璃面卡片、细体大数字、大留白——高级感靠黑暗、克制与光。

**三个关键词**：

| 关键词 | 含义 | 落地 |
|---|---|---|
| **Dark（黑）** | 背景是 `#000000`，不是深灰 | 提面只靠玻璃卡与 1dp 描边，不靠抬高底色 |
| **Restraint（克制）** | 强调色是预算，不是装饰 | 青紫渐变每屏 ≤3 处，只用于圆环、选中标记、开关开启态、关键状态点 |
| **Light（光）** | 光是状态，且只从圆环发出 | 已连接 = 渐变环 + 30% 外发光；其余状态换色不换形 |

**三个"不要做"**：

1. **不要用深灰冒充黑**，也不要用实心深灰卡片、重阴影。卡片无影，靠描边立形。
2. **不要把渐变当填充**。渐变只走线：环、选中行左缘、开关轨道（35% 透明）。健康曲线下的渐变淡出是唯一例外。
3. **不要卡中套卡**。一张卡 = 一个分组；卡内分隔只用 `hairline`。

---

## 2. 色彩 Token

| Token | 值 | 用途 |
|---|---|---|
| `bg` | `#000000` | 全局背景（纯黑 OLED） |
| `surfaceGlass` | `#0B0D10` | 玻璃面卡片、弹层、FAB |
| `glassBorder` | `#1E2430` | 卡片 1dp 微光描边 |
| `hairline` | `#161B24` | 卡内/导航分隔线 |
| `ink` | `#F5F7FA` | 主文字 |
| `ink2` | `#8A94A6` | 次级文字（蓝灰） |
| `ink3` | `#5B6577` | 三级文字/占位/未选图标 |
| `accentA` | `#22D3EE` | 电光青（渐变起点） |
| `accentB` | `#8B5CF6` | 紫（渐变终点） |
| `ok` | `#2FBF9B` | 在线/健康 |
| `warn` | `#E8B33D` | 告警 |
| `err` | `#E05B4A` | 错误/拦截 |
| `ringIdle` | `#232833` | 圆环静止态（只此一处） |

派生：

- `StarveilAccent.colors` = `[accentA, accentB]`；`StarveilAccent.brush(size)` 为 135° 线性渐变（左上→右下）。
- `StarveilColors.accentWash` = `accentA` 12%，选中行底色，**唯一允许的着色填充**。

对比度（`ContrastTest` 断言）：`ink`/`ink2` 在 `bg` 与 `surfaceGlass` 上 ≥4.5:1；`accentA`/`accentB` 作为图形 ≥3:1；`ok`/`warn`/`err` 作为文字 ≥4.5:1；`ink3` 允许低对比（占位符）。

---

## 3. 字体与字阶

无衬线一体，技术标注用系统等宽；**不引入衬线字体**，不外置字体文件（零体积增量）。

| Token | 规格 | 用途 |
|---|---|---|
| `display` | Sans Light 54sp / 58 | 速率大数字（`tnum`） |
| `ringWord` | Sans Light 30sp / 38 | 圆环中心状态词 |
| `title` | Sans Medium 26sp / 34 | 页面标题 |
| `headline` | Sans Medium 17sp / 24 | 区块标题、行主文 |
| `body` | Sans 15sp / 22 | 正文 |
| `label` | Sans 13sp / 19 | 副文、说明 |
| `tech` | Mono 11sp / 16，字距 0.08em | 单位、协议、分组小标题（大写） |
| `numInline` | Sans Regular 20sp / 26 | 列表内数字（延迟、计数） |
| `log` | Mono 12.5sp / 19 | 日志正文 |

**基线规则**：同一行内数字与单位用 `Modifier.alignByBaseline()`；两列速率的三行（标签/数字/累计）分别共基线。

---

## 4. 间距、圆角、动效

- 间距阶梯：**4 / 8 / 12 / 16 / 24 / 32 / 48**（`StarveilSpacing`）。页面边距 24dp。
- 圆角（`StarveilShapes`）：玻璃卡 20dp、弹层顶部 20dp、输入框 12dp、按钮/芯片/徽标全圆胶囊。
- 动效（`StarveilMotion`）：状态切换 260ms ease-out；圆环连接成功扫光一次（720ms）；**禁止弹簧过冲**。
- 列表底部为 FAB 留白 `StarveilSpacing.fabClearance`（96dp）。

---

## 5. 组件

| 组件 | 规格 |
|---|---|
| `StarveilCard` | `surfaceGlass` + 1dp `glassBorder` + 20dp 圆角，无阴影，不嵌套 |
| `StarveilDivider` | 卡内 `hairline`，默认内缩 16dp 到文字列 |
| `StarveilRow` | 卡内行：左右 16dp、上下 12dp |
| 底部导航 | 纯黑 + 顶部 `hairline`；24dp 线性图标 + 11sp 文字；选中 `ink` + **图标上方 4dp 青色圆点**；未选 `ink3`；五等分，点击域 ≥48dp |
| `StarveilSwitch` | 开 = `accent` 渐变 35% 轨道 + `ink` 拇指；关 = `ink3` 40% + `ink2` 拇指 |
| `StarveilChip` | 玻璃面胶囊 + `glassBorder`；选中描边 `accentA`、文字 `ink` |
| `StarveilBadge` | 胶囊，1dp `accentA` 描边、`accentA` 文字，`tech` 字阶 |
| `StarveilButton` | 玻璃面 + `glassBorder` + `ink` 文字；`secondary` 为无填充描边胶囊 |
| `StarveilFab` | 56dp 玻璃圆 + `accentA` 描边 + `ink` 加号 |
| `StarveilSearchField` | 玻璃胶囊，占位 `ink3`，光标 `accentA` |
| 图标 | 单套线性，1.5dp 描边、圆角端点（`StarveilIcons`） |

---

## 6. 逐页要点

### 6.1 首页

圆环 240dp / 4dp 环宽，**全状态位置尺寸固定**；已连接 = 渐变环 + 30% 外发光，未连接 = `ringIdle`，连接中 = 渐变扫光，告警 = `warn` 环，阻断 = `err` 环。速率两列中轴对称、不装卡。当前节点卡（国旗 22sp + 名称 + 青徽标 + 延迟）与节点健康卡（1.5dp `accentA` 曲线 + 18% 淡出 + `hairline` 基线）各一张玻璃卡。页底留白 32dp。

### 6.2 节点

标题 + 刷新/更多线性图标；玻璃胶囊搜索框；分组芯片行；**整列一张玻璃卡**，行间 `hairline`。行：状态点 8dp → 国旗 → 名称（单行省略）→ 第二行 分组 · 协议（等宽） · 地址；右端延迟与首行共基线。选中行 = `accentWash` + 左缘 2dp `accentA`。长按为底部弹层（Edit 第一）。列表底部留白 96dp。

### 6.3 规则

预设三行 + 去广告开关 + 入口行组在同一张玻璃卡；单选标记 = 青环 + 白点 / `ink3` 空心环。最近流向一张玻璃卡：规则名等宽、右端计数 `numInline`、直连 `ok` / 代理 `accentA`。

### 6.4 日志

全量等宽 12.5sp / 行高 19，裸黑底不装卡、行间无分隔线。级别只用文字色：DEBUG `ink3`、INFO `ink2`、WARN `warn`、ERROR `err`。复制为 `accentA` 文字按钮。

### 6.5 设置

组标题 `tech` 大写 `ink2`，组 = 一张玻璃卡，卡内 `hairline`，组间 32dp。分组：连接 / 按应用分流 / 后台保活 / 备份 / 泄漏自检 / 开发者选项 / 关于。Token 与版本信息用等宽小字 `ink2`。**无「外观」组**。

---

## 7. 可访问性

- 点击域 ≥48dp；底部导航每项 ≥56dp 高。
- `ink2` 在 `bg`/`surfaceGlass` 上 ≥4.5:1（`ContrastTest` 断言）。
- 颜色不是唯一信号：状态点带 `contentDescription`，级别字母本身可读，连接核心有状态 + 动作描述。
- 系统关闭动画时，旋转与扫光停止（`rememberAnimationsEnabled`）。
