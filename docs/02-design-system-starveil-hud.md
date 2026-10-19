# 02 · 设计系统定义（Starveil HUD）

> **本文是 Starveil 全部 UI 的唯一真理源。** 写 UI 时有疑问，先查本文；本文与代码冲突时，以本文为准并同步修改代码。
> 与骨架的关系：本文扩展 `core/ui` 已有的 `StarveilColors` / `StarveilTypography` / `StarveilMotion`（`core/ui/src/main/java/com/justnpcx/starveil/core/ui/theme/`），不推翻已有色值；新增 Token 在此定义，落地时补进代码。
> 命名规则：Compose 引用名 = Token 名（如 `StarveilColors.GlowPrimary`）；禁止在 Screen 里写裸色值（`Color(0xFF…)` 只允许出现在 `Theme.kt`）。

---

## 1. 设计语言陈述

**一句话**：Starveil HUD 是"深空黑驾驶舱"——深空黑底色上，青蓝辉光标记一切活着的状态，动效有呼吸感，信息密度为专家服务。

**三个关键词**：

| 关键词 | 含义 | 落地 |
|---|---|---|
| **Glow（辉光）** | 光 = 状态。活着的东西发光，死掉的东西黯淡 | 连接核心、速率数字、健康状态用辉光；装饰性辉光禁止 |
| **Alive（生命感）** | 界面不是静态面板，是活的系统 | 呼吸脉冲、流动弧线、实时曲线；静止超过 2 秒的界面必须有呼吸或微动 |
| **Dense（信息密度）** | 专家模式：首页即全景，不藏一级菜单 | Dashboard 同时可见状态、速率、节点、健康、流量；拒绝"点三次才看到延迟"的设计 |

**三个"不要做"（反模式）**：

1. **不要用 Material 默认 elevation 阴影**——Starveil 不用灰色投影表达层级，用"辉光 + 背景亮度阶梯"（Void → Panel → Raised）表达层级。看到 `elevation` 就说明设计错了。
2. **不要用彩虹色装饰**——色彩只表达状态（连通/断开/警告/错误）。装饰性多彩 = 噪音。图表是唯一例外，且限 5 色内。
3. **不要用"变个图标"表达状态切换**——连接 → 断开必须是"核心熄灭/点亮"的完整动效（颜色 + 呼吸 + 尺寸 + 辉光四项同时变），不是换一张图标。

---

## 2. 色彩 Token 表

### 2.1 深色主题（主，默认）

