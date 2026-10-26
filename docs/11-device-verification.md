# 真机验证记录

设备：各轮不同，逐节标注。主力是 vivo V2520A（PD2520，Android 17 / SDK 37，1080×2376）、
iQOO Neo7 SE（V2238A，Android 16 / SDK 36，1080×2400）与 Fire TV Stick 4K Max（AFTKRT，SDK 30，1080p）。
连接方式：无线调试（mDNS 序列号形如 `adb-…._adb-tls-connect._tcp`）；电视走 `IP:5555`。

本文件记录**在真机上实际验证过什么**，以及验证过程中发现的问题。它不是计划，是结果。
早年的「未验证」一节是 2026-10-17 的快照，其中部分项已在后续轮次关闭，逐条以各轮记录为准。

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
IP——这台机器的 WiFi 本身经上游代理出口（`node.example.com` = `203.0.113.10`），直连与
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

```text
router: failed to search process: procfs: not found
```

`useProcFS()` 返回 `true`，内核改用 procfs 查连接归属；Android 上读别的应用
`/proc/<pid>/fd` 被 SELinux 禁止，每次都失败且**静默忽略**——规则下发、永不匹配、
流量落到默认路由。改为 `false` 后走 `ConnectivityManager.getConnectionOwnerUid`，
日志出现 `router: found package name: com.vivo.browser`。

**2. WiFi 下流量走移动数据**（`f99206f`）

`reportDefaultInterface` 从 `allNetworks` 取第一个有 INTERNET 的网络，而该列表
**无序**：WiFi 与蜂窝并存时它报了 `rmnet_data4`（蜂窝），内核把出站全绑上去：

```text
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
|---|---|---|
| before（`fd923ae`，默认 GOGC，无 memory_limit） | 85,985 KB（84.0 MiB） | 84,732 KB（82.7 MiB） |
| after（`b0c36a8`，GOGC=50 + memory_limit 96 MiB → Go 64 MiB） | 83,986 KB（82.0 MiB） | 85,256 KB（83.3 MiB） |

两个包都是 release 模式（R8），为了安装用 debug 证书签名；除 `debug` 配置块外代码相同。前台刚连上时 before 118,020 KB、after 132,287 KB，但 after 那次前台多停留了约一分钟并伴随 DNS 报错，不可比，不列入结论。

**结论**：没有可测出的改善，目标未达标。

- 5 分钟差 2.0 MiB，10 分钟差 −0.5 MiB；而 before 自己在 5→10 分钟之间就漂移了 1.2 MiB，after 漂移 1.3 MiB。差异在噪声内。
- 5 分钟 after 的构成：Java 5.6 MB、Native 9.2 MB、Code 17.6 MB、Private Other 23.1 MB、System 25.8 MB、Graphics 0.4 MB。Go 堆只有约 9 MB，`memory_limit`（Go 上限 64 MiB）根本没有触到。
- 80 MB 的地板由共享代码页（libbox.so，61.7 MB stored / 19.5 MB deflated）、Compose 的 Private Other 和系统共享页决定，不是 GOGC 能压下去的。要真正达标，下一根杠杆是 libbox 的 so 体积或更轻的 UI，不在本轮范围。
- GOGC=50 没有观察到卡顿；保留 50 作护栏（对更大的节点列表是无害上限），但它不是达标证据。“M4 内存达标”这个结论**不能**写。

**增补二 v2 作废**：任务书里的「`SetMemoryLimit` 组合拳 + 内存水位触发 `FreeOSMemory`」不再实现：上面的数据说明 Go 堆不是瓶颈（约 9 MB，离 64 MiB 上限很远），加软上限不会改变 PSS；`FreeOSMemory` 也没有安全的 JNI 出口（见上）。

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

### Kill Switch 双保险（2026-10-18）

**两层锁**

- 应用内：`KillSwitchMode` 三档，`buildBlocking()` 的配置级阻断（`f781661`）。
- 系统级：系统的「始终开启的 VPN + 阻止未经过 VPN 的连接」。设置页 Kill Switch 段
  新增「打开系统 VPN 设置」，跳 `Settings.ACTION_VPN_SETTINGS`（vivo 上实测打开的是
  「VPN → 个人 VPN」，列表里有 Starveil），失败时回退无线设置/设置首页。

**实测一**：应用内锁的边界

vivo V2520A / Android 17，选「始终阻断」后点连接：

| 步骤 | 结果 |
|---|---|
| 连接 | 界面「已阻断 / Kill Switch 正在阻断全部流量」，`tun0` 172.19.0.1/30 |
| Chrome 打开 example.com | `ERR_CONNECTION_REFUSED` |
| `am force-stop com.justnpcx.starveil` | VpnService 0 个、`tun0` 消失 |
| 再打开 example.com | 正常加载 |

应用内锁随进程结束——这就是需要第二层锁的原因。顺带确认：`StarveilVpnService.onStartCommand`
只起前台通知并跟踪底层网络，不会自动连接；所以系统 always-on 在 Starveil 未运行时是
**fail-closed**（断网直到手动打开 App 重连），不会自己恢复隧道。设置页文案已写明这一点。

**实测二**：计划内切换会重建 tun

连接态下把预设从「绕过中国大陆」切到「全局代理」再切到「全局直连」，`tun0` 的 ifindex
从 **237 变 238**——tun 是新接口，不是同一条。

代码路径也一致：`RealKernelBridge.apply` 做 `closeService → stopVpn → awaitVpnDown → 重启`，
日志是 `kernel restarting; tunnel goes down for the swap`。

任务书里「计划内热重载 tun 不重建」在当前内核上不成立。要保住 tun 需要
`startOrReloadService`，而 P0 已实测它在活服务上必然 `initialize cache-file: timeout`
（见本文「热切换」一节），所以这不是 T4 能修的 bug；本轮记录事实，并用系统级锁覆盖这段
约 1 秒的空窗。原来 `ConnectionController.disconnect()` 里「tun never goes away」的注释
是错的，已一并改正。

**未做**：系统级锁的真机验证

方法（需要人在设备旁，lockdown 开启后服务起不来会整机断网）：

```bash
adb shell settings put secure always_on_vpn_app com.justnpcx.starveil
adb shell settings put secure always_on_vpn_lockdown 1
adb shell am force-stop com.justnpcx.starveil
# Chrome 应无法联网；恢复：
adb shell settings delete secure always_on_vpn_app
adb shell settings put secure always_on_vpn_lockdown 0
```

这次没跑：手机不在手边，lockdown 若把 adb 一并阻断就无法远程恢复。抓包同理——本机是
局域网对等节点、不是网关，看不到手机流量；要在网关（路由器）上按手机 IP 抓，才能证明
物理网卡零泄漏。

### 调查后确认「不是问题」

**直连 IPv6 报 `network is unreachable`**：当时默认网络是 WiFi `Jim_5G`，它只有
link-local 地址、没有 v6 出口（`ping6 -I rmnet_data4` 通、`-I wlan0` 不通）。蜂窝有
IPv6。所以这是正确结果，不是分流 bug。

### connect / disconnect 的失败语义（2026-10-19）

**先核实**：`DashboardViewModel` 的 connect 分支把 `ConnectionOutcome.Blocked` 一律映射成
`BLOCKED`，忽略了 `started`。这不是有意设计——没有任何注释解释它，而 disconnect 分支在
`9930507` 里已经把同一情形改成 `DISCONNECTED`。是同一 bug 在另一条路径上的遗漏。

**修的时候又牵出两处同类的谎报**：

- 启动时内核状态是 `Stopped`，`onStoppedElsewhere` 却按 Kill Switch 策略直接写
  `BLOCKED`。结果是「始终阻断」下冷启动就显示「阻断中」，而当时没有内核、没有 tun。
- `onRunningElsewhere` 也把策略当作阻断依据，一个正常转发的内核在「始终阻断」下会被
  说成阻断。

三处统一为：**`BLOCKED` 只描述「有一个正在丢包的隧道」**，判据是桥接层的
`kernel.blocking`，不是策略，也不是 `started` 之外的猜测。策略只决定「要不要去建立
阻断」，建立失败就必须说失败。

**失败语义对照表**

| 路径 | 失败情形 | 界面 | 实际流量状态 |
|---|---|---|---|
| connect | 始终阻断，阻断内核启动成功 | 已阻断 · 「Kill Switch 正在阻断全部流量」 | 全部丢弃（阻断配置生效） |
| connect | 始终阻断，阻断内核启动失败 | 未连接 · 「Kill Switch 启动失败，流量未被阻断。」 | 直连，未阻断 |
| connect | 配置无法生成 | 未连接 · 「无法为该目标生成配置」 | 直连 |
| connect | 内核拒绝启动 | 未连接 · 「内核启动失败[：原因]」 | 直连 |
| connect | 没有目标 | 未连接 · 「请先在「节点」页选择节点或分组」 | 直连 |
| disconnect | 断开时阻断，黑洞建立成功 | 已阻断 · 「Kill Switch 正在阻断全部流量」 | 全部丢弃（tun 保留、无内核读取） |
| disconnect | 黑洞建立失败（没有 tun 可留） | 未连接 · 「Kill Switch 启动失败，流量未被阻断。」 | 直连 |
| disconnect | Kill Switch 关闭 | 未连接 | 直连 |
| 任意 | 内核停止但没有黑洞（外部 stop / 崩溃） | 未连接 | 直连 |

**真机验证（vivo V2238A / Android 16，2026-10-19）**

用 `cmd appops set com.justnpcx.starveil ACTIVATE_VPN` 在 connect 的 consent 检查之后、
`establish()` 之前撤销授权，制造「阻断内核启动失败」：

```text
openTun: ...
kernel start failed: ... openTun failed [java.lang.IllegalStateException] mtu=9000: VPN consent not granted
```

| 项 | 结果 |
|---|---|
| 圆圈 | **未连接**（不再是「阻断中」） |
| 提示 | **Kill Switch 启动失败，流量未被阻断。** |
| tun0 | 不存在 |
| Chrome 打开 example.com | **正常加载**（「Example Domain」），证明确实没拦住 |

对照：同一台机器、同一档「始终阻断」，授权正常时连接 → 「已阻断」，Chrome 无法联网。
两条路径现在说的是同一件事：**界面上的阻断 = 真的有一个在丢包的隧道**。

### 10-19 内核连击回归（2026-10-19）

当天内核动了四刀，此前没有任何真机记录：`eb0ad0e` mixed + xudp → `c140dd1` 回退
gvisor → `77aa442` mixed 的 Android 适配 → `553100b`/`3ae7cbf` 本地 DNS 限界 →
`612dc9f` HY2 端口跳跃。设备 vivo V2238A / Android 16，预设「绕过大陆」，节点
订阅里的 HY2 节点（`mport=20000:30000`）。

**1. TCP 连通性（mixed 栈）**

| 目标 | 结果 |
|---|---|
| Chrome `example.com`（走代理） | 加载成功（Example Domain） |
| Chrome `www.baidu.com`（直连） | 加载成功（百度一下） |
| Chrome `www.qq.com`（直连） | 加载成功（腾讯网） |
| 微信 | 会话列表刷新，状态栏 VPN 图标，13.3 KB/s |

`c140dd1` 回退的原因是 mixed 下 TCP 全黑洞（SYN_SENT / SYN_RECV）；修复后未复现，
mixed 下国内 10–16 MB/s、境外 ~4 MB/s。

**回归中发现并修复**：AAAA 黑洞

首轮 Chrome 打不开百度和腾讯（`ERR_CONNECTION_RESET`），但 curl 到同一 IPv4 地址是
200。内核日志给出原因：

```text
connection: open connection to [2408:...]:443 using outbound/direct[direct]:
dial wlan0 (37): dial tcp [2408:...]:443: connect: network is unreachable
```

Wi-Fi 没有 IPv6 出口，但 tun 带 IPv6 地址，于是所有 AAAA 目标都被路由进隧道；应用已经
和 tun 完成握手，看到的是 reset 而不是「网络不可达」，Happy Eyeballs 因此不回退 IPv4。

修复（`972e7ed`）：tun 不再声明 IPv6 地址，`dns.strategy = ipv4_only`。之后
`network is unreachable` 计数为 0，Chrome 与微信均正常。

**2. 本地 DNS**

| 项 | 方法 | 结果 |
|---|---|---|
| 路由器管理页 | `curl http://192.168.1.1` | HTTP 200 |
| 局域网域名 | `openwrt.lan`（仅 LAN 可解析） | HTTP 200，`Server: nginx/1.30.3 (OpenWrt)` |
| DNS 不进隧道 | 上述 `.lan` 由 `local` 服务器解析 | 成功；若进隧道会 NXDOMAIN |
| 启停竞态 | 断开→连接后立刻解析+连接 `www.baidu.com` ×3 | 214 ms / 55 ms / 88 ms，`dns: exchange failed` = 0 |

**3. HY2 端口跳跃**

| 项 | 结果 |
|---|---|
| 生成配置 | `server_ports: ["20000:30000"]` |
| 端口采样（每 5 s，90 s） | 28410 → 23108 → 24955 → 21103 → 29470 |
| 跳跃期间短请求 | 12/12 HTTP 200 |
| 跨跳跃长连接 | 100 MB 下载 HTTP 200，24.9 MB/s |

跳跃间隔约 30–40 s，端口变化时连接不断。

**未覆盖**：纯 IPv6 网络。2026-10-19 尝试验证，环境不具备条件：

- `ip -6 route show` 为空，wlan0 只有 `fe80::` link-local（路由器不下发 RA）；
- `gsm.sim.state = ABSENT`，没有 SIM，`ip link` 里没有 rmnet/wwan；
- 节点域名没有 AAAA 记录——即使接入网有 IPv6，当前节点也建立不了隧道。

当前实现有意关闭隧道 IPv6（与 `ipv4_only` 一致）。在双栈网络里，IPv6 流量不会进隧道
（tun 没有 v6），会直连底层网络，属于已知泄漏面。要支持 IPv6 需要三件事：可配置的
IPv6 模式（给 tun 加回 v6 与路由）、配套的 DNS 策略、以及一个带 AAAA 的节点；同时要
明确 `ipv4_only` 下是阻断还是放行 IPv6。

### HY2 性能：与 NekoBox 同机对比（2026-10-19）

同一台 vivo、同一订阅、同一节点，homeproxy 关闭；测试为 100 MB 单流/4 并发下载
（cachefly）与 20 MB 上传（Cloudflare `__up`）。

| 节点 | 指标 | Starveil 0.7.2 | NekoBox 1.4.2 |
|---|---|---|---|
| HY2 | 单流下载 | 12.9 MB/s | 16.6–27.5 |
| HY2 | 4 并发下载 | 26.5 MB/s | 39.4 |
| HY2 | 单流上传 | 6.19 MB/s | 6.11 |
| VLESS | 单流下载 | 13.9 | 14.6 |
| VLESS | 4 并发下载 | 29.3 | 29.7 |
| VLESS | 单流上传 | 5.0 | 3.9 |

**根因**：NekoBox 1.4.2 打包 sing-box 1.12.19-neko，两边用的 `sing-quic` 都是
v0.5.2，所以不是客户端库版本。差异在 outbound 包装层：1.14 默认
`ChromeParrot = true`（QUIC 模仿 Chrome 的传输行为），1.12 没有这个特性。

