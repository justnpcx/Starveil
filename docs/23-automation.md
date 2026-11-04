# External automation (Tasker, MacroDroid, adb)

Starveil exposes three intents that start, stop and toggle the tunnel. They are
the same actions the app's own connect button performs — the same
`ConnectionController`, the same Kill Switch rules, the same per-app routes —
so an automation cannot put the tunnel into a state the UI could not.

There is no UI and no task: `AutomationActivity` is `NoDisplay`,
`excludeFromRecents` and `noHistory`, and it finishes in `onCreate`. The action
runs on a process scope, so it survives the caller walking away the moment the
intent is delivered.

## The three actions

| Action | What it does |
|---|---|
| `com.justnpcx.starveil.action.START` | Connects, if not already connected. |
| `com.justnpcx.starveil.action.STOP` | Disconnects, if connected. |
| `com.justnpcx.starveil.action.TOGGLE` | Connects when stopped, stops when running. |

Explicit component (recommended — it skips intent resolution):

```
com.justnpcx.starveil/.automation.AutomationActivity
```

The activity is exported; any app on the device can send these intents. That is
the point of the contract, and it is safe because the contract is only start,
stop, toggle. **The actions read and write no data** — no node list, no
settings, no logs. If you need more than on/off, the local API
(`docs/14-local-api.md`) is the richer surface, and it is off by default.

## adb

```bash
adb shell am start -n com.justnpcx.starveil/.automation.AutomationActivity \
  -a com.justnpcx.starveil.action.START

adb shell am start -n com.justnpcx.starveil/.automation.AutomationActivity \
  -a com.justnpcx.starveil.action.STOP

adb shell am start -n com.justnpcx.starveil/.automation.AutomationActivity \
  -a com.justnpcx.starveil.action.TOGGLE
```

The action is asynchronous: `am start` returns before the tunnel is up. Allow a
few seconds, then check the app or `adb shell dumpsys activity services`.

## Tasker

1. **Task → Add Action → System → Send Intent.**
2. Action: `com.justnpcx.starveil.action.START` (or STOP / TOGGLE).
3. Package: `com.justnpcx.starveil`; Class:
   `com.justnpcx.starveil.automation.AutomationActivity`.
4. Target: **Activity** (not Service, not Broadcast).
5. Leave the extras empty.

Attach the task to a profile — Wi-Fi connected, time of day, a widget tap —
and it behaves exactly like pressing the ring.

## MacroDroid

1. **Add Action → Applications → Send Intent.**
2. Target: **Activity**; Action: `com.justnpcx.starveil.action.TOGGLE`.
3. Package: `com.justnpcx.starveil`; Class:
   `com.justnpcx.starveil.automation.AutomationActivity`.

## When VPN consent is missing

The first `START` on a fresh install cannot bring the tunnel up: the platform
has not been asked for VPN permission yet, and only an activity can ask. In that
case the action opens the main screen, which runs the normal consent flow, and
writes one line to logcat:

```
I StarveilAutomation: START: VPN permission not granted; opening the app
```

It never fails silently. The same is true for the other cases that need a
decision — no node selected, an unbuildable config, or the kernel refusing to
start: the app opens instead of the intent doing nothing visible.

## Idempotence

`START` while connected, and `STOP` while disconnected, do nothing and log it.
Two `TOGGLE`s in a row return to the original state: the actions are serialised
through a mutex and each reads the kernel state the previous one left, so a
burst of intents cannot land the tunnel in a half state.