| Token | 色值 | 用途 | Compose 引用 |
|---|---|---|---|
| `Void` | `#050810` | 最深背景（屏幕底） | `StarveilColors.Void`（已有） |
| `Panel` | `#0B101D` | 一级面板 | `StarveilColors.Panel`（已有） |
| `Raised` | `#121A2E` | 卡片 / 底部 Sheet | `StarveilColors.Raised`（已有） |
| `Overlay` | `#050810` 90% 不透明 | 弹窗 / 遮罩 | `StarveilColors.Overlay`（新增） |
| `Border` | `#1E293B` | 描边（默认） | `StarveilColors.Border`（新增） |
| `BorderGlow` | `#38BDF8` 40% 不透明 | 辉光描边（选中卡片、激活态） | `StarveilColors.BorderGlow`（新增） |
| `TextPrimary` | `#E2E8F0` | 主文字 | `StarveilColors.TextPrimary`（新增；骨架用 `onBackground` 同值，需收敛为 Token） |
| `TextSecondary` | `#94A3B8` | 次要文字 | `StarveilColors.TextDim`（已有，即 TextSecondary，别名统一） |
| `TextDisabled` | `#475569` | 禁用文字 | `StarveilColors.TextDisabled`（新增） |
| `GlowPrimary` | `#38BDF8` | 品牌主色：青蓝辉光 | `StarveilColors.GlowPrimary`（已有） |
| `GlowSecondary` | `#818CF8` | 品牌辅色：靛蓝辉光 | `StarveilColors.GlowSecondary`（已有） |
| `GlowPrimaryDim` | `#38BDF8` 16% 不透明 | 辉光晕（光晕层） | `StarveilColors.GlowPrimaryDim`（新增） |
| `Connected` | `#34D399` | 已连接 / 健康 | `StarveilColors.Connected`（已有） |
| `ConnectedGlow` | `#34D399` 24% 不透明 | 已连接辉光态 | `StarveilColors.ConnectedGlow`（新增） |
| `Connecting` | `#38BDF8` | 连接中 / 工作中 | `StarveilColors.Connecting`（已有） |
| `Disconnected` | `#64748B` | 断开（黯淡，非禁用） | `StarveilColors.Disconnected`（新增；骨架用 `TextDim` 表达断开，需替换为本 Token） |
| `Blocked` | `#F87171` | 被阻断 / 告警 | `StarveilColors.Blocked`（已有） |
| `BlockedGlow` | `#F87171` 24% 不透明 | 被阻断辉光态 | `StarveilColors.BlockedGlow`（新增） |
| `Warning` | `#FBBF24` | 警告（弱网、订阅过期、证书将过期） | `StarveilColors.Warning`（新增） |
| `WarningGlow` | `#FBBF24` 24% 不透明 | 警告辉光态 | `StarveilColors.WarningGlow`（新增） |
| `Error` | `#F87171` | 错误（与 Blocked 同值，语义区分） | `StarveilColors.Error`（新增；骨架 `error = Blocked` 保持） |
| `Chart1` | `#38BDF8` | 图表主线（下行流量） | `StarveilColors.Chart1`（新增） |
| `Chart2` | `#818CF8` | 图表次线（上行流量） | `StarveilColors.Chart2`（新增） |
| `Chart3` | `#34D399` | 图表：延迟 / 健康 | `StarveilColors.Chart3`（新增） |
| `Chart4` | `#FBBF24` | 图表：警告阈值线 | `StarveilColors.Chart4`（新增） |
| `Chart5` | `#F87171` | 图表：错误 / 丢包 | `StarveilColors.Chart5`（新增） |
| `ChartGrid` | `#1E293B` 60% 不透明 | 图表网格线 | `StarveilColors.ChartGrid`（新增） |

**辉光规则**：辉光 = 主色 + 24%（状态辉光）或 16%（光晕层）透明度，叠加 2–3 层描边模拟模糊（见 `ConnectionCore.kt` 已有写法：0.08 / 0.16 两层 + 主环）。禁止用纯白色辉光（连接中弧线用白是现有骨架写法，需改为 `GlowPrimary` 亮化版）。

### 2.2 浅色主题（备选，非设计目标）

| Token | 色值 | 用途 |
|---|---|---|
| 背景 / 表面 / 表面变体 | `#F8FAFC` / `#FFFFFF` / `#E2E8F0` | 骨架 `LightScheme` 已有，保持 |
| 主 / 辅 / 三级 | `#0369A1` / `#4F46E5` / `#047857` | 骨架 `LightScheme` 已有，保持 |
| 主文字 / 次文字 | `#0F172A` / `#64748B` | 骨架 `LightScheme` 已有，保持 |
| 错误 | `#DC2626` | 骨架 `LightScheme` 已有，保持 |
| 警告（新增） | `#D97706` | 浅色下警告色需加深以保对比度 |
| 描边（新增） | `#CBD5E1` | 浅色描边 |

**浅色主题原则**：浅色是"能用"，不是"设计目标"。新组件只按深色设计，浅色做色值映射即可，不单独设计。

### 2.3 对比度要求

- 主文字 / 背景 ≥ **7:1**（`#E2E8F0` on `#050810` ≈ 13:1，通过）。
- 次要文字 / 背景 ≥ **4.5:1**（`#94A3B8` on `#050810` ≈ 6:1，通过）。
- 禁用文字可放宽至 3:1，但禁用态必须同时用"不可点 + 图标"双重表达，不能只靠颜色。

---

## 3. 字体排印

**字阶表**（基于骨架 `StarveilTypography`，补齐缺失项；`letterSpacing` 全部显式定义，禁止默认）：

