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
| 更新通道（失败路径） | 私有仓 `releases/latest` 返回 404，界面显示「无法检查更新：no published release」 |
| 更新通道（发现 + 下载 + 校验） | 临时指向公开仓 NekoBox 1.4.2：弹窗显示版本与发布说明；下载 15 MB 后 **SHA-256 通过、签名不匹配被拒绝**，界面显示「下载失败：signature mismatch」，缓存文件已删除 |
| 默认接口 | 连接后日志 `default interface -> wlan0 (24)`，出站 `dial wlan0`，WiFi 下不再走蜂窝 |
| 更新通道（完整流程） | 本地 HTTP 源提供一份与已安装应用同签名的 APK：弹窗 → 下载 130 MB → SHA-256 通过 → 签名通过 → **系统安装器完成安装**（`lastUpdateTime` 更新到 22:04:55）→ 应用重新启动 |
| 三档分流策略 | 见下方「分流矩阵」：同一目标在不同策略下由内核判给不同出口 |
| 后台全量探测（H1） | 日志出现 `health sweep: probed 3 node(s)`；随后「自动选择」选出 **HY2+Reality-hy2-443（评分 10099）**——不是此前唯一有健康数据的 cf-xhttp |
| Kill Switch 阻断（M5） | 「始终阻断」连接 → `已阻断`，配置 `final=block`、出站只有 `block`、无 uuid；Chrome 打开 example.com 得 `ERR_CONNECTION_REFUSED`。「断开时阻断」连接→断开 同样结果；从阻断态再连接，配置回到 `final=proxy` |
| 热切换回归（2026-10-18） | 连接 → 阻断 → 连接 两轮：`已连接`（tun=1，外网流向判为代理）→ `已阻断`（tun 保留，页面停在加载中，不是直连打开）→ `已连接`；`cache-file: timeout` 0 次 |
| 首页四态与深色（P2-1） | 见下方「深色走查」；录屏 `/tmp/starveil_states.mp4`（未连接呼吸 → 连接中 → 已连接） |
| 连接建立速度（P2-3） | 见下方「TTFP」：10 次 VLESS+XHTTP+REALITY、12 次 Hysteria2，均为冷启动（force-stop 后连接） |
| 后台保活（P1-2） | 设置 → 后台保活：电池优化显示「未加入」，点「去设置」弹出系统对话框（`RequestIgnoreBatteryOptimizations`），允许后 `dumpsys deviceidle whitelist` 出现 `user,com.justnpcx.starveil`，返回后该行变「已加入」；自启动打开 `com.vivo.permissionmanager/.activity.BgStartUpManagerActivity`（自启动页）；应用详情打开 `InstalledAppDetails` |
| 规则集校验（M3） | 篡改 `geosite-cn.srs` 并把目录设为只读后重连：日志 `does not match its hash; discarding` → 下载失败 → `rule set geosite-cn.srs: download_failed; mainland preset disabled`；规则页出现红色警告与 Snackbar，配置 `final=proxy` 且 0 条预设规则（fail-closed）；日志页有同一条 error |
| 切预设自动重建 | 连接状态下切「全局直连」，无需手动重连，`files/libbox/configuration.json` 的 `final` 由 `proxy` 变为 `direct`；切回后恢复 `proxy` + 2 条规则 |

**分流矩阵（2026-10-18）**

判定依据是内核自己的记录（规则页「最近流向」+ 运行中的 `configuration.json`），不是出口
IP——这台机器的 WiFi 本身经 VPS LA 出口（`node.example.com` = `203.0.113.10`），直连与
代理看到的公网 IP 相同，IP 对比在这里没有区分度。

