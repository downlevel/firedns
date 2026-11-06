# User feedback — FireDNS

Requests and suggestions collected from users, to drive future work. When an item is done, tick it and link the commit or release; when it is rejected, keep it with the reason so it is not reopened.

Sources:
- **[R1]** Reddit, r/firetvstick — [launch post](https://www.reddit.com/r/firetvstick/comments/1xp97fk/i_made_a_free_opensource_app_to_switch_dns_on/) (November 2026)

## Feature requests

- [ ] **Exclude apps from FireDNS (per-app allowlist)** — [#1](https://github.com/downlevel/firedns/issues/1) — [R1] Let the user pick installed and system apps that keep using the network DNS. Android supports it via `VpnService.Builder.addDisallowedApplication` (today only FireDNS excludes itself, `DnsVpnService.kt`). Needs: an app picker usable with the D-pad, persisted package list, restarting the tunnel on change.
- [ ] **Make NextDNS easier to find** — [R1] A user asked to "add NextDNS" without realising custom DoH already supports it. Options: a NextDNS template in *Add DNS* that only asks for the configuration ID, or a clearer hint on the Home screen.

## Compatibility

- [ ] **Google TV / Android TV** — [#2](https://github.com/downlevel/firedns/issues/2) — [R1] Asked whether it works on Google TV. The app should work (minSdk 28, `LEANBACK_LAUNCHER`) but it has never been tested. Test on a Google TV device or emulator, then update *Supported devices* in the README.
- [x] **Pi-hole** — [R1] A user confirmed it works with a Pi-hole as a custom DNS.

## Documentation / FAQ

Questions that came up repeatedly. Added to the README FAQ (onboarding still to consider).

- [x] **"Does it block YouTube / Prime Video / Hotstar ads?"** — No. Those ads come from the same servers as the video, so no DNS-based blocker can block them. This was the most frequent question.
- [x] **"Does it need to run in the background / can I force close it?"** — It must stay running (foreground service, starts on boot). Force stopping it reverts to the network DNS.
- [x] **"Fire OS has a forced extra DNS, so DNS blocking doesn't work"** — Explain what FireDNS covers: the network's public DNS servers are routed into the tunnel too (see ARCHITECTURE.md). Also explain what it does not cover: apps with their own built-in DoH, and local DNS addresses (router, Pi-hole), which stay outside the tunnel by design.
- [x] **"Can I use it with my VPN?"** — No, Android allows only one VPN at a time. Already in the FAQ; consider mentioning it in onboarding too.
- [x] **Pi-hole vs FireDNS** — They are complementary: a Pi-hole can be added as a custom DNS in FireDNS.

## Rejected / out of scope

- **Blocking Amazon updates** — [R1] Not a built-in feature: update domains change often and blocking them also blocks security patches. Users who want it can use a custom NextDNS/Pi-hole with their own blocklist.
- **Blocking ads inside streaming apps** — Not technically possible at the DNS level (see FAQ above).