| 样式 | 字号 / 字重 / 行高 / 字距 | 使用场景 | Compose 引用 |
|---|---|---|---|
| `displayLarge` | 48sp / Light / 56 / 0 | 速率大数字（Dashboard） | 已有（Mono） |
| `displayMedium` | 36sp / Light / 44 / 0 | 速率数字（次级） | 已有（Mono） |
| `headlineLarge` | 32sp / SemiBold / 40 / 0 | 地球/星图标题（新增） | 新增 |
| `headlineMedium` | 28sp / SemiBold / 36 / 0 | 大标题（新增） | 新增 |
| `titleLarge` | 20sp / SemiBold / 28 / 0.15 | 屏标题 | 已有 |
| `titleMedium` | 16sp / SemiBold / 24 / 0.15 | 卡片标题、节点名 | 已有 |
| `titleSmall` | 14sp / SemiBold / 20 / 0.1 | 列表项标题（新增） | 新增 |
| `bodyLarge` | 16sp / Regular / 24 / 0.5 | 正文 | 已有 |
| `bodyMedium` | 14sp / Regular / 20 / 0.25 | 正文（次级）、描述 | 已有 |
| `bodySmall` | 12sp / Regular / 16 / 0.4 | 辅助说明（新增） | 新增 |
| `labelLarge` | 14sp / Medium / 20 / 0.1 | 按钮文字 | 已有 |
| `labelMedium` | 12sp / Medium / 16 / 0.5 | 标签、分组名（新增） | 新增 |
| `labelSmall` | 11sp / Medium / 16 / 0.5 | 速率单位、时间戳 | 已有（Mono） |

**数字强制等宽**：速率、延迟、流量、端口、时间戳一律 Mono。理由：数字跳变时等宽字体不抖动（骨架 `Typography.kt` 注释已说明，本文定为硬规则）。

**字体**：系统默认 sans-serif + `FontFamily.Monospace` 数字。暂不引入自定义字体文件（减小包体；未来如需"科技感字体"，评估 JetBrains Mono 或 Space Grotesk 的体积与授权后再定，禁止直接打包未验证授权的字体）。

---

## 4. 间距 / 圆角 / 阴影

### 4.1 间距刻度（禁止使用刻度外的值）

| Token | 值 | 用途 |
|---|---|---|
| `SpaceXS` | 4dp | 图标与文字间距 |
| `SpaceS` | 8dp | 列表项内间距、紧凑元素 |
| `SpaceM` | 12dp | 卡片内小间距 |
| `SpaceL` | 16dp | 屏边距、卡片内距（默认） |
| `SpaceXL` | 24dp | 区块间距（核心与速率之间） |
| `SpaceXXL` | 32dp | 大区块分隔 |

**现有问题**：`DashboardScreen.kt` 用了裸 `16.dp` / `24.dp` / `8.dp`，与刻度恰好一致但未引用 Token。落地时收敛为 `StarveilSpacing` 对象，禁止裸值。

### 4.2 圆角

| 组件 | 圆角 |
|---|---|
| 卡片 | 20dp |
| 按钮 | 12dp（主要按钮）；胶囊（Full）用于小标签/开关 |
| 连接核心 | 圆形（110dp 半径，即 `ConnectionCore` 的 220dp 尺寸） |
| 底部 Sheet / 对话框 | 顶部 28dp |
| 节点卡片 | 16dp |

### 4.3 阴影（辉光代替 elevation）

- **禁止** Material 默认 `elevation` 阴影（灰色投影）。
- 层级用"背景亮度 + 辉光描边"表达：选中卡片 = `BorderGlow` 描边 + `GlowPrimaryDim` 光晕层；激活态 = 辉光强度 +1 档。
- 弹窗用 `Overlay` 遮罩 + 28dp 圆角，不用投影。

---

## 5. 动效规范

### 5.1 时长分级

| 级别 | 时长 | 用途 |
|---|---|---|
| 瞬时 | 120ms | 按压反馈、开关切换 |
| 快 | 220ms | 状态切换（连接→断开）、颜色过渡（骨架 `StateTransition` 已有） |
| 标准 | 350ms | 页面切换、卡片展开 |
| 慢 | 500ms | 大布局变化、首次引导 |
| 环境 | 2600ms 呼吸 / 1100ms 旋转 | 持续氛围动效（骨架 `BREATH_PERIOD_MS` / `SPIN_PERIOD_MS` 已有） |

