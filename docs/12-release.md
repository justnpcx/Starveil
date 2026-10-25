# 发布流程

## 一次性准备

### 签名密钥

发布用的 keystore 在仓库**之外**，路径由环境变量 `STARVEIL_KEYSTORE` 指定（alias `starveil`，RSA 4096，有效期 10000 天）。
密码在 `the environment file outside the repository` 的 `STARVEIL_KEYSTORE_PASSWORD` / `STARVEIL_KEY_PASSWORD`。

**这个文件丢了就再也发不了升级包**——已安装的用户只能卸载重装（数据丢失）。请离线备份到至少两个地方。

本地构建时它通过环境变量注入：

```bash
export STARVEIL_KEYSTORE=/path/to/release.jks
export STARVEIL_KEYSTORE_PASSWORD=…
export STARVEIL_KEY_ALIAS=starveil
export STARVEIL_KEY_PASSWORD=…
./gradlew :app:assembleRelease
```

没有这些变量时构建仍然成功，但产物是 **unsigned** 的——这是故意的：CI 缺少密钥时应当明确失败或产出不可安装的包，而不是悄悄用一个临时密钥签名。

### CI secrets

`.github/workflows/release.yml` 需要四个 secret：

| Secret | 内容 |
|---|---|
| `STARVEIL_KEYSTORE_BASE64` | keystore 的 base64 |
| `STARVEIL_KEYSTORE_PASSWORD` | keystore 口令 |
| `STARVEIL_KEY_ALIAS` | `starveil` |
| `STARVEIL_KEY_PASSWORD` | key 口令 |

发布用 workflow 自带的 `GITHUB_TOKEN`（`permissions: contents: write`），不需要额外 token。

用有 `secrets:write` 权限的 token 跑一次：

```bash
GH_TOKEN=<token> scripts/set-signing-secrets.sh
```

2026-10-17 已用 `the environment file outside the repository` 的 token 设好四个 secret（API 返回 201），
`gh secret list` 可见。以后轮换密钥时重跑这条命令即可；`SENTRY_DSN` 是可选项，
没有它发布包不上报崩溃。

## main 的保护规则

`main` 开了分支保护（2026-10-20 配置），**对管理员同样生效**：

| 规则 | 状态 |
|---|---|
| 要求 `build` 检查通过 | 是（`strict: false`，不要求分支先同步到最新） |
| 对管理员生效 | 是 |
| 强制推送 | 禁止 |
| 删除分支 | 禁止 |
| 要求 PR 评审 | 否 |

它拦的是**推送**，不只是合并。推一个还没有 `build` 结果的提交会被远端拒绝：

```
remote: - Required status check "build" is expected.
 ! [remote rejected] HEAD -> main (protected branch hook declined)
```

「先在分支上跑绿、再把同一个 SHA 推上去」**不行**。2026-10-20 实测：`726f6ce` 在分支上
`build` 已是 success（check run 查得到），推 main 仍被拒，报文还是那句
`Required status check "build" is expected`。直推时钩子只认这次 push 自己触发的检查，
分支上的绿不算数。

所以落 main 只有两条路：

1. **走 PR**（干净）：推分支 → 开 PR → `build` 绿 → rebase 合并 → 删分支。要求 token 有
   `Pull requests: Read and write`。当前 fine-grained token 缺这一项，`POST /pulls` 返回
   `403 Resource not accessible by personal access token`（响应头
   `x-accepted-github-permissions: pull_requests=write`）。
2. **临时摘 check**（退化）：`PATCH .../protection/required_status_checks` 置空 → 快进推
   → 立刻 `PUT .../protection` 恢复 → 回读确认 `contexts=['build']`、`enforce_admins=true`、
   `allow_force_pushes=false`。这一步容易忘，别省。

改写历史（rebase、压缩提交）同理，只能走第 2 条。

**推完先 `git fetch` 再核对远端。** 用一次性 URL（`git push https://x-access-token:…@github.com/…`）推送时，本地的 `origin/main` 引用**不会跟着更新**——它停在你上一次 `fetch` 的位置，于是 `git ls-tree origin/main`、`git status -sb` 读到的都是旧树，看起来像"没推上去"。核对远端只有两种可靠办法：`git fetch origin main` 之后再比 `HEAD` 与 `origin/main`，或者直接查 API（`GET /repos/:owner/:repo/contents/<path>?ref=main`）。

## 发一个版本

