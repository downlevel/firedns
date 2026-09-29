# Roadmap — FireDNS

## Milestone 0 — Setup and feasibility spike
- [x] Write the local VPN spike → `spike/` (APK `spike/firedns-spike.apk`, test procedure in `spike/README.md`)
- [x] Run the spike on a real Fire TV: minimal VpnService routing only the virtual DNS + UDP forwarding to 1.1.1.1; check that browsing works, non-DNS traffic stays out of the tunnel and `addDisallowedApplication` is honored
- [x] Record test device model and Fire OS version → results in `spike/README.md` (Fire TV Stick HD 1st gen, Fire OS 7.7.1.6: all good)
- [ ] Repeat the checks on a **Fire OS 8** device (4K Max 2nd gen or 4K 2nd gen, where network blocks were reported) — before 1.0 if possible
- [ ] Test a network change (Wi-Fi ↔ Ethernet or another SSID)
- [ ] (Optional, informational) Try `private_dns_mode` over ADB, for comparison in the README
- [x] Decide license, applicationId and GitHub handle → MIT, `dev.downlevel.firedns`, github.com/downlevel
- [x] Create the `downlevel/firedns` repository with MIT LICENSE and Android .gitignore
- [x] Before going public: README with lawful-use disclaimer, internal analysis kept out of the repository, everything in English
- [x] Create the Android project (Kotlin, minSdk 28, Compose + `androidx.tv:tv-material`, version catalog)
- [x] TV manifest: `LEANBACK_LAUNCHER`, 320×180 banner, `leanback`/`touchscreen` `required=false`
- [x] ktlint and Android Lint

## Milestone 1 — Infrastructure
- [x] `ci.yml` workflow: ktlint + lint + unit tests + assembleDebug on push/PR
- [x] Release keystore generated and uploaded to GitHub Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
- [x] `release.yml` workflow: on `v*` tags → keystore check → signed assembleRelease → `firedns.apk` → GitHub Release (verified with v0.9.x)
- [x] Check that `releases/latest/download/firedns.apk` downloads the latest release (verified with Downloader)
- [x] `AppContainer` (manual DI) and `FireDnsApp`
- [x] `DnsProfile`, `Settings` models + `Presets.kt` (Cloudflare, Google, AdGuard, Quad9 with DoH URLs and bootstrap IPs)
- [x] Custom address validation (`DnsAddress.parse`: IPv4/IPv6 → UDP, `https://` → DoH) + unit tests
- [x] `SettingsRepository`/`ProfileRepository` on DataStore JSON with `schemaVersion` + unit tests
- [x] Fixed debug keystore, so CI debug APKs install over each other

## Milestone 2 — Design (UI)
- [x] `tv-material` theme (graphite/orange palette, TV type scale, uniform focus style in `FireFocus`)
- [x] Navigation (Onboarding → Home → Editor / Settings → About) and Back key handling
- [x] Components: PowerToggle, StatusHeader, ProtocolBadge, ProfileCard, AddProfileCard, Banner, SettingsRow/FireSwitch, ProfileMenuDialog, ConfirmDialog, FireButton, FireTextField
- [x] Empty / loading / error / fallback states with fake data (previews in `ui/Previews.kt`; `FakeVpnController`)
- [x] EN + IT strings in `strings.xml`
- [x] Profiles wired to real data: select, add, edit and delete custom profiles
- [x] Checked on Fire TV: D-pad navigation, look and readability
- [x] App icon for the Fire OS 7 launcher: flame on a transparent background (v0.9.3); 16:9 banner on the TV alias
- [x] Screenshots in `docs/screenshots/` and in the README

## Milestone 3 — Core features
- [x] IPv4 + UDP/TCP packet parser/builder with checksums + unit tests (IPv6 postponed)
- [x] `DnsVpnService`: TUN with virtual DNS, /32 routes, `allowFamily(AF_INET6)`, own app excluded, foreground notification with "Turn off"
- [x] `DnsForwarder`: one coroutine per query, 3 s timeout, answer written back into the TUN, SERVFAIL on errors
- [x] `DohResolver` (OkHttp, RFC 8484 POST `application/dns-message`, bootstrap DNS) + MockWebServer tests
- [x] `UdpResolver` with `protect()`ed socket, dropping answers with a different ID
- [x] `/32` routes into the TUN for the **public** DNS servers of the underlying network (Fire OS adds them to the VPN)
- [x] RST to TCP connections to intercepted DNS addresses; ICMP dropped
- [x] `DnsCache` LRU with TTL and ID rewrite + tests
- [x] `VpnController`: start/stop, hot profile switch, state as `StateFlow`
- [x] Onboarding + `VpnService.prepare()` consent + permission-denied handling
- [x] Home wired to the ViewModel: toggle, profile selection, real state
- [x] Profile editor: IP→UDP / `https://`→DoH detection, validation, save/edit/delete
- [x] `FallbackPolicy` + `ChainResolver` over the network DNS (LinkProperties/NetworkCallback), banner + notification, retry every 60 s
- [x] `BootReceiver` autostart when consent is still valid (also after an app update)
- [x] `onRevoke()` handling (another VPN → "Another VPN took over" error)
- [x] Network changes: fallback DNS updated, TUN re-created when the public DNS servers change
- [x] Verified on device: real DNS switch (AdGuard blocks `doubleclick.net`, Cloudflare does not), hot profile switch, streaming, turning off, notification, fallback with an unreachable custom DNS
- [ ] Verify on device: reboot with the VPN on, network change, leak test with unique names (as in the spike)
- [x] Settings (start on boot, fallback) and About screen with disclaimer
- [ ] QR code to the repository on the About screen (nice-to-have)

## Milestone 4 — Polish & release
- [ ] Manual test checklist on every device: first launch, ON/OFF, profile switch, custom UDP and DoH DNS, reboot, unreachable upstream (fallback and recovery), another VPN, network change, standby, VoiceView
- [ ] Measure added latency and idle RAM/CPU on the weakest Stick
- [x] README with screenshots, Downloader install guide, FAQ (VPN slot, privacy), disclaimer
- [x] Test releases v0.9.0 – v0.9.3 through the signed release pipeline
- [x] Make the repository public and test the install from Downloader (code 8088747)
- [ ] Tag `v1.0.0` → Release → register a Downloader short code
- [ ] Announce on r/fireTV, r/Adguard, r/nextdns, XDA (privacy/ad-blocking positioning only)

## Milestone 5 — v1.1 (nice-to-have)
- [ ] Profile latency test with "ms" badges on the cards and the fastest highlighted
- [ ] Quick toggle/tile (check what Fire OS allows first)
- [ ] In-app language picker
- [ ] Import/export of custom profiles
- [ ] IPv6 virtual DNS for IPv6-only networks