### 5.2 缓动与弹簧

| 场景 | 参数 |
|---|---|
| 状态切换缓动 | `CubicBezierEasing(0.2, 0.0, 0.0, 1.0)`（骨架 `StateTransition` 已有） |
| 按压弹簧 | `dampingRatio = MediumBouncy`，`stiffness = StiffnessMediumLow`（骨架 `pressSpring()` 已有） |
| 按压缩放 | 0.96（骨架 `PRESSED_SCALE` 已有） |
| 列表项出现 | 220ms + 20dp 位移 + 透明度 0→1，逐项延迟 30ms（禁止同时弹出） |

### 5.3 状态切换动效（硬规则）

连接 → 断开、断开 → 连接等状态切换，必须同时变化四项：**颜色 + 呼吸（有/无）+ 核心尺寸 + 辉光强度**，时长 220ms。禁止只换图标/只换颜色。

### 5.4 触觉反馈映射表

| 操作 | 触觉 |
|---|---|
| 连接成功 | 短促双击（`HapticFeedbackType.LongPress` 轻量版或 `TextHandleMove`，落地时二选一并统一） |
| 断开 | 单次轻震 |
| 节点切换成功 | 单次轻震 |
| 断线 / 被阻断 | 长震（警告感） |
| 按钮按压 | 无（避免噪音）；仅核心、开关、关键操作给触觉 |
| 列表滑动 | 无 |

**注意**：骨架中尚无触觉代码（`ConnectionCore.kt` 无 haptic）。本文为定义，落地时在 `ConnectionCore` 与关键操作处统一接入，禁止各处自行发明震动。

### 5.5 性能预算（动效）

- 动画期间帧率 ≥ 60fps（目标 120fps 设备上 120fps）。
- 无限动画（呼吸/旋转）在界面不可见时必须暂停（`Lifecycle` 感知）；当前骨架 `ConnectionCore` 的 `rememberInfiniteTransition` 无条件运行，需在 Phase 3 修复（离开页面 / 息屏时暂停）。
- 禁止在动画中做布局尺寸动画（骨架 `Motion.kt` 注释已有此规则：只动 alpha/scale/rotation）。

---

## 6. 组件规范

### 6.1 ConnectionCore（核心组件）

| 项 | 规范 |
|---|---|
| 尺寸 | 默认 220dp；小尺寸 160dp（Widget/紧凑场景）；大尺寸 280dp（平板，未来）——尺寸参数化，禁止写死多份 |
| 结构 | 外层光晕（2 层描边，alpha 0.08 / 0.16）+ 主环（6dp 描边）+ 核心圆点 |
| 四态 | CONNECTED：`GlowPrimary` + 呼吸（1.0→1.06，2600ms）+ 核心 22dp；CONNECTING：旋转充能弧（1100ms/圈）+ 核心 22dp；DISCONNECTED：`Disconnected` 黯淡 + 静态 + 核心 14dp；BLOCKED：`Blocked` + 红色警戒环 + 核心 22dp（骨架已有，尺寸变化需补 220ms 动画，当前骨架是瞬变） |
| 交互 | 点击 = 连接/断开；长按 = 弹出节点快切；触觉见 §5.4 |
| 无障碍 | `contentDescription` = "连接状态：已连接，点击断开"（随状态变）；最小触摸目标 48dp（220dp 已远超） |

**现有缺口**：骨架 `ConnectionCore.kt` 的辉光是"堆叠描边模拟模糊"（注释自认 placeholder），核心尺寸变化无动画，旋转/呼吸动画无条件运行，无触觉，无语义描述。Phase 3 逐项补齐。

### 6.2 速率显示

- 结构：标签（`labelSmall`，`↓ DOWN` / `↑ UP`）+ 大数字（`displayMedium` Mono）+ 累计（`bodyMedium`，`total 1.2 GB`）。
- 数字格式化：`B/s` / `KB/s` / `MB/s`（1 位小数）/ `GB/s`；禁止出现 `1023 KB/s`（应进位为 `1.0 MB/s`）；零值显示 `0 B/s`，不显示 `--`。
- 更新频率：前台 1s，后台降频；数字变化用 220ms 颜色/透明度过渡，禁止数字本身做滚动动画（性能）。