**修复**：HY2 outbound 显式 `disable_chrome_parrot: true`。同机复测单流
**20.3 / 24.9 / 24.4 MB/s**，4 并发 **36.0 MB/s**，上传 6.39 MB/s——与 NekoBox 持平或更好。
代价是失去 Chrome QUIC 伪装，属于速度与抗探测的取舍。

排除项（均实测无效）：TUN 栈（mixed 12.9 > system 10.6 > gvisor 10.1）、
MTU 9000/1500、Go 内存上限/GOGC、`endpoint_independent_nat`（只改善并发）、
QUIC 窗口（默认已 8 MB/20 MB）、强制带宽提示（服务端不接受，直接零流量）。

## 未验证

> 本节是 **2026-10-17 的状态快照**，不是当前结论。之后几轮已关闭其中若干项（如 DNS 埋点，已补 224 条采样），
> 也有仍未关闭的（如 1000 节点滚动的手机帧率），以文末各轮记录为准。

以下项目需要真机，且当时无法完成（设备不在网、无相应素材）：

| 项 | 原因 |
|---|---|
| 1000 节点滚动 120fps | 设备上只有 3 个节点，没有大订阅 |
| 后台常驻 < 80 MB | debug 包 213 MB 中 96 MB 是 APK mmap，不可比；需要 release 包 |
| 断线重连 < 3 s | 断网需关闭 WiFi，而 adb 走 WiFi，会切断自身 |
| DNS 缓存 p50 < 30 ms | 尚无埋点 |
| 操作反馈 < 100 ms | 未逐屏测 |
| R8 混淆后的运行时回归 | 2026-10-17 开启 R8，尚未在真机跑过 |
| 去广告 | 2026-10-26 在电视上验过：**不通过**（广告域名照常走代理），见文末「去广告真机验证」 |

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

## 红军整改 R1–R12（2026-10-20）

基线 `5ae5b29`（0.7.4）。真机项 R1、R2、R4 已于 2026-10-20 23:46–23:52 在
vivo V2238A（Android 16）上跑完，release 包（本地签名，`versionCode=704`）。

| 项 | 代码 / 单测证据 | 真机 |
|---|---|---|
| R1 网络回调注销 | `NetworkCallbackSlotTest`：注册一次、注销一次、两条终止路径幂等、注册失败可重试 | **通过**：10 次启停，`network callback released` 出现 **10** 次；`dumpsys connectivity` 中本应用唯一 NetworkRequest 数 11 → 12（非逐轮累积；修复前 10 轮为 +11） |
| R2 生命周期互斥 | `LifecycleGateTest`：50 个并发转换无重叠、失败转换不持锁 | **通过**：20 次快速点按（间隔 0.35 s），pid 11800 不变、`initialize cache-file` 0 次、`FATAL EXCEPTION` 0 次、终态「未连接」稳定 |
| R3 脱敏 base64url | `CrashRedactionTest`：43 字符含 `-`/`_` 的 REALITY 公钥被替换；39 字符不误杀 | 不需要 |
| R4 探针 DoH | `TcpNodeProbeTest`：域名走 DoH、IP 直连不发查询、DoH 失败不回退系统解析、解析不计入时延 | **通过**：点测速后 logcat 出现 `StarveilDoh: DoH answered in 39 ms`，`falling back` 计数 0（设备未 root，无 tcpdump，以解析器日志为等效证据） |
| R5 JSON REALITY | `SingBoxJsonSubscriptionParserTest`：VLESS/Trojan 的 pbk / sid / sni / fp / mlKem 完整，`dropped=0` | 不需要 |
| R6 Clash 三协议 | `SubscriptionParsersTest`：vmess / hysteria2（obfs + ports 跳跃）/ tuic 各一条；明文 VLESS 为 NONE，trojan 仍 TLS | 不需要 |
| R7 XHTTP extra | `VlessLinkTest`（3x-ui 链接解析）、`ConfigBuilderTest`（配置含 extra 且无 `xmux`）、`LinkExportRoundTripTest`（xhttp extra 与 ws headers 往返） | 不需要 |
| R8 分组 N+1 | `RegionGroupDerivationTest`：`observeAll` 期间 `getProfileIdsByGroup` 调用数为 0 | 不需要 |
| R9 采样单事务 | `RoomNodeHealthRepositoryTest`：走复合方法一次；窗口修剪与 cap 行为不变 | 不需要 |
| R10 国旗负缓存 | `NodeCountryResolverTest`：60 s 内不重试、到期重试可成功、成功结果进程级缓存 | 不需要 |
| R11 还原事务化 | `BackupManagerTest`：第二条 profile 写入失败后，原有分组保留、节点与规则均未写入 | 不需要 |
| R12 FD 所有权 | 未改动，`closeKernelFds()` 与注释保持原样 | 不需要 |

### 实测记录

- **R1**：`am force-stop` → 启动 → 10 次连接/断开，每次间隔 3 s / 6 s。结束时
  `dumpsys connectivity` 中 `RequestorPkg: com.justnpcx.starveil` 的唯一 id 由 11 变 12；
  同一日志窗口里 `network callback released` 恰好 10 条，说明每次 `onDestroy` 都配对了注销。
  修复前同一实验为 10 轮 +11 个 id，且只能靠 `am force-stop` 清空。
- **R2**：20 次点按共 7 s 内完成，转换被 `LifecycleGate` 串行化；结束后进程号未变，
  无 `initialize cache-file: timeout`，无崩溃，界面停在「未连接」而不是卡在「正在连接」。
- **R4**：节点页点延迟徽标触发一次 TCP 探针，解析器日志显示 DoH 应答 39 ms，
  全程无 `falling back to the system resolver`。该设备无 root，无法 tcpdump 抓 53 端口，
  以「DoH 有应答且无回退」作为等效证据。

### 复核用命令

```bash
adb shell dumpsys connectivity | grep 'RequestorPkg: com.justnpcx.starveil' | grep -o 'id=[0-9]*' | sort -u | wc -l
adb logcat -d | grep -c 'network callback released'
adb logcat -d | grep -c 'initialize cache-file'
adb logcat -d | grep -E 'StarveilDoh|falling back'
```

## 2026-10-22 · 曜 深空曜黑单主题换肤验收

任务书：《Starveil UI 设计规范任务书 ——「曜」深空曜黑 · 单主题（v3，定稿）》。
开工基线：`83e9d80`（v1 浅色奢华令牌层），非任务书写于的 `ba1ffbd`——仓库有 force-push 史，以开工时实查为准。

### 落地

| 提交 | 内容 |
|---|---|
| `9fb1f7d` | 令牌层 + 通用组件 + 单主题（删除 ThemeMode/dynamicColor 与其设置 UI、字符串） |
| `d3c864d` | 首页 |
| `633ce92` | 节点 |
| `8e0d979` | 规则 |
| `4a65f84` | 日志 |
| `93eaa79` | 设置 |
| 走查提交 | 旧令牌桥接删除、二级页面（onboarding/编辑器/按应用/保活/隐私/场景）Token 化、小组件配色、docs/02 重写 |

### 走查结果

- **旧灰紫色值**（HUD 时代 `#050810`/`#0B101D`/`#121A2E`/`#1E293B`/`#38BDF8`/`#818CF8`/`#34D399`/`#64748B`/`#94A3B8`/`#475569`/`#E2E8F0`/`#F87171`/`#FBBF24` 等）在 `*.kt`/`*.xml` 中：**0 处**。
- **纱主题色值**（暖白 `#FAF9F6`、香槟金 `#B08D57`/`#AA8A50`、暖灰 `#6E675C` 等）：**0 处**。
- **裸色值**：仅 `core/ui/theme/Palette.kt`（令牌定义）与 `widget/StarveilWidget.kt`（Glance RemoteViews 读不到 CompositionLocal，按令牌值逐字写出并注释）。
- **MaterialTheme.typography / colorScheme**：0 处（全部改为 `StarveilType` / `StarveilColors`）。
- **主题切换/外观 UI**：0 处（`ThemeMode`、`dynamicColor` 及其字符串已删除）。

### 每屏强调色计数（默认状态）

| 屏 | 处数 | 明细 |
|---|---|---|
| 首页 | 3 | 圆环渐变、自动选择徽标、健康曲线 |
| 节点 | 2 | FAB 描边、选中行标记（芯片选中描边为状态相关，不计入默认态） |
| 规则 | 2（开去广告时 3） | 预设单选环、规则市场右值；去广告开关开启时 +1 |
| 日志 | 2 | 复制按钮、选中筛选芯片描边 |
| 设置 | 3 | Kill Switch 选中环、自动重连、断开切换（均为默认开启） |

### 几何与可访问性

- 首页两列速率：标签/数字/累计三行分别共基线，单位以 `alignByBaseline` 贴数字基线（单测外的真机项，见下）。
- 圆环 240dp / 4dp 全状态零位移：`ConnectionCoreTest` 断言 `sizeFor` 与 `diameterFor` 恒为 240、描边 4dp。
- 对比度：`ContrastTest` 7 条全绿（ink/ink2 ≥4.5、accent ≥3、状态色 ≥4.5、选中行 wash ≥4.5）。
- 列表底部 FAB 留白：节点 96dp、规则 96dp；日志/设置无 FAB。

### 构建

- `:app:assembleDebug` 通过；`testDebugUnitTest` 全绿；`:app:lintDebug` **0 errors**（37 warnings，均为既有资源类告警）。
- 字体：未外置字体文件，增量 0KB（预算 200KB）。

### 真机截图与实测（2026-10-23）

设备：vivo V2238A（1080×2400，3x）。装的是 **release 包**（与手机原有版本同签名 `f904a417…`，覆盖安装，节点/订阅数据保留）。

截图：`docs/recordings/obsidian-2026-10-22/`（home/nodes/rules/logs/settings）。

实测结论（uiautomator bounds + 像素采样）：

| 项 | 证据 |
|---|---|
| 纯黑底 | 五页左上角与页底采样均为 `(0,0,0)` |
| 圆环尺寸 | `ConnectionCore` 边界 720×666 px = 240×222 dp（含 18dp bottomTrim），与 240dp 契约一致 |
| 两列速率共基线 | 下行/上行标签同为 y 882–921；数字同为 945–1135；单位同为 1059–1105；累计同为 1147–1193 |
| 单位贴数字基线 | `B/s` 顶部 1059 落在数字块 945–1135 内，不再挂右上角 |
| 玻璃卡 | 五页均检出 `surfaceGlass #0B0D10` 与 `glassBorder #1E2430` 像素（节点/规则/设置最多，日志仅搜索框与芯片） |
| FAB 不压字 | 节点：FAB 1737–1904，最后一行文字止于 1090；规则：FAB 1911–1983，同行文字止于 x=784（FAB 起于 x=912） |
| 底部导航 | 标签 2211–2250，青色选中点在其上方；**修复了首次真机发现的问题**：导航栏未吃 `navigationBarsPadding`，标签被手势条压住、点击落到系统手势区，已补 inset |

**改前图**：只有首页一张（验收开始时抓的），其余四页的「改前」需要回退到 `83e9d80` 重新打包才能复现，未做；以任务书里对旧 UI 的三条问题（灰的发脏 / 布局不整齐 / 没有高贵感）作为对照说明。

### FAB 间距（已修，2026-10-23 复量）

首次真机发现节点页 FAB 距底栏 58dp、规则页 32dp。原因是 Scaffold 的 `contentWindowInsets` 又把导航栏 inset 加了一遍（底栏自己已经 pad 过）。改为只取顶部与左右 inset 后，在 Fire TV（1920×1080，density 2）复量：

- 底栏 hairline y=951
- 节点页 FAB 包围盒 y 807–918（112×112 px = 56dp）
- 规则页 FAB 底边 y=918

两页均为 **33px ≈ 16.5dp**，与设计的 16dp 一致。

### 电视端（Fire TV Stick 4K Max，AFTKRT）

- 系统 Android 11（SDK 30），**`ro.product.cpu.abilist` 只有 `armeabi-v7a`，`abilist64` 为空** —— 这台是 32 位用户空间，所以装的是 `app-armeabi-v7a-release.apk`；arm64 包装上去会在加载 libbox 时失败。
- 1920×1080 / density 320 → 960×540dp，横屏走的是 dashboard 的滚动分支；五页截图在 `docs/recordings/obsidian-2026-10-22/tv-*.jpg`，四角采样均为 `(0,0,0)`。
- 电视无触屏，用 `input tap` 注入坐标可正常导航；首次安装走了 onboarding，Skip 后进入主界面。

## 2026-10-23 · 0.8.0「曜」验收整改（U1–U7）

依据：用户 2026-10-23 真机截图 8 张 + v3 验收清单。设备：Fire TV Stick 4K Max（1920×1080 / density 2 / Android 11），订阅实测节点 2 个 + 1 个测试节点（`example.com`，用于凑满三行）。

| 项 | 提交 | 实测 |
|---|---|---|
| U1 自动选择静默 + 徽标改语义 | `cdac44b` | 比较改为按 id（原按 label：卡片用 displayName 带国旗、库里是 name，同一节点永远"变了"）；结果由卡片自身（名称 + 延迟）呈现；徽标显示 自动/手动；动作降级为卡内文字链；点卡片进节点页 |
| U2 健康曲线缓存先行 | `3eb94b3` | `carryForward()` 用最近 120 条已存样本补首帧；窗口内样本够数时原样透传（2 个单测）；骨架改用玻璃面 + ink 高光 |
| U3 节点行红色块 / 延迟列 | `a0f13bb` + `5ba1327` | 根因：`SwipeToDismissBox` 静止时也组合 `backgroundContent`。`progress` 静止非零（第一次没修掉）、`requireOffset()` 在组合期读取会崩（"offset was read before being initialized"，已修）；最终以 `targetValue` 门控。三行列表实测：滑动红 **0 px**，行底 `#0B0D10`；延迟 76dp 定宽右对齐，"ms" 完整 |
| U4 规则空状态卡片化 | `5e86ade` | 空状态进玻璃卡 |
| U5 设置分组间距 | `45f8ea5` | 组间 32→16dp，标题与卡片 8dp |
| U6 两处排版 | `d57ef0e` | 规则市场副标题单行省略（中文词内断行消失）；Kill Switch 链接独立成行，段落恢复整行宽 |
| U7 连接态渐变环 | 本次 | 连接态实测：环上左侧均值 (71,136,170) 青、右侧 (91,117,175) 紫——渐变成立；环心 (959.5, 285.5) 两态一致，状态字 y 269–340 两态一致（连接态外发光使包围盒大 17px/边，属辉光不是位移） |

截图：`docs/recordings/obsidian-fixes-2026-10-23/`。

**未完成**：手机（vivo V2520A）在整改期间掉线（mDNS 无广播、5555 refused），以上为电视端实测；手机端待重新开启无线调试后补拍（尤其中文断行与竖屏零位移）。电视端同时发现一个非整改项：横屏连接后页面会因焦点滚动，圆环整体上移——竖屏无此路径，已记录未修。

