# Starveil

> **自用神器**：最新内核、最新技术、性能极致、交互现代、未来科技感十足的科学上网客户端。
>
> Starveil（星纱）——星空 + 面纱，穿越 + 隐身。另起的新项目，与 Mirage 无代码继承关系。

## 愿景

- **自用第一**：只为一个用户（作者）服务。minSdk 29，中英双语，零新手引导，默认专家模式。
- **性能极致**：启动即连、一切操作 <100ms 反馈、断线秒级自愈。
- **未来科技感**：深空黑 + 青蓝辉光 HUD，连接核心是界面的心脏，动效有生命感。
- **独立产品**：自有品牌、自有架构、自有发布节奏；内核走自维护 sing-box fork + patch stack。

## 架构

```
:app ── 单 Activity + Compose Navigation + BottomBar + Hilt 装配
  ├── :feature:dashboard ── 首页 HUD（连接核心、速率、节点选择器）
  ├── :feature:profiles  ── 节点 / 分组 / 订阅
  ├── :feature:rules      ── 路由规则 / 规则集
  ├── :feature:logs       ── 日志查看器 / 诊断
  └── :feature:settings   ── 设置
:core:ui           ── 设计系统 Starveil HUD（主题、动效、组件）
:core:kernel       ── 内核桥接 KernelBridge（App 与内核的唯一契约）
:core:model        ── 干净数据类（Profile / ProxyGroup / Rule）
:core:subscription ── 订阅解析接口
```

**模块边界（硬规则）**：

- `:app` 与 `:feature:*` **禁止直接依赖内核**（无 sing-box 导入、无 JNI、无进程管理），只通过 `KernelBridge`。
- `:core:kernel` 不依赖任何 UI；`:feature:*` 只通过 ViewModel 与 core 交互。
- 新增协议只需改 `:core:kernel` + 对应编辑页，不出现多处重复分发表。

## 模块职责

| 模块 | 职责 | 状态 |
|---|---|---|
| `:app` | 单 Activity、导航图、Hilt 装配、DI 绑定 | 骨架完成 |
| `:feature:dashboard` | 连接核心 + 速率 + 节点选择器占位 | 骨架完成（Fake 内核可跑通点击→连接→速率） |
| `:feature:profiles` | 节点/分组/订阅 | 占位 |
| `:feature:rules` | 路由规则/规则集 | 占位 |
| `:feature:logs` | 日志查看器/诊断 | 占位 |
| `:feature:settings` | 设置 | 占位 |
| `:core:ui` | Starveil HUD 主题、动效规范、ConnectionCore | 骨架完成 |
| `:core:kernel` | `KernelBridge` 接口 + `FakeKernelBridge` + `RealKernelBridge`（TODO） | 骨架完成 |
| `:core:model` | `Profile` / `ProxyGroup` / `Rule` | 骨架完成 |
| `:core:subscription` | `SubscriptionParser` 接口 + Fake 实现 | 骨架完成 |

## 技术栈

- Kotlin 2.2.20 + Jetpack Compose（Material 3 Expressive）+ Navigation Compose
- MVVM + 单向数据流（StateFlow）+ Hilt（KSP）
- Room + DataStore（后续）、Coroutines + Flow、WorkManager
- minSdk 29 / targetSdk 35 / compileSdk 36
- 内核：sing-box fork（Phase 2 接入 `RealKernelBridge`）

## 构建

```bash
# 需要 JDK 17 + Android SDK（compileSdk 36）
gradle :app:assembleDebug
# 或用 Android Studio 打开本目录，IDE 会处理 Gradle Wrapper。
```

> **构建状态**：⚠️ **未验证编译**。骨架编写环境的机器上没有 JDK / Android SDK / Gradle，`assembleDebug` 未实际跑过。请在有 SDK 的机器上首次编译，编译通过后再删掉这条警告。

## 安全红线（写代码前必读）

1. **绝不提交签名密钥**：`.gitignore` 已排除 `*.keystore`、`keystore.properties`、`local.properties`。签名配置只放本地，CI 只做 `assembleDebug` + lint，不签名、不读密钥。
2. **日志绝不含凭证**：配置、订阅 URL、UUID、密码、私钥一律不进日志；`SendLog` 类分享前必须脱敏。
3. **并发红线**：禁止 `GlobalScope`（用结构化并发，Scope 随生命周期取消）；禁止 `allowMainThreadQueries`；迁移失败禁止 `fallbackToDestructiveMigration`。
4. **存储**：节点凭证走加密存储（Keystore + SQLCipher / EncryptedSharedPreferences）；备份默认加密。

## 路线（见实施计划）

Phase 0 止血 → Phase 1 骨架（本仓库）→ Phase 2 内核 → Phase 3 界面 → Phase 4 性能 → Phase 5 智能 → Phase 6 打磨。

## 许可证

本项目采用 **GNU General Public License v3.0**（见 `LICENSE`）。独立 ≠ 闭源：基于 GPL 代码的衍生品必须继续开源并提供源码。
