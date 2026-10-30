# 0.9.0 色彩与对比度实测表

本表由 `core/ui/src/test/java/com/justnpcx/starveil/core/ui/theme/ContrastTest.kt`
的 `report every measured ratio` 用例直接输出（WCAG 2.1 相对亮度），不是手算。

复现：

```
./gradlew :core:ui:testDebugUnitTest -i | grep CONTRAST_
```

## 1. 双模式对比度（方案六 §10 清单）

| 组合 | 浅色 | 深色 | 阈值 |
|---|---|---|---|
| ink on bg | 15.38 | 14.47 | 4.5 |
| ink on card | 16.48 | 12.86 | 4.5 |
| ink2 on bg | 8.76 | 10.90 | 4.5 |
| ink2 on card | 9.39 | 9.69 | 4.5 |
| accent on bg | 5.96 | 10.80 | 4.5 |
| selInk on selBg | 4.92 | 7.20 | 4.5 |
| err on card | 6.54 | 9.67 | 4.5 |
| warn on bg | 5.13 | 10.92 | 4.5 |
| warn on card | 5.50 | 9.71 | 4.5 |

全部通过 AA（4.5:1）。深色 warn 定案值 **#FFB59E**（10.92 on bg / 9.71 on card）。

## 2. 控件色值（FIX-9 自查）

| 控件 | 浅色 | 深色 | 来源 |
|---|---|---|---|
| Switch 轨道（开） | #0B57D0 | #A8C7FA | `accent` |
| Switch 滑块（开） | #FFFFFF | #FFFFFF | 常量 |
| Switch 轨道（关） | #444746 @40% | #C4C7C5 @40% | `ink2` @0.40 |
| Switch 滑块（关） | #444746 | #C4C7C5 | `ink2` |
| RadioButton 选中 | #0B57D0 | #A8C7FA | `accent`（显式绑定） |
| RadioButton 未选 | #444746 | #C4C7C5 | `ink2`（显式绑定） |
| 设置页自绘单选环/点 | #0B57D0 / #444746 | #A8C7FA / #C4C7C5 | `accent` / `ink3` |
| CircularProgressIndicator | #0B57D0 | #A8C7FA | `colorScheme.primary` = `accent` |
| Checkbox | 未使用 | 未使用 | — |
| Slider | 未使用 | 未使用 | — |

结论：没有 Material 默认蓝残留。Switch 是自绘的（`StarveilSwitch`），本来就是
`accent`；两处 M3 `RadioButton` 原本走 `colorScheme.primary`（同样等于
`accent`），本轮改为显式传入 `RadioButtonDefaults.colors`，避免库升级后
默认值漂移。Checkbox 与 Slider 在本版本中没有任何调用点。