## 2026-10-24 · 0.8.1 验收复核（V1–V3）

设备：Fire TV Stick 4K Max（1920×1080 / density 2 / Android 11），真实订阅 2 个节点。

### V1 健康曲线首帧（U2 返工）— 已修，根因两条

1. **年龄剪枝写在插入事务里**：`recordSample()` 先插入新样本，再 `DELETE ... WHERE atMs < atMs-30min`；采样器在 App 启动时就开始记录，所以隔夜后第一次探测会把整段历史删掉——而那正是首帧要画的数据。窗口 30 分钟、节奏 30 秒，于是每次冷启动都要等 5 个样本（约 2 分钟）才出图。U2 的读侧兜底因此永远无数据可兜。
   修法：存储改为**定长环**（每节点 120 条，不按时间删）；"窗口"降级为读侧概念，`carryForward()` 在窗口样本不足时用最近存量。
2. **横屏下健康卡高度为 0**：`Modifier.weight()` 放在可滚动 Column 里拿到 0 高度，卡片被组合出来但不可见——电视上等于没有健康区。修法：横屏改用固定图表高度。

实测（电视）：`am force-stop` → 启动 → 卡片直接显示 `98% up` 与曲线，**无 collecting**；修复前同一路径要空等数分钟。

未取到：手机竖屏冷启动的 ≤300ms 逐帧测量（手机掉线）。电视上健康卡在首屏下方，无法用同一方法测"首帧可见"。

### V2 自动选择行为自测 — 通过

| 步骤 | 实测 |
|---|---|
| 当前即最优时连点 5 次 | 第 1 次切到更优节点后，后续 4 次**零提示**、节点不变；徽标为 `Auto` |
| 手动选较差节点 | 徽标变 `Manual` |
| 再触发一次自动选择 | 切回较优节点，徽标回 `Auto`，无 toast；结果由卡片（节点名 + 延迟）呈现 |

### V3 规则页自证 — 通过

- 空状态在卡内：`No rules of your own` 所在区域背景采样 `(11,13,16)` = `surfaceGlass`，不是黑底裸排。
- 规则市场副标题单行：`Curated packs, pinned and hash-checked` 单行显示（`maxLines=1` + 省略号，与语言无关，中文词内断行同样不会再发生）。

截图：`docs/recordings/obsidian-fixes-2026-10-24/`。

## 2026-10-24 · M4-1 1000 节点滚动

**夹具**：`app/src/benchmark/.../SeedReceiver.kt`（仅 benchmark 变体，release 包里没有这个 receiver）。广播 `com.justnpcx.starveil.benchmark.SEED` 写入 1000 个节点：12 个地区的国旗/中文名、vless 协议、两种端口、每节点 6 条延迟样本（40–320ms，每 25 个节点最后一条为超时）。写完后在外部目录落一个 `seed.done` 标记，benchmark 读到才继续。

**测量**（Fire TV AFTKRT，1920×1080 override / 60Hz 面板 / 32 位）：

| 场景 | 帧数 | fps | Janky | p50 | p90 | p95 | p99 |
|---|---|---|---|---|---|---|---|
| 稳态滚动（3 次 fling，中位） | 743 | **29.9** | **0.0%** | 6ms | 8ms | 8ms | 10ms |
| 打开列表后第一次滚动 | 386 | — | **31.9%** | 11ms | 32ms | 46ms | 69ms |

方法：`dumpsys gfxinfo <pkg> reset` → `input swipe`（fling，80ms）→ 2.5s → `dumpsys gfxinfo`，取 `Total frames rendered` / `Janky frames` / 百分位；fps = 帧数 ÷ 墙钟窗口。三次试验取中位。

**结论**：
- 稳态下 1000 行列表 **0% 掉帧**，单帧 p99 10ms，远在 16.7ms 预算内；29.9fps 是这台电视在 1080p override 下的合成节奏（60Hz 面板、每两个 vsync 出一帧），不是丢帧——gfxinfo 的 Janky 计数为 0 也印证了这点。
- 唯一真正的掉帧在**打开列表后的第一次滚动**（31.9%），属于首屏组合 + 首帧绑定的一次性成本，是后续要优化的点。

**Macrobenchmark**：`NodesScrollBenchmark` 已随夹具入库，但在本机跑不通——`UiDevice.click()` 与 `input tap` 在这台电视上对 Compose 底部导航的首次点击会被吞掉（屏幕休眠也会介入），多次重试仍不稳定。上面的数字因此来自同一夹具上的 gfxinfo framestats。手机恢复连接后应补一次 Macrobenchmark 的 `FrameTimingMetric` 结果。

## 2026-10-24 · M4-4 .so 瘦身评估

**现状（arm64-v8a release 包）**

| 库 | 原始 | APK 内（deflate） |
|---|---|---|
| libbox.so | 61.70 MB | 19.57 MB |
| libsqlcipher.so | 2.10 MB | 1.03 MB |
| libsentry.so | 0.88 MB | 0.32 MB |
| 其余 3 个 | 0.04 MB | 0.01 MB |
| 合计 | 64.7 MB | 20.9 MB |

libbox.so 的段分布（`size -A`）：`.text` 22.4 MB、`.data.rel.ro` 26.7 MB、`.rela.dyn` 6.9 MB、`.rodata` 4.2 MB、`.noptrdata` 0.64 MB、`.eh_frame` 0.13 MB、`.comment` 204 B。

**已经做掉的**：符号表与调试段**不存在**（无 `.symtab`/`.debug_*`，Go 链接期 `-s -w` 生效），按 ABI 拆分已在做。

**还剩什么，能量化到多少**

1. **动态重定位未打包**：`.rela.dyn` 6.9 MB / 每项 18 B ≈ 38 万条，且没有 `.relr.dyn`。重新链接时加 `--pack-dyn-relocs=android`（API 23+ 可用的 APS2）通常能压到 1/3 左右，**原始省约 4 MB、APK 内省约 1.0–1.3 MB**。代价是要重编 libbox.aar（当前是预编译产物）。
2. **按功能裁剪内核**：`.text` 22.4 MB 就是整个 sing-box 代码，唯一的真大头。用 sing-box 的 build tag 去掉不需要的入站/协议（QUIC、TUN、WireGuard 等）能省多少只能靠一次真实构建测出来——没有源码构建就没有数字，这里不编。
3. **`useLegacyPackaging = true` 是既定取舍，不是漏配**：它让 .so 在 APK 里保持压缩（下载 19.6 MB 而不是 61.7 MB），代价是安装时解压到 `/data/app`（多占约 62 MB 存储）。改回 false 等于用 62 MB 存储换 42 MB/次的下载量，对 GitHub 直发 APK 的分发方式不划算。

**对内存目标的意义（实测）**

Fire TV，3 个节点、隧道已连接、benchmark 变体（R8，与 release 同级）：

```text
TOTAL PSS 78.4 MB   RSS 151.0 MB   swap 0.2 MB
  .so mmap   11.1 MB PSS / 33.1 MB RSS
  GL mtrack  30.5 MB
  Native heap 10.9 MB
  Dalvik      4.8 MB
```

结论：**80 MB 目标在这台机器上已经达成（78.4 MB），而 libbox 只占 PSS 的 11.1 MB**。即便把 .so 砍掉 10%，PSS 也只降约 1 MB——瘦身不是这个目标的有效杠杆；真正的大项是 GL 表面（30.5 MB，圆环所在的硬件层）与系统共享页。要再往下走，方向是渲染层（圆环的离屏缓冲），不是内核库。

## 2026-10-24 · C 组：T4 / T5 / SqlCipher 仪器测试

### SqlCipher 仪器测试（悬了很久的一条，已关闭）

`./gradlew :core:data:connectedDebugAndroidTest` 在 Fire TV AFTKRT（Android 11 / API 30）实跑：

```text
tests=9 failures=0 errors=0 time=26.086s
  a_failed_migration_throws_and_does_not_wipe_the_file
  a_wrong_passphrase_cannot_open_the_database
  keystore_passphrase_is_stable_and_non_empty
  database_file_is_encrypted_and_round_trips_credentials
  migration_1_to_2_keeps_every_row_and_adds_the_sample_table
  migration_2_to_3_keeps_every_row_and_adds_the_region_column
  migration_3_to_4_keeps_every_row_and_adds_the_country_column
  migration_4_to_5_gives_existing_samples_full_weight
  the_composite_key_makes_a_re_probe_a_replace
```

结论：不是"应该跑过"，是**真的在真机上跑过且全绿**（26 秒，含四次迁移）。

### T4 内核崩溃取证

见 `docs/18-kernel-crash-capture.md`。做了实操：`am crash` 制造的崩溃在 `logcat -b crash` 里拿到了完整记录；dropbox 可读但 Java 崩溃不落墓碑（符合预期）。**没做到**的部分也写明了：非 root 设备 `kill -11` 被 SELinux 拒绝，没有拿到真实 native 墓碑样本。文档里同时给出"内核日志未落盘"这一缺口的修复建议。

### T5 网关指纹

见 `docs/19-gateway-fingerprint.md`：采集 7 个"出口形状"字段（OUI、网关 IP、DHCP option 54/55、DNS 集合、门户 URL、管理页证书），**只存本机 + 哈希**，不上传不导出，用途是场景匹配与诊断。设计稿，无代码；文末列了实现前必须先验证的三个问题（ARP 受限、主动探测是否默认关、Mesh 抖动窗口）。

### T9 设备清理（回执）

| 设备 | 处理 | 结果 |
|---|---|---|
| Fire TV AFTKRT | `pm clear` → `pm uninstall com.justnpcx.starveil`；删 `/data/local/tmp/*.apk` 与 `/sdcard/*.png`；恢复 `screensaver_enabled=1`、`screen_off_timeout=600000`、`svc power stayon false` | 应用已移除，临时文件 0 个，休眠设置已还原 |
| vivo V2520A | 本轮全程离线（mDNS 无广播、5555 refused），未做任何操作 | 0.8.1 由用户自行通过应用内更新安装 |

## 2026-10-24 · M4-2 DNS 埋点（采样待设备）

**埋点**：`DnsMetrics`（core:data，@Singleton）——512 条定长环，4 KB，记录每次解析的耗时与来源（`DOH` / `FALLBACK` / `CACHE` / `LITERAL` / `FAILED`），百分位用**最近秩**（不插值：p95 报"27.4ms"是样本给不出的精度）。

**接入点**：
- `DohResolver.resolve/resolveStrict`：DoH 成功、系统回退、失败三条路径都记；
- `NodeCountryResolver` 的进程内记忆命中记 `CACHE`（这就是"缓存命中标记"）；
- 开关在**开发者选项**（默认开，关掉即停止记录，环里已有数据保留）。

**UI**：开发者选项里一行开关 + 一行统计（`n 次 · p50 · p95 · 回退% · 缓存%`）+ 清除，屏幕可见时每 2 秒刷新一次（不让 DNS 路径去驱动 UI）。

**测试**：`DnsMetricsTest` 6 条——空环不编数、最近秩、5 样本的 p95、环滚动只留最新 512、缓存/回退占比、关掉开关后不再记录且不丢已有。

**采样（2026-10-24 补跑，电视端）**：装 HEAD 的 debug 包（含 M4-2 埋点，R8 无关紧要，量的是 DNS 不是 UI），导入一条 2 节点的订阅，逐节点选中后各点 100 次「测所选节点」——每次探针在计时之外先做一次 `resolveStrict` DoH 查询，所以一次点击 = 1 条真实样本。

```text
224 samples · p50 51 ms · p95 138 ms · fallback 0% · cache 0%
```

- **224 ≥ 200**，采样量达标；数字来自真机真网络（Aliyun 优先、DNSPod 兜底），不是夹具。
- **fallback 0%**：200 次里没有一次退到系统解析器，说明 DoH 这条主路径在现网稳定。
- **cache 0%**：国家解析的进程内记忆只在同一主机第二次被问时计数，而列表把结果写回行之后就不再问——这个计数器在当前流程里几乎不会动，属于埋点口径问题，不是缺陷；若要观察缓存收益，应把它挂到探针的解析路径上（同一主机每次探测都会重查，那里才有真实的重复）。
- 截图：`docs/recordings/m42-2026-10-24/dns-stats-224.png`。

**顺带修掉的一个标签错误**：节点页右上那个刷新图标，无障碍描述写的是「Test all nodes / 测试全部节点」，但它的动作是 `latencyTester.testSelectedNode()`（M3 的「选项 A」，只测所选节点，没选时提示 "Pick a node first."）。读屏用户会听到一个不存在的功能。已把文案改成 `profiles_test_selected` = "Test the selected node / 测试所选节点"，装机后 `uiautomator dump` 确认 `content-desc="Test the selected node"`。

## 2026-10-24 · M4-3 逐页帧数据（电视端）

**设备与包**：Fire TV Stick 4K Max（AFTKRT，SDK 30，1080p / density 320 / 32 位），装的是 **v0.8.1 正式包**（`armeabi-v7a`，R8，非 benchmark 变体）。电视断电重启后 IP 变成 192.168.50.118，ADB 走 mDNS 发现的 5555。

**顺带确认（用户上轮提的"验证 App 回 onboarding"）**：全新安装后首次启动确实回到引导第一页（`Get started` → 导入方式 → 主界面），未出现跳过引导直接进主页的情况。

**方法**：每页 3 轮往返 —— 先点去别的页，`dumpsys gfxinfo <pkg> reset`，再 `input tap` 目标页，1.2 s 后读 `framestats`。帧间隔取相邻 `IntendedVsync` 之差，>20 ms 记一次掉帧；随后静置 2 s 再读一次，作为"无输入时是否仍在重绘"的对照。表中取 3 轮中位数。

| 页面 | 帧数 | 动画窗口 | 平均间隔 | p95 | p99 | 最差单帧 | 掉帧率 | 静置 2 s |
|---|---|---|---|---|---|---|---|---|
| Home | 45 | 750 ms | 17.1 ms | 16.7 ms | 18.5 ms | 33.3 ms | 2.3% | 0 帧 |
| Nodes | 45 | 749 ms | 17.1 ms | 16.7 ms | 18.6 ms | 33.3 ms | 2.3% | 0 帧 |
| Rules | 44 | 752 ms | 17.5 ms | 16.7 ms | 33.3 ms | 33.3 ms | 4.7% | 0 帧 |
| Logs | 44 | 750 ms | 17.5 ms | 16.7 ms | 33.3 ms | 33.3 ms | 4.7% | 0 帧 |
| Settings | 44 | 751 ms | 17.5 ms | 16.7 ms | 33.3 ms | 50.0 ms | 4.7% | 0 帧 |

**冷/热启动**（`am start -W`，各 3 次）：冷启动 TotalTime 690 / 435 / 486 ms（中位 **486 ms**），热启动 115 / 143 / 77 ms。

**读法**

