# 01 · 架构扩展性审计（Starveil 骨架）

> 审计对象：`~/workspace/starveil/`（54 个文件，2026-10-13 骨架提交 `25ea16a`）
> 审计方法：逐文件阅读全部 `.kts`、`.kt`、Manifest、CI、README。结论只依据骨架中实际存在的内容，未看到的写"骨架中未包含"，不推测。
> Verdict 口径：**通过** = 骨架已满足要求；**有缺口** = 有基础但关键部分缺失；**缺失** = 骨架中完全没有。

## 结论总览

| # | 审计项 | Verdict | 一句话 |
|---|---|---|---|
| 1 | 模块边界 | 通过（有缺口） | 依赖单向无环；但 DI 绑定使 `:app` 直接依赖 `:core:kernel`，且 10 个模块的 `android{}` 配置重复 |
| 2 | 内核桥接 | 有缺口 | 接口干净但不足以承载真实 sing-box：缺状态流、错误类型、日志回调、批量测速、配置重载 |
| 3 | 数据层 | 缺失 | Room / DataStore 只在 version catalog 里声明，没有任何 Entity / DAO / DataStore 代码 |
| 4 | 导航与状态 | 有缺口 | 单 Activity + Compose Navigation 结构对；缺深链、SavedStateHandle、进程恢复 |
| 5 | 可扩展点 | 混合 | 新增界面 1 处（通过）；新增协议 4+ 处（有缺口）；新增设置项缺失 |
| 6 | 自用 → 公开发布差距 | 缺失为主 | i18n、隐私政策、崩溃上报、更新通道、备份加密、无障碍等均未包含 |
| 7 | 未来功能承载力 | 有缺口 | 6 项未来功能中，2 项可直接承载，4 项需新增模块/接口 |
| 8 | 工程化 | 有缺口 | CI 只有构建 + lint；无单元测试、无 UI 测试、无 Baseline Profile、无静态检查 |

---

## 1. 模块边界

### 1.1 实际依赖图（依据各模块 `build.gradle.kts`）

```
:app → :feature:dashboard, :feature:profiles, :feature:rules, :feature:logs, :feature:settings
:app → :core:ui, :core:kernel, :core:model, :core:subscription
:feature:dashboard → :core:ui, :core:kernel, :core:model
:feature:profiles / :feature:rules / :feature:logs / :feature:settings → :core:ui
:core:subscription → :core:model
:core:kernel → （无第一方依赖）
:core:model → （无第一方依赖）
:core:ui → （无第一方依赖）
```

**Verdict：通过。** 依赖全部单向，无循环。feature 之间互不依赖，可独立编译。`:core:model` / `:core:kernel` / `:core:ui` 零第一方依赖，是干净的底层。

### 1.2 缺口 A：DI 绑定让 `:app` 直接依赖 `:core:kernel`

- 证据：`app/build.gradle.kts` 含 `implementation(project(":core:kernel"))`；`app/src/main/java/com/justnpcx/starveil/di/KernelModule.kt` 直接 `return FakeKernelBridge()`。
- 问题：README 宣称"`:app` 与 `:feature:*` 禁止直接依赖内核"，但 DI 绑定本身就需要引用具体实现类。当前写法下，"禁止依赖内核"只对 feature 成立，对 `:app` 不成立——边界在 DI 处漏了。
- 修复建议（三选一，推荐①）：
  1. **接受为例外并写明**：在 README 与 `KernelModule.kt` 注释中声明"`:app` 依赖 `:core:kernel` 仅用于 DI 绑定，是唯一 sanctioned 例外；feature 仍禁止"。成本最低，边界清晰。
  2. **把绑定下沉**：`:core:kernel` 提供一个 `KernelBridgeFactory`（无 DI 注解），`:app` 只依赖接口 + 工厂，不直接 `new` 实现类。
  3. **独立 `:app:di` 模块**：DI 绑定单独成模块，`:app` 不直接出现内核依赖。

### 1.3 缺口 B：10 个模块的 `android{}` 配置逐字重复