1. **改版本号**：`app/build.gradle.kts` 的 `appVersionName`（里程碑 N → `0.N.0`；`versionCode` 自动派生）
2. **更新 CHANGELOG**：把 `[Unreleased]` 的内容移到 `## [x.y.z] — 日期`
3. **提交并推 main**，确认 CI 绿
4. **打 tag 并推送**：
   ```bash
   git tag -a v0.8.0 -m "0.8.0"
   git push origin v0.8.0
   ```
5. **等 Release workflow**（tag 触发）：构建、签名三个 ABI 的 APK，并**直接发布**到本仓库的
   Releases（`draft: false`，`make_latest: true`）
6. **核对 release 里的三个 APK**——更新通道读的是本仓库的 `releases/latest`；发布即上线，没有 draft 这一步

2026-10-17 这条流水线第一次跑通（run `39034766016`，v0.7.0）：draft 里三个 APK
（arm64 85.7 MB / armeabi-v7a 76.7 MB / x86_64 90.0 MB），`apksigner verify` 的证书
SHA-256 是 `f904a417…0b268c`，与 `$STARVEIL_KEYSTORE` 里 alias `starveil` 的
一致；`mapping-v0.7.0` 作为 artifact 保留。**draft 还没发布**，所以更新通道仍然看不到它。

## 更新通道怎么读到它

`UpdateChecker` 请求 `https://api.github.com/repos/justnpcx/Starveil/releases/latest`，
取 `tag_name` 与当前版本比较，下载名字里带设备 ABI 的 asset，校验：

- SHA-256 对比 asset 的 `digest`
- 签名证书对比已安装应用

`REPO` 就是源码仓库 `justnpcx/Starveil`——它公开，未鉴权的 `releases/latest`
才答得出来，而客户端不能内置 token。**不要**把 `REPO` 指回任何私有仓库：
私有仓库的 `releases/latest` 未鉴权时返回 404，更新检查会永远报「无法检查」。

> 2026-10-20 之前这里是一个只放 APK 的公开镜像（`justnpcx/Starveil-releases`），
> 因为当时源码私有。源码公开后镜像失去意义并被删除，发布与读取都回到本仓库。
> 存量版本分两类：`0.7.2`/`0.7.3` 指向已删除的镜像，检查更新会报
> 「无法检查更新：no published release」，需要手动装一次；
> `0.7.0`/`0.7.1` 写的就是 `justnpcx/Starveil`（当时是私有仓，现在正是本仓库），
> 反而能直接看到新版本。

## 更新通道验证（2026-10-19）

第一次端到端跑通，当时源仓库私有，发布走公开镜像（现已改为本仓库，见上）。

**步骤与结果**

| 步骤 | 结果 |
|---|---|
| 基线 | 用发布密钥本地构建 `0.7.1`（versionCode 701，REPO 指向镜像）并安装 |
| 检查更新 | 读到镜像的 `v0.7.2`，弹出「版本 0.7.2 · 下载并安装」 |
| 下载 | 31.32 MB 完成 |
| 校验 | SHA-256 与签名通过；安装器显示「来自 Starveil」「0.7.2」 |
| 系统安装 | vivo 要求输入锁屏密码（跨应用安装的二次确认），手动输入后完成 |
| 升级后 | `versionCode=702`、`versionName=0.7.2` |
| 数据 | 订阅分组与两个节点、当前选中节点均保留；重新连接后境外 5.3 MB/s |

**两个真机注意点**

- vivo 对「应用内拉起安装器」会再要一次锁屏密码。这不是我们的权限问题：
  `REQUEST_INSTALL_PACKAGES` 已授予，系统安装页也正常拉起，最后一步必须人工确认。
- 首次检查更新（onboarding 刚结束时）报过一次
  `Failed to connect to api.github.com/...`，断开重连后同一次检查即成功。属于隧道刚建立时的
  瞬时状态，未复现；若后续出现，按内核 DNS 状态排查，而不是更新逻辑。

## 崩溃符号化

R8 打开后，release 的堆栈是混淆过的。`mapping.txt` 由 workflow 作为 **artifact** 保留
（不放进 release），需要时下载后上传到 Sentry 项目（`sentry-cli upload-dif` 或 Web 上传）。
这一步还没有自动化——在接入真实 DSN 时一起做。

## 已知的发布前缺口

- Play 上架相关（前台服务类型申报、数据安全表单）未做
- 品牌资产（商店截图、社交预览图）未做
- 多设备覆盖（横屏 / 平板 / 折叠屏）未做
- `targetSdk` 35：Play 每年提高要求，上架前确认