- 五个页面的切换都是 44–45 帧、约 750 ms 的动画，平均帧间隔 17.1–17.5 ms ≈ 57–58 fps（60 Hz 面板的两 vsync 节奏），p95 恰好 16.7 ms —— 绝大多数帧在预算内。
- 每页 1–2 帧越过 20 ms（2.3–4.7%），最差单帧 33.3 ms，Settings 一次 50 ms；位置都在切换的第一帧，即目标页的首次组合，与 M4-1 里"第一次滚动 31.9% 掉帧"是同一个成本来源（首次组合），不是稳态问题。
- 静置 2 秒 **0 帧**：没有页面在空闲时继续跑动画或重绘。注意本次是未连接状态，连接环的呼吸动画未运行；连接态的静置对照要等有订阅时补。

**输入延迟在这台电视上无法隔离（如实记录）**

`input tap` 在本机单次要 **860–870 ms**（Java 版 `input` 的 app_process 冷启动），而首帧在命令返回之前就已上屏，所以 `framestats` 里 `NewestInputEvent` 全是 0，`首帧完成 − 命令返回` ≈ 0–13 ms 只说明"命令还没退出"，不是延迟。想绕开它直接 `sendevent /dev/input/event2`（`amazon_touch`，shell 已在 `input` 组、设备 660）被 **SELinux** 拒绝（`Permission denied`，是策略不是权限位）。因此上表不含 tap→present：这个数只能等手机（触摸 + `input` 更快）或 rooted 电视。

截图：`docs/recordings/m43-2026-10-24/`。

## 2026-10-25 · Step 0：03 苹果毛玻璃令牌抄录与令牌层

**来源**：`apple-google-demo`（muse 分享页 → iframe → 03 面板）。不是目测：用 Playwright 打开 03 面板后读 `getComputedStyle`，逐项取计算值。

| 令牌 | 值 | 出处（Demo 03 的元素） |
|---|---|---|
| `bg` | `#E9EDF2` | `.screen` 底色 / 色卡 |
| 页面渐变 | `linear-gradient(160deg, #E8EEF5, #EEF3EE 55%, #F3ECE4)` | `.screen` 的 `background-image` |
| 氛围晕染 | 蓝 `rgba(90,160,255,.28)` @12%/6%、绿 `rgba(80,210,170,.22)` @88%/30%、琥珀 `rgba(255,170,90,.20)` @70%/92%，各自 300×200 / 280×220 / 320×240，65% 处淡出 | `.glassfx` |
| `surfaceGlass` | `rgba(255,255,255,.68)` + `blur(18px) saturate(1.5)`，圆角 22，阴影 `rgba(31,38,135,.07) 0 4 18` | `.pcard` / `.nlist` |
| `surfaceThin` | `rgba(255,255,255,.55)` | `.search` / `.flag` |
| `surfaceChrome` | `rgba(255,255,255,.60)` | `.pnav` |
| `glassBorder` / `hairline` | `rgba(255,255,255,.75)` 1px（卡边、导航顶线、卡内行分隔共用） | `.pcard` 边框、`.nrow` 下边框 |
| `ink` | `#1D1D1F` | 正文 |
| `ink2` | `rgba(29,29,31,.62)`（Demo 为 .60，见下） | `.rsub` |
| `ink3` | `rgba(29,29,31,.48)`（Demo 为 .42，见下） | `.tot` / 未选中图标 |
| `accent` | `#0071E3` | `.chip` 边与字、导航选中点、选中行 10% 底 |
| `accentText` | `#0066CC` | Demo 自己的次级蓝（"选它"按钮、进度条） |
| `ok` | `#1D7A3A` | `.onl`「在线 100%」 |
| `warn` | `#AC4D00` | **Demo 未出现**，用户补定（对底面 4.68:1） |
| `err` | `#E5484D` | `.sdot` 失败点 |
| `errText` | `#BE3238` | 派生：Demo 的红做文字只有 3.33:1 |
| 曲线 | 描边 `accent` 1.5dp，填充 `accent` @14% | `.spark` 的两条 path |
| 圆角 | 卡片 22dp、搜索框胶囊、旗标 6dp、FAB 圆形 | 计算值 |

**为过 4.5:1 做的三处调整**（§5 允许，逐条记数）：
- `ink2` .60 → **.62**：4.29 → 4.59（`ink2` 是全应用的说明行，属正文）。
- `ink3` .42 → **.48**：2.59 → 3.06（三级字与未选中图标，按 3:1 的承意图形下限）。
- Demo 的亮绿状态点 `#30A46C` 在底面只有 2.68:1，8dp 的点是承意图形，统一改用 `ok`（4.58:1），不再单列 `okDot`。

**无法照抄的一处**：backdrop blur。Compose 没有原生背景模糊（`Modifier.blur` 模糊的是自身内容，不是身后），三方库被 §5 禁止。落地方式：卡片用 68% 白叠在静态氛围底上——底是平滑渐变，视觉上等价于把底模糊后透上来；导航条因为下面会滚过文字，按 §5 的降级条款改成**不透明浅色实面**（页面底色 + 60% 白）。这一条要在真机上按帧率再确认一次。

**替换范围**：`Palette.kt` 整体重写（`ObsidianPalette` → `GlassPalette`，`accentA/accentB` 合并为 `accent`），`Theme.kt` 改 `lightColorScheme`，卡片/开关/芯片/徽标/按钮/搜索框/FAB/圆环/曲线全部换到新令牌，新增 `StarveilBackground`（页面渐变 + 三处晕染）与 `StarveilButton` 的内容重载（引导页不再用 Material 的填充按钮）。

## 2026-10-25 · 03 毛玻璃换肤：全量走查

**搜索口径**（`--include=*.kt,*.xml,*.md`，排除 `build/`）：

| 目标 | 结果 |
|---|---|
| 曜令牌色值 `#000000` / `0B0D10` / `22D3EE` / `8B5CF6` | 0 处（原 `docs/02` 与 `StarveilWidget.kt` 的残留已改） |
| 旧灰紫 / 纱主题色值 `FAF9F6` / `B08D57` | 0 处 |
| `Obsidian` / `obsidian` 命名 | 0 处 |
| `Palette.kt` 之外的 `Color(0x…)` | 4 处，全部在 `StarveilWidget.kt`：Glance 的 RemoteViews 树读不到 CompositionLocal，按 §6 的例外条款写死并注明来源 |

**提交与改动的对应关系（按提交名找代码前先看这段）**：

- 设置页的玻璃组与单选标记实际落在 `6aeb069`（Step 0 一次性替换令牌时带过去的），`14a711b` 只改了 `KeepAliveScreen` 的状态色 —— 那个提交名比改动大。
- `6aeb069` 还夹带了一个误提交的 `.write-probe`（写权限探针），已在 `35f4755` 删除。
- 其余提交与文件名一一对应。换肤本体 `6aeb069`…`892b59f` 这 10 个提交只含 `kt` / `png` / `md`，没有构建产物（唯一例外是上面那个探针，已在 `35f4755` 删掉）；之后 `f0a300f` 只补了这段说明。

**逐屏强调色计数**（`accent` 的出现处，不含 `accentText` 文字）：

| 页面 | 处数 | 明细 |
|---|---|---|
| 首页 | 1 | 圆环（未连接时是 `ink3`，连接后才是 accent） |
| 节点页 | 3 | FAB 描边、选中行左缘、选中行淡底（同一处选中态的两笔） |
| 规则页 | 1 | 单选标记 |
| 日志页 | 0 | 复制是 `accentText` |
| 设置页 | 1 | 单选标记 |

**回归复核**：U3（节点行右侧色块/暖调底）——选中行只有 `accentWash` + 左缘竖线，右侧 ms 与「untested」无遮挡；U4（空状态收进卡片）——规则页空状态仍在卡内；U5（设置组间距 32dp）——保持；U6（规则市场副标题单行省略、VPN 链接独立成行）——保持；U1（自动选择徽标语义）——行为未动，仅换色。

**已知遗留**（本次换肤未引入，也不在换肤范围内）：

- 电视 1080p 横屏下，圆环中心词 `DISCONNECTED` 比 240dp 圆环宽约 40px——0.8.1 就有，属字阶与圆环直径的既有矛盾，任务书禁止改字号，未动。
- Compose 无原生 backdrop blur，卡片用半透明 + 静态氛围底等效，导航条用不透明实面（§6 降级条款）。
- 桌面小组件按 §6 例外写死解析后的色值。

### 性能复核（换肤后，2026-10-25）

第一版把氛围底画在 `drawBehind` 里，每帧重跑 1 条线性渐变 + 3 个大半径径向渐变（1920×1080 全覆盖）。电视上直接掉到 30fps：

| 版本 | 首页切换 | 帧数 | p95 帧间隔 | 掉帧率 |
|---|---|---|---|---|
| 换肤第一版（每帧重绘渐变） | — | 33 | 33.4ms | **97.0%** |
| 换肤 + 背景烘焙成位图 | — | 44 | 16.7ms | **4.7%** |
| 0.8.1（曜，同机同场次对照） | — | 41–43 | 16.7ms | 7.0–12.2% |

烘焙后逐页（3 轮中位数，release/R8）：

| 页面 | 帧数 | 窗口 | 平均间隔 | p95 | 掉帧率 |
|---|---|---|---|---|---|
| Home | 44 | 747ms | 17.5ms | 16.7ms | 4.7% |
| Nodes | 44 | 748ms | 17.5ms | 16.7ms | 4.7% |
| Rules | 44 | 751ms | 17.5ms | 16.7ms | 4.7% |
| Logs | 44 | 749ms | 17.5ms | 16.7ms | 4.7% |
| Settings | 43 | 749ms | 17.9ms | 16.7ms | 4.8% |

结论：**回到 0.8.1 的同一水平**（0.8.1 当轮为 2.3–4.7%，本次 4.7–4.8%，差异在一帧以内）。背景烘焙前后逐点比色差 ≤1/255，视觉无变化。

做法：`drawWithCache` 里按尺寸把渐变与三处晕染画进一张 `ImageBitmap`，之后每帧只做一次贴图。

### FAB 遮挡复核（未完全通过）

- 列表底部留白 96dp：滚到末尾时，最后一行文字的下沿在 y=759，FAB 圆的上沿在 y=807 —— **末尾 48px 空隙，末行不被遮挡**。
- 静止状态下抓了 6 个滚动位置，文字与 FAB 圆（含 56dp 外框）零相交。
- **结构性风险（已修）**：行右侧的延迟/「untested」原本右边界在 x=1832，而 FAB 圆覆盖 x 1776–1888 —— 只要某行滚到 y 807–919 这条带里，右侧文字就会从按钮底下经过。
- 修法（方案 1）：节点行右侧加 **72dp 右槽**（FAB 56 + 边距 16），常量 `FAB_GUTTER`。改后实测：行文字右边界 x=1720，FAB 圆左边界 x=1776，**56px（28dp）净空**，任何滚动位置都不可能相交。代价是延迟列不再贴卡片右缘、整列左移 56dp。截图：`docs/recordings/glass-2026-10-25/nodes-fab-gutter.png`。

## 2026-10-25 · 对话框里的芯片（Material 默认色漏入）

**现象**：新增规则 / 新增场景 / 分组菜单三个对话框里的芯片是 Material `FilterChip` 的默认色——选中是淡紫 `secondaryContainer`，未选中描边是 `outline`（我们把它设成了白色 `glassBorder`，于是在对话框上是一圈白框）。

**改法**：四处 `FilterChip` 全部换成 `StarveilChip`，并给它加了 `ChipSurface.Dialog`：

| 状态 | 填充 | 描边 | 文字 |
|---|---|---|---|
| 未选中 | `bg` | `ink3` | `ink2` |
| 选中 | `accentWash`（accent 10%） | `accent` | `accentText` |

**实测（电视，取像素）**：选中芯片 (221,235,247) = accent 10% 叠在 `surfaceSolid` 上的精确值；未选中 (233,237,242) = `bg`；无淡紫、无白框。截图：`docs/recordings/glass-2026-10-25/dialog-rule-chips.png`、`dialog-scene-chips.png`。

顺带把另外两处也换了（节点页分组菜单对话框、节点编辑器里的 `ChipRow`），否则同一个 Material 默认色还会在那里露出来。

## 2026-10-25 · P0-5 自动选择：透明化 + 防抖 + 真切换

**开工实查**：远端 main HEAD = `95f1d4f`（任务书基线 `7c13c14` 已过期：其间发了 0.8.4 与对话框芯片修复）。

**改了什么**

- `NodePicker` 新增 `STICKINESS_MARGIN = 3`（分制：99 分覆盖 350ms，所以 3 分 ≈ 10ms）、`AutoPick`、`decide(candidates, currentId, ...)`、`pickerPolicySummary()`。
- `DropRecovery.autoPick()` 把「当前节点」纳入决策；`DashboardViewModel.autoSelect()` 改成按 `AutoPick` 走，并把每次判断写进日志：探针地址（`TCP host:port`）、策略（`cooldown=30min, margin=3pts(≈10ms)`）、以及 kept / switched 的具体分数与差值。
- 切换行为本身没改（目标收集器在已连接时 `applyConfigChange()`，一直是真的重建配置），改的是「凭什么切」和「为什么不切」现在有数可查。

**实测（单元测试，`core:model`）**

| 用例 | 输入 | 结果 |
|---|---|---|
| 5ms 小胜不切 | 60ms vs 55ms，同可用率 | `switch=false`，delta=1 < 3 |
| 真优势才切 | 200ms vs 60ms | `switch=true`，delta≥3，选中挑战者 |
| 超时节点必须离开 | 当前节点连续 3 次失败 | `switch=true`（粘性不保护已停摆的节点） |

**V-2（行为自测）另立一节**：本项只验代码与单测，行为验收写在下面「V-2 自动选择行为自测」一节（Fire TV，行为类；两条均通过）。

## 2026-10-25 · P0-1 大列表：虚拟化 / 全量搜索 / 超时折叠（帧率待手机）

**实查结论（先定位再改）**

| 要求 | 现状 | 处理 |
|---|---|---|
| 只渲染可见行 | 已经是 `LazyColumn`（`ProfilesScreen.kt:384`） | 无需改 |
| 离屏行不画健康曲线 | 曲线在行内，`LazyColumn` 不为离屏行组合 | 无需改 |
| 搜索全量 | `ProfilesViewModel.listState` 在**完整列表**上 `filterBy(query)`，与分组过滤是先后关系，不是「只搜当前屏」 | 无需改 |
| 超时节点默认隐藏 + 可展开 | **缺** | 本次实现 |

**本次改动**：`ProfilesScreen` 用 `state.health` 分出 `NodeStatus.DOWN` 的行，默认不渲染；卡片底部加一行 `N 个超时 · 显示` / `收起 N 个超时节点`（`accentText`，点击切换）。数据不动，只是不画。没有超时节点时这一行不出现。文案双语已过 `checkTranslations`（514 条全译）。

**未做（任务书验收项）**：1000 节点合成订阅的手机 Macrobenchmark。手机 192.168.50.140 可达但 5555 关闭，按规矩 8 不能拿 Fire TV 的数顶替，本项帧率留待手机接入后补测。夹具当时还有个更根本的问题（广播种子从未执行），已在后面「P0-1 夹具修复」一节定位并修掉。

## 2026-10-25 · P2-12 / P2-13 / P2-14 结论

### P2-12 QS Tile / Widget / 通知：已核查，无需新增