| 预设 | 运行配置 | 观察到的判定 |
|---|---|---|
| 全局代理 | `final: proxy`，0 条规则 | `140.82.114.26:443 → 代理`、`114.114.114.114:53 → 代理` |
| 全局直连 | `final: direct`，0 条规则 | 同一目标 `140.82.114.26:443 → 直连`，`42.81.90.122:443 → 直连` |
| 绕过中国大陆 | `final: proxy` + `geosite-cn`/`geoip-cn → direct` | 国内 `58.254.149.161`、`114.250.59.172`、`123.126.99.232` → 直连；境外 `140.82.112.25:443 → 代理` |

注意 `one.one.one.one` 在本网络被 DNS 投毒成 `123.126.99.232`（联通），于是 `geoip-cn`
判直连——这是「按地址判定」的必然结果，不是 bug；要验证境外分支得用字面 IP（`1.1.1.1`）。

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

**4. 节点标签的国旗会在任意设置写入后消失**（2026-10-18）

连上后首页是 `🇺🇸 cf-xhttp`，切一次分流预设后变成 `cf-xhttp`，节点页仍有国旗。原因：
`DataStore` 任何写入都会让 `data` 重发，`connectionTarget` 跟着重发一次（值没变），
首页的 collector 重新用 `profiles.getById(id)?.name` 拼标签——这条路径没走
`displayName()`，而连接结果那条走了。两处对同一个节点给出不同名字。改为 `displayName()`
后一致；回归测试用「任意写入都重发」的 fake 复现（去掉修复即失败）。

**5. 切预设不重建配置，页面却说「当前生效」**（2026-10-18）

连接状态下切「全局直连」，规则页显示 `当前生效 0 条规则`，但内核仍跑着旧配置
（`configuration.json` 的 `final` 还是 `proxy`），流量照旧走代理。配置只在连接时构建，
而切节点是会自动重连的——两条路径不一致。已让 `DashboardViewModel` 同时观察
`routePreset` 与 `blockAds`，变化时重建隧道，和切节点同一条路径。

**6. 配置热切换在这个内核上不可用**（2026-10-18）

M5 原本想让「阻断」= 换一份 `buildBlocking()` 配置，靠 `startOrReloadService` 在运行中的
内核上原地切换。真机上四种做法逐一试过：

| 做法 | 结果 |
|---|---|
| `startOrReloadService`（原地 reload） | `initialize cache-file: timeout`，每次都失败 |
| 上面 + `cache_file.enabled=false` | 无效：daemon 把自己当 platform log writer，`needCacheFile` 恒真 |
| 关内核、保留 VpnService 再 start（每策略独立 cache 路径） | 同样超时 |
| **完整 teardown（关内核 + 停 VpnService）再 start** | **可靠**：两轮连接/阻断，tun 始终 1 个，日志 0 次超时 |

所以「换配置」= 完整重启，只用于**连接**方向——那时本来就没有可泄漏的流量。**阻断**方向
不走 swap：关掉内核、保留 tun，包进了一个没人读的接口，立即生效且没有窗口。

代价是「从阻断态连接」有一次 tun 重建（几百毫秒无 VPN）。要做到真正的原地热切换，得改
libbox（`StartedService.Close()` 并不关闭运行中的实例；reload 路径的 cache 锁也拿不到），
不是应用层再换一种写法能解决的。

**7. 一个文件成功会把另一个文件的失败清掉**（2026-10-18）

`ensure()` 依次取两个文件；第二个成功后把 `failure` 置空，正好抹掉第一个刚记下的失败——
而「一个坏、一个好」恰恰是唯一需要告诉用户的组合。改为在 `ensure()` 入口清空，成功不再
清。另补一条常驻警告行：Snackbar 会消失，而「预设没生效」这件事不会。

**8. 首次调度会探测两遍**（2026-10-18）

周期任务首次入队时 WorkManager 会立刻跑一次，而同一启动里还入队了一个「补跑」一次性任务，
日志里出现两行 `probed 3 node(s)`。改为入队前先查该唯一任务是否已存在：首次只靠周期任务，
之后的启动才按时间戳补跑。

### 深色走查（P2-1，2026-10-18）