- 证据：`app`、`core/*`、`feature/*` 的 `build.gradle.kts` 各自重复 `compileSdk = 36`、`minSdk = 29`、`compileOptions`、`kotlinOptions`。
- 问题：改一个 SDK 版本要改 10 处，必然漂移。
- 修复建议：引入 `build-logic` convention plugin（`starveil-android-library` / `starveil-android-application`），把公共配置收进一处。骨架阶段即可做，越晚越贵。

### 1.4 缺口 C：小瑕疵

| 问题 | 证据 | 修复建议 |
|---|---|---|
| `compose.ui.tooling.preview` 用 `implementation` | `core/ui/build.gradle.kts` | 改为 `debugImplementation`，避免发布包带预览库 |
| `kotlinOptions { jvmTarget }` 已废弃 | 各模块 `build.gradle.kts` | 改用 `kotlin { compilerOptions { jvmTarget } }` DSL |
| `:app` 依赖 `:core:subscription` 但无人使用 | `app/build.gradle.kts` | 订阅功能落地时再加，或保留并注释"预留"；当前是未使用的依赖 |
| `release { isMinifyEnabled = false }` | `app/build.gradle.kts` | 公开发布前必须开启 R8 并补 Hilt/Room/序列化 keep 规则（见 §6） |

---

## 2. 内核桥接

### 2.1 现状

`KernelBridge`（`core/kernel/src/main/java/com/justnpcx/starveil/core/kernel/KernelBridge.kt`）仅 4 个方法：

```kotlin
suspend fun start(config: String): Result<Unit>
suspend fun stop(): Result<Unit>
suspend fun queryStats(): TrafficStats
suspend fun urlTest(link: String): Long   // -1 = 失败
```

Fake/Real 切换在一处：`KernelModule.provideKernelBridge()`（`app/.../di/KernelModule.kt`），注释明确"Phase 2 换这一个函数"。

**Verdict：有缺口。** 接口方向正确（App 不碰内核），但承载真实 sing-box 接入时，至少缺以下 6 类能力：

### 2.2 缺失的方法与类型

| 缺失项 | 为什么必须有 | 建议形态 |
|---|---|---|
| **状态流** | 内核状态变化（启动中/已连接/崩溃/被杀）是事件，不是轮询能解决的；当前 `queryStats()` 轮询拿不到"内核崩了" | `val state: StateFlow<KernelState>`，`KernelState = Starting / Running / Stopped / Crashed(reason)` |
| **错误类型** | `Result<Unit>` 丢掉了错误分类；UI 无法区分"配置非法"与"内核崩溃"与"权限被拒" | `sealed interface KernelError { ConfigInvalid, StartFailed, Crashed, PermissionDenied, Unknown }`，`Result` 改为带类型的返回 |
| **日志回调** | 日志查看器需要内核日志流；当前无任何日志出口，Phase 3 日志屏接不上 | `val logs: SharedFlow<LogLine>`，`LogLine(level, tag, message, timestamp)`；日志在桥接层即脱敏（见 §6） |
| **批量测速** | `urlTest(link)` 一次测一个；节点列表一键测速需要并发批量 + 取消 | `suspend fun urlTestBatch(links: List<String>): Map<String, Long>` 或 `Flow<UrlTestResult>` 流式回传 |
| **配置重载** | 换节点/改规则不应每次都 stop→start；需要热重载 | `suspend fun reload(config: String): Result<Unit>`；或 `restart()` 语义明确化 |
| **Kill Switch / 阻断态** | `ConnectionState.BLOCKED` 已存在（`ConnectionCore.kt`），但桥接层无任何 API 能产生它 | `suspend fun setKillSwitch(enabled: Boolean)` 或在 `KernelState` 中表达"已阻断" |
| **版本与能力查询** | UI/诊断需要知道内核版本、支持的协议 | `suspend fun version(): KernelVersion`（sing-box 版本 + fork commit） |

### 2.3 接口设计问题

