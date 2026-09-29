# FireDNS — local VPN spike

A **throwaway** prototype (Milestone 0 in `../TASKS.md`). Its only purpose is to verify, on a real Fire TV, FireDNS's riskiest technical assumption:

> A local VPN (VpnService) that routes **only** the IP of a virtual DNS works on Fire OS, all other traffic stays outside the tunnel, and the app can exclude itself from the tunnel.

This is not code to reuse as is: no Compose, no DoH, no persistence. Queries go over UDP to `1.1.1.1`.

## How it works
```
any app ── DNS query ──► 10.111.222.1:53 (virtual DNS, only route in the TUN)
                                 │
                     SpikeVpnService reads the packet from the TUN
                                 │  protected UDP socket (outside the tunnel)
                                 ▼
                              1.1.1.1:53 ──► answer written back into the TUN
```
- `addRoute(10.111.222.1/32)` + `addDnsServer(10.111.222.1)`: only DNS enters the tunnel.
- `addDisallowedApplication(packageName)`: the spike's own traffic does not re-enter the tunnel.
- Foreground service with a notification, `foregroundServiceType="specialUse"` (for Fire OS 14 / API 34+).

## Install
Prebuilt APK: `firedns-spike.apk` (debug, minSdk 28).

1. On the Fire TV: *Settings → My Fire TV → Developer options* → enable **ADB debugging** (and *Apps from unknown sources* if asked). The IP is under *About → Network*.
2. From a computer:
   ```bash
   adb connect <FIRE_TV_IP>:5555        # accept the prompt on the TV
   adb install -r firedns-spike.apk
   adb logcat -s FireDnsSpike           # (in another terminal) live log
   ```
3. On the TV open **FireDNS Spike** from *Your Apps*.

To rebuild: `./gradlew assembleDebug` (needs JDK 17 + Android SDK platform 35) or open the folder in Android Studio.

## Test procedure
Record the results in the table at the bottom, **for each device**.

| # | Action | Expected result |
|---|---|---|
| 1 | Open the app and press **Network info** (VPN off) | Log with model/API, Wi-Fi/Ethernet DNS servers, Private DNS state. Write them down. |
| 2 | **Start VPN** → accept the system dialog | The VPN consent dialog appears; then "Tunnel up…", notification and key icon. |
| 3 | Open Silk / YouTube / Prime Video and browse for a minute, then go back to the app | The **query** counter grows, lines like `domain A → NOERROR in N ms`. **No network outage.** |
| 4 | From the computer: `adb shell ping -c1 spike-test.example.com` | The log shows `spike-test.example.com A → NXDOMAIN`: system queries go through the tunnel too. |
| 5 | Press **App exclusion test** | `✓ Exclusion OK …` (`✗ EXCLUSION NOT HONORED` would be a serious problem, see below). |
| 6 | Check **non-DNS packets in tunnel** after 3-4 | Ideally 0. Packets `proto=6 dst=10.111.222.1` (TCP to the virtual DNS: DNS over TCP / opportunistic DoT) are acceptable; packets to **other IPs** = partial routes are not honored. |
| 7 | Stream a video for 5 minutes | No unusual buffering; reasonable average DNS latency (< 50-80 ms). |
| 8 | **Network info** with the VPN on | A "VPN" network shows up with DNS `10.111.222.1`, `default=true`. |
| 9 | Put the TV to sleep (hold Home → Sleep), wake it after a few minutes | The VPN is still on and queries resume. |
| 10 | Change network (Wi-Fi ↔ Ethernet or another SSID), if possible | Queries keep flowing without restarting the VPN. |
| 11 | **Stop VPN** | "Tunnel closed", notification gone, browsing works again with the network DNS. |
| 12 | Start the VPN again, then reboot the Fire TV | After the reboot the VPN does **not** come back (the spike has no boot receiver) but consent is kept: pressing **Start VPN** does not show the dialog again. |

### How to read the results
- **3, 4, 5, 6 OK** → the local VPN approach is confirmed: go ahead with Milestone 1 as described in `ARCHITECTURE.md`.
- **Network outage at step 3** (as reported for Private DNS on the 4K Max 2nd gen) → record model/Fire OS and the full `adb logcat`; find out whether it is the TUN, the virtual DNS or IPv6 (IPv6-only/dual-stack networks).
- **5 fails** → the real app must `protect()` every socket, DoH included (OkHttp with a custom `SocketFactory`), instead of relying on `addDisallowedApplication`.
- **6 shows traffic to other IPs** → Fire OS routes more than expected: review the routes.
- **Frequent `✗ SocketTimeoutException` errors** → slow upstream or network: useful to tune timeouts and fallback.
- **Lines with `(over MTU!)`** → large DNS answers: the real app would need truncation (TC flag) or a larger MTU.

## Results

| Device | Fire OS / API | 1-2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Fire TV Stick HD (1st gen, 2024) | Fire OS 7.7.1.6 (Android 9, API 28) | ✓ | ✓ | ✓ 20/20 | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✓ | ✓ | 72/72 queries answered. The VPN network also lists the Wi-Fi's static DNS servers (`8.8.8.8`, `fe80::1`), but 20/20 queries with unique names went through the tunnel. The 15 non-DNS packets were all to `10.111.222.1`: TCP (`proto=6`) and ICMP (`proto=1`). Network change not tested. |
| | | | | | | | | | | | | | |

### Outcome (2026-09-29)
Assumption confirmed on Fire OS 7: the local VPN routing only DNS works, honors the app exclusion and partial routes, and survives streaming, sleep and reboot (VPN consent is kept). To be repeated on Fire OS 8. The resulting decisions are in `../ARCHITECTURE.md` (extra Fire OS DNS servers, non-UDP traffic).
