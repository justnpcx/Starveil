# Starveil

> **自用神器**：最新内核、最新技术、性能极致、交互现代、未来科技感十足的科学上网客户端。
>
> Starveil（星纱）——星空 + 面纱，穿越 + 隐身。另起的新项目，与 Mirage 无代码继承关系。

> **⚠️ 内测版 0.7.1**
>
> 功能已经齐了，但**多项性能指标和真机行为还没验证**——预设分流、按应用分流、XHTTP+REALITY 目前只在离屏环境用真实内核验过配置与路由判定，没有端到端跑过手机。
> 已知问题逐条列在 [CHANGELOG 的 Known issues](CHANGELOG.md#known-issues)。
> 这是内测版：**不承诺 SLA**，也不保证数据不丢——重要配置请先导出备份。
>
> 反馈：提 [Issue](https://github.com/justnpcx/Starveil/issues)；隐私说明见 [docs/10-privacy-policy.md](docs/10-privacy-policy.md)，App 内「设置 → 关于 → 隐私政策」是同一份内容。
>
> 崩溃上报默认开启（设置 → 诊断可关闭）；节点地址、链接、UUID 和密钥在发送前会被替换。
>
> 更新走 GitHub Releases（设置 → 关于 → 检查更新），下载后校验 SHA-256 与签名证书，再由系统安装器确认。

## 愿景

- **自用第一**：只为一个用户（作者）服务。minSdk 29，中英双语，三步首次引导（可跳过），之后零打扰。
- **性能极致**：启动即连、一切操作 <100ms 反馈、断线秒级自愈。
- **未来科技感**：深空黑 + 青蓝辉光 HUD，连接核心是界面的心脏，动效有生命感。
- **独立产品**：自有品牌、自有架构、自有发布节奏；内核走自维护 sing-box fork + patch stack。

## 架构

```
:app ── 单 Activity + Compose Navigation + BottomBar + Hilt 装配
  ├── :feature:dashboard ── 首页 HUD（连接核心、速率、节点选择器）
  ├── :feature:profiles  ── 节点 / 分组 / 订阅
  ├── :feature:rules      ── 路由规则 / 规则集
  ├── :feature:editor     ── 节点编辑
  ├── :feature:logs       ── 日志查看器 / 诊断
  └── :feature:settings   ── 设置 / 开发者选项
:core:ui           ── 设计系统 Starveil HUD（主题、动效、组件）
:core:kernel       ── 内核桥接 KernelBridge（App 与内核的唯一契约）
:core:model        ── 干净数据类（Profile / ProxyGroup / Rule / RulePack）
:core:data         ── Room / DataStore / 规则集与规则包下载 / Keystore
:core:subscription ── 订阅解析（base64 / Clash YAML / sing-box JSON / SIP008）
:core:probe        ── 延迟探测、健康采样、泄漏自检、内核流向日志
```

**模块边界（硬规则）**：

- `:app` 与 `:feature:*` **禁止直接依赖内核**（无 sing-box 导入、无 JNI、无进程管理），只通过 `KernelBridge`。
- `:core:kernel` 不依赖任何 UI；`:feature:*` 只通过 ViewModel 与 core 交互。
- 新增协议只需改 `:core:kernel` + 对应编辑页，不出现多处重复分发表。

## 模块职责

| 模块 | 职责 | 状态 |
|---|---|---|
| `:app` | 单 Activity、导航图、Hilt 装配、DI 绑定、Widget / Tile / 通知动作 | 完成 |
| `:feature:dashboard` | 连接核心、速率、节点健康、自动选择 | 完成 |
| `:feature:profiles` | 节点 / 分组 / 订阅 / 导入 / 分享 | 完成 |
| `:feature:editor` | 节点编辑 | 完成 |
| `:feature:rules` | 路由规则 / 规则集 / 规则市场 / 最近流向 | 完成 |
| `:feature:logs` | 日志查看器 / 搜索 / 导出 | 完成 |
| `:feature:settings` | 设置 / 按应用分流 / 备份 / 隐私 / 本地 API | 完成 |
| `:core:ui` | Starveil HUD 主题、动效规范、ConnectionCore | 完成 |
| `:core:kernel` | `KernelBridge` + `RealKernelBridge` + libbox（sing-box 1.14.3 fork） | 完成 |
| `:core:model` | `Profile` / `ProxyGroup` / `Rule` / 健康评分 / 选点 / 地区识别 | 完成 |
| `:core:subscription` | 订阅解析（base64 / Clash YAML / sing-box JSON / SIP008）与分享链接 | 完成 |
| `:core:probe` | 延迟探测、健康采样、泄漏自检、内核流向日志 | 完成 |

## 技术栈

- Kotlin 2.2.20 + Jetpack Compose（Material 3 Expressive）+ Navigation Compose
- MVVM + 单向数据流（StateFlow）+ Hilt（KSP）
- Room + DataStore、Coroutines + Flow、WorkManager、Glance（Widget）
- minSdk 29 / targetSdk 35 / compileSdk 36
- 内核：自维护 sing-box fork（`starveil-1.14.x`，v1.14.3 + patch stack），经 libbox JNI 接入

## 构建

```bash
# 需要 JDK 17 + Android SDK（compileSdk 36）
./gradlew :app:assembleDebug

# 发布前必跑：单元测试 + 翻译校验 + lint
./gradlew testDebugUnitTest checkTranslations lintDebug
```

`checkTranslations` 会比对每个模块的 `values/` 与 `values-*`：键集合必须双向一致，格式化占位符必须完全相同。缺 `%1$s` 或把 `%1$s`/`%2$s` 写反，编译和 lint 都发现不了，只有在中文界面打开那一屏时才会崩或显示错值。

内核 AAR 的构建方式见 [docs/04-libbox-build.md](docs/04-libbox-build.md)——**一次只编一个 ABI**，全 ABI 的 `gomobile bind` 会把内存打爆。

## 安全红线（写代码前必读）

1. **绝不提交签名密钥**：`.gitignore` 已排除 `*.keystore`、`keystore.properties`、`local.properties`。签名配置只放本地，CI 只做 `assembleDebug` + lint，不签名、不读密钥。
2. **日志绝不含凭证**：配置、订阅 URL、UUID、密码、私钥一律不进日志；`SendLog` 类分享前必须脱敏。
3. **并发红线**：禁止 `GlobalScope`（用结构化并发，Scope 随生命周期取消）；禁止 `allowMainThreadQueries`；迁移失败禁止 `fallbackToDestructiveMigration`。
4. **存储**：节点凭证走加密存储（Keystore + SQLCipher / EncryptedSharedPreferences）；备份默认加密。

发现安全问题请按 [SECURITY.md](SECURITY.md) **私密上报**，不要开公开 issue。

## 路线

| 里程碑 | 状态 |
|---|---|
| M0 止血 · M1 骨架 · M2 内核 · M3 界面 | 已交付（0.3.0） |
| M4 性能 | 代码完成；冷启动与配置生成有实测，其余等真机 |
| M5 智能 | 代码完成（自动选点 / 断线切换 / 按应用分流 / 地区分组） |
| M6 打磨 | 代码完成（备份 / 通知 / Tile / Widget / 无障碍） |
| **M7 内测** | **当前：中英双语、隐私政策、反馈通道已就位；崩溃上报待接入** |
| P3 公开准备 | 代码完成（四种订阅格式 / 规则市场 / 本地 API / 三步引导 / QUIC 结论）；真机验收待做 |
| M8 公开 | 未开始（开仓与 F-Droid 是用户决策） |

## 文档

- [产品指南与里程碑定义](docs/03-top-tier-product-guide.md)
- [隐私政策](docs/10-privacy-policy.md)
- [真机验证记录](docs/11-device-verification.md)
- [QUIC 连接迁移预研](docs/13-quic-migration-spike.md)
- [本地 Clash 兼容 API](docs/14-local-api.md)
- [规则市场](docs/15-rule-market.md)
- [双轨制 UX 设计（P3，定稿）](docs/16-dual-mode-ux.md)
- [基准对比方法（P3，定稿）](docs/17-benchmark-report.md)
- [未完成任务总览](docs/18-open-tasks.md)
- [安全策略与漏洞上报](SECURITY.md)
- [更新日志](CHANGELOG.md)
- [libbox 构建](docs/04-libbox-build.md)
- [发布流程](docs/12-release.md)

## 许可证

本项目采用 **GNU General Public License v3.0**（见 `LICENSE`）。独立 ≠ 闭源：基于 GPL 代码的衍生品必须继续开源并提供源码。