| 问题 | 证据 | 修复建议 |
|---|---|---|
| `start(config: String)` 传入原始 JSON 字符串 | `KernelBridge.kt` | JSON 是 sing-box 的格式，App 层拼 JSON = 内核格式泄漏到 App。改为：`core:kernel` 内建 `ConfigBuilder`（纯函数，Profile/Rule → sing-box JSON，可单元测试），桥接接收 `StarveilConfig` 类型化对象，JSON 只在桥接内部生成 |
| `urlTest` 用 `-1` 哨兵值表示失败 | `KernelBridge.kt`、`FakeKernelBridge.kt` | 哨兵值易漏判。改为 `Result<Long>` 或 `sealed UrlTestResult { Success(ms), Timeout, Unreachable }` |
| `queryStats()` 轮询式 | `KernelBridge.kt`、`DashboardViewModel.kt`（1 秒轮询） | 保留轮询作兜底，增加 `val stats: StateFlow<TrafficStats>` 推送式；轮询间隔应可配（前台 1s / 后台降频） |
| 切换仅编译期 | `KernelModule.kt` | 增加 BuildConfig 字段或 Debug 设置项"强制 Fake 内核"，方便在真机上对比 Fake/Real 而不用改代码 |

---

## 3. 数据层

**Verdict：缺失。**

| 项 | 骨架现状 | 证据 |
|---|---|---|
| Room | 仅在 `gradle/libs.versions.toml` 声明 `room-runtime` / `room-ktx` / `room-compiler`，无任何 Entity / DAO / Database 代码 | 全仓库无 `@Entity` / `@Dao` / `RoomDatabase` |
| DataStore | 仅在 catalog 声明 `datastore-preferences`，无任何使用 | 全仓库无 `DataStore` / `Preferences` 代码 |
| 模型 | `Profile` / `ProxyGroup` / `Rule` 为纯数据类（`core/model/`），无持久化注解 | `Profile.kt`、`ProxyGroup.kt`、`Rule.kt` |
| 迁移策略 | 骨架中未包含 | 无 `Migration` / `AutoMigration` / 版本号 |

### 修复建议（Phase 2 必须落地，顺序重要）

1. **新增 `:core:data` 模块**：Room Database + DAO + Repository。`:feature:*` 只依赖 Repository 接口，不直接碰 DAO（否则数据层边界漏）。
2. **模型分层**：`core:model` 的数据类 = 领域模型；Room Entity 单独定义（可与领域模型同构，但不要用 `@Entity` 污染领域模型）；Repository 做映射。避免 Mirage 式"宽表 Entity 即模型"的坑。
3. **迁移策略**：从 v1 起启用 `AutoMigration` + 显式 `Migration` 测试（`Room` 的 `MigrationTestHelper`）；**禁止 `fallbackToDestructiveMigration`**（README 已列为红线，需在代码里用 lint/注释 enforce）。
4. **字段扩展**：`Profile` 当前只有 6 个字段（`Profile.kt`），协议级设置（REALITY、mux、Hysteria2 调优）不要往 `Profile` 里加——按 `Profile.kt` 注释的方案，用"每协议一个设置对象"（`VlessSettings` / `Hysteria2Settings`…）挂在 Profile 下，或用 `settings: Map<String, String>` + 类型化访问器。禁止把 Profile 膨胀成宽表。
5. **加密存储**：节点凭证（密码/UUID/私钥）不进明文 Room；方案二选一：SQLCipher（整库加密）或字段级加密（Keystore 密钥 + AES-GCM）。决定后写进 `:core:data` 的 README（骨架中未包含，需新增）。

---

## 4. 导航与状态

**Verdict：有缺口。** 结构正确，恢复与深链缺失。

