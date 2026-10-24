# 18 · 内核崩溃的取证方法

> 状态：方法 + 一次实操记录（2026-10-24，Fire TV AFTKRT / Android 11）。
> 本文只讲"崩溃之后去哪儿找证据"，不讲怎么避免崩溃。

## 1. 为什么"内核崩溃"等于"进程崩溃"

libbox 是编译期打包的**进程内**库（`core/kernel/libs/libbox.aar`），不是独立进程。所以内核出事的形态只有两种：

- **Go panic**：sing-box 往 stderr 打一段栈，然后 `abort()` → 进程收到 SIGABRT。
- **原生段错误**：SIGSEGV/SIGBUS，进程直接死。

两种都是"App 进程消失"，用户看到的是闪退，不是"隧道断了但界面还在"。

## 2. 三类证据，三条命令

| 证据 | 命令 | 能拿到什么 | 局限 |
|---|---|---|---|
| 崩溃缓冲区 | `adb logcat -b crash -d` | Java/ART 崩溃的完整栈；native abort 会带上 `Abort message` 与 Go 栈的末尾 | 环形缓冲，重启后会被挤掉 |
| 内核自己的 stderr | `adb logcat -d \| grep StarveilKernel` | `sing-box[n]: ...` 的运行时日志，含 panic 前的最后几条 | 只在 logcat 缓冲里；App 内的日志环是内存的，进程死了一起没 |
| 墓碑 / dropbox | `adb shell dumpsys dropbox --print SYSTEM_TOMBSTONE`（`/data/tombstones` 需 root） | native 崩溃的墓碑：信号、fault address、各线程栈、映射表 | **只有 native 崩溃才有**；Java 崩溃不产生墓碑 |

## 3. 实操记录（2026-10-24，电视）

用 `am crash` 制造一次真实崩溃，验证前两条路径：

```
$ adb shell am crash com.justnpcx.starveil
$ adb logcat -d -b crash | tail
10-24 00:16:10.818 16877 16877 E AndroidRuntime: FATAL EXCEPTION: main
10-24 00:16:10.818 16877 16877 E AndroidRuntime: Process: com.justnpcx.starveil, PID: 16877
10-24 00:16:10.818 16877 16877 E AndroidRuntime: android.app.RemoteServiceException: shell-induced crash
10-24 00:16:10.818 16877 16877 E AndroidRuntime:   at android.app.ActivityThread$H.handleMessage(...)
```

```
$ adb shell dumpsys dropbox --print SYSTEM_TOMBSTONE
Drop box contents: 572 entries
Searching for: SYSTEM_TOMBSTONE
(No entries found.)
```

结论：dropbox 在这台机器上**可读**，但 Java 崩溃不落墓碑——符合预期。**没能在非 root 设备上制造一次真正的 native 崩溃**（`kill -11` 被 SELinux 拒绝：`Operation not permitted`），所以墓碑路径只验证到"可读且为空"，没有拿到真实墓碑样本。这一条如实记在这里，不假装做过。

## 4. 已知缺口：内核日志没有落盘

App 内的日志环是内存里的；进程一死，"panic 前的最后 20 行"就只剩 logcat 缓冲区里那点，而 logcat 会被后续日志挤掉。复现一次崩溃现场时，往往已经太晚。

建议（未实现）：把 `StarveilKernel` 的 stderr 尾部（例如最近 200 行 / 64 KB）滚动写到 `files/kernel-tail.log`，崩溃后由用户从「日志」页导出。成本是每次启动一个文件句柄和少量 IO，收益是崩溃后还能看到内核自己说了什么。

## 5. 与 Kill Switch 黑洞验收共用同一套证据

Kill Switch 的"断开即黑洞"验收要在**宿主机**侧证明"包没出去"，这与崩溃取证是同一类工作——都是"从设备外部记录事实"：

```bash
# 宿主机（网关）上，隧道断开期间抓 60 秒
sudo tcpdump -i <lan-if> -n -c 200 'host <phone-ip> and not port 53'
# 期望：没有到节点地址的包；只有 LAN/本地解析
```

判断标准：断开后 60 秒内，发往节点 IP:port 的包数为 **0**；同时手机上应用仍能打开局域网页面（证明是"黑洞"而不是"没网"）。
