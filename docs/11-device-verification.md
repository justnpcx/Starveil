# 真机验证记录

设备：vivo V2520A（PD2520），Android 17（SDK 37），1080×2376，系统语言 zh-CN。
连接方式：无线调试（mDNS 序列号形如 `adb-…._adb-tls-connect._tcp`）。

本文件记录**在真机上实际验证过什么**，以及验证过程中发现的问题。它不是计划，是结果；
未验证的项目在最后一节。

## 2026-10-17 · 0.7.0

从 0.3.0 覆盖安装到 0.7.0（versionCode 700），数据保留。

### 通过

| 项 | 证据 |
|---|---|
| 数据库 2→3 迁移 | 升级后节点、分组、选中状态全部保留，无崩溃 |
| XHTTP + REALITY | `cf-xhttp` 节点连接成功，下行 22 KB/s；内核 `1.14.3-c81f178` |
| 预设分流 | baidu 走 `geoip-cn` 直连并正常打开；google 经代理正常打开 |
| 流向归属 | 规则页显示 `172.217.118.4:443 → 直连 · package_name=com.android.chrome` |
| 按应用分流 | Chrome 设为「直连」后，其 Google 连接记录为 `package_name=com.android.chrome → 直连`，页面无法打开（国内直连应有的结果） |
| 设置页 | 显示 `sing-box 内核 1.14.3-c81f178`、`Starveil 0.7.0`、隐私政策入口 |
| 冷启动 | 450 ms（`am start -W`，debug 包），目标 800 ms |
| 配置生成 | 5 ms / 500 节点，目标 100 ms |
| 中英双语 | 系统语言 zh-CN 下整屏中文：未连接 / ↓ 下行 / ↑ 上行 / 累计 / 首页·节点·规则·日志·设置 |
| 崩溃上报开关 | 带 DSN 构建时诊断区出现，切换写入 `crash_reporting.xml`（false → true）；无 DSN 构建时该区隐藏且应用正常启动 |

### 发现并修复

**1. 按应用分流从未生效**（`effa588`）

配置里规则正确，内核日志却是：

```
router: failed to search process: procfs: not found
```

`useProcFS()` 返回 `true`，内核改用 procfs 查连接归属；Android 上读别的应用
`/proc/<pid>/fd` 被 SELinux 禁止，每次都失败且**静默忽略**——规则下发、永不匹配、
流量落到默认路由。改为 `false` 后走 `ConnectivityManager.getConnectionOwnerUid`，
日志出现 `router: found package name: com.vivo.browser`。

**2. WiFi 下流量走移动数据**（`f99206f`）

`reportDefaultInterface` 从 `allNetworks` 取第一个有 INTERNET 的网络，而该列表
**无序**。WiFi 与蜂窝并存时它报了 `rmnet_data4`（蜂窝），内核把出站全绑上去：

```
dial rmnet_data4 (28): dial tcp 142.250.73.67:443: i/o timeout
```

改为「validated 优先、非计费优先」后：`default interface -> wlan0 (24)`、`dial wlan0`。

**3. 三处文案写死在 Kotlin 里**（`116846b`）

中文界面下底部导航仍是 Home/Nodes/…，核心状态仍是 `CONNECTED`（枚举名），速率区仍是
`↓ DOWN` / `total …`。`checkTranslations` 查不到——它们不是资源。已全部改为字符串资源。

### 调查后确认「不是问题」

**直连 IPv6 报 `network is unreachable`**：当时默认网络是 WiFi `Jim_5G`，它只有
link-local 地址、没有 v6 出口（`ping6 -I rmnet_data4` 通、`-I wlan0` 不通）。蜂窝有
IPv6。所以这是正确结果，不是分流 bug。

## 未验证

以下项目需要真机，且当时无法完成（设备不在网、无相应素材）：

| 项 | 原因 |
|---|---|
| 1000 节点滚动 120fps | 设备上只有 3 个节点，没有大订阅 |
| 后台常驻 < 80 MB | debug 包 213 MB 中 96 MB 是 APK mmap，不可比；需要 release 包 |
| 断线重连 < 3 s | 断网需关闭 WiFi，而 adb 走 WiFi，会切断自身 |
| DNS 缓存 p50 < 30 ms | 尚无埋点 |
| 操作反馈 < 100 ms | 未逐屏测 |
| 按地区分组（正向） | 三个节点名都不含地区，只能验到「没有匹配」这条正确路径 |
| 更新通道全流程 | 手机在验证前掉线；私有仓期间 `releases/latest` 返回 404，只能验到失败路径 |
| R8 混淆后的运行时回归 | 2026-10-17 开启 R8，尚未在真机跑过 |

## 下一轮真机验证清单

按依赖顺序，一次会话可完成：

1. **R8 release 包冒烟**：安装 `assembleRelease` 产物，连接、分流、按应用、备份恢复各走一遍
2. **更新通道**：检查更新 → 弹窗 → 下载 → 双校验 → 系统安装器
3. **重连计时**：关闭蜂窝再打开，记录内核 `Starting → Running` 的时间
4. **内存**：release 包后台驻留 PSS
5. **120fps**：导入一个 500–1000 节点的订阅，滚动并用 `dumpsys gfxinfo` 抓帧
6. **DNS p50**：先加埋点，再读
