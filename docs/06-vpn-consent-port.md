# The VPN consent port — sanctioned exception to the kernel-dependency rule

## The rule

> `:app` and `:feature:*` must never depend on the kernel directly (no sing-box
> imports, no JNI, no process management) — only through `KernelBridge`.

## Why consent needs an exception

Showing the system VPN consent dialog requires `VpnService.prepare()`, and the
`VpnService` subclass that owns the tun lives in `:core:kernel`. The click that
triggers it is a Compose button inside `:feature:dashboard`. `KernelBridge` is
frozen, so the consent call cannot go through it.

## The exception, narrowly drawn

`:core:kernel` exposes exactly one extra, kernel-free type:

```kotlin
fun interface VpnConsent { fun consentIntent(): Intent? }
fun starveilVpnConsent(context: Context): VpnConsent
```

- `:feature:*` never touches it — `DashboardScreen` takes a plain
  `(onGranted: () -> Unit) -> Unit` callback, so the feature module has no
  kernel import at all.
- `:app` uses only `VpnConsent` / `starveilVpnConsent`. It never sees
  `StarveilVpnService`, sing-box, JNI or process management.
- The port carries **no kernel capability**: it returns an `Intent` the OS
  shows, nothing more. It cannot start, stop or configure the kernel.

## Rule wording going forward

The rule should read: *no kernel dependency except the `VpnConsent` port* —
sing-box imports, JNI and process management remain forbidden outside
`:core:kernel`, and `:feature:*` stays completely kernel-free.

## Residual risk

If a second kernel-adjacent UI need appears (e.g. a "revoke VPN" action), do
**not** widen this port ad hoc — bring it to review and decide whether it
belongs in the port or in a dedicated `:core:vpn` module.
