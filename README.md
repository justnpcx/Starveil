<div align="center">

<img src="docs/icon/starveil-c2.png" width="96" alt="Starveil 图标">

# Starveil 星纱

**星光为路 · 薄纱为盾**

基于 sing-box 内核的 Android 网络调试与规则分流工具——连接明细、DNS 采样、泄漏自检与诊断包导出，让每一条流量都可观测、可解释、可复现。

[![Release](https://img.shields.io/github/v/release/justnpcx/Starveil-releases?label=下载最新版)](https://github.com/justnpcx/Starveil-releases/releases)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10%2B-green.svg)](#下载与安装)
[![Kernel](https://img.shields.io/badge/kernel-sing--box%201.14.3-orange.svg)](docs/04-libbox-build.md)

[下载](#下载与安装) · [特性](#特性) · [快速上手](#快速上手) · [文档](#文档) · [更新日志](CHANGELOG.md)

</div>

> **当前状态：0.9.x 内测。** 功能完整、日常可用；个别性能指标仍在真机验证中（见 [真机验证记录](docs/11-device-verification.md)）。重要配置请先用 App 内「备份与恢复」导出加密备份。
>
> **免责声明**：本项目为网络调试与学习研究用途的开源工具。使用者应遵守所在地法律法规与网络服务条款；因使用本工具产生的任何后果由使用者自行承担。

<!-- 发布前放入四张真机截图：docs/screenshots/01-home.png 02-nodes.png 03-rules.png 04-diagnostics.png
## 截图
<div align="center">
<img src="docs/screenshots/01-home.png" width="200"> <img src="docs/screenshots/02-nodes.png" width="200"> <img src="docs/screenshots/03-rules.png" width="200"> <img src="docs/screenshots/04-diagnostics.png" width="200">
</div>
-->

## 特性

**调试与诊断（本项目的重心）**
- **连接明细**：每条连接连了哪里、命中哪条规则、走的哪个节点，全程可查
- **DNS 采样统计**：逐次记录解析耗时，给出 p50 与预算对比（30 ms），慢在哪里一目了然
- **泄漏自检与诊断包**：一键自检 DNS/路由泄漏；诊断包（版本、内核、DNS 统计、脱敏日志）分享前可预览
- **流向日志**：内核级连接流向与应用日志分级过滤、搜索、导出

**连接与分流**
- 规则分流：域名 / 域名后缀 / 关键字 / IP 段 × 代理 / 直连 / 阻断，另有规则集与预设（国内直连等）
- 场景自动化：按 Wi-Fi SSID / 移动数据自动切换路由预设
- Kill Switch 双模式：隧道断开时阻断（黑洞式）与始终阻断；局域网地址恒直连
- 按应用分流、自动选择最优节点、断线自动切换、节点健康评分（最近 30 分钟可达率与平滑延迟）

**协议与订阅**
- 7 种协议：VLESS · VMess · Trojan · Shadowsocks · Hysteria2 · TUIC · WireGuard
- 传输与安全：XHTTP / REALITY / TLS 等主流组合
- 订阅四格式解析：base64 · Clash YAML · sing-box JSON · SIP008；扫码、剪贴板、链接、手动填写四路导入

**隐私与安全**
- 节点凭证 SQLCipher 整库加密；备份为口令加密文件，无口令不可恢复
- 日志与分享强制脱敏：节点地址、UUID、密钥在写出前替换；崩溃上报可关，发送前脱敏
- 本地 API（127.0.0.1 + Token）供自动化调用，默认关闭

**体验**
- 中英双语（可跟随系统或独立切换）、昼夜双模式 + 5 种主题色 + 三档字号
- 桌面小部件与快捷设置磁贴；应用内检查更新（SHA-256 与签名双校验后才交系统安装器）

## 下载与安装

1. 到 [Starveil-releases](https://github.com/justnpcx/Starveil-releases/releases) 下载最新 APK（需 Android 10 / API 29 以上）。
2. 安装时如系统提示，允许「安装未知应用」。
3. 已装旧版可在 App 内「关于 → 版本更新」直接升级；更新包在下载后校验 SHA-256 与签名证书，不匹配不会安装。

## 快速上手

1. **导入**：点节点页右下角 + ，选扫一扫 / 剪贴板 / 订阅 / 手动任一方式添加节点。
2. **连接**：回首页点连接圆环。默认预设下国内流量直连、其余走所选节点。
3. **排障**：连不上或慢——先看「连接明细」与「DNS 日志」，再跑一次「泄漏自检」，最后导出诊断包附到 Issue。

## 架构

单 Activity + Jetpack Compose，多模块硬边界：

```
:app ── 导航 / 装配（禁止直连内核，只经 KernelBridge）
  ├── :feature:dashboard   首页（连接、遥测、健康）
  ├── :feature:profiles    节点 / 分组 / 订阅
  ├── :feature:rules       路由规则 / 最近流向
  ├── :feature:editor      节点编辑
  ├── :feature:logs        日志
  └── :feature:settings    设置 / 诊断 / 备份
:core:kernel ── sing-box 1.14.3 自维护 fork（libbox）+ KernelBridge 唯一契约
:core:probe  ── 延迟探测 / 健康采样 / DNS 采样 / 泄漏自检
:core:data   ── Room(SQLCipher) / DataStore / 订阅解析
```

技术栈：Kotlin + Compose (Material 3) + Hilt + Coroutines/Flow + Room + WorkManager。模块职责与边界规则见 [产品指南](docs/03-top-tier-product-guide.md)。

## 构建

```bash
# 需要 JDK 17 + Android SDK（compileSdk 36）
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest checkTranslations lintDebug   # 发布前必跑
```

`checkTranslations` 校验全模块中英字符串键与占位符双向一致。内核 AAR 的构建方式（一次只编一个 ABI）见 [docs/04-libbox-build.md](docs/04-libbox-build.md)。签名密钥不入库，CI 只做 debug 构建与 lint。

## 安全与隐私

- 凭证加密存储、日志脱敏、备份加密，细节见 [隐私政策](docs/10-privacy-policy.md)（与 App 内版本同一份）。
- 发现安全问题请按 [SECURITY.md](SECURITY.md) 私密上报，不要开公开 Issue。

## 路线

| 阶段 | 状态 |
|---|---|
| 核心能力（7 协议 / 规则分流 / Kill Switch / 订阅） | 已交付 |
| 调试套件（连接明细 / DNS 采样 / 泄漏自检 / 诊断包） | 已交付 |
| 体验打磨（双语 / 主题 / 字号 / 场景自动化） | 0.9.x 进行中 |
| 长期日常使用验证与性能收尾 | 进行中 |
| 公开发布准备 | 规划中 |

## 文档

- [产品指南与里程碑定义](docs/03-top-tier-product-guide.md) · [隐私政策](docs/10-privacy-policy.md) · [真机验证记录](docs/11-device-verification.md)
- [本地 API](docs/14-local-api.md) · [规则市场](docs/15-rule-market.md) · [libbox 构建](docs/04-libbox-build.md) · [发布流程](docs/12-release.md)
- [更新日志](CHANGELOG.md) · [安全策略](SECURITY.md)

## 许可证

**GNU General Public License v3.0**（见 `LICENSE`）。本项目基于 sing-box 等 GPL 开源工作，衍生与分发须继续以 GPL 开源并提供源码。
