# Node reachability behind a transparent proxy

Why two subscription nodes reported `reality verification failed` and
`context deadline exceeded` for weeks, and what actually fixed them.

This is an **environment** note, not an app bug — but it changes how
`NodeLatencyTester`'s number must be read, and it is the first thing to
rule out before blaming a node (or the app) for a failed dial.

## TL;DR

A transparent proxy on the LAN was **redirecting the nodes' ports**. Traffic
to `:443`/`:8443` was pulled into the proxy and relayed through a tunnel whose
own exit was the *same server the nodes live on* — a hairpin. REALITY never
completed inside it.

The nodes were fine. The subscription was fine. The app was fine.

## Symptom

Two nodes from a 3x-ui subscription, both REALITY over `flow=xtls-rprx-vision`:

| Node | Port | Error |
|---|---|---|
| `npc-speed` | `:443` | `reality verification failed` |
| `cn-speed` | `:8443` | `context deadline exceeded` |

A third node, added by hand and pointing at the same host on **`:2053`**,
worked. `:2053` is the tell — see below.

## Three false leads

Each was plausible, each was eliminated with evidence. Worth recording so
nobody re-walks them.

### 1. "The subscription refresh never ran"

True, but a separate bug — and fixing it did **not** fix the nodes.

`SubscriptionUpdateScheduler.schedule()` had no caller until `766cc79`. A group
imported before that has a URL and **no periodic work**, so it can never
refresh. The group menu also had no manual refresh action.

Fixed in `c452a61` ("Refresh subscription" in the group menu, which also
re-arms the schedule). Device-verified: the banner reports the node count, and
`dumpsys jobscheduler` gains a `SystemJobService` job at roughly the 6h
cadence. **But the refreshed subscription contained the same two nodes** — the
server had not changed them.

### 2. "The subscription content is wrong"

Verified correct. Every REALITY parameter in the link matched the server:

- `pbk` — the panel's stored `publicKey` was checked against a key derived from
  the server's `privateKey` with X25519. Byte-for-byte identical.
- `sid` — present in the server's `shortIds`.
- `sni` — present in the server's `serverNames`.
- `uuid`, `flow` — matched the inbound's client entry.

