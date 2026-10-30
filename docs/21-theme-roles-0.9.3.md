# 0.9.3 主题角色赋值与对比度实测

本文件是任务书 **TOK-3**（Theme 全角色显式赋值说明）与 **TOK-4**（对比度数值表）的交付物。
数字由 `core/ui/src/test/java/com/justnpcx/starveil/core/ui/theme/ContrastTest.kt`
直接打印，不是手算。

复现：

```
./gradlew :core:ui:testDebugUnitTest -i | grep CONTRAST_
```

## 1. TOK-3：Material 颜色角色的赋值位置

主题只有一个入口：`core/ui/.../theme/Theme.kt` 的 `StarveilPalette.scheme(base)`。
它把 Material 的 `ColorScheme` 从 `lightColorScheme()` / `darkColorScheme()` 取来后
**逐项覆盖**，没有一项留给默认值 —— 默认值就是那条淡紫（`primaryContainer` 与
`surfaceContainer*` 系列的历史漏网点）。

| ColorScheme 角色 | 赋值 | 说明 |
|---|---|---|
| primary / secondary / tertiary | `accent` | 功能蓝，不是品牌渐变 |
| onPrimary / onSecondary / onTertiary | 浅 `#FFFFFF` / 深 `#00325B` | 蓝底上的字 |
| primaryContainer / secondaryContainer / tertiaryContainer | `selBg` | 选中药丸的底 |
| onPrimaryContainer / onSecondaryContainer / onTertiaryContainer | `selInk` | 选中药丸上的字与图标 |
| background | `bg` | 页面 |
| onBackground | `ink` | 正文 |
| surface | `card` | 卡片、弹窗、面板 |
| onSurface | `ink` | 卡片上的正文 |
| surfaceVariant | `segBg` | 输入框、分段控件槽 |
| onSurfaceVariant | `ink2` | 次级文字 |
| surfaceContainerLowest / Low / Container / High / Highest | `card` | 全部收敛到卡片色，杜绝分层淡紫 |
| surfaceBright | `card` | 同上 |
| surfaceDim | `bg` | 同上 |
| outline | `chipB` | 未选中描边 |
| outlineVariant | `line` | 分隔线 |
| error / onError | `err` / 浅白 · 深 `#3B0907` | 失败 |
| errorContainer / onErrorContainer | `card` / `err` | 失败块 |
| scrim | 黑 | 遮罩 |
| inverseSurface / inverseOnSurface | `ink` / `card` | Snackbar 一类反色面 |
| inversePrimary | `accent` | — |
| surfaceTint | 透明 | 抬升只画阴影，不染色 |
| primaryFixed / primaryFixedDim / secondaryFixed / secondaryFixedDim / tertiaryFixed / tertiaryFixedDim | `selBg` | 固定容器，不随明暗反转 |
| onPrimaryFixed / onPrimaryFixedVariant / onSecondaryFixed / onSecondaryFixedVariant / onTertiaryFixed / onTertiaryFixedVariant | `selInk` | 同上 |

0.9.3 新增的角色（`StarveilPalette` 里的自有令牌，不进 Material scheme，按需读取）：

| 令牌 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `accentSoft` | #E8F0FE | #26303F | 智能卡、链接图标的底 |
| `floatDisc` | #FFFFFF | #232426 | 环盘加号的盘面 |
| `swOff` | #E9EAEC | #36373B | 开关关态轨道 |
| `swOut` | #747775 | #8E918F | 开关关态描边与圆块 |
| `swOnTh` | #FFFFFF | #0B57D0 | 开关开态圆块 |
| `swChk` | #0B57D0 | #D3E3FD | 开关开态对勾 |
| `hubScenes` | #EFEBFB / #6C4BD8 | #2C2440 / #B79CFF | 聚合页场景行（底 / 字形） |
| `hubLogs` | #E8F0FE / #0B57D0 | #22344D / #8AB4F8 | 日志行 |
| `hubDiagnostics` | #E4F3EC / #137333 | #1E3328 / #81C995 | 诊断自检行 |
| `hubSettings` | #FDEEE2 / #D96C00 | #3A2A1A / #F0A35E | 设置行 |
| `hubAboutBed` | #F1EFFD | #2A2440 | 关于行的 C2 图标底（图形本身不翻色） |

## 2. TOK-4：对比度实测

正文阈值 4.5:1，次级/图形阈值 3:1（WCAG 2.1 相对亮度）。

| 组合 | 浅色 | 深色 | 阈值 | 结果 |
|---|---|---|---|---|
| ink on bg | 15.38 | 14.47 | 4.5 | 通过 |
| ink on card | 16.48 | 12.86 | 4.5 | 通过 |
| ink2 on bg | 8.76 | 10.90 | 4.5 | 通过 |
| ink2 on card | 9.39 | 9.69 | 4.5 | 通过 |
| accent on bg | 5.96 | 10.80 | 4.5 | 通过 |
| selInk on selBg | 4.92 | 7.20 | 4.5 | 通过 |
| err on card | 6.54 | 9.67 | 4.5 | 通过 |
| warn on bg | 5.13 | 10.92 | 4.5 | 通过 |
| warn on card | 5.50 | 9.71 | 4.5 | 通过 |
| 聚合·场景 字形 on 底 | 4.94 | 6.43 | 3.0 | 通过 |
| 聚合·日志 字形 on 底 | 5.57 | 5.98 | 3.0 | 通过 |
| 聚合·诊断 字形 on 底 | 5.20 | 6.88 | 3.0 | 通过 |
| 聚合·设置 字形 on 底 | 3.04 | 6.62 | 3.0 | 通过（余量最小，见注） |
| 开关 关态圆块 on 轨道 | 3.76 | 3.73 | 3.0 | 通过 |
| 开关 开态对勾 on 圆块 | 6.39 | 4.92 | 3.0 | 通过 |
| accent on accentSoft | 5.57 | 7.75 | 3.0 | 通过 |
| 渐变最亮端（#4285F4）on 盘面 | 3.56 | 4.36 | 3.0 | 通过 |
| 渐变粉端（#E85D9E）on 盘面 | 3.24 | 4.80 | 3.0 | 通过 |

**注**：浅色「设置」行 3.04:1 是表中余量最小的一项。字形是 20dp 线形图标、底是
44px 方块，属于 WCAG 的非文本对比范畴（3:1），已达标；若后续把该行改为文字，
需要重新取色。深色 `warn #FFB59E` 在 card 上 9.71:1，按定案值保留，未做任何替换。
