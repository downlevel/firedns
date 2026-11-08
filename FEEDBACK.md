# User feedback — FireDNS

Requests and suggestions collected from users, to drive future work. When an item is done, tick it and link the commit or release; when it is rejected, keep it with the reason so it is not reopened.

Sources:
- **[R1]** Reddit, r/firetvstick — [launch post](https://www.reddit.com/r/firetvstick/comments/1xp97fk/i_made_a_free_opensource_app_to_switch_dns_on/) (November 2026)
- **[P1]** GitHub — [Gathin23/cleanyourtv PR #3448](https://github.com/Gathin23/cleanyourtv/pull/3448), linked in the R1 thread by u/mishrah10 as the setup they used (GitHub author `mishrah49`, presumably the same person). A Python script that sets Android's Private DNS (DoT, `hostname` mode) to a NextDNS profile over ADB. Closed unmerged on 2026-11-07 because the maintainer keeps DNS tools out of that project. Not a request to FireDNS: shared as a reference.

## Feature requests

- [ ] **Exclude apps from FireDNS (per-app allowlist)** — [#1](https://github.com/downlevel/firedns/issues/1) — [R1] Let the user pick installed and system apps that keep using the network DNS. Android supports it via `VpnService.Builder.addDisallowedApplication` (today only FireDNS excludes itself, `DnsVpnService.kt`). Needs: an app picker usable with the D-pad, persisted package list, restarting the tunnel on change.
- [ ] **Make NextDNS easier to find** — [R1] A user asked to "add NextDNS" without realising custom DoH already supports it. Options: a NextDNS template in *Add DNS* that only asks for the configuration ID, or a clearer hint on the Home screen.
- [ ] **Tell users when an update is available** — [R1] A user asked how to know there is a new version. The app has no update check today: users have to look at GitHub Releases and reinstall the APK. Options: compare the installed version with the latest GitHub release and show a notice in the app, or at least link to the releases page from Settings.

## Compatibility

- [ ] **Google TV / Android TV** — [#2](https://github.com/downlevel/firedns/issues/2) — [R1] Asked whether it works on Google TV. The app should work (minSdk 28, `LEANBACK_LAUNCHER`) but it has never been tested. Test on a Google TV device or emulator, then update *Supported devices* in the README.
- [ ] **Other Android TV boxes (Xiaomi Box, KD 9 stick)** — [R1] Asked whether it runs on them. Same status as Google TV: it should work on Android 9+, but it has never been tested. Track together with #2.
- [ ] **Fire TV Stick 4K: install fails** — [R1] A user gets "package damaged or incompatible". Possible causes: Fire OS 6 on the original 2018 model (minSdk is 28, so Fire OS 7+ is required) or an incomplete download. Waiting for the user's Fire OS version. If it is 6.x, state it clearly under *Supported devices* in the README.
- [x] **Pi-hole** — [R1] A user confirmed it works with a Pi-hole as a custom DNS.

## Documentation / FAQ

Questions that came up repeatedly. Ticked items are in the README FAQ (onboarding still to consider).

- [x] **"Does it block YouTube / Prime Video / Hotstar ads?"** — No. Those ads come from the same servers as the video, so no DNS-based blocker can block them. This was the most frequent question.
- [x] **"Does it need to run in the background / can I force close it?"** — It must stay running (foreground service, starts on boot). Force stopping it reverts to the network DNS.
- [x] **"Fire OS has a forced extra DNS, so DNS blocking doesn't work"** — Explain what FireDNS covers: the network's public DNS servers are routed into the tunnel too (see ARCHITECTURE.md). Also explain what it does not cover: apps with their own built-in DoH, and local DNS addresses (router, Pi-hole), which stay outside the tunnel by design.
- [x] **"Can I use it with my VPN?"** — No, Android allows only one VPN at a time. Already in the FAQ; consider mentioning it in onboarding too.
- [x] **Pi-hole vs FireDNS** — They are complementary: a Pi-hole can be added as a custom DNS in FireDNS.
- [ ] **Verify the "no DNS blocker can stop YouTube / Hotstar ads" claim** — [R1] Two users report that NextDNS now blocks YouTube ads (one also mentions ControlD), and one says Hotstar ads are blocked once the Fire TV's fallback to 8.8.8.8 or the ISP DNS is disabled. Not confirmed: no official NextDNS source found, and public sources mostly say the opposite. Test on a Fire TV with NextDNS as a custom DoH address, then correct the README FAQ if it holds up. Update: the Hotstar user (u/mishrah10) says it works on their Fire TV and linked [P1], so the test was most likely run with Private DNS over ADB, not with FireDNS. [P1] also states that NextDNS began rolling out a YouTube ad option as a beta in November 2026, without a source. [P1] documents a blocklisted domain resolving to `127.0.0.1` on a Fire TV Stick 3rd gen (Fire OS 7.7), not Hotstar or YouTube ads being blocked. Asking which provider and blocklist they used. To test: FireDNS with a NextDNS profile and automatic fallback off, then open Hotstar and check the NextDNS logs.
- [ ] **"Is a DNS address hardcoded by Fire OS covered?"** — [R1] Related to the Hotstar report. FireDNS routes into the tunnel only the public DNS servers reported by the network, so an address that Fire OS uses without reporting it may bypass the profile. Test it and document the result. According to [P1], on Fire OS a static IP with only DNS 1 filled in gets `8.8.8.8` as DNS 2, and Private DNS in `hostname` mode avoids any fallback to another resolver. Not yet verified whether that `8.8.8.8` is reported by the network, which would put it inside the FireDNS tunnel.
- [ ] **README says a VPN is the only way without root** — [P1] shows that Private DNS can be set over ADB, which needs no root. Change the *Why does it need VPN permission?* answer to "without root or ADB", as in ARCHITECTURE.md.
- [ ] **Private DNS and FireDNS active together** — [P1] Untested. Find out how they interact, and warn users against combining them if they conflict, before linking any ADB script from the README.
- [x] **"What is the difference from entering the DNS manually?"** — [R1] Same DNS, but one click instead of reconfiguring the Wi-Fi by hand, plus DNS-over-HTTPS and automatic fallback.
- [x] **"What do I gain by changing DNS?"** — [R1] Asked twice in plain terms. Privacy, ad and tracker blocking in many apps and websites, malware protection, sometimes faster loading.
- [x] **"Does it help with streaming or ISP throttling?"** — [R1] It does not change the video traffic. It can help when the ISP interferes at the DNS level or DNS picks a poor streaming server, not when the ISP slows the traffic itself.
- [x] **"Does it block Fire TV home screen ads?"** — [R1] Not tested. It depends on the provider's blocklist, and blocking Amazon domains may break parts of the home screen.
- [ ] **"How do I update the app?"** — [R1] Download the latest APK and install it over the current version. See the update notice under *Feature requests*.

## Rejected / out of scope

- **Private DNS through ADB inside FireDNS** — [P1] Already out of scope in PRD.md. It needs ADB, supports DoT providers only and has no fallback, so an unreachable resolver leaves the TV without internet. Earlier research also found reports of total network loss on the Fire TV Stick 4K Max 2nd gen. [P1] was tested only on a Fire TV Stick 3rd gen.
- **Blocking Amazon updates** — [R1] Not a built-in feature: update domains change often and blocking them also blocks security patches. Users who want it can use a custom NextDNS/Pi-hole with their own blocklist.
- **Blocking ads inside streaming apps** — Considered not technically possible at the DNS level (see FAQ above). Being rechecked after user reports about NextDNS and Hotstar; any such filtering would come from the DNS provider, not from a FireDNS feature.