现有三处一键入口：`StarveilTileService`（快捷设置磁贴）、`StarveilWidget`（Glance 桌面小组件，显示状态并可点进应用）、通知上的 `DisconnectReceiver`（`ACTION_DISCONNECT`）。任务书要求「只做用户一键可达的动作，不要堆新入口」——连接 / 断开 / 查看状态这三件事已经各自有一处可达，再加 START / STOP / TOGGLE 广播只是给自动化工具开的口子，不是给用户的一键入口。**关项，不新增。**

### P2-13 多订阅合并 / 过滤 / 去重：按任务书暂缓

任务书写明「P0 / P1 没做完不要提前做」。当前 P0 只完成 P0-5 与 P0-1 的非帧率部分，P1 未开工，因此本项**不启动**。

### P2-14 Android 性能基线：方法已定，数值待手机

**方法（写进本文件，后续每轮照此测）**：

1. 机型与系统版本、是否充电、屏幕亮度、是否飞行模式，逐项记录；一次基线内不得变化。
2. workload 固定为：冷启动进首页 → 停留 30s → 切节点页 → 1000 节点夹具上 3 次 fling → 切规则/日志/设置各一次 → 返回首页。
3. 指标：`dumpsys gfxinfo <pkg> framestats` 的掉帧率与 p95 帧间隔；`am start -W` 的 TotalTime；`dumpsys meminfo <pkg>` 的 PSS；一轮 10 分钟的 `dumpsys batterystats` 差值作能耗参考。
4. 每项测 3 次取中位，原始数写进本文件，不写「整体达标」这种汇总。

**本轮没有数值**：手机 192.168.50.140 可达但 5555 关闭（无线调试未开），按规矩 8 不能拿 Fire TV 的数顶替。方法已定，等手机接入后按上述 workload 补首轮基线。

## 2026-10-25 · P0-1 夹具修复：广播种子从来没写进去过

**定位结论（比数字更重要）**：1000 节点夹具的 `SeedReceiver` 在真机上**从来没有执行过**，`am broadcast` 却一直返回 `result=0`。原因是 `pm clear` 之后应用处于 **stopped 状态**，stopped 包收不到广播——命令成功、接收器没跑，看起来像写进去了。M4-1 当时把电视上的失败归因为"`input tap` 被 Compose 底部导航吞掉"，那个判断是错的。

**修法（两处，都在 benchmark 侧）**：

1. `NodesScrollBenchmark.seed()`：`pm clear` 之后先 `am start` 把进程拉起来，再发广播，并加 `--include-stopped-packages`。
2. `SeedReceiver`：1000 节点 + 6000 样本改成**一个事务**（新增 `database()` entry point + `withTransaction`）。原来是 7000 次独立事务，手机上一次就超出广播的执行预算，进程被中途杀掉。为此给 `:app` 加了 `libs.room.ktx`（只有 benchmark 变体用到）。

**验证**：修完后广播能让应用跳过引导、节点确实写进库（应用在前台时实测）。

**Macrobenchmark 仍未跑出数**：两次尝试都中断——第一次是我在测量期间执行 `uiautomator dump`，与 instrumentation 的 UiAutomation 冲突，benchmark 进程崩溃（`UiAutomationService already registered`）；第二次重跑后手机无线调试掉线（`ping 192.168.50.140` 100% 丢包、mDNS 无广播），无法继续。**本轮没有任何手机帧率数字，不写结论。**

**下次接上手机后的步骤**：装 `app-arm64-v8a-benchmark.apk` + `benchmark-benchmark.apk` → `am instrument -w -e class com.justnpcx.starveil.benchmark.NodesScrollBenchmark com.justnpcx.starveil.benchmark/androidx.test.runner.AndroidJUnitRunner` → **测量期间不要执行任何 `uiautomator` / `screencap`**。

## 2026-10-25 · V-2 自动选择行为自测（Fire TV，行为类，非性能）

**为什么可以用电视**：V-2 测的是行为（是否切换、是否弹提示），不是帧率 / 点按延迟 / DNS / 后台，规矩 8 限制的是后四类。

**① 当前即最优时连点 → 应 0 提示、节点不变**：通过

实测：导入订阅后第一次点「Auto select」选中了节点（日志 `switched none → HY2+Reality-hy2-443, delta 0 pts (0 → 0)`，因为此前没有在用的节点，属预期）；随后再点 4 次，日志全部是（当时两个节点都还没有读数，所以分数是 0；有读数之后的 `kept` 见 ② 的第二段）：

```text
auto-select: probes=TCP node.example.com:443; cooldown=30min, margin=3pts(≈10ms);
  kept 🇺🇸 HY2+Reality-hy2-443: best challenger … scores 0, delta 0 < 3
```

- 首页没有任何提示条（uiautomator 里只有速率文本，无 notice），节点未变。
- 日志每行都带探针地址、冷却时长、粘性阈值、胜者分数、差值与结论——这就是 P0-5 要的透明化。

**② 制造明显更优节点后再触发 → 1 次切换、随后不再切**：通过

实测（Fire TV，同一条订阅的两个节点）：

```text
# 当前节点是较慢的那个（vless-2053，10063 分），点一次「Auto select」
auto-select: probes=TCP node.example.com:443; cooldown=30min, margin=3pts(≈10ms);
  switched 🇺🇸 HY2+Reality-vless-reality-2053 → 🇺🇸 HY2+Reality-hy2-443,
  delta 36 pts (10063 → 10099)

# 再点一次，不应再切
auto-select: …; kept 🇺🇸 HY2+Reality-hy2-443: best challenger … scores 10099, delta 0 < 3
```

- 切换是真的：卡片上的当前节点跟着换了，日志里 from → to 与分数差都在。
- 只切一次：紧接着再点，结论是 `kept`，delta 0。
- 分数不是编的：10099 = 可用率 100% × 100 + 延迟满分 99（4ms 已到下限），10063 = 同一可用率、延迟约 177ms。

**过程中的一个插曲（对用户报的现象有解释力）**：第一次尝试时两个节点都是 `untested`，日志里 `scores 0, delta 0`，怎么点都不切——因为**没有任何读数时所有节点同分，粘性规则正确地不让它动**。这也说明"自动选择从不切换"的观感，很可能是"没有可用读数"，而不是切换逻辑坏了。

**V-2 结论**：两条都通过（设备 Fire TV；行为类，不涉及帧率/延迟/DNS/后台）。验证的是 P0-5 的切换与防抖逻辑本身；手机接入后无需重测 V-2，除非要复核点击手感（那属于 P1-11）。
## 2026-10-25 · P1-6 无损 Override：不适用（附证据）

**结论：Starveil 的模型里不存在"订阅覆盖规则"这一类问题，所以 Override 开关没有对象。**

| 任务书假设 | 本仓库实况 |
|---|---|
| 订阅带规则，刷新会覆盖用户规则 | 订阅只写 `profiles` / `groups`；`RuleDao` 的唯一调用方是 `RuleRepository`，订阅导入与刷新路径不引用它 |
| 规则市场应用前给 diff | 规则市场是 5 个**固定 geosite 快照**（`.srs` 二进制，按 sha256 钉住），不是规则条目列表 —— 没有可逐条 diff 的东西；它的"预览"就是包自身的描述，"回滚"就是关掉开关 |

核实命令：`grep -rn "RuleDao\|ruleDao" --include=*.kt app feature core` 只命中 `RuleRepository` 与 DI/数据库定义；`core/subscription` 与 `RuleRepository` 无交叉。

**如果以后规则市场改成"可编辑的规则条目"**，本项才成立：那时需要 `rules` 表加 `source` 列（user / pack），刷新只换 pack 行，关 Override 删 pack 行恢复 user 行。现在加这一层是为不存在的场景写代码。

## 2026-10-25 · P1-8 规则顺序可视化（已验收）

**改了什么**：

- 每条用户规则前加序号列 `#1`、`#2`…（`tech` 字阶、`ink3`、固定 34dp 宽，三位数不挤）。
- 列表末尾加**兜底行**：`#N 其余流量 → 代理/直连`。它不是一条可编辑的规则，是预设的属性（`GLOBAL_DIRECT` → 直连，其余 → 代理），所以没有开关也没有编辑入口 —— 但列表必须有终点，否则读起来像"没匹配的流量消失了"。
- 新增 `RulesUiState.fallbackAction()` 与文案 `rules_fallback_title`（中英各一条，`checkTranslations` 515 条全译）。

**验收（Fire TV，2026-10-25）**

| 项 | 结果 |
|---|---|
| 序号列 | 规则页显示 `#1` / `#2` / `#3`，等宽、`ink3`，与规则名同一行 |
| 兜底行 | `#3 Everything else → Proxy`，无开关、无 Edit/Delete —— 它是预设属性，不是可编辑规则 |
| 上下移动真的改顺序 | 把 `#1 Domain suffix: ads.example.com (Proxy)` 下移一格后，uiautomator 读到 `#1 video.example.com (Direct)` / `#2 ads.example.com (Proxy)` |
| 顺序即命中结果 | `ConfigBuilderTest` 新增用例：同一条域名两条规则（proxy / direct），正序生成的配置第一条命中 `proxy`，倒序命中 `direct`（`firstActionAfter` 断言，`core:kernel` 119 个测试全绿） |

截图：`docs/recordings/glass-2026-10-25/rules-order-and-fallback.png`。

**没做**：真机流量下的 `Recent flow` 对照（需要连上隧道并让流量命中规则）。命中的第一手证据目前是"配置里规则的先后"，sing-box 的 first-match 语义是上游文档保证的，不是本仓库代码。


## 2026-10-25 · P1-9 订阅用量与到期横幅（已验收）

**做了什么**

- `core:model` 新增 `SubscriptionUserInfo` 与 `parseSubscriptionUserInfo()`：解析 `subscription-userinfo`（`upload/download/total/expire`，字节与 Unix 秒），**缺字段保持 null 而不是 0**——"0 / 0 已用"比"没显示额度"更像假话。
- `SubscriptionFetcher` 增加 `fetchWithMeta()`（默认实现回落 `fetch()`，老 fake 不用改）；`HttpUrlConnectionFetcher` 在 `disconnect()` 前读出该响应头。
- `SubscriptionUpdater` 解析后挂到 `Success.userInfo`；`ProfilesViewModel` 在刷新成功时写入偏好；`PreferencesRepository` 新增 `subscriptionUsage`（按 group id，手写编码，不引 JSON 依赖）。
- 节点页在列表上方显示一行：`已用 3.0 GB / 100 GB · 剩余 N 天`；**没有该头时整行不出现**（不是显示"暂无数据"）。

**实测（单元测试）**

| 用例 | 断言 |
|---|---|
| 完整头 | 四个数都对，`usedBytes` = upload + download |
| 只给 total | upload/used 保持 null，total 有值 |
| 垃圾头 / 负数 / 空 | 解析为 null，不抛异常 |
| 分隔符与空格 | ` upload = 10 ;download=20, total=30 ` 也能解 |
| 剩余天数 | 向上取整；过期或没有 expire 时为 null |
| 更新器带头发刷新 | `Success.userInfo.usedBytes = 30`、`totalBytes = 100` |
| 更新器无头 | `Success.userInfo == null`（不是 0） |

`core:model` 与 `core:subscription` 测试全绿；`checkTranslations` 519 条全译。

**验收（Fire TV，2026-10-25，经 cloudflared 临时隧道）**

测试订阅由本机起的小服务提供，两条路径内容相同、只差响应头，经 `trycloudflare` 临时隧道暴露成 https 供电视导入（Android 拒绝明文 http，这是唯一能验的路子）。

| 订阅 | 响应头 | 界面 |
|---|---|---|
| `/with` | `upload=1GiB; download=2GiB; total=100GiB; expire=2030-01-01` | 芯片下方一行 **`Used 3.0 GB of 100 GB · 1164 days left`** |
| `/without` | 无 | **整行不出现**，列表直接接在芯片下面 |

- 3.0 GB = 1 GiB 上传 + 2 GiB 下载，100 GB 总量、1164 天都是头里给的数，不是算错。
- 降级不是显示"暂无数据"，是**什么都不显示**——用户对没有的额度无能为力，一行道歉只会占地方。

**验收过程中修的一个设计错误**：横幅原先在"没筛分组"时会退回到任意一个分组的用量，于是筛到没有额度的那个订阅、屏幕上却显示别人的数字。改成**按当前分组取值**：筛了分组只看该分组，没筛才看当前节点所属分组。

截图：`docs/recordings/glass-2026-10-25/usage-with-header.png`、`usage-without-header.png`。

## 2026-10-25 · P1-7 新手默认规则组（已验收）

**做了什么**

- **默认分流改了**：以前没有任何存储值时是 `GLOBAL_PROXY`（全部走代理），现在是 **`BYPASS_MAINLAND`**（国内直连、其余走代理）；**去广告默认开**。这正好是任务书要的那一组。
- **新手模式是推导出来的，不是新开关**：`beginnerMode` = "从未写过 `ROUTE_PRESET` 且没有旧版 `BYPASS_MAINLAND` 键"。用户一旦改预设，键被写入，新手模式自动结束——不会出现"模式说 A、实际跑 B"的双份真相。
- **规则页顶部一张卡**（`Beginner setup` / 新手模式）：三行说明 + `Customize`。点它调用 `exitBeginnerMode()`，把当前生效值写实（`BYPASS_MAINLAND` + `BLOCK_ADS=true`），**路由不变**，只是不再叫默认。

**验收（Fire TV，`pm clear` 后全新状态）**

| 项 | 结果 |
|---|---|
| 默认组已生效 | 规则页预设卡片选中 `Bypass mainland China`，去广告开 |
| 新手卡片可见 | `Beginner setup` + `· Mainland sites go direct` / `· Everything else goes through the tunnel` / `· Ads and trackers are blocked` + `Customize` |
| 一键切到完整编辑 | 点 `Customize` 后卡片消失，预设仍是 `Bypass mainland China`、`Block ads` 仍在 |
| 默认组里没有"把国内送进代理"的规则 | 预设即 `BYPASS_MAINLAND`，国内规则由预设自己发（`DOMESTIC_NAMES` / `DOMESTIC_ADDRESSES` → direct） |

截图：`docs/recordings/glass-2026-10-25/beginner-setup.png`。

**一个观察**：卡片首次进入时被列表的初始滚动带到了视口上方，需要往回滑才看得见。不是渲染问题（滑回来完整可见），是列表滚动位置没有在首次进入时归零。没改——这属于列表行为，不在本项范围，记在这里备查。

## 2026-10-25 · P1-10 重连与出口反馈（页脚 + 唤醒已验收）

**做了什么**

- **页脚出口行**：连接时在健康卡下方常驻一行 `Exit <地址> · <RTT> ms`；断开即消失。RTT 是完整的一次 HTTPS 往返（DNS + TLS 都算），所以是几百毫秒而不是节点延迟的那个个位数。
- **唤醒自愈**（`WakeRecovery`）：监听 `ACTION_SCREEN_ON` / `ACTION_USER_PRESENT`，1.5 s 后探一次出口，失败再等 3 s 复探；**两次都失败**、且 kernel 正在转发、且 `auto-reconnect` 开着，才 `connection.reapply()`。一次失败不算——刚唤醒时射频还没起来，一次失败是常态。
- **每次唤醒都写一行日志**（成功也写），否则"检查到底跑没跑"无法从日志回答。
- **未连接或 Kill Switch 阻断时不探测**：阻断模式下探测必然失败，去重建就是跟这个设置对着干。