### 6.3 节点卡片

- 结构：节点名（`titleMedium`）+ 延迟迷你曲线（sparkline，见 §7.2）+ 延迟数字（Mono）+ 分组标签 + 状态点（绿/灰/红）。
- 状态：选中 = `BorderGlow` 描边 + 光晕；超时 = 延迟显示 `timeout` + 灰色；手动排序时禁止自动重排（用户顺序优先于延迟排序）。
- 尺寸：卡片 16dp 圆角，内距 16dp，最小高度 72dp。

### 6.4 分组标签

- 胶囊形（Full 圆角），`labelMedium`，背景 `Raised`，文字 `TextSecondary`；选中态背景 `GlowPrimary` 20% + 文字 `GlowPrimary`。

### 6.5 速率曲线图

- 见 §7.1。组件名：`TrafficChart`。

### 6.6 按钮

| 类型 | 样式 |
|---|---|
| 主按钮 | 背景 `GlowPrimary`，文字深空黑，12dp 圆角，`labelLarge` |
| 次按钮 | 背景 `Raised`，文字 `TextPrimary`，`Border` 描边 |
| 危险按钮 | 背景 `Blocked` 20%，文字 `Blocked`，用于"清除全部 / 删除" |
| 图标按钮 | 48dp 触摸目标，图标线性 1.5dp 描边 |

### 6.7 BottomBar

- 5 个目的地（Dashboard / Nodes / Rules / Logs / Settings），选中态：图标填充 + `GlowPrimary` + 标签；未选中：线性图标 + `TextSecondary`。
- 高度 80dp（含手势区）；背景 `Panel` + 顶部 1dp `Border` 分隔线；无阴影。

### 6.8 对话框 / Toast / Snackbar

- 对话框：`Raised` 背景，顶部 28dp 圆角，按钮右对齐；危险操作对话框标题用 `Blocked` 色警示。
- Snackbar：`Raised` 背景 + `BorderGlow` 左侧 4dp 色条；错误 Snackbar 色条 `Blocked`。
- Toast：仅用于非关键提示；关键操作（连接失败、订阅失败）必须用 Snackbar + 操作按钮（"重试" / "查看"），禁止只用 Toast。

### 6.9 空状态 / 错误状态

| 场景 | 内容 |
|---|---|
| 无节点 | 核心黯淡 + "暂无节点" + 主按钮"导入订阅" + 次按钮"手动添加" |
| 无订阅 | 同上，引导到订阅导入 |
| 订阅失败 | 错误 Snackbar + "查看详情" + 保留上次可用配置（禁止清空已有节点） |
| 内核崩溃 | 核心 BLOCKED 态 + "内核已停止，点击重试" + "查看日志"按钮 |
| 无网络 | 核心 DISCONNECTED + "无网络连接"（不误报为"内核故障"） |

---

## 7. 可视化规范

### 7.1 流量曲线（`TrafficChart`）

- 采样：1 秒 1 点，环形缓冲 48 点（与骨架 Dashboard 的 sparkline 一致）。
- 渲染：下行 `Chart1` 实线 + 下方 16% 填充；上行 `Chart2` 虚线；网格 `ChartGrid`；峰值点标注小圆点 + 数值。
- 交互：点击查看某点数值；缩放留待 Phase 5（不阻塞首版）。
- 性能：Canvas 绘制，禁止每帧重建 Path 对象；数据为空时画空网格 + "暂无流量"，不画零线。

### 7.2 节点健康历史图

- 延迟 EMA 曲线（`Chart3`），30 分钟窗口；可用率以背景条显示（绿 >99% / 黄 95–99% / 红 <95%）。
- 丢包用 `Chart5` 红点标记在曲线下方。
- 数据不足（<5 个样本）时显示"采集中"，不画误导性曲线。

### 7.3 地球 / 星图（实验室功能）

