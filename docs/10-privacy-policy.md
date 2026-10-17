# 隐私政策 / Privacy Policy

适用版本：Starveil 0.7.0（内测）  
Applies to: Starveil 0.7.0 (beta)

---

## 中文

### 一句话

Starveil 在本机运行。它没有服务器，不上报数据，也没有账号。

### 收集什么

什么都不收集。没有账号体系、没有统计分析、没有广告标识符，当前版本也不做崩溃上报。

本项目没有自建服务端，因此即使想收集也没有可发送的目的地。

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

### 备份

备份文件写入你选择的位置，使用 scrypt 派生密钥 + AES-256-GCM 加密。口令不会被保存，也无法找回——没有口令，文件对任何人都无用，包括作者。

### 删除

卸载应用会连同数据库和 keystore 条目一起删除。导出的备份文件由你自己保管或删除。

### 政策变更

崩溃上报会在后续版本加入，届时本文件与 App 内的「隐私」页面会同时更新，并在更新日志中说明。

### 联系方式

在项目的 GitHub 仓库提 issue。

---

## English

### In one sentence

Starveil runs on your device. It has no server, reports nothing, and has no account system.

### What is collected

Nothing. No accounts, no analytics, no advertising identifier, and no crash reporting in this version.

The project runs no server of its own, so there is nowhere for data to be sent even if it were collected.

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

### Backups

A backup is written wherever you choose, encrypted with a scrypt-derived key and AES-256-GCM. The passphrase is never stored and cannot be recovered — without it the file is useless to everyone, including the author.

### Deletion

Uninstalling removes the database and the keystore entries with it. Exported backup files are yours to keep or delete.

### Changes

Crash reporting will be added in a later version. When it is, this document and the in-app Privacy screen will change together, and the changelog will say so.

### Contact

Open an issue on the project's GitHub repository.