**验收（Fire TV，debug 签名包）**

| 项 | 结果 |
|---|---|
| 页脚出现且数字在动 | `Exit <地址> · 384 ms` → 30 s 后 `· 169 ms` → `· 179 ms` |
| 断开后不留旧值 | 广播断开 → 页面 `DISCONNECTED`，`Exit` 行数 0 |
| 唤醒检查真的跑 | 灭屏 20 s → 唤醒 12 s 后日志：`wake: exit <地址> in 176 ms, tunnel kept` |
| 未连接时唤醒不探测 | 同样灭屏/唤醒，日志里没有 `wake:` 行 |
| 判定逻辑 | `WakeRecoveryPolicyTest`：两次都失败才重建 / 有一次成功不重建 / kernel 未运行不重建 / 关掉自动重连不重建 |

截图：`docs/recordings/glass-2026-10-25/exit-footer.png`。**地址做了打码**——那台节点就是维护者自己的机器，公开仓库里不出现它的地址；原文里的数字与格式未改。

**出口地址为什么可信**：这台电视所在的局域网本身也从同一台机器出去，所以"连上/没连上"看到的 IP 相同，光看数字分不出有没有走隧道。真正的证据是内核自己的路由与日志：`ip route get 1.1.1.1 uid <app>` → `dev tun0`，且 sing-box 日志里能看到 `inbound/tun[tun-in]` + `router: found package name: com.justnpcx.starveil` + `outbound/hysteria2[proxy]`。

**网络切换那一半是既有实现，本轮没新增**：`SceneController` 监听默认网络，子网变化时 `rebuildForSubnets()` → `connection.reapply()`，日志是 `network changed: rebuilding for N local subnet(s)`；进程被系统杀掉后由 `ConnectionReconciler` + `DropRecovery` 负责恢复。

**没做的一项（说清楚原因）**：没有在电视上做物理网络切换验证。Fire TV 只有 Wi-Fi，关掉 Wi-Fi 会同时断掉 adb，一旦恢复失败这台设备就失联——这个风险不值得为一条已经存在于代码里的路径去冒。

**一个没解释清楚的现象**：某次安装后界面显示 `CONNECTED`，但 `ip route get 1.1.1.1 uid <app>` 走的是 wlan0、`tun0` 不存在，日志页也是空的。强制停止再启动、重新连接后恢复正常。没查到根因，先记在这里。

## 2026-10-25 · V-3 断开阻断：抓到一个假"阻断中"（0.8.4 实测，main 已修）

**怎么测的**：电视（v0.8.4 正式包）把 Kill Switch 设为 `Block on disconnect` → 连接 → 广播断开 → 在设备上跑一段 `setsid` 脚本做 A/B/C 三段探测。脚本脱离 adb 运行，因为阻断会让 adb 一起断，从外面敲命令会中途丢掉。

| 阶段 | 路由 | 结果 |
|---|---|---|
| A 已连接 | `1.1.1.1 dev tun0` | 隧道在，符合预期 |
| B 断开（block） | `1.1.1.1 dev tun0` | **tun 仍在**——blackhole 保留 tun、不转发，与设计一致 |
| C 强杀进程后 | `1.1.1.1 via 192.168.50.1 dev wlan0` | tun 没了，网络恢复 |

**发现的问题**：C 之后重新打开应用，界面显示 **`BLOCKING`**，但同一时刻：

- `/sys/class/net` 里没有 `tun*`；
- `dumpsys activity services com.justnpcx.starveil` 里没有 VpnService；
- `ping -c 3 1.1.1.1` 拿到 **283–479 ms 的真实往返**（隧道里的 ICMP 由内核本地应答，只有 0.4 ms，两者一眼能分开）。

也就是说：**界面宣称在阻断，流量其实从 wlan0 直接出去了。** 这正是 Kill Switch 存在的理由，也是任务书 V-3 要抓的东西。

**根因**：`RealKernelBridge.applyLocked` 只在启动成功时写 `_blocking.value = blocking`，失败路径只做了 `stopVpn()` + 置 `Error`，**没有把 `_blocking` 清回 false**。于是"上一次是阻断"的标志能活过一条失败的重建，界面据此画出 `BLOCKING`。

**修法**（main，未随 0.8.4 发布）：失败路径显式 `_blocking.value = false`，并写明原因——这个标志描述的是"正在生效的阻断"，一次没起来的重建不构成阻断。

**验证**：`core:kernel` 单测全绿。**电视上没有复测这个修复**：0.8.4 是已发布包，修复只在 main；复现还得再强杀一次进程。

**关于"抓包"这个词**：这台电视和本机在同一个交换式局域网里，宿主机被动抓包看不到电视发往路由器的流量（不是集线器）。要抓只能在路由器上，或者做 ARP 欺骗——后者会打断家里网络，没做。所以本项的证据是"设备侧路由 + VPN 服务是否存在 + 真实往返时间"，不是报文捕获，这一条如实记下。

## 2026-10-25 · P0-1 手机帧率 + P2-14 首轮基线（vivo V2238A，Android 16）

**设备**：vivo V2238A（iQOO Neo7 SE），Android 16 / SDK 36，1080×2400，充电中、屏幕常亮、动画缩放全部 0。

**夹具**：benchmark 变体 + `SeedReceiver` 播种 1000 节点（`seed.done` 已写出），节点页显示 `全部` 分组。

**workload**：`dumpsys gfxinfo <pkg> reset` → 10 次 `input swipe 540 1900 540 700 160`（间隔 0.7 s）→ 读 `dumpsys gfxinfo`。

| 指标 | 数值 |
|---|---|
| 渲染帧数 | 1136 |
| Janky frames（Android 12+ 口径） | **3（0.26%）** |
| Janky frames（legacy 口径） | 342（30.11%） |
| p50 / p90 / p95 / p99 帧间隔 | 10 / 14 / 14 / 17 ms |
| Missed Vsync | 0 |

**冷启动**（`am start -W`，force-stop 后三次）：275 / 268 / 231 ms，中位 **268 ms**。
**内存**：`dumpsys meminfo` TOTAL PSS **81 MB**（RSS 229 MB）。

**Macrobenchmark 没跑成**：`NodesScrollBenchmark` 在本机连续两次失败——第一次是 UiAutomation 冲突（测量期间我跑了 uiautomator），第二次进程挂住十几分钟、输出停在 `test=scrollThousandNodes`、无任何帧数据，最后被我终止（`shortMsg=Process crashed.` 是终止的结果，不是原因）。所以上面的数字来自 gfxinfo + 同一套 1000 节点夹具，**不是 Macrobenchmark 的 FrameTimingMetric**，方法差异如实记下。

**关于两个 Janky 口径**：`0.26%` 是 Android 12 起按"错过 vsync 且没有及时补帧"的口径；`30.11%` 是旧口径（超过 16 ms 就算）。同一批数据两个数差两个数量级，只报一个会误导，两个都留。

## 2026-10-25 · P0-4 Doze：灭屏 10 分钟隧道仍在（vivo V2238A）

**做法**：手机连上真实节点（订阅导入，hysteria2）→ `input keyevent 223` 灭屏 → 等 10 分钟 → 不碰屏幕，直接查状态。

| 检查项 | 结果 |
|---|---|
| 电源状态 | `mWakefulness=Asleep`（确实进了休眠） |
| tun 接口 | `/sys/class/net/tun0` 仍存在 |
| 路由 | `ip route get 1.1.1.1 uid 2000` → **dev tun0** |
| 进程 | 仍有 1 个 `com.justnpcx.starveil` 进程 |
| 连通性 | `ping -c 3 1.1.1.1` 3/3 收到，0% 丢包 |
| 内存 | PSS 182 MB（连接态，含内核） |

**边界**：只测了 10 分钟。Android 真正的 Doze 维护窗口是 30 分钟级，`Doze` 的深度限制（网络挂起、Job 延迟）在 10 分钟里还没完全展开，所以这条只能说明"短时灭屏不掉"，**不能说明"过了一个完整 Doze 窗口还在"**。要下更强的结论得再等 30 分钟以上。

**顺带**：这台手机装了锁屏密码，`wm dismiss-keyguard` 无效，灭屏后再想操作界面必须先解锁——所以后面的 DNS 指标读取、曲线首帧、逐页点按延迟都卡在"屏幕锁着"这一步。

## 2026-10-25 · P0-2 / P0-3 / P1-11：三个数字，三个边界（vivo V2238A）

手机已解锁、锁屏已关（`screen_off_timeout=2147483647`、`svc power stayon true`），隧道连着真实节点。

### P0-3 DNS 采样：机制对，样本量差一个数量级

`设置 → 开发者选项 → DNS 采样` 读数：**`20 次 · p50 370 ms · p95 1927 ms · 回退 0% · 缓存 5%`**。

- 机制是通的：环形 512、默认开启、缓存命中率与回退率都在算。
- **样本只有 20 次，离验收要的 200 次很远**。解析只由订阅刷新和节点健康探测产生，而 1002 个夹具节点的 host 大量重复，一次探测不会产生成百上千次解析。要凑到 200 次得跑几小时真实使用——或者把探测改成逐节点解析，那是改产品行为，没做。
- 已有的 20 次里 **p50 370 ms、p95 1927 ms**，离 §7.1 的 `p50 < 30 ms` 差一个数量级。这些是走美国隧道的冷解析（缓存命中只有 5%），不是稳态；样本太少不能当结论，但值得记一笔：**首解析慢是真实存在的**。

### P0-2 / V-1 曲线首帧：约 7 秒，但分辨率只有 2.3 秒

做法：断开 → 连接 → 每轮 `uiautomator dump` 读首页速率文本，记第一次出现非零值的时刻。

| 采样时刻 | 下行 / 上行 |
|---|---|
| t = 2.4 s | 0 / 0 |
| t = 4.7 s | 0 / 0 |
| **t = 7.1 s** | **127 / 557** |

其中 2–3 秒是隧道建立本身。**轮询分辨率 2.3 秒**（每次 dump 就是这个量级），所以这个数只能算上界估计，不是"首帧时间"的精确值。

### P1-11 逐页点按延迟：没有可交付的数字

每页 `gfxinfo reset` → 点该 tab → 2 秒后读：

| 页 | 帧数 | Janky |
|---|---|---|
| 首页 | 4 | 2（50%） |
| 节点 | 2 | 1（50%） |
| 规则 | 10 | 1（10%） |
| 日志 | 8 | 1（12.5%） |
| 设置 | 8 | 1（12.5%） |

**帧数只有 2–10 帧，百分比没有统计意义**——切页一次就是一次转场，2 帧里有 1 帧"慢"和 200 帧里有 100 帧"慢"完全是两回事。端到端的"点按到可用"延迟用 adb 测不出来（要 240 fps 相机，或 Macrobenchmark，而后者在这台机器上跑不起来）。**这一项本轮不宣称有数字**，只留下切页时的帧健康度。

## 2026-10-25 · P1-11 逐页点按延迟（已测出）与 P0-2 的 60fps 尝试（失败，如实记录）

### P1-11：改用 framestats 的 `HandleInputStart → FrameCompleted`

方法：每页 `dumpsys gfxinfo reset` → `input tap` → 等 1.2–2.5 s → 读 `framestats`，取**第一个 `InputEventId != 0` 的帧**，算 `FrameCompleted − HandleInputStart`。三次取中位。这两个时间戳都来自同一块单调时钟，不需要外部对齐。

| 页 | 三次 | 中位 |
|---|---|---|
| 首页 | 14.8 / 14.9 / 13.7 | **14.8 ms** |
| 节点 | 10.9 / 9.1 / 13.2 | **10.9 ms** |
| 规则 | 13.3 / 13.4 / 10.9 | **13.3 ms** |
| 日志 | 10.4 / 13.8 / 12.3 | **12.3 ms** |
| 设置 | 9.1 / 9.1 / 10.1 | **9.1 ms** |

全部 ≤ 15 ms，也就是**一帧之内**（60 Hz 下 16.7 ms）。这条测的是"输入被应用处理到这一帧画完"，不含系统把事件送到应用的那几毫秒，也不含合成器上屏——比 Macrobenchmark 的 `FrameTimingMetric` 窄一点，但比之前"2–10 帧算百分比"那个垃圾数据有意义得多。

规则页第一次测不出数（`InputEventId` 一直是 0），原因是只等了 1.2 s，规则页渲染比这慢；把等待放到 2.5 s 后三次都有数。

### P0-2：60 fps 录像这条路在这台机器上走不通

试了三次，两个独立的问题：

1. **`screenrecord` 是可变帧率**：15 秒只出 9–13 帧，静止画面不产生帧。想看"首帧"必须靠 PTS，但分辨率取决于画面什么时候变，不是 60 fps 的等间隔采样。
2. **录像期间点连接没生效**：三次录像里，点完之后 `tun0` 始终不存在、画面也没变化（帧间差 < 0.15，只有触点圆点那点动静）。不录像时同一个坐标点了就能连上，录像时就不行——原因没查清，可能与录屏占用了合成器有关。

所以 **P0-2 仍然只有之前那个轮询数：约 7.1 s（分辨率 2.3 s）**，这一轮没有把它变精确。要真测首帧，得在设备上开 `screenrecord --bugreport` 或用 Macrobenchmark 的 `FrameTimingMetric`，后者在这台手机上跑不起来（见上一节）。

## 2026-10-25 · P0-2 复测：首帧 ≈ 2.0 s（`screenrecord --bugreport`，推翻之前的 7.1 s）

**做法**：`screenrecord --bugreport --time-limit 14` 录制 → 第 2 秒点连接 → 抽帧后按区域算帧间差：

- **环区**（`(200,240)-(880,860)`）判断"点按被接收、状态变了"；
- **速率区**（`(60,1080)-(1020,1420)`）判断"第一个采样画出来了"。

**结果**（帧时间戳来自视频 PTS）：

| 时刻 | 环区差 | 速率区差 | 含义 |
|---|---|---|---|
| 1.99 s | **3.62** | 1.60 | 点按落下的那一帧 |
| 2.15 s | 1.12 | 0.01 | 状态文字切换 |
| 2.24–3.61 s | ≈0 | ≈0 | 隧道在建立，界面没动 |
| **4.17 s** | 0.04 | **2.47** | **速率区第一次变化——首个采样上屏** |
| 5.18 / 6.17 / 7.18 / 8.18 … | ≈0 | 1.4–2.9 | 之后每秒变一次（统计节奏就是 1 s） |

所以 **点按 → 首个采样 ≈ 2.0–2.2 s**（点按帧到首帧之间最多 0.3 s 的帧粒度误差）。

**这推翻了之前那个 7.1 s**。原因不是测量玄学，是我上一轮的方法太粗：`uiautomator dump` 一次要 2.3 s，我只在 2.4 / 4.7 / 7.1 s 采了三次，看到 4.7 s 还是 0 就以为首帧在 7 s 附近。**采样间隔比被测现象还长，测出来的只能是上界，而且是差了三倍的上界。** 那条记录保留在下面不删，作为方法教训。