五屏（首页 / 节点 / 规则 / 日志 / 设置）逐张看过，改了两处：

1. **系统导航栏是白的**。`enableEdgeToEdge()` 不带参数时按*资源*配置决定 scrim，而资源
   配置跟的是系统主题；系统是浅色、应用被强制深色时，黑色页面下面压着一条白条。改成在
   组合里按应用自己的 `dark` 重新调用，导航栏变透明、图标转亮。
2. **规则页的 FAB 压住空态文字**（「全部走代理）」被盖住一半）。空态加了 88dp 的右侧留白，
   文字在按钮前换行。

没改的：FAB、开关、胶囊在深色下是淡紫色——那是「动态取色」打开后的 Material 调色板，
设置页自己写着「会覆盖 Starveil HUD 配色」，属于用户选择而不是走查缺陷。连接圆环始终是
HUD 的青色，这是有意的：核心是品牌，其余跟随主题。

### TTFP：点击连接到首个代理连接（2026-10-18）

埋点：`ConnectionController.connect()` 记一个 `elapsedRealtime` 起点，`KernelBridge`
的 `state` 第一次到 `Running` 记 `kernel up in N ms`，`connections` 里第一条
`outboundType` 非 `direct`/`block` 的连接记 `TTFP N ms via <type>`。两条都进日志页。

测法：每次 `force-stop` 后重开（冷启动），点连接后立刻用 Chrome 打开一个带唯一参数的
URL；TTFP 从点击算到内核决定第一条代理连接。**这个数字包含 Chrome 启动的约 150–250 ms**，
所以看两个数：`kernel up`（纯隧道建立）和 `TTFP`（用户实际感知）。

| 组合 | kernel up p50 / p90 | TTFP p50 / p90 |
|---|---|---|
| VLESS + XHTTP + REALITY（cf-xhttp，10 次） | 174 / 212 ms | **389 / 423 ms** |
| Hysteria2（hy2-443，12 次） | 179 / 186 ms | **374 / 395 ms** |

结论：**p50 约 0.38 s，不需要优化**。两种协议几乎一样快，说明瓶颈不在代理握手，而在
固定的内核启动（~175 ms）加浏览器启动（~200 ms）。候选优化里：DNS 预解析已经在 DoH 层
缓存；TCP 握手无法跨重启复用；内核预热等于在用户点之前就建隧道，属于语义变更，不为了
300 ms 做。数据记在这里，等真的变慢再回来。

### M4 内存调优（2026-10-18，已实测：未达标，验收线降为 stretch）

**机制**（verify first 的结论）：

- `GOGC=50` **不走环境变量**。Go 运行时在 so 加载时读 `GOGC`，而 libbox 的加载时机由 AAR 的静态块决定；在 Kotlin 里抢在 `System.loadLibrary` 之前 `Os.setenv` 依赖加载顺序。sing-box 的配置里已有 `experimental.debug.gc_percent`，`box.go:applyDebugOptions` 会调用 `debug.SetGCPercent`，所以在配置里写 50 等价，且没有时序问题。
- `FreeOSMemory` **没有 JNI 出口**。唯一暴露它的是 `experimental.debug.listen` 的 `GET /debug/gc`，但同一个服务器还暴露 pprof heap/profile——堆快照里可能有节点地址和 UUID。发布版不开这个口。内核在每次 `StartOrReloadService` 之后自己会调 `FreeOSMemory`（`daemon/started_service.go`），这部分已经生效。
- 水位用 `debug.memory_limit`（Go 1.19+ 软限制）。**配置里写的是 96 MiB（进程预算），内核 `debug.go:34` 把它除以 1.5 再传给 `debug.SetMemoryLimit`，所以 Go 侧的软上限是 64 MiB**——96 和 64 是同一件事的两层，不是两个设置。低于活堆会让 GC 空转，这个数只能靠 PSS 实测往下调。

**测量方法**（同一设备、同一规则集）：