| 项 | 骨架现状 | 证据 |
|---|---|---|
| 单 Activity + Compose Navigation | 通过。`MainActivity` 只负责窗口 + BottomBar，导航在 `StarveilNavHost` | `MainActivity.kt`、`StarveilNavHost.kt` |
| 新增界面 | 通过。加 1 个 `Destination` 枚举项 + 1 个 `composable`，BottomBar 自动跟随 | `StarveilNavHost.kt` 注释已说明 |
| 深链（`sn://`、`clash://` 分享链接） | **缺失**。Manifest 只有 LAUNCHER intent-filter，无任何 `VIEW` intent-filter；无 `onNewIntent` | `app/src/main/AndroidManifest.xml` |
| 状态恢复 | **缺失**。`DashboardViewModel` 未用 `SavedStateHandle`；进程被杀后连接 UI 状态丢失 | `DashboardViewModel.kt`（构造函数只有 `KernelBridge`） |
| 返回栈 | 部分通过。BottomBar 导航用 `popUpTo + saveState + restoreState + launchSingleTop`，符合规范 | `MainActivity.kt`（`StarveilBottomBar`） |
| 路由类型 | 字符串路由（`"dashboard"` 等），无类型安全路由 | `StarveilNavHost.kt` |

### 修复建议

1. **深链（Phase 2–3）**：Manifest 给 `MainActivity` 加 `VIEW` intent-filter（`sn://`、`ss://`、`vmess://`、`vless://`、`trojan://`、`clash://install-config` 等），`onNewIntent` 解析后经导航到对应导入流程；**导入必须有用户确认对话框**（恶意链接防护，Mirage 已有此做法）。
2. **SavedStateHandle**：`DashboardViewModel` 注入 `SavedStateHandle`，恢复 `selectedNodeName`、`connectionState`（内核真实状态以 `KernelBridge.state` 为准，UI 状态只恢复展示层）。
3. **类型安全路由（可选）**：字符串路由在 5 个目的地时够用；超过 8 个目的地或带参数的深链增多时，迁到 Navigation Compose 的类型安全路由（`@Serializable` 路由类）。当前不强制。
4. **Splash**：未用 `core-splashscreen`；冷启动白屏/黑屏由 `themes.xml` 的 `windowBackground` 兜底。公开发布前接入 SplashScreen API。

---

## 5. 可扩展点：新增一项要改几处？

| 新增项 | 要改几处 | Verdict | 依据与修复建议 |
|---|---|---|---|
| **新增一个界面** | **1 处**：`StarveilNavHost.kt`（枚举 + composable） | 通过 | BottomBar 自动跟随；是骨架最干净的扩展点 |
| **新增一个协议** | **至少 4 处**：① `ProfileType` 枚举（`core/model/Profile.kt`）② `ConfigBuilder`（不存在，需新建）③ 协议编辑页（不存在，需新建）④ 订阅解析（`SubscriptionParser` 实现） | 有缺口 | 无协议注册表；`ProfileType` 枚举是集中式分发表，新增协议必然改枚举。修复建议：**协议注册表模式**——`core:kernel` 内建 `ProtocolRegistry: Map<ProfileType, ProtocolDescriptor>`，Descriptor 含解析器、配置生成器、编辑页路由；新增协议 = 注册 1 个 Descriptor + 1 个编辑页，不改现有文件。`ProfileType.OTHER` 占位不能代替注册表 |
| **新增一个设置项** | **缺失**：无设置基础设施 | 缺失 | 无 DataStore、无 Preference 模型、无设置 UI 框架。修复建议：新增 `:core:preferences`（或并入 `:core:data`）：`StarveilPreferences`（DataStore 封装）+ `SettingDescriptor`（key / 类型 / 默认值 / 分组）；设置屏按 Descriptor 自动生成 UI，新增设置项 = 注册 1 个 Descriptor |
| **新增一个订阅格式** | **2 处**：新 `SubscriptionParser` 实现 + 注册到解析入口（入口不存在，需新建） | 有缺口 | `SubscriptionParser` 接口本身干净（纯函数），但无"格式 → 解析器"的分发入口。修复建议：`SubscriptionParserRegistry`（按 URL scheme / 内容嗅探分发），与协议注册表同模式 |

