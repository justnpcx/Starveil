# Phase 2 修复记录：三个架构缺口

> 对应 `docs/01-architecture-extensibility-audit.md` 第 9 节的三个最关键缺口。
> 本次只修这三项，不改三份设计文档正文，不碰 UI 行为。

## 修复 1：数据层（`:core:data` 新模块）

**取舍**：审计建议 `:core:data`，任务允许放 `:core:model` 内或新建模块。选新建 `:core:data`——保持 `:core:model` 零依赖的纯领域模型，数据层自带 Room + DataStore + Hilt 依赖，不污染模型层。

| 内容 | 文件 |
|---|---|
| 实体（与领域模型分离） | `core/data/.../entity/ProfileEntity.kt`、`GroupEntity.kt`、`RuleEntity.kt` |
| DAO（Flow 查询 + suspend 增删改） | `core/data/.../dao/ProfileDao.kt`、`GroupDao.kt`、`RuleDao.kt` |
| 数据库（version 1，exportSchema） | `core/data/.../StarveilDatabase.kt` |
| Repository 接口 + Room 实现（实体 ↔ 领域模型映射） | `core/data/.../repository/ProfileRepository.kt`、`GroupRepository.kt`、`RuleRepository.kt` |
| 偏好（DataStore，typed keys） | `core/data/.../preferences/PreferencesRepository.kt` |
| DI（单例：Database / DAO / Repository / Preferences） | `core/data/.../di/DataModule.kt` |
| 模块注册 + `:app` 依赖 | `settings.gradle.kts`、`app/build.gradle.kts`、`core/data/build.gradle.kts` |

**红线**：无 `allowMainThreadQueries()`；无 `fallbackToDestructiveMigration()`（迁移策略写在 `StarveilDatabase` KDoc：显式 Migration + 备份恢复，永不静默删库）。`RuleEntity` 的排序列命名 `sort_order`（`order` 是 SQL 关键字）。`ProxyGroup.profileIds` 是派生字段（profiles 存 `groupId`），Repository 在读取时组合，不落库。`Profile.upsert` 更新时保留已存 `settingsJson`，不静默擦除协议设置。

**仍未做**：SQLCipher / 字段级加密二选一（凭证加密，审计 §3 要求决定后写进 `:core:data` README）；Migration 测试（`MigrationTestHelper`，等 v2 schema 出现时补）；Repository 的单元测试。

## 修复 2：`KernelBridge` 扩展

| 新增 | 说明 |
|---|---|
| `val state: StateFlow<KernelState>` | Sealed：Stopped / Starting / Running / Stopping / Error |
| `sealed KernelError` | ConfigInvalid / StartFailed / Crashed / PermissionDenied / Unknown；各带 message / cause / recoverable |
| `val logs: SharedFlow<KernelLogLine>` | 级别 + tag + 内容 + 时间戳；桥接层脱敏后发出 |
| `suspend fun urlTestBatch(...)` | 并发批量测速，逐条返回类型化结果 |
| `urlTest` 返回类型 | **Breaking**：`Long`（`-1` 哨兵）→ `UrlTestResult`（Success / Timeout / Unreachable） |

**取舍**：
- `urlTest` breaking 的理由：哨兵值是审计点名的缺陷；经 grep 确认**没有任何 UI 代码调用 `urlTest`**，唯一受影响的是两个实现类，改动零扩散。
- 日志用 `SharedFlow` 而非 `setLogListener`：Flow 支持多订阅者、结构化并发、无手动注册/注销泄漏；`replay = 64` + 缓冲使 emit 不挂起调用方。
- `DashboardViewModel` **未改行为**：它只用 `start/stop/queryStats`（签名未变），调用点经检查无破损；消费 `kernel.state` 的完整映射属于 Phase 3 Dashboard 工作，不在此处半改状态机。
- `KernelModule` 未变：仍提供 `FakeKernelBridge`，一处切换点保持。

**仍未做**：`reload`（配置热重载）。

**后续已补**（更新于 M3）：
- `version()`（内核版本查询）—— `KernelBridge.version()`，读 `Libbox.version()` 而不是在 Kotlin 里写死（app 发的是编好的 libbox，写死的版本号会悄悄漂移）。Settings 页「Kernel」一节展示，出问题时报这个号。
- Kill Switch API（审计 §2.2）—— `KernelBridge.stopKeepingTunnel()`：停内核但**留住 tun**，"隧道还在但没人转发"本身就是拦截态；`KillSwitchMode` 三档在 Settings 页可选。

## 修复 3：注册表模式

| 内容 | 文件 |
|---|---|
| 协议注册表（描述符 + `default()` + `withExtra`） | `core/kernel/.../registry/ProtocolRegistry.kt` |
| 订阅格式注册表（sniff 分发 + Fake fallback） | `core/subscription/.../registry/SubscriptionFormatRegistry.kt` |
| DI 集中组装 | `app/.../di/RegistryModule.kt` |

**取舍**：
- 注册表类本身无 DI 注解（纯类，测试不用 Hilt）；组装集中在 `:app` 的 `RegistryModule`，与 `KernelModule` 的既有模式一致（保持 `:core:kernel` 无 DI 注解）。
- `ProtocolRegistry` 在 `:core:kernel` 内（配置生成是它的消费者），因此给 `core/kernel` 补了 `:core:model` 依赖；`SubscriptionFormatRegistry` 在 `:core:subscription` 内。
- **没有删掉任何枚举分发代码**：经 grep 确认，骨架里不存在 `ProfileType` / 订阅格式 / 设置项的 `when` 分发——注册表是抢先落地，新增协议/格式时直接注册，不再新增分发。
- **没有做设置项注册表**：设置项同样不存在任何分发（`SettingsScreen` 只是占位文案），按任务要求注明即可；等 Phase 3 设置 UI 落地时用 `SettingDescriptor` 注册表模式。
- 协议描述符的 `linkParser` / `configBuilder` 目前是 stub（返回 null / 空 Map，带 TODO）：真实实现是 Phase 2 后续工作，本次只定注册形态。

## 静态复核（本机无 JDK / Android SDK，无法真实编译）

- 全部新增/修改 `.kt` 括号平衡通过；引用的文件与类均存在；DI 提供方与注入方一致（DataModule 提供 Database/DAO/Repository/Preferences；RegistryModule 提供两注册表；KernelModule 提供 KernelBridge）。
- grep 确认新代码无 `GlobalScope`、无 `allowMainThreadQueries`、无 `fallbackToDestructiveMigration`、无硬编码密钥。
- `DashboardViewModel` / `KernelModule` 调用点经检查与新接口兼容（`start/stop/queryStats` 签名未变；`urlTest` 无 UI 调用方）。
- **未验证编译**：需在有 JDK 17 + Android SDK 的机器上跑 `gradle :app:assembleDebug` 做首次真实编译验证。
