# 隐私政策 / Privacy Policy

适用版本：Starveil 0.7.0（内测）  
Applies to: Starveil 0.7.0 (beta)

---

## 中文

### 一句话

Starveil 在本机运行。它没有服务器，不上报数据，也没有账号。

### 收集什么

只有一种上报：崩溃时的一份报告。没有账号体系、没有统计分析、没有广告标识符。

本项目没有自建服务端——报告发往 Sentry，内容见下一节。

### 崩溃报告

崩溃时会把堆栈、应用版本、Android 版本和设备型号发往 Sentry。

**节点地址、UUID、分享链接、订阅地址和密钥在离开设备前会被替换成占位符。** 不附带其他内容：没有截图、没有屏幕内容，也不记录你打开过哪些页面。

可在「设置 → 诊断 → 发送崩溃报告」关闭，关闭后不发送任何内容。

### 什么留在设备上

节点配置、分组、路由规则、日志和延迟历史存放在应用私有目录下、经 SQLCipher 加密的数据库里。数据库密钥保存在 Android keystore 中，不随备份导出。

### 会连接到哪里

只有两类地址：

1. 你导入的节点，以及你添加的订阅地址；
2. 开启「绕过中国大陆」时，从 jsDelivr 或 GitHub 下载的规则文件（`geosite-cn`、`geoip-cn`）。

第 2 类请求到达的是公共 CDN，不是作者。除此之外应用不发起任何网络请求。

### 权限

| 权限 | 用途 |
|---|---|
| VPN（`BIND_VPN_SERVICE`） | 建立隧道、转发流量。这是应用的核心功能 |
| 通知 | 显示隧道运行中的常驻通知，其中包含「断开连接」操作 |
| 查询带启动入口的应用（清单里声明 `<queries>`，不是 `QUERY_ALL_PACKAGES`） | 仅用于绘制「按应用分流」列表；结果不离开设备，也不写入日志 |
| 位置（`ACCESS_FINE_LOCATION`） | **只**用于读取当前 Wi-Fi 的名称（Android 10 起系统要求），供「场景自动化」匹配。不采集位置、不记录移动轨迹、不上传。拒绝后场景仍可按移动数据或「任意 Wi-Fi」触发 |

### 备份

备份文件写入你选择的位置，使用 scrypt 派生密钥 + AES-256-GCM 加密。口令不会被保存，也无法找回——没有口令，文件对任何人都无用，包括作者。

### 删除

卸载应用会连同数据库和 keystore 条目一起删除。导出的备份文件由你自己保管或删除。

### 政策变更

崩溃报告是本政策描述的唯一外发数据，由「设置 → 诊断」控制。若上报内容发生变化，本文件与 App 内的「隐私」页面会同时更新，并在更新日志中说明。

### 联系方式

在项目的 GitHub 仓库提 issue。

---

## English

### In one sentence

Starveil runs on your device. It has no server, reports nothing, and has no account system.

### What is collected

One report exists, and only when something crashes. No accounts, no analytics, no advertising identifier.

The project runs no server of its own — the report goes to Sentry, described next.

### Crash reports

A crash sends the stack trace, app version, Android version and device model to Sentry.

**Node addresses, UUIDs, share links, subscription URLs and keys are replaced with placeholders before they leave the device.** Nothing else is attached: no screenshots, no screen contents, and no record of which screens you opened.

Settings → Diagnostics → Send crash reports turns it off, and off means nothing is sent.

### What stays on the device

Node profiles, groups, routing rules, logs and latency history live in a SQLCipher-encrypted database in the app's private storage. The database key is held in the Android keystore and is not included in backups.

### What it connects to

Two kinds of address only:

1. The nodes you imported, and the subscription URLs you added.
2. With the mainland preset on, the rule-set files (`geosite-cn`, `geoip-cn`) downloaded from jsDelivr or GitHub.

Those requests reach a public CDN, not the author. The app makes no other network requests.

### Permissions

| Permission | Purpose |
|---|---|
| VPN (`BIND_VPN_SERVICE`) | Establish the tunnel and forward traffic — the core function |
| Notifications | The ongoing tunnel notice, which carries the Disconnect action |
| Apps with a launcher entry (a `<queries>` declaration, not `QUERY_ALL_PACKAGES`) | Only to draw the per-app routing screen; never leaves the device and is never logged |
| Location (`ACCESS_FINE_LOCATION`) | **Only** to read the name of the current Wi-Fi (Android 10 requires it), so scenes can match it. No location is collected, no movement is recorded, nothing is uploaded. Without it, scenes still work for mobile data or "any Wi-Fi" |

### Backups

A backup is written wherever you choose, encrypted with a scrypt-derived key and AES-256-GCM. The passphrase is never stored and cannot be recovered — without it the file is useless to everyone, including the author.

### Deletion

Uninstalling removes the database and the keystore entries with it. Exported backup files are yours to keep or delete.

### Changes

The crash report is the only outbound data this policy describes, and it is controlled by Settings → Diagnostics. If what it contains changes, this document and the in-app Privacy screen change together, and the changelog says so.

### Contact

Open an issue on the project's GitHub repository.