Recipe in [Verifying a subscription's REALITY key](#verifying-a-subscriptions-reality-key).

**Consequence: "re-pull the subscription" can never fix a node whose
parameters are already correct.** If the data is right, refreshing just
rewrites it.

### 3. "It's the Xray `minClientVer` trap"

Plausible — the environment did match the documented trap: Xray **26.10.10**
(≥ 26.7.11) with `realitySettings.minClientVer` **empty** on both inbounds,
which makes Xray fall back to a built-in minimum of `26.3.27` and reject
sing-box-based clients during the REALITY handshake.

It was still not the cause here: with the proxy disabled, both nodes connected
unchanged. Left as a latent hazard (setting `minClientVer` to `1.0.0` is
harmless), but it was not this bug.

The links' `support-x25519mlkem768=true` flag was eliminated the same way.

## The actual cause

### Finding it: same IP, different port, 100× RTT difference

`connect()` to the node's host, varying only the port:

| Port | TCP connect | Verdict |
|---|---|---|
| 22 / 53 / 80 / **443** / 853 / **8443** | **1.1–1.6 ms** | terminated locally |
| 2053 / 2096 | 165–190 ms | the real path |
| 2083 / 2087 / 2095 / 8880 / 54321 | refused | genuinely closed |

One IP, one route, two orders of magnitude. Nothing on the internet can
produce that — something local was answering.

### Confirming it: `traceroute` on the same IP, two ports

```
-T -p 443   →  1  192.168.50.1   0.73 ms
               2  <node host>    1.66 ms      ← "reached" across an ocean in 2 hops

-T -p 2053  →  1  192.168.50.1  →  2  10.0.0.1  →  3  192.168.1.1
               4  10.145.128.1  →  5  120.80.101.189  →  9  219.158.32.6
              12  ...                                172 ms   ← the real path
```

`:443` stops at the gateway; `:2053` goes the distance. Definitive.

**It was not a TLS man-in-the-middle.** `openssl s_client` to `:443` still
returned the server's genuine REALITY decoy certificate, so TLS was not being
intercepted — the redirect was at the TCP layer. That is exactly why the first
instinct ("a plain TCP relay cannot break REALITY") was wrong; see below.

### Proving causation: A/B

Disabling the proxy on the soft router made every port take the real path
(443/8443/2053/2096 all ~165–175 ms). Then, on the phone:

| Node | proxy on | proxy off |
|---|---|---|
| `npc-speed` `:443` | `reality verification failed` | **CONNECTED** |
| `cn-speed` `:8443` | `context deadline exceeded` | **CONNECTED** |

End-to-end, not just a status label:

- the phone's browser loaded an IP-echo page and reported the **proxy host's**
  address, i.e. traffic really exited through the tunnel;
- the phone's `tun0` counters moved (RX ≈ 3.9 MB);
- the app's own byte counters grew in real time;
- `logcat` had **zero** REALITY / deadline / dial errors.

### Mechanism (hypothesis, consistent with all of the above)

The proxy's own upstream was a node on the **same host**, on a port that is
*not* redirected (`:2053`). So the phone's connection to `<host>:443` was
redirected into the proxy, carried through the `:2053` tunnel, and the host
then dialled **its own public `:443`** — a hairpin, with REALITY +
`xtls-rprx-vision` nested inside another `xtls-rprx-vision` tunnel.

The ports that failed are exactly the ports that were redirected. The node that
worked is the one on a port that was not.

## What this means for Starveil

**`NodeLatencyTester` measures a plain TCP handshake to `server:port`** (that is
deliberate — it needs no tunnel, so it works before connecting and for whichever
node is selected). On a network like this one, for a node on a redirected port,
**the number it reports is the RTT to the local interceptor, not to the node.**

Observed: a node 170 ms away reported **4 ms**, while a node on the same host on
a non-redirected port reported 165 ms. A suspiciously *good* latency on a
distant node is a red flag, not good news.

The doc comment on the tester already says it measures the network round-trip
and not the proxy's handshake; this adds a second way it can mislead.

**Nothing in the app needs to change for this.** The app faithfully dials what
it is told; the environment decided where the packets went. But two operational
rules follow:

1. **Do not put nodes on ports a transparent proxy redirects.** The common
   redirect sets include 80/443 and often 8443. `:2053` and `:2096` were clean.
2. **Or exempt the node's host from the proxy** — a direct/bypass routing rule
   for the node's domain keeps the rest of the network proxied while letting
   the app reach its nodes directly. Concrete steps for the router used here
   (OpenWrt + `luci-app-homeproxy`) are below.

### Applying the exemption (luci-app-homeproxy)

The client config has a **Routing Rules** tab (`routing_rule` sections), which
is the right place when the routing mode is `bypass_mainland_china`. The
simpler-looking *Proxy Domain List* / *Direct Domain List* textareas are
**hidden** in that mode — they only render when the routing mode is `custom`,
so they are not an option here.

**UI:** Services → HomeProxy → Client → **Routing Rules** → Add

| Field | Value |
|---|---|
| Label | `justnpc-direct` (anything) |
| Enable | ✓ |
| **IP CIDR** | **`69.63.200.132/32`** |
| **Action** | `Route` (the default) |
| **Outbound** | **`Direct`** |

### Match on the IP, not the domain — the domain will never match

**A domain rule cannot work for a REALITY node.** The app dials the node's real
hostname, but the ClientHello it sends carries the node's **disguise SNI**, not
the hostname. For the nodes here:

| Node | dials | ClientHello SNI |
|---|---|---|
| `npc-speed` | `api.justnpc.com:443` | `intel.com` |
| `cn-speed` | `api.justnpc.com:8443` | `home.amazon.com` |

The proxy sniffs the ClientHello and therefore sees `intel.com` /
`home.amazon.com` — `api.justnpc.com` never appears in any packet, so a rule on
it is dead weight. (Measured: with such a rule in place the nodes still failed,
the tunnel came up and every relayed connection died with `EOF` — which is what
REALITY's fallback to the decoy destination looks like from the client side.)

Use **IP CIDR** instead. It matches the destination address, which the redirect
provides directly and which needs no sniffing.

Do **not** work around this by adding the disguise SNI (`intel.com`) as a
domain rule — that would send all of Intel's traffic direct.

### Applying it

**UI:** Services → HomeProxy → Client → **Routing Rules** → Add, with the table
above.

**Equivalent CLI** (the Direct outbound's tag is `direct-out`):

```sh
uci add homeproxy routing_rule
uci set homeproxy.@routing_rule[-1].label='justnpc-direct'
uci set homeproxy.@routing_rule[-1].enabled='1'
uci set homeproxy.@routing_rule[-1].ip_cidr='69.63.200.132/32'
uci set homeproxy.@routing_rule[-1].action='route'
uci set homeproxy.@routing_rule[-1].outbound='direct-out'
uci commit homeproxy
/etc/init.d/homeproxy restart
```

### Verifying it

**A TCP connect-time probe cannot verify this.** The connection is terminated by
the proxy either way, so `connect()` still returns in ~1 ms whether the rule
matched or not — that is how the wrong rule got past the first check. Verify
**end-to-end** instead: connect the app to the node and confirm it carries
traffic (`tun0` counters move, an IP-echo page reports the expected exit).

An alternative to the exemption is to move the node to a port outside the
redirect set, but that changes the subscription for every client, so the
bypass rule is usually the smaller change.

## Recipes worth keeping

### Telling a redirected port from a real one

Cheap and conclusive, no packet capture needed:

```bash
# Vary only the port. ~1ms means something local answered; the real RTT means
# the packets left the building.
traceroute -T -p <port> -n <node-ip>

# Or measure directly:
python3 -c "import socket,time
for p in (443, 8443, 2053):
    s=socket.socket(); s.settimeout(5); t=time.time()
    try: s.connect(('<node-ip>', p)); print(p, round((time.time()-t)*1000,1), 'ms')
    except Exception as e: print(p, 'ERR')
    finally: s.close()"
```

A port that answers in ~1 ms regardless of where the host actually is is being
terminated locally. Do **not** trust a connect-time probe on it.

### Verifying a subscription's REALITY key

To check whether a subscription's `pbk` matches the server, derive the public
key from the server's `realitySettings.privateKey` (base64url, 32 raw bytes)
with X25519 against the base point, and compare base64url (unpadded):

```python
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
import base64
b64d = lambda s: base64.urlsafe_b64decode(s + '=' * (-len(s) % 4))
derived = base64.urlsafe_b64encode(
    X25519PrivateKey.from_private_bytes(b64d(private_key)).public_key()
        .public_bytes(Encoding.Raw, PublicFormat.Raw)
).decode().rstrip('=')
```

`cryptography` is not always installed; RFC 7748 X25519 is ~30 lines of pure
Python if needed.

### Driving the 3x-ui v3 API

The panel UI is a React SPA and its API is **not** the classic v2 one. Two
things cost time:

1. **Login needs a CSRF token.** `GET <base>/` returns HTML containing
   `<meta name="csrf-token" content="...">`. `POST <base>/login` (form fields
   `username`, `password`) must carry that value in an **`X-CSRF-Token`**
   header. Without it the panel answers **`403` with an empty body** — which
   reads like a WAF or an IP allowlist and is neither.
2. **The paths are singular and mixed-method.** Working endpoints:

   | Endpoint | Method |
   |---|---|
   | `/panel/api/inbounds/list` | GET |
   | `/panel/api/inbounds/options` | GET |
   | `/panel/api/server/status` | GET |
   | `/panel/api/setting/all` | **POST** (GET is 404) |

   Note `setting` (singular). `/panel/api/settings/all` is a 404.

To discover other paths, fetch the SPA entry chunk and grep it — the entry
chunk name is in the HTML served for the panel route:

```bash
curl -s <base>/panel/ | grep -oE 'assets/index-[A-Za-z0-9_-]+\.js'
curl -s <base>/assets/index-XXXX.js | grep -oE 'panel/api[a-zA-Z0-9_/{}$.:-]*'
```

The Xray log endpoint was not found; everything under
`/panel/api/server/logs/*` returns `{"success":true,"obj":null}`, which looks
like a catch-all rather than an empty log.

## Environment reference

| Thing | Value |
|---|---|
| Node host | `api.justnpc.com` → an AS906 DMIT Cloud Services host in Los Angeles |
| Panel | `https://api.justnpc.com:38828/npc-gate/` (3x-ui v3.10.0) |
| Panel port | 38828 — **not** in the redirect set, so it is reachable directly |
| Subscription service | port 2096, custom path, `subEncrypt=true` (base64 body) |
| Xray | 26.10.10 |
| Inbounds | shadowsocks `:38822`, REALITY `:8443`, REALITY `:443`, XHTTP+TLS `:38883` |
| Redirect set observed | 22, 53, 80, 443, 853, 8443 |

Credentials, subscription IDs and node keys are deliberately **not** recorded
here — they live in the panel and in the app's encrypted store.