**核心结论**：骨架的扩展点设计是"一处枚举 + 手动分发"，不是"注册表"。枚举式分发在 5 个目的地时够用，在协议/设置/订阅格式三类扩展上会随数量线性变脏。**Phase 2 引入注册表模式是架构上最值得的一次重构**，且越早越便宜。

---

## 6. 自用 → 公开发布的差距清单

| # | 项 | 骨架现状 | 发布前必须补什么 |
|---|---|---|---|
| 1 | **i18n** | 只有英文。`strings.xml` 仅 `app_name`；界面文案硬编码英文（"Dashboard"、"Nodes"、"No node selected"），且混有中文占位文案（"待实现…"） | `values-zh` 全量翻译；所有文案进 `strings.xml`（含 ViewModel 里的错误文案）；`LocaleConfig`；伪本地化测试（`en-XA`） |
| 2 | **新手引导** | 无（自用故意不要） | 首次启动引导：导入订阅 → 选节点 → 一键连接，三步；空状态引导（见文档三 §2） |
| 3 | **隐私政策** | 无 | App 内 + GitHub 各一份；写清：不收集节点凭证、不上传流量日志、崩溃上报含什么 |
| 4 | **崩溃上报** | 无 | Sentry 或 Firebase Crashlytics；**上报前脱敏**（过滤 URL/UUID/密码）；提供"关闭上报"开关 |
| 5 | **更新通道** | 无 | GitHub Releases 检查 + SHA-256 + 签名证书指纹双校验；稳定/测试通道；"仅 Wi-Fi 更新" |
| 6 | **Play 合规** | 部分：Manifest 已声明 `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `POST_NOTIFICATIONS` / `RECEIVE_BOOT_COMPLETED`；`allowBackup=false`（好） | 前台服务必须声明 `foregroundServiceType`（`specialUse` 需在 Play 申报用途）；`FOREGROUND_SERVICE_SPECIAL_USE` 需提供 Play 要求的用途说明；数据安全表单；目标 SDK 跟随 Play 要求 |
| 7 | **无障碍** | 基本缺失。`ConnectionCore` 用 `clickable(indication = null)` 且无语义描述；全仓库无 `contentDescription`（除 BottomBar 图标的 label） | 每个可交互元素 `contentDescription`；`semantics` 合并；TalkBack 走查；对比度 ≥4.5:1 |
| 8 | **大字体 / RTL** | `supportsRtl=true` 但无 RTL 测试；字体用 `sp`（会跟随系统缩放）但无大字体布局验证 | 1.3x / 1.5x 字体缩放走查；RTL 镜像走查（BottomBar、图表方向） |
| 9 | **多设备覆盖** | 无 WindowSizeClass；无横屏/平板/折叠屏适配 | `WindowSizeClass` 自适应布局；横屏 Dashboard 不溢出；折叠屏展开态 |
| 10 | **备份加密** | 无备份功能 | 备份默认加密（scrypt + AES-256-GCM，每次随机 salt/IV）；导出时明确告知含敏感数据；恢复时校验完整性 |
| 11 | **日志脱敏** | 只有 README 红线，无代码 | 日志层统一脱敏过滤器（UUID/密码/Token/私钥/完整订阅 URL）；`SendLog` 分享前二次过滤；Debug 日志默认关 |
| 12 | **权限最小化** | Manifest 声明 6 个权限并注释"需论证" | 发布前逐个论证：`RECEIVE_BOOT_COMPLETED` 是否真需要（自启是自用需求，公开发布需默认关）；`POST_NOTIFICATIONS` 运行时申请 |
| 13 | **R8 / 混淆** | `isMinifyEnabled = false` | 开启 R8 + 补 Hilt/Room/序列化 keep 规则；发布前做混淆后回归测试 |
| 14 | **版本号策略** | `versionCode = 1`，`versionName = "0.1.0-skeleton"`，无策略文档 | semver + versionCode 单调递增规则；写进 CI 或发布文档 |

---

## 7. 未来功能承载力

| 未来功能 | 现有架构能否承载 | 需新增什么 |
|---|---|---|
| **健康历史评分**（延迟 EMA + 可用率 + 丢包） | 有缺口 | `KernelBridge` 增加批量测速/状态流（§2.2）；`:core:data` 增加延迟样本表（时间序列）；新增评分器（纯函数，可单元测试）；Dashboard 增加健康曲线图（文档二 §7） |
| **按应用选节点** | 有缺口 | `Profile`/`Rule` 模型增加 per-app 字段；`ConfigBuilder` 支持按包名分流；新增应用列表数据源（`PackageManager` 封装进 `:core:data` 或新 `:core:apps`）；需 VPN 模式下的 UID 映射 |
| **AI 分组** | 可直接承载 | 内容层：预置规则 + 分组，不新增模块。依赖远程规则集（`ConfigBuilder` 支持规则集引用，Phase 2 落地） |
| **WebDAV 同步** | 有缺口 | 新增 `:core:sync`（WebDAV 客户端 + 冲突解决 + 加密备份）；依赖 §6-10 的备份加密；同步内容默认排除明文凭证 |
| **桌面端 KMP** | 有缺口 | **关键点**：当前 `:core:model` / `:core:subscription` 是 `android-library` 模块，不是 KMP 模块，无法直接共享给桌面端。两种选择：(a) 现在就将这两个模块改为 KMP（`kotlin-multiplatform` 插件 + `commonMain`），成本小；(b) 接受未来迁移成本，桌面端立项时再抽取。若桌面端是"一定会做"，选 (a)；若只是"可能"，选 (b) 并在 README 标注决策 |
| **旁路由导出** | 可直接承载 | `ConfigBuilder` 输出 sing-box JSON + 文件分享；不新增模块。需文件分享的 FileProvider 与脱敏检查（导出配置含凭证，必须二次确认） |

---

## 8. 工程化

| 项 | 骨架现状 | Verdict | 修复建议 |
|---|---|---|---|
| CI 构建 | `build.yml`：JDK 17 + Android SDK + `assembleDebug` | 通过 | 已满足骨架阶段 |
| CI Lint | `gradle lintDebug` | 通过 | 建议增加 `lint` 基线管理（`lint-baseline.xml`），避免存量警告淹没新增问题 |
| 单元测试 | catalog 有 JUnit，但**没有任何 `src/test` 目录** | 缺失 | Phase 2 起：`ConfigBuilder`、`SubscriptionParser`、`健康评分器` 必须有单元测试；CI 增加 `gradle testDebugUnitTest` |
| UI 测试 | catalog 有 Espresso / androidx-junit，但无任何 `src/androidTest` | 缺失 | 至少覆盖：导航（5 个目的地可达）、Dashboard 连接流程（Fake 内核）、设置屏不闪退 |
| 静态检查 | 无 detekt / ktlint | 缺失 | 二选一接入；规则从严起步（`UnnecessaryAbstractClass`、`MagicNumber` 等可先放宽），存量代码用基线 |
| Baseline Profile | 无 `baselineprofile` 模块 | 缺失 | Phase 4 增加：覆盖启动 → 连接 → 切节点 → 看日志的真实旅程；CI 生成并提交 |
| Macrobenchmark | 无 | 缺失 | Phase 4 增加：冷启动、滚动帧率基准（见实施计划 §3.1 目标表） |
| 依赖治理 | version catalog 集中管理 | 通过 | 建议增加 `dependencyGuard` 或 `gradle-versions-plugin` 定期检查过期依赖 |

---

## 9. 最关键的 3 个架构缺口

1. **数据层完全缺失**——Room / DataStore 只在 catalog 里，没有任何持久化代码；Phase 2 若先写 UI 再补数据层，必然返工。
2. **`KernelBridge` 承载不了真实内核**——缺状态流、错误类型、日志回调、批量测速；现在补接口是 1 天的事，等 RealKernelBridge 写了一半再改是 1 周的事。
3. **扩展点是枚举式分发而非注册表**——协议/设置/订阅格式新增时都要改多处；Phase 2 引入注册表模式是成本最低的架构投资。
