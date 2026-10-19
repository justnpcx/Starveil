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
| `RELEASE_MIRROR_TOKEN` | 对 `justnpcx/Starveil-releases` 有 `contents:write` 的 token；workflow 用它把 APK 发到公开镜像 |

用有 `secrets:write` 权限的 token 跑一次：

```bash
GH_TOKEN=<token> scripts/set-signing-secrets.sh
```

2026-10-17 已用 `the environment file outside the repository` 的 token 设好四个 secret（API 返回 201），
`gh secret list` 可见。以后轮换密钥时重跑这条命令即可；`SENTRY_DSN` 是可选项，
没有它发布包不上报崩溃。

## 发一个版本

1. **改版本号**：`app/build.gradle.kts` 的 `appVersionName`（里程碑 N → `0.N.0`；`versionCode` 自动派生）
2. **更新 CHANGELOG**：把 `[Unreleased]` 的内容移到 `## [x.y.z] — 日期`
3. **提交并推 main**，确认 CI 绿
4. **打 tag 并推送**：
   ```bash
   git tag -a v0.8.0 -m "0.8.0"
   git push origin v0.8.0
   ```
5. **等 Release workflow**（tag 触发）：构建、签名三个 ABI 的 APK，并**直接发布**到公开镜像
   `justnpcx/Starveil-releases`（`draft: false`，`make_latest: true`）
6. **核对镜像里的三个 APK**——更新通道读的是镜像的 `releases/latest`；发布即上线，没有 draft 这一步

2026-10-17 这条流水线第一次跑通（run `39034766016`，v0.7.0）：draft 里三个 APK
（arm64 85.7 MB / armeabi-v7a 76.7 MB / x86_64 90.0 MB），`apksigner verify` 的证书
SHA-256 是 `f904a417…0b268c`，与 `$STARVEIL_KEYSTORE` 里 alias `starveil` 的
一致；`mapping-v0.7.0` 作为 artifact 保留。**draft 还没发布**，所以更新通道仍然看不到它。

## 更新通道怎么读到它

`UpdateChecker` 请求 `https://api.github.com/repos/justnpcx/Starveil-releases/releases/latest`，
取 `tag_name` 与当前版本比较，下载名字里带设备 ABI 的 asset，校验：

- SHA-256 对比 asset 的 `digest`
- 签名证书对比已安装应用

镜像仓库是公开的、只放 APK；源码与文档保持私有。**不要**把 `REPO` 指回源码仓库——
私有仓库的 `releases/latest` 未鉴权时返回 404，而客户端不能内置 token。

## 更新通道验证（2026-10-19）

第一次端到端跑通，源仓库保持私有，发布走公开镜像。

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