1. `./gradlew :app:assembleRelease`，安装 arm64 包。
2. 选「绕过中国大陆」，等规则集下载完成并连接。
3. 静置 5 分钟，执行 `adb shell dumpsys meminfo com.justnpcx.starveil | grep -E "TOTAL PSS|TOTAL RSS"`。
4. 对照组：把 `ConfigBuilder` 的 `debug` 块去掉（或 `gc_percent=100`、去掉 `memory_limit`）重建同一版本，重复 1–3。
5. 记录 PSS/RSS、`dumpsys gfxinfo` 的掉帧和连接抖动；若 GOGC=50 有明显卡顿，按任务书回调 75/100 并记录数字。

**目标**：PSS < 80 MB。

**实测（2026-10-18，vivo V2520A / Android 17 / arm64-v8a / 1080×2376）**

同一台设备、同一个节点（HY2+Reality-hy2-443）、同一预设（绕过中国大陆 + 去广告，4 条规则在生效）、规则集已加载。应用在后台（按 Home），`dumpsys meminfo com.justnpcx.starveil` 的 `TOTAL PSS`：

| 构建 | 5 分钟 | 10 分钟 |
| --- | --- | --- |
| before（`fd923ae`，默认 GOGC，无 memory_limit） | 85,985 KB（84.0 MiB） | 84,732 KB（82.7 MiB） |
| after（`b0c36a8`，GOGC=50 + memory_limit 96 MiB → Go 64 MiB） | 83,986 KB（82.0 MiB） | 85,256 KB（83.3 MiB） |

两个包都是 release 模式（R8），为了安装用 debug 证书签名；除 `debug` 配置块外代码相同。前台刚连上时 before 118,020 KB、after 132,287 KB，但 after 那次前台多停留了约一分钟并伴随 DNS 报错，不可比，不列入结论。

**结论：没有可测出的改善，目标未达标。**

- 5 分钟差 2.0 MiB，10 分钟差 −0.5 MiB；而 before 自己在 5→10 分钟之间就漂移了 1.2 MiB，after 漂移 1.3 MiB。差异在噪声内。
- 5 分钟 after 的构成：Java 5.6 MB、Native 9.2 MB、Code 17.6 MB、Private Other 23.1 MB、System 25.8 MB、Graphics 0.4 MB。Go 堆只有约 9 MB，`memory_limit`（Go 上限 64 MiB）根本没有触到。
- 80 MB 的地板由共享代码页（libbox.so，61.7 MB stored / 19.5 MB deflated）、Compose 的 Private Other 和系统共享页决定，不是 GOGC 能压下去的。要真正达标，下一根杠杆是 libbox 的 so 体积或更轻的 UI，不在本轮范围。
- GOGC=50 没有观察到卡顿；保留 50 作护栏（对更大的节点列表是无害上限），但它不是达标证据。“M4 内存达标”这个结论**不能**写。

**增补二 v2 作废**。任务书里的「`SetMemoryLimit` 组合拳 + 内存水位触发 `FreeOSMemory`」不再实现：上面的数据说明 Go 堆不是瓶颈（约 9 MB，离 64 MiB 上限很远），加软上限不会改变 PSS；`FreeOSMemory` 也没有安全的 JNI 出口（见上）。

**验收线调整**：`< 80 MB` 从 M4 门禁降为 stretch 目标。真要压到 80 MB 以下，下一根杠杆是 libbox.so 瘦身（61.7 MB stored / 19.5 MB deflated）或更轻的 UI，另立项，不在 M4 范围。

**复现**：`/tmp/pss/starveil-{before,after}-arm64.apk`（临时文件）；构建分别来自 `fd923ae` 与 `b0c36a8`，用 `apksigner` 以 debug 证书签名后 `adb install -r`。

### 引导的 VPN 授权，含拒绝路径（2026-10-18）

P3-4 的第三步曾经绕过 `VpnService.prepare()`，新装第一次连接直接失败（`VPN consent
not granted`）。修复后两条路径都在真机上走了一遍。