**这一轮的运气成分**：`--bugreport` 并没有把帧率变成恒定 60 fps（14 秒只有 30 帧，仍是"画面变了才出帧"），只是这次点按生效、隧道建立得快（约 1.5 s），恰好落在有帧的时刻上。换一次网络慢的场景，帧粒度误差会变大。

## 2026-10-25 · V-3 复测（电视，main + 修复）：假"阻断中"不再出现，但暴露另一个缺口

**先回答"tun 为什么建不起来"**：现在建得起来。同一台电视、同一个包，点连接后日志是

```text
StarveilKernel: TTFP 1910 ms via hysteria2
StarveilKernel: writeStatus: up=23139/23572 down=6676/6676
```

`/sys/class/net/tun0` 在，计数器在动。之前那些"显示已连接但没有 tun"是**状态谎言**，不是隧道建不起来——现在这个谎言被修掉了。

**复测场景（Kill Switch = 断开时阻断）**：

| 步骤 | tun | UI |
|---|---|---|
| 连接后 | 有 | `CONNECTED` |
| 广播断开（进入阻断） | **有**（blackhole 保留 tun） | — |
| 强杀进程 | 无 | — |
| 重新打开 | 无 | **`DISCONNECTED`** |

**修复有效**：修复前这一步会显示 `BLOCKING`（宣称阻断，实际 `ping 1.1.1.1` 拿到 271 ms 真实往返）；现在是 `DISCONNECTED`，与实际一致。

**但顺带暴露一个缺口**：重新打开后，`DISCONNECTED` 且流量是通的（`ip route get` → wlan0，ping 271 ms）。也就是说 **"断开时阻断"这条策略没能活过进程重启**——界面没说谎，但用户以为的"断网保护"在进程被杀后不再生效。

这不是本轮改动引入的：`ConnectionReconciler` 只在"进程死了但 VpnService 还在"时接管，进程和隧道一起被杀时它什么也不做。要不要在启动时按 kill switch 策略补一个阻断，是产品决定（开机就全阻断对用户是另一种伤害），我没有擅自改。

**证据命令**：`ls -d /sys/class/net/tun0`、`ip route get 1.1.1.1 uid 2000`、`ping -c 2 1.1.1.1`。

## 2026-10-25 · Kill Switch 在进程被杀后不再失守（已修，电视实测）

**问题**：V-3 复测时发现，`断开时阻断` 选着、进程被系统杀掉后，隧道没了、阻断也没了，流量直接出去。界面这次没说谎（显示 `DISCONNECTED`），但**用户以为的保护没了**。

**改法**：策略是持久的，内核不是。启动时补一步——

- `KillSwitchController.shouldBlockOnStart(mode, kernelRunning)`：纯函数，`kernelRunning=false` 且策略要求阻断时才动手；
- `ConnectionController.enforceKillSwitchOnStart()`：满足条件就 `kernel.block()`（起一个只阻断的内核）；
- `StarveilApp` 在"接管遗留隧道 / 自动重连"之后调用它。

已经连着的、或用户主动断开后已经在阻断的（blackhole 保留 tun，内核还在跑），都不会被重复打断。

**电视实测**（Kill Switch = 断开时阻断）：

| 步骤 | tun | 路由 | 实际连通 | UI |
|---|---|---|---|---|
| 连接 | 有 | tun0 | 通 | `CONNECTED` |
| 强杀进程 | 无 | wlan0 | 通 | — |
| **重新打开** | **有** | **tun0** | **ping 0.4 ms（内核本地应答，非真实往返）** | **`BLOCKING`** |

内核日志同时给出证据：每个连接都被拒绝——

```text
ERROR connection: open connection to 18.65.10.177:443 using outbound/block[block]: operation not permitted
ERROR connection: open connection to 223.5.5.5:443 using outbound/block[block]: operation not permitted
```

**一个观感细节**：重启后界面先闪一下 `DISCONNECTED`，约 8 秒后变 `BLOCKING`——阻断内核是异步起来的，状态跟着真实情况走，不是一开始就假装。

**单测**：`KillSwitchControllerTest` 新增两条——要求阻断的模式在无内核时补阻断、有内核时不动。

## 2026-10-25 · P0-3 DNS 采样：200 次达成，但数字不能用"快"的方式拿到

**结果**（`设置 → 开发者选项 → DNS 采样`，vivo V2238A，隧道连着真实节点）：

**`200 次 · p50 904 ms · p95 1416 ms · 回退 0% · 缓存 0%`**

**怎么凑到 200 的**：不是"快速验证"，是**这台手机自己探测夹具节点攒出来的**——1002 个节点的健康探测在连着的状态下每分钟大约解析 5–9 个 host，从 20 次涨到 200 次花了约 25 分钟。中途试过一条"几秒钟跑完"的路，失败了（见下）。

**试过的快速路径（失败）**：写了一个仪器测试，用 `DohResolver` 并发解析 240 个 `10.0.0.x.nip.io`（都有答案的独立域名），跑在手机上、走真实 DoH。第一次 0.8 秒"跑完"——因为测试 APK 没有 `INTERNET` 权限，每次解析立刻失败；补上权限后，240 次解析跑了 **15 分钟没跑完**（隧道下每次约 0.9 s，加上失败重试），最后被我终止。所以这条路**不快，也不可靠**，测试文件和清单都已删掉，没留在仓库里。

**这两个数字要怎么看**：

- `p50 904 ms` **不能当成"用户浏览时的 DNS 延迟"**。夹具节点的 host 是合成的（`*.example.com` 之类），大量是 NXDOMAIN，每次都要走完所有 DoH 端点才回；真实浏览命中真实域名会快得多。
- 能当结论的是机制本身：**并发限制生效、缓存计数在算（这批全是新 host 所以 0%）、回退 0%**——没有出现"DoH 全挂、偷偷走明文"的情况。
- 离 §7.1 的 `p50 < 30 ms` 差得远，但这批样本**不能用来判定那条指标**，工作负载不对。要判它得用真实浏览流量采样。

## 2026-10-25 · homeproxy A/B：做完了，但结论是"不可比"

**做法**：软路由上关掉 homeproxy（家宽出口从 `69.63.200.132` 变成本地 ISP）→ 清空 DNS 采样 → 跑 9 分钟 → 读；再开回来 → 清空 → 同样 9 分钟 → 读。

| 组 | 窗口 | 样本 | p50 | p95 | 回退 | 缓存 |
|---|---|---|---|---|---|---|
| homeproxy 开（稳态，较早一次） | 25 min | 200 | 904 ms | 1416 ms | 0% | 0% |
| homeproxy 关 | 9 min | **285** | **2264 ms** | 3859 ms | 0% | 0% |
| homeproxy 开（对照，本次） | 9 min | **18** | 923 ms | 1083 ms | 0% | 0% |

**为什么不能下结论**：两组的**采样速率差 16 倍**（285 vs 18）。采样来自节点健康探测，而"关掉/打开代理"本身就是一次网络变化——`SceneController` 会因此重建隧道并触发一轮探测，所以"关"那一组测到的是**churn 状态**，不是稳态。p50 从 904 涨到 2264，无法区分是代理关掉造成的，还是重建风暴自己造成的。

**能说的**：代理开着时，两次独立测量的 p50 都是 ~900 ms（200 样本 904、18 样本 923），这个数稳定；"关掉"那一组的 2264 ms 只能作为"churn 期间会变慢"的记录，不能当作代理的代价。

**要做成可比实验**，需要在切换后先静置 10 分钟让重建停止，再清空样本测 9 分钟——本轮没做，因为每次切换都会动到全家网络。

**隧道本身在两组里都是通的**（关掉时代理下累计 4.3 MB 下行、`ping 223.5.5.5` 正常），所以慢不是断网。

## 2026-10-25 · 03 主题 / 圆环 / FAB / U1–U7 无回归复核（Fire TV，main）

**设备**：Fire TV Stick 4K Max（1920×1080），装的是 main 的 debug 签名包（含 P0-1/2/4/5、P1-6~11、V-3、kill switch 修复）。订阅 2 个节点。

| 项 | 结果 | 依据 |
|---|---|---|
| U1 自动选择徽标 | 本轮未重跑（要节点可测），代码未动 | — |
| U2 健康曲线缓存先行 | 首页健康卡直接显示 `100% up` 与曲线，**无 collecting** | 截图 |
| U3 节点行红块 / 延迟列 | 右侧无红块；全图红色像素只出现在 🇺🇸 旗（x 114–150）；延迟 `5 ms` / `6 ms` 完整右对齐 | 像素扫描 |
| U4 规则空状态卡片化 | `No rules of your own` 在卡内，不在裸底上 | 截图 |
| U5 设置分组间距 | 视觉保持（未逐像素量，只能说不难看、没塌） | 截图 |
| U6 副标题单行 + VPN 链接独立成行 | `Curated packs, pinned and hash-checked` 单行；`Open system VPN settings` 独占一行 | 文本 + 截图 |
| U7 圆环 | **03 主题下连接态是实心 accent `#0071E3`**，不是「曜」的渐变——代码注释写明 `One blue line, the whole way round. The ring is a state indicator, not a decoration.` 12 个角度采样全是 `(0,113,227)`；环中心 (959,285) 与 0.8.1 记录的 (959.5,285.5) 一致 | 12 点采样 |
| FAB 净空 | 行文字右边界 **x=1713**，FAB 左边界 **x=1776** → **63px 净空**（文档记 56px，比它更宽） | 像素测量 |

**新元素的落位**（本轮新增，逐个确认没有把老版式挤坏）：

- P1-7 新手卡：预设已选 → 正确地不出现；
- P1-8 序号 + 兜底行：用户规则为 0 时是 `No rules of your own`，`Rules in force` 卡里是系统规则（`Default rule → 30 Proxy`、`rule_set=geoip-cn → 3 Direct`）；
- P1-9 用量横幅：这条订阅不发 `subscription-userinfo` → 正确地整行不出现；
- P1-10 页脚：健康卡下方，本轮已单独验收。

**复核中发现一处过时文案（已修）**：设置页 kill switch 的说明还写着"上面的模式由应用自己执行，进程结束就失效"。kill switch 修好之后这句不再准确——现在是**进程结束时暂停、下次启动恢复**。中英两条都改，`checkTranslations` 525 条全译。

**U7 的判据变更说明**：0.8.0「曜」验收里 U7 是"连接态渐变环（左青右紫）"，03 毛玻璃换肤时**故意**改成实心 accent（注释给了理由）。所以这次复核的判据是"实心 accent + 中心位置不变"，不是"渐变"。把旧判据当基准会得出"回归"的错误结论。

## 2026-10-26 · F-1 输入框未激活边框（代码完成，真机截图待手机解锁）

**基线**：开工时 main HEAD = `d808993e881f683daf80e012887c8058133194b7`（0.8.5 发布后的文档提交）。

**根因（复核后与任务书一致）**：`Theme.kt` 把 `outline` 映射成 `glassBorder`（白 75%），全仓没有任何 `OutlinedTextFieldDefaults` 覆盖，所以未激活边框是白 75%，画在对话框实色面 `#F6F8FA` 上等于没有；激活态走 accent 蓝，所以只有点进去才看得出是输入框。

**修法**：新增共用组件 `core/ui/.../components/StarveilTextField.kt`（`FieldSurface.Page` / `FieldSurface.Dialog`），照 `StarveilChip` 的对话框款取色：

| 状态 | 颜色 |
|---|---|
| 未激活边框（对话框） | `ink3` |
| 激活边框 | `accent` |
| 未激活标签 | `ink2` |
| 激活标签 | `accentText` |
| 光标 | `accent` |
| 出错态 | 保持 Material 默认（主题已映射到 `errText`），未改动 |

**不动全局** `glassBorder` / `hairline` / `outline`：玻璃卡片的边还要靠白 75%。

**覆盖清单（对话框 / 实色面，全部改完）**：

| 位置 | 用途 |
|---|---|
| `RulesScreen` 新建/编辑规则 | 「值」输入框（原发点） |
| `RulesScreen` 流向弹层 | 搜索框（`Surface(fillMaxSize())` 在 Dialog 内，同属实色面） |
| `ProfilesScreen` 导入节点 | 多行输入（`minLines = 3`，原发点） |
| `ProfilesScreen` 新建/改名分组 | 分组名 |
| `ScenesScreen` ×2 | 场景名称、SSID 列表 |
| `SettingsScreen` 备份口令 | 密码输入（保留 `PasswordVisualTransformation`） |
| `OnboardingScreen` 添加订阅 | 引导页订阅地址 |

**页面点位（待截图审计，未改）**：`PerAppScreen` 搜索框、`ProfileEditorScreen` 的 `Field`。两者都在页面底色上，`glassBorder` 同样接近隐形，但任务书要求先截图再决定，**没有盲改**。

**阻塞**：手机（vivo V2238A）在开工时已锁屏且需要密码，`wm dismiss-keyguard` 无效，无法进入应用截图。任务书要求的「修前未激活 / 修后未激活 / 修后激活」三张真机截图**一张都没取到**，本项按任务书的停止规则只停该项的验收，代码与覆盖清单已核实。

## 2026-10-26 · F-2「美国」分组点开为空（代码 + 单测完成，真机截图待手机解锁）

**根因（复核后与任务书一致）**：列表过滤只有 `list.filter { it.groupId == filter }`；而 `groupByRegion()` 建地区组时**不移动节点**，成员是按名字/国家在读取时派生的，地区组本身没有任何节点。所以按地区组的 id 过滤必然是空集，节点仍在 Subscription 组里。

**选了方案 A**（地区组按地区过滤，不按 groupId）：

- 地区组是"派生的视图"，不是"容器"——把它从 chip 里去掉（方案 B）会让用户少一个入口，而按地区过滤正好符合这个组存在的意义；
- 方案 A 不需要改任何数据：刷新订阅后地区组照旧自动重算，不存在"成员要同步"的问题。

```kotlin
val filterGroup = filter?.let { id -> groupList.firstOrNull { it.id == id } }
val inGroup = when {
    filter == null -> list
    filterGroup?.region != null -> list.filter { NodeRegions.resolve(it.name, it.country) == filterGroup.region }
    else -> list.filter { it.groupId == filter }
}
```

**单测**（`feature:profiles`，全绿）：

| 用例 | 断言 |
|---|---|
| 点「美国」组 | 只出现 `US 01`，不出现 `Hong Kong 02`；`hasNoMatches = false` |
| 地区组里确实没有该地区节点 | 列表为空且 `hasNoMatches = true`（空状态文案仍成立） |

**没动的**：地区分类词表（`NodeRegion`）、地区组删组不删节点的规则、地区组连接走 URL_TEST 的行为——本项只改列表过滤一处。

**阻塞**：同 F-1，手机锁屏，无法取「点美国组前后」的真机截图。

## 2026-10-26 · F-3 新建规则的匹配行被右缘截断（代码完成，截图待手机解锁）

