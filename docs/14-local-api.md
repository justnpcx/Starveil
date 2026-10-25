# The local Clash-compatible API

Starveil can expose a small, Clash/Mihomo-compatible HTTP API on the device, so
a third-party dashboard (MetaCubeXD and the tools built for it) can read the
tunnel's traffic and switch nodes without Starveil having to build a dashboard
of its own.

It is **off by default**. Nothing listens until the switch in
**Settings → Developer options → Local API** is turned on.

## Why the kernel's API, not a second server

sing-box already ships a Clash-compatible server (`experimental/clashapi`).
Pointing it at loopback is a config change, not a feature:

- it reads the kernel's real traffic counters, proxy list and connection table;
- `PUT /proxies/{name}` goes through the same selector the kernel uses, so a
  switch from a dashboard is a real switch;
- the server is already audited as part of the pinned core, and a hand-written
  HTTP server would be a second thing to keep safe.

The app's part is the switch, the token, and passing both into the config at
connect time.

## Address and port

`127.0.0.1:9090`

- **Loopback only.** The bind address is a constant in
  `core/model/LocalApi.kt`; there is no setting that can make it `0.0.0.0`, and
  a unit test asserts the generated config never contains a wildcard
  controller.
- **Fixed port.** A dashboard has to be pointed at an address, and 9090 is what
  every Clash/Mihomo tool assumes. The port is safe to fix because the listener
  cannot be reached from off the device and every request needs the token.
  sing-box retries `EADDRINUSE` three times on Android, which covers the moment
  during a config swap when the old listener is still closing.

## Authentication

Every request needs:

```
Authorization: Bearer <token>
```

The token is 32 random bytes, base64url-encoded (43 characters), generated on
first use. It is wrapped by an AES-GCM key in the Android Keystore — only the
ciphertext is on disk — and it is never logged.

The token is shown in **Settings → Developer options**. **Reset token** revokes
it: the next config carries the new value and every dashboard using the old one
gets `401`.

A missing or wrong token returns `401 Unauthorized` before any handler runs.

## Endpoints

The minimal set, as served by the pinned core:

| Method | Path | What it returns |
|---|---|---|
| GET | `/traffic` | Up/down byte rates, one JSON object per line (streaming) |
| GET | `/proxies` | Every proxy and group, with the selected member |
| GET | `/connections` | Active connections, with host, rule and chain |
| PUT | `/proxies/{name}` | Select a member of a selector/urltest group |

`{name}` is the group's tag. Starveil's tags are `auto` (a group),
`proxy` (a single node), `proxy-<n>` (group members) and `per-app-<n>`.

### Trying it

```sh
TOKEN='<from Settings>'
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:9090/proxies
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:9090/traffic
curl -X PUT -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' \
     -d '{"name":"proxy-2"}' http://127.0.0.1:9090/proxies/auto
```

From another device on the LAN the connection is refused: the listener is bound
to loopback, not to the interface the LAN can reach.

## Not in scope

- WebSocket push (`/traffic` is the streaming endpoint).
- Any write endpoint beyond selecting a proxy.
- A bundled dashboard or external UI.

## Verification

The config contract is unit-tested: off emits `"clash_api":{}` (the traffic
manager needs the key to exist), on emits the loopback controller and the
secret, and no form ever emits a wildcard address.

The device checks — `curl` with the token, `401` without it, and a refused
connection from the LAN — were not run in this change: no phone was available.
They are the acceptance test for the next device session.
