# QUIC 连接迁移预研（P3-6）

时间盒：一次调研，不写生产代码。结论先行，证据在后。

## 结论

**sing-box 1.14（含本项目的 fork）不支持 QUIC 连接迁移。** 客户端侧在
`quic-go` 里就写明了不支持；fork 里和"迁移"最接近的东西是 Hysteria2 的端口跳跃，
而它是**直接改写远端地址**，不是迁移。

因此"WiFi ↔ 蜂窝切换不断流"在当前内核上做不到，不是应用层能补的。

## 证据

1. `github.com/sagernet/quic-go v0.61.0-sing-box-mod.9`（fork 的 `go.mod:47`），
   `connection.go:2468`：

   > We don't support connection migration yet, so we don't have any use for the
   > preferred_address.

   `preferred_address` 是服务端主动引导迁移的机制；客户端明确不做。

2. 同文件 `connection.go:3123`：`server cannot initiate connection migration` ——
   即使服务端发来迁移请求，客户端也会拒绝。

3. fork 里唯一相关的改动，`connection.go:1296`：

   > for hysteria2 port hopping, direct change remote address without connection
   > migration logic

   端口跳跃是把 `remoteAddr` 换掉，**本地地址和 socket 都不动**。真正的迁移要求
   换的是本地四元组（新接口的新地址），并且 QUIC 连接 ID 与路径验证都按 RFC 9000
   走。这段代码恰好绕开了那套机制，所以不能当作迁移能力。

4. 传输层：`transport/` 与 `protocol/` 下没有任何 migration 相关实现（grep
   `Migration` 只命中一条与 QUIC 无关的迁移文档链接）。

## 如果要做，需要什么

按依赖顺序，缺一不可：

- **quic-go 侧**：实现客户端路径迁移（`PATH_CHALLENGE` / `PATH_RESPONSE`、连接 ID
  更换、`disable_active_migration` 的处理）。上游明确说没有，等于要自己写。
- **sing-box 侧**：把"本地网络变了"这个信号传给 QUIC 连接，并允许 dialer 重新绑定
  到新接口。当前 `dialer` 在连接建立后不再监听网络变化。
- **Android 侧**：VpnService 的 tun 在切换网络时会被重建（`establishTun` 先
  `closeTun`），所以在应用进程里"保住 socket"这条路本身就被系统断掉了；要么让 tun
  跨网络存活（VpnService 不支持），要么接受重建。

第三点是关键：即使前两点都做了，Android 上 tun 的重建仍然会切断已有连接。真正的
"切网络不断流"在这套架构里没有落点。

## 备选方案：切网络后的快速恢复

不做迁移，改做"断得短"。当前已经测得的数字（docs/11 的 TTFP）：

- 内核启动到可用：**p50 174 ms**（VLESS+XHTTP+REALITY），179 ms（Hysteria2）
- 用户点击到首个代理连接：p50 389 ms

也就是说，**一次完整的重连已经在 0.5 s 量级**。真正的问题不是重连慢，而是
"网络变了"到"开始重连"之间的检测延迟：

- 现在：`SceneController` 的 5 秒去抖 + 场景匹配，只在有场景时触发重连；没有场景时
  由内核自身的连接失败驱动。
- 可做（未做，属于功能而非预研）：网络变化时若隧道在运行且没有场景接管，**主动重建**
  一次，把检测延迟从"下一次连接失败"压到 5 秒去抖。收益是可见的，成本是一个已有
  路径的复用（`ConnectionController.restart()`）。

建议：先做这条，不碰 QUIC。等上游支持迁移、且 Android 允许 tun 跨网络存活，再回来看
真正的迁移。

## 下一步建议

1. **不做** QUIC 迁移：三条依赖都不在掌控内，其中一条（tun 重建）是平台限制。
2. **可做** "网络变化即重建"：5 秒去抖后主动 `restart()`，把恢复时间从"下一次失败"
   变成"最多 5 秒 + 0.4 秒"。需要先定义"什么算网络变化"（默认网络 transport 变化，
   不是信号强度变化）。
3. 若要做 2，先补一条真机测量：手动关 WiFi 再开，记录隧道恢复到可用的时间——现在
   没有这个基线。