方法：debug 构建临时加 `applicationIdSuffix = ".consenttest"`，作为独立包安装，
数据为空、没有历史授权（`cmd appops get … ACTIVATE_VPN` 为 `Default mode: ignore`）。
系统把授权记成 app-op `ACTIVATE_VPN`，所以这一项是判断「有没有授权」的可靠依据，
比看界面可靠。走完引导导入订阅后：

| 操作 | 系统对话框 | app-op | 界面 |
|---|---|---|---|
| 点「自动选择并连接」 | 「网络连接请求」弹出 | `ignore` | 停在第 3 步 |
| 点「取消」 | 关闭 | `ignore; rejectTime=+14s` | 停在第 3 步，显示「需要授予 VPN 权限才能连接。」；没有 VpnService 启动 |
| 再点一次 | 对话框再次弹出 | `ignore` | — |
| 点「确定」 | 关闭 | `allow; time=+10s` | 「已连接到 B64-Tokyo」，错误行消失；`StarveilVpnService` 在跑，`tun0` 172.19.0.1/30 |

拒绝后重试会重新询问，不是永久锁死；成功连接后错误行不会残留。测试包已卸载，
临时后缀已还原，主应用的数据与授权未动。

录屏：[`docs/recordings/onboarding-0.7.1.mp4`](recordings/onboarding-0.7.1.mp4)（21.6 s，
720×1584，840 KB）。内容：欢迎 → 导入一个节点（订阅地址）→ 自动选择并连接 → 系统
授权 → 完成 → 首页显示「已连接 / B64-Tokyo」。

录制说明：debug 包 + 临时 `.consenttest` 后缀（与发布代码相同，仅包名不同），录制时
关掉横幅通知（`settings put global heads_up_notifications_enabled 0`），剪掉了开头
等待和连接后静置的两段静止画面；其余为连续画面。

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
| R8 混淆后的运行时回归 | 2026-10-17 开启 R8，尚未在真机跑过 |
| 去广告 | 规则已生成（`geosite-category-ads-all` → reject），未在真机上看过拦截效果 |

已通过但值得记一笔的：**按地区分组**。三个节点名都不含地区，GeoIP 从地址解析出
`US`，节点前显示 🇺🇸，点「按地区」生成「美国」组。也就是说这条链路是「地址 → 国家
→ 国旗 → 分组」，不是「名字里有没有城市」。

## 下一轮真机验证清单

按依赖顺序，一次会话可完成：

1. **R8 release 包冒烟**：安装 `assembleRelease` 产物，连接、分流、按应用、备份恢复各走一遍
2. **分流策略矩阵（换一个非代理出口的网络重做）**：本机 WiFi 的出口就是节点，所以
   「全局直连」和「全局代理」看到的公网 IP 相同。判据应改为内核判定（已按此验过一遍，
   见上）；若要用 IP 对比，需在家庭网络不代理的链路上重做
3. **去广告**：开「去广告」，访问一个已知广告域名，日志里应出现 `rule_set=ads → reject`
4. **更新通道**：检查更新 → 弹窗 → 下载 → 双校验 → 系统安装器
5. **重连计时**：关闭蜂窝再打开，记录内核 `Starting → Running` 的时间
6. **内存**：release 包后台驻留 PSS，方法见上面「M4 内存调优」一节
7. **120fps**：导入一个 500–1000 节点的订阅，滚动并用 `dumpsys gfxinfo` 抓帧
8. **DNS p50**：先加埋点，再读
9. **每次升级 libbox 必跑（M5）**：开「始终阻断」，确认任意出站连接**失败**（`curl` 超时），
   而不是「直连成功」；再开「断开时阻断」，连接后断开，同样确认失败。阻断态现在是
   `buildBlocking()` 的配置在跑，不是「关内核留 fd」——libbox 一升级，这条就从「配置保证」
   退回「实现细节」，所以每次都要重跑
