# PRD — FireDNS

## Vision
Change the DNS of a Fire TV in one click from the remote, without forgetting and reconfiguring the Wi-Fi network. An **open-source, free** portfolio project focused on privacy, ad blocking and speed.

## Problem
On Fire TV (Fire OS) the only way to change DNS is to forget the Wi-Fi network and set it up again in the advanced settings (static IP + manual DNS), typing everything with the remote. It is tedious and discourages anyone who wants a faster DNS or one that blocks ads and trackers. Since Android 10 apps can no longer change the Wi-Fi configuration: FireDNS works around this with a **local VPN** (VpnService) that intercepts only DNS queries and forwards them to the chosen provider.

## Target users
- Fire TV owners running **Fire OS 7+**: Fire TV Stick 4K Max, 4K Plus, Cube, Fire TV Stick HD (2024), TVs with Fire TV built in.
- Tinkerers who install apps with Downloader or by sideloading.
- Usage context: couch, remote, a few quick actions ("turn on AdGuard", "back to Cloudflare").
- Excluded: **Vega OS** devices (no APKs, no sideloading).

**Positioning:** the app is presented **exclusively** for privacy, ad/malware blocking and speed/stability. It is never promoted for bypassing blocks or geographic restrictions, nor distributed through channels dedicated to unauthorized content.

## Features
### Must-have (v1.0)
- **One-button ON/OFF** of the local DNS VPN.
- **Built-in DoH profiles**: Cloudflare, Google, AdGuard DNS, Quad9.
- **Custom DNS profiles** (several, named): the user enters an IP (→ UDP) or an `https://…` URL (→ DoH); the protocol is detected automatically.
- **Active profile selection** from the Home screen; switching profile while the VPN is on applies without turning it off and on.
- **Start on boot** (optional, on by default after the first activation).
- **Fallback**: if the chosen DNS fails N times in a row, queries go to the system DNS and a notice is shown; the chosen DNS is retried periodically.
- **Onboarding** that explains the VPN permission before the system prompt.
- **English and Italian UI** (follows the system language).
- **About screen** with version, repository link and lawful-use disclaimer.

### Nice-to-have (v1.1+)
- **Latency test** of the profiles (ms shown on the cards, fastest highlighted).
- **Quick toggle/tile** from the launcher (check what Fire OS allows).
- In-app language picker, independent from the system.
- Import/export of custom profiles.

## User stories
- As a Fire TV user I want to turn on an ad-blocking DNS with one button, without touching the Wi-Fi settings.
- As a user I want to switch provider (e.g. from Cloudflare to AdGuard) by selecting a card with the D-pad.
- As a user I want to add my NextDNS/Pi-hole DNS by entering an IP address or a DoH URL and naming it.
- As a user I want to edit and delete my custom profiles.
- As a user I want DNS protection to come back on by itself after the Fire TV restarts.
- As a user I want to keep browsing if the chosen DNS goes down, and be told that fallback is active.
- As a first-time user I want to understand why the app asks for a VPN permission and that my traffic is not sent anywhere else.

## Data model (conceptual)
- **DnsProfile**: id, name, kind (built-in/custom), protocol (DoH/UDP), DoH URL (optional), server IP addresses, DoH bootstrap IPs (built-in only).
  - Built-in profiles are defined in code (read-only); custom ones are saved by the user.
- **Settings**: active profile id, VPN desired ON/OFF, start on boot, fallback enabled, onboarding done.
- **Runtime state (not persisted)**: VPN state (off / starting / active / fallback / error), upstream failure counter.

Relations: Settings → one active DnsProfile (1:1); custom profiles 0..N.

## Non-functional requirements
- **Performance**: < 10 ms added latency per query over the upstream round trip; in-memory DNS cache honoring TTLs; negligible CPU/RAM at rest (the Stick has limited resources).
- **Reliability**: the VPN must never leave the device without DNS (fallback); the service survives in the foreground with a notification.
- **Security/privacy**: no telemetry, no analytics, no backend; non-DNS traffic does **not** enter the tunnel; queries are sent only to the chosen provider (or to the system DNS during fallback). DoH for all built-in profiles.
- **Accessibility**: fully D-pad navigable, focus always visible, compatible with VoiceView (the Fire OS screen reader), WCAG AA contrast, text readable from 3 m.
- **i18n**: EN (default) + IT, all strings in `strings.xml`.
- **Compatibility**: minSdk 28 (Fire OS 7), recent targetSdk; tested on at least 2-3 Fire TV generations, including Fire OS 8 and, if possible, Fire OS 14.
- **Legal/communication**: lawful-use disclaimer in the app and in the README; no references to bypassing blocks.

## Out of scope (v1)
- Private DNS (DoT) through network ADB.
- Vega OS support.
- Amazon Appstore publishing (possibly later).
- DNS-over-TLS and DNS-over-QUIC.
- Query log/history and statistics.
- Local blocklists / custom filters.
- Per-app rules (per-app split tunnel).
- Running alongside a "real" VPN (Android has a single VPN slot).

## Open questions
- Can the name "FireDNS" cause trademark issues (Amazon "Fire") if published on the Appstore? — TBD
- Fallback threshold and timing (N consecutive failures, retry interval): currently 3 failures / retry every 60 s — to be validated on more devices.
- Does Fire OS expose the "Always-on VPN" setting? If so, it could replace or complement the boot receiver.
- IPv6 upstream support: needed for IPv6-only networks? — TBD
- Demand validation (Reddit/XDA threads, a published guide) before or alongside the launch?
