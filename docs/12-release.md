# 发布流程

## 一次性准备

### 签名密钥

发布用的 keystore 在仓库**之外**：`/root/starveil-release.jks`（alias `starveil`，RSA 4096，有效期 10000 天）。
密码在 `/root/smart-home/.env` 的 `STARVEIL_KEYSTORE_PASSWORD` / `STARVEIL_KEY_PASSWORD`。

**这个文件丢了就再也发不了升级包**——已安装的用户只能卸载重装（数据丢失）。请离线备份到至少两个地方。

本地构建时它通过环境变量注入：

```bash
export STARVEIL_KEYSTORE=/root/starveil-release.jks
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

用有 `secrets:write` 权限的 token 跑一次：

```bash
GH_TOKEN=<token> scripts/set-signing-secrets.sh
```

2026-10-17 已用 `/root/smart-home/.env` 的 token 设好四个 secret（API 返回 201），
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
5. **等 Release workflow**（tag 触发）：构建、签名、上传三个 ABI 的 APK，并创建 **draft** release
6. **检查并发布 draft**——更新通道读的是 `releases/latest`，**draft 不会被看到**，发布之后才算上线

## 更新通道怎么读到它

`UpdateChecker` 请求 `https://api.github.com/repos/justnpcx/Starveil/releases/latest`，
取 `tag_name` 与当前版本比较，下载名字里带 `arm64` 的 asset，校验：

- SHA-256 对比 asset 的 `digest`
- 签名证书对比已安装应用

**仓库私有期间该接口返回 404**，App 显示「no published release」。M8 转公开后自动可用；
在此之前更新只能靠手动侧载。

## 崩溃符号化

R8 打开后，release 的堆栈是混淆过的。`mapping.txt` 由 workflow 作为 **artifact** 保留
（不放进 release），需要时下载后上传到 Sentry 项目（`sentry-cli upload-dif` 或 Web 上传）。
这一步还没有自动化——在接入真实 DSN 时一起做。

## 已知的发布前缺口

- Play 上架相关（前台服务类型申报、数据安全表单）未做
- 品牌资产（商店截图、社交预览图）未做
- 多设备覆盖（横屏 / 平板 / 折叠屏）未做
- `targetSdk` 35：Play 每年提高要求，上架前确认