- 渲染方式：**先 Canvas 2D 正交投影**（轻量，零依赖）；只有当 2D 版本帧率达标且用户反馈积极时，才评估 OpenGL / 3D 地球。
- 内容：节点位置（经纬度来自节点配置或 GeoIP，缺失则不显示该节点，不编造位置）+ 流量弧线（当前活跃连接）+ 本地位置光点。
- 性能预算：CPU <5%，内存 <20MB；**离开页面立即释放**（取消动画、清空画布）；默认关闭，设置中"实验室功能"开关开启。
- 禁止：默认开启、后台运行、为星图单独申请定位权限（位置只来自节点配置，不取设备 GPS）。

### 7.4 日志配色

| 级别 | 颜色 |
|---|---|
| INFO | `TextSecondary` |
| WARN | `Warning` |
| ERROR | `Error` |
| 规则命中高亮 | `GlowPrimary` 背景 16% + `TextPrimary` |
| 敏感内容 | 脱敏后显示为 `••••`（见文档一 §6-11，日志层脱敏） |

- 字体：等宽 11sp / 行高 15sp（骨架日志查看器沿用 Mirage 的 11sp/15sp 经验值）。
- 筛选：级别筛选 + 关键词搜索；新行到达时若用户未在滚动则跟随到底。

---

## 8. 图标与触感

- **图标风格**：线性图标，描边 1.5dp，24dp 视图框；选中态用填充版（Material Symbols 的 Filled/Outlined 对）。
- **启动图标**：深空黑底 + 青蓝辉光核心（ConnectionCore 的简化版：圆环 + 圆点），自适应图标（Adaptive Icon），禁止用 Mirage 旧图标或 NekoBox 粉。
- **触觉**：见 §5.4 映射表。

---

## 9. 无障碍

| 项 | 要求 |
|---|---|
| 对比度 | 主文字 ≥7:1，次要 ≥4.5:1（见 §2.3） |
| 最小触摸目标 | 48dp × 48dp（所有可交互元素；`ConnectionCore` 220dp 已超，BottomBar 图标需包 48dp 触摸区） |
| ContentDescription | 每个可交互元素必须有；状态变化时描述同步变（如 ConnectionCore）；装饰性元素 `contentDescription = null` 并标注 |
| 焦点顺序 | TalkBack 焦点按视觉顺序：核心 → 速率 → 节点选择器 → 列表；禁止焦点跳变 |
| 大字体 | 系统字体 1.3x / 1.5x 下布局不溢出、不截断；数字用 Mono 但允许换行 |
| 动效减弱 | 遵循系统"减弱动效"设置：开启时呼吸/旋转动画降为静态辉光，状态切换保留 120ms 颜色过渡 |

## 组件收敛（2026-10-19）

`core/ui/components/StarveilCard.kt` 与 `StarveilChip.kt` 是唯一的卡片与筛选 chip
实现：

- **Card**：`surfaceVariant`、16dp 圆角、idle 无边框；选中才加 2dp glow 边框与 16%
  halo。不要再用 `StarveilColors.Border` 给 idle 卡片描边——那是深色 HUD 的颜色，
  浅色主题下就是一条黑边。
- **Chip**：固定 16dp 圆角（**不要 percent**，percent 会随文本长度变成圆/胶囊两种
  形状）；选中为 24% primary 混合 + 1dp 55% primary 边框。

新代码必须使用这两个组件。存量替换清单：

| 页面 | 位置 | 现状 | 替换为 |
|---|---|---|---|
| Dashboard | `DashboardScreen.kt:210` 节点健康区 | `Card` | `StarveilCard` |
| Dashboard | `DashboardScreen.kt:468` 当前节点卡 | `Card` | `StarveilCard` |
| Rules | `RulesScreen.kt:883` 路由预设卡 | `Card` | `StarveilCard` |
| Rules | `RulesScreen.kt:399/572` 规则行 | `Surface` | 行可保留；若成卡用 `StarveilCard` |
| Logs | 无卡片 | — | 新增卡片用 `StarveilCard` |
| Settings | 无卡片 | — | 新增卡片用 `StarveilCard` |
| Profiles | 已完成迁移 | — | — |

空状态统一用现有 `EmptyState`（Profiles 已用）。
