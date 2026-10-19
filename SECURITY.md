# Security policy

Starveil is a VPN client: it holds node credentials, a signing key never enters
this repository, and the app can install its own updates. Reports about those
paths are welcome and will be taken seriously.

## Supported versions

Only the latest published release is supported. The project is in beta; there
are no long-term branches and no backports.

| Version | Supported |
|---|---|
| latest release (0.7.x) | yes |
| anything older | no — please reproduce on the latest first |

## Reporting a vulnerability

**Do not open a public issue.** Use GitHub's private reporting:

1. Open the repository's **Security** tab.
2. Choose **Report a vulnerability**.
3. Describe the issue there.

If private reporting is unavailable, open an issue that says only "security
report, please contact me" — no details — and a private channel will be
arranged.

Please include:

- the app version (Settings → About) and the APK's ABI;
- device model and Android version;
- what an attacker gains, and the steps to reproduce;
- logs with node links, UUIDs, passwords, subscription URLs and keys redacted.

## Scope

In scope:

- the app and its `:core:*` modules;
- the kernel bridge and the config handed to libbox;
- the update channel (digest and signing-certificate checks, install path);
- the local Clash-compatible API (loopback only, Bearer token);
- backup encryption and credential storage.

Out of scope:

- the proxy servers, their panels, or the network path to them;
- third-party rule sets and subscription providers;
- rooted or otherwise modified devices;
- denial of service against your own device or network;
- social engineering.

## What to expect

This is a single-maintainer project without a bounty programme. Reports are
read on a best-effort basis; expect an acknowledgement within a few days. If a
fix ships, the release notes will say so without naming you unless you ask.

## Safe harbour

Good-faith research is welcome. Do not access other people's data, do not run
denial-of-service tests, and give the maintainer a reasonable window to fix the
issue before publishing details.