**现状**：`MatcherKind` 四个选项（域名 / 域名后缀 / 域名关键词 / IP_CIDR）在 `horizontalScroll` 的一行里，窄屏上第 4 个被裁掉，且没有任何"还能往右滑"的提示。

**修法**：行外再包一层 `Box`，用 `drawWithContent` 在右缘画 32dp 的渐隐（透明 → `surfaceSolid`），**只在 `canScrollForward` 为真时画**——宽屏上不会出现一个没有意义的提示。行尾加 `StarveilSpacing.sm` 的留白，最后一个芯片不贴边。

- 芯片本身的颜色、选中逻辑、顺序一行未动；
- 渐隐色取 `surfaceSolid`，因为这一行在对话框里（与 F-1 同一个实色面）；颜色在 draw 块外读好再传进去（`StarveilColors` 是 composable 查找，`drawWithContent` 不是 composable 作用域）。

**阻塞**：手机锁屏，无法取「修前截断 / 修后渐隐」的真机截图。

## 2026-10-26 · F-4 首页上行速率单位断行（代码完成，截图待手机解锁）

**现状**：`SpeedColumn` 里数字和单位是两个 `Text`，单位没有禁换行。上行 `1003 B/s` 时，`B/s` 在斜杠处断成 `B/` + `s`。

**修法**：两个 `Text` 都加 `softWrap = false` + `maxLines = 1`。数值格式、单位换算、字阶（`display` + `tnum`、单位 13sp）一律没动。

**阻塞**：手机锁屏，无法取 1003 B/s / 1023 KB/s / 1.0 MB/s 三档的真机截图。

## 2026-10-26 · V-1 两件需要解释的事（结论，未改代码）

### V-1.1 健康探针与现行隧道不是同一条路

**三个数字是三件事**：曲线是延迟 EMA（`emaMs`），红点是失败样本（`LATENCY_UNREACHABLE = -1` 也算一条样本，见 `NodeHealth.kt` 注释），在线率是加权可达比例（`availabilityPct`）。所以"曲线平 + 在线 25%"不矛盾：25% 说的是窗口里多数样本没回来，曲线只画回来的那些。

**探针测什么**：`TcpNodeProbe` 先用 DoH 严格解析节点域名（失败即失败，不回退系统解析），再对 `server:port` 做一次 **TCP connect**，手动 3 s、后台批量 5 s 超时。源码注释写明了它的边界：只测"能不能连上这个地址"，不验证代理协议。

**为什么隧道好好的、探针却超时**：内核拨节点用的是**受保护的 socket**（`ConfigBuilder` 注释：node address 由内核在底层网络上拨），绕开隧道；而探针是应用自己的普通 socket。已实测应用流量在连接时进隧道（`ip route get 1.1.1.1 uid 10101` → `dev tun0`）。所以连接期间探针这条 TCP 是**进隧道**的，与内核那条不是同一条路；隧道是已经建立的 hysteria2 会话，不会因为新建连接失败而断。

**为什么被标 DOWN 还连着**：健康数据是参考值，没有任何代码在探针失败时拆隧道；只有内核报错才触发 `DropRecovery`。

**未确认**：没有抓包证明"探针在隧道里绕一圈"，这一条是从路由表推出来的，不是报文证据。**没有改健康评分公式。**

### V-1.2 那 208 ms 不是隧道 DNS

- 这组数来自 `DnsMetrics`，唯一写入方是 `DohResolver`，而 `DohResolver` 只被 `TcpNodeProbe` 用来解析**节点域名**。它测的是 GeoIP / 节点解析的 DoH 往返，**不是**隧道里给应用用的 DNS。
- sing-box 的 `dns` 块（cn / proxy / local 三个服务器）完全在应用进程之外，应用没有任何埋点读它，所以"隧道 DNS 多少毫秒"目前**没有数据**。
- 30 个样本、缓存 0%：采样环在内存里、进程重启清零，且每个节点解析的是自己的 host，彼此不命中。样本量也不够。
- §7.1 的 `p50 < 30 ms` 原文是"**DNS 缓存** p50 < 30 ms"（`docs/11` 老清单第 454 行），这批 0% 缓存，不能拿来对照。早先一轮 224 样本是 `p50 51 ms`。

**结论**：`p50 208 ms` 是 GeoIP DoH 冷解析的实测值，不是"DNS 变慢了"，也不能用来判定 §7.1。要判定，得给隧道 DNS 加埋点（现在没有），并且至少几百个真实浏览样本。

## 2026-10-26 · F-1 补完：真机截图、页面点位审计、边框统一

**手机已解锁**（用户提供密码），以下都是 vivo V2238A 上的真机截图，目录 `docs/recordings/f1-f4-2026-10-26/`。

### 修前（0.8.5 + benchmark 变体，未含本批改动）

| 截图 | 内容 |
|---|---|
| `before_rule_dialog.png` | 新建规则的「值」输入框：**看不到边框** |
| `before_import_dialog.png` | 导入节点输入框：**看不到边框** |
| `before_group_dialog.png` | 新建分组 |
| `before_scene_dialog.png` | 新建场景（名称 + Wi-Fi 名称） |
| `before_backup_dialog.png` | 备份口令 |
| `before_us_group.png` | 点「美国」→ **该分组内没有节点** |
| `before_dash.png` | 首页速率 |

### 修后（main + 本批改动）

逐点像素测量（顶边框那一行的颜色与像素数）：

| 对话框 | 未激活 | 激活 |
|---|---|---|
| 新建规则 | 灰 `(142,143,145)`，680 px | 蓝 `(0,113,227)`，398 px |
| 导入节点 | 灰 `(142,143,145)`，392 px | 蓝 `(0,113,227)`，398 px |
| 新建分组 | 灰 `(142,143,145)`，394 px | 蓝 `(0,113,227)`，398 px |
| 新建场景 | 灰 `(142,143,145)`，394 px | 蓝 `(0,113,227)`，400 px |
| 备份口令 | 灰 `(142,143,145)`，394 px | 蓝 `(0,113,227)`，400 px |

`(142,143,145)` 就是 `ink3` 在 `surfaceSolid` 上的合成值；修前同一位置的灰边框像素数是 **0**。

### 页面点位审计（按任务书要求先截图再决定）

| 点位 | 审计结果 | 处理 |
|---|---|---|
| `PerAppScreen` 搜索框 | 修前截 `audit_perapp.png`，整屏找不到任何一条灰色边框（`gray border: None`） | **改了**：与对话框同一套颜色 |
| `ProfileEditorScreen` 的 `Field` | 与 PerApp 同属页面底色、同样是 `glassBorder`，且填充透明 | **改了**：同一套颜色（截图已补，见下） |

**结论：`FieldSurface` 这个参数被删掉了。** 审计证明"页面"和"对话框"在这套调色板下是同一个问题——`glassBorder` 是白 75%，而输入框填充透明，边框是唯一的形状提示；页面底色和对话框实色面都压不住它。所以 `StarveilTextField` 现在只有一种静默边框：`ink3`。主题里的 `outline` / `glassBorder` 一个字没动，卡片边仍靠它。

### 没做到的

- **引导页的订阅对话框**没截到：它只在首次安装出现，要 `pm clear` 才能复现，而清数据会丢掉手机上那条订阅。代码已覆盖（`OnboardingScreen` 走同一个组件），但**没有截图**。
- **`ProfileEditorScreen` 的修后截图**：已补（`after_editor_inactive.png` / `after_editor_active.png`）。节点页 → 手动创建 → VLESS，表单里「名称」字段未激活边框 `(136,138,141)` 478 px，激活后 `(0,113,227)` 482 px。
- **F-4 的真实流量截图**：手机那轮节点连不上，只截到 `0 B/s`，这张仍然缺。后来在 **Fire TV（AFTKRT，1920×1080 @320dpi）** 上补了真流量对照——用 `curl` 打满上/下行，抓 `tv_up_989kbs.png`（上行 `989 KB/s`，数值+单位占 250 px，单行 y 571–649）与 `tv_down_8p5mbs.png`（下行 `8.5 MB/s`，216 px，同一行）。这两张能证明单位跟着数值走、不再掉行，但**不能替代手机那张**：电视 540 dp 宽，手机（V2238A，1080×2400）只有约 393 dp，窄列的临界宽度没被覆盖。`softWrap = false` + `maxLines = 1` 是结构约束，不是靠这两张图推出来的。

## 2026-10-26 · 去广告真机验证（不通过）

**设备与包**：Fire TV Stick 4K Max（AFTKRT），Starveil 0.8.5（versionCode 805），隧道已连（hysteria2）。分流预设「Bypass mainland China」，`Block ads` 开关为开（`tv_rules_ads_on.png`）。

**测法**：设备上推了一个静态 `curl`，用 `--resolve` 直接给域名指定真实 IP——设备 shell 没有 `/etc/resolv.conf`，这样绕开 DNS，只测路由判定。对照组是同一时刻、同一条隧道的普通域名。

| 目标 | IP | 结果 |
|---|---|---|
| `doubleclick.net`（广告） | 74.125.137.102 | **301**，走 `outbound/hysteria2[proxy]` |
| `googlesyndication.com`（广告） | 142.251.45.4 | **302**，走 `outbound/hysteria2[proxy]` |
| `www.cloudflare.com`（对照） | 104.16.132.229 | 403，走 `outbound/hysteria2[proxy]` |

三个都是真实 HTTP 响应，没有一个是 reject。日志原文（`tv_logs_ads_proxied.png`）：

    INFO outbound/hysteria2[proxy]: outbound connection to 74.125.137.102:443
    INFO outbound/hysteria2[proxy]: outbound connection to 142.251.45.4:443

**结论：规则在，但没有生效。** 不是「开关没开」，也不是「规则集没下载」——规则集缺失时 `ConfigBuilder` 会把 `ads` 整条从 config 里删掉，而这里连 reject 都没有，说明规则存在、只是没命中。

**为什么没命中（代码层）**：`ConfigBuilder` 生成的 `route.rules` 里没有任何 `{"action": "sniff"}`，`tunInbound()` 也没有 `sniff` 字段。tun 收到的连接只有目标 IP，而 `ads` 是 `rule_set`（geosite 域名集）——**域名匹配不到 IP 连接，规则永远不会命中**。同一份配置里 `geosite-cn`（「国内域名直连」）依赖同样的机制，**按同一机制推断也会失效**——但没有单独验证：要证明它，得找一个「在 geosite-cn 里、IP 不在 geoip-cn 里」的样本，否则 `geoip-cn` 会把结论盖住。能确定生效的只有按 IP 的 `geoip-cn`。

**顺带看到、未定论**：日志里还有一条 `outbound/hysteria2[proxy]: outbound packet connection to 192.168.50.1:53`——应用自己的 DNS 查询走了代理。局域网地址本应命中 `lan` 的 `ip_cidr` 规则（`localSubnets` 来自当前网络接口），没有命中。可能和 V-1 里「探针 socket 进隧道」是同一件事，也可能 `localSubnets` 在建立隧道时是空的。**没有继续查，也没有改代码。**

**没做的**：没有加 sniff 规则去验证「加了就能拦」。这是改代码，且会影响所有域名类规则（去广告、geosite-cn、用户手写的域名规则），应该单独开一项、带回归再改。→ **同日晚些时候已修并复测，见下一条。**
## 2026-10-26 · 修复：路由里没有 sniff，所有域名规则都是死的

**改动**：`ConfigBuilder.routeRules()` 现在把 `{"action":"sniff","sniffer":["tls","http","quic"]}` 作为 `route.rules` 的**第一条**，两条返回路径（带 mainland 与不带）都加。数值格式、预设、规则顺序、`final` 一律没动。

**为什么是第一条**：`sniff` 是**非终结动作**——它读连接的头几个字节，把域名写进 metadata，然后匹配继续往下走。放第一条，后面所有 `rule_set`（ads、geosite-cn、规则包）和用户手写的 `domain` 规则才拿得到域名。只声明 `tls` / `http` / `quic` 三种带名字的协议，SSH、WireGuard 这类不会被按着头等一个永远不会出现的域名；`timeout` 用上游默认 300 ms，超时就按没嗅到处理，和加这条规则之前一样。

**为什么是这些协议**：域名只出现在 TLS ClientHello、HTTP 请求行、QUIC Initial 里。`dns` / `stun` 的嗅探结果对路由没用，列进去只是给每个 UDP 包多一次解析。

**单测**：新增 2 个（`sniffing comes first…`、`sniffing is there under global direct too`），改 4 个旧断言——它们原本把「第一条规则」写死成 ip_cidr / package_name，现在第一位是 sniff：
- `route rules are emitted in order…`、`with no user rules only the local network rule is emitted`、`per-app exceptions…`：期望串前面补上 sniff。
- `Shadowsocks config … no tls block`：原来是 `assertFalse(json.contains("\"tls\""))`，被嗅探器列表里的 `\"tls\"` 撞了个假阳性，改成检查 `\"tls\":{` 这个块。**这是测试写得太宽，不是配置回归。**

`./gradlew :core:kernel:testDebugUnitTest` 全绿。

**真机复测**：Fire TV（AFTKRT），`assembleRelease` 用同一把 release key **就地升级**（`firstInstallTime` 未变、订阅与设置都在，节点列表重启后回来了），隧道连上后同一个静态 `curl`：

| 目标 | 修前 | 修后 |
|---|---|---|
| `doubleclick.net` | 301，走 `outbound/hysteria2[proxy]` | **rc=35，66 ms**，日志里**没有 outbound 行**（连接没走到任何出口） |
| `googlesyndication.com` | 302，走 `outbound/hysteria2[proxy]` | **rc=35，95 ms**，同样没有 outbound 行 |
| `www.cloudflare.com`（对照） | 403，走 proxy | 403，走 proxy，411 ms |
| `www.baidu.com` HTTP | 走 proxy | `outbound/direct[direct]: outbound connection to 103.235.46.96:80` |
| `www.baidu.com` HTTPS | 200 | 200 |
| `www.cloudflare.com` 明文 HTTP | — | 403，170 ms（`http` 嗅探器生效） |

**两个结论**：
1. 去广告从「规则在、永不命中」变成「广告域名在建连阶段被拒」，66/95 ms 是本地拒绝的耗时，对照组要 411 ms。
2. **`geosite-cn` 也一起活了**——百度从走代理变成 `outbound/direct`。上一条里那句「按同一机制推断也会失效」现在是实测结果，不再是推断。

**没测的**：只有这两个广告域名和百度。`300 ms` 嗅探超时的边界（首包晚于 300 ms 的连接会按未嗅到处理）没构造样本。规则包（rule market）的域名规则走同一机制，逻辑上同时恢复，但没有单独验。

**发布验证（v0.8.6）**：Release workflow 由 tag 触发直接发布（run `40280974995`），三个 ABI 的 APK 都在，`draft=false`。`apksigner verify` 的证书 SHA-256 是 `f904a417…0b268c`，与 release key 一致；`aapt2` 读出 `versionCode=806 versionName=0.8.6`。电视上换装这份官方包（就地升级）后复测：对照 `www.cloudflare.com` 403，`doubleclick.net` rc=35 / 47 ms——修复在发布产物里同样生效，不只是本地那版。

