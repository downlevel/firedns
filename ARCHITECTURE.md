# Architecture — FireDNS

## Tech stack
| Area | Choice | Why |
|---|---|---|
| Language/runtime | Kotlin (JVM 17), Coroutines + Flow | Modern Android standard. |
| Platform | Android app, minSdk 28 (Fire OS 7+), targetSdk 35 | Covers 4K Max, 4K Plus, Cube, Stick HD 2024 and Fire OS TVs. |
| UI | Jetpack Compose + **Compose for TV** (`androidx.tv:tv-material`) | TV components with D-pad focus handling built in. |
| DNS mechanism | Local **VpnService** that routes only DNS addresses | The only way without root/ADB that works on Android 10+; all other traffic bypasses the tunnel. |
| Upstream DNS | **DoH** (RFC 8484) for built-in profiles and `https://` URLs; **UDP/53** for custom IPs | DoH encrypts queries (privacy); UDP for Pi-hole/local DNS compatibility. |
| HTTP client | OkHttp (HTTP/2, connection pool) | Mature, reuses connections → low DoH latency. |
| DNS/IP parsing | Small in-house parser (IPv4 + UDP/TCP headers); DNS payload mostly opaque, only ID/TTL/question read | No heavy dependencies; only needed to rebuild packets and for the cache. |
| Storage | Jetpack **DataStore** + kotlinx.serialization (JSON) | A few profiles and settings: no database needed. |
| Dependency injection | Manual (one `AppContainer`) | Small app; Hilt would add complexity without value. |
| Backend | None | 100% local app, zero cost. |
| Auth | None | No accounts. |
| Hosting/Deploy | **GitHub Releases** (signed APK) + **Downloader** | The standard free sideloading channel for Fire TV. |
| CI/CD | **GitHub Actions** | Lint/tests on every push and PR, signed release on tags. |
| Code quality | ktlint + Android Lint | Consistent style, readable code for visitors. |

## Components
```
┌──────────────────────── Fire TV (Fire OS) ────────────────────────┐
│                                                                   │
│  Any app ── DNS query ──► virtual DNS 10.111.222.1                │
│                                    │ (+ public network DNS /32)   │
│                                    ▼                              │
│  ┌──────────────── FireDNS ─────────────────────────────────┐     │
│  │ DnsVpnService (foreground)                               │     │
│  │   TUN fd ─► read loop ─► DnsForwarder ─► TUN write       │     │
│  │                            │     ▲                       │     │
│  │                        DnsCache  │   FallbackPolicy      │     │
│  │                            ▼     │                       │     │
│  │               DnsResolver (active profile)               │     │
│  │                ├─ DohResolver (OkHttp, bootstrap IPs)    │     │
│  │                └─ UdpResolver (protect()ed socket)       │     │
│  │               ChainResolver (network DNS, fallback)      │     │
│  │                                                          │     │
│  │ Compose for TV UI ◄─► HomeViewModel ◄─► ProfileRepository│     │
│  │                          │              SettingsRepository│    │
│  │                          ▼                 (DataStore)   │     │
│  │                 VpnController (start/stop)               │     │
│  │ BootReceiver ──► VpnController                           │     │
│  └──────────────────────────────────────────────────────────┘     │
│                         │ upstream traffic (outside the tunnel)   │
└─────────────────────────┼─────────────────────────────────────────┘
                          ▼
     Cloudflare / Google / AdGuard / Quad9 (DoH)  ·  custom DNS (UDP/DoH)
```

### Tunnel details
- `VpnService.Builder`:
  - `addAddress(10.111.222.2, 32)`: IPv4-only tunnel (IPv6 inside the tunnel postponed, see Open questions).
  - `addDnsServer(10.111.222.1)`: virtual IP, no real server behind it.
  - `addRoute(10.111.222.1, 32)` plus one `/32` route per public network DNS (see below): **only** DNS addresses enter the tunnel, everything else goes direct.
  - `allowFamily(AF_INET6)`: with no IPv6 address in the VPN, Android would block **all** IPv6 traffic of the apps; this lets it out normally.
  - `addDisallowedApplication(packageName)`: FireDNS's own upstream traffic does not re-enter the tunnel (no loops). UDP sockets are also `protect()`ed.
  - The TUN fd is non-blocking on API 28: a dedicated coroutine uses `poll()` with a 500 ms timeout + `read()`; MTU 1500.
- **Forwarder**: reads IP/UDP packets to port 53 of an intercepted address, extracts the DNS payload and sends it upstream **concurrently** (one coroutine per query, 3 s timeout), then writes the answer back into the TUN with source/destination swapped and checksums recomputed. TCP to an intercepted address gets an immediate RST; ICMP and everything else is dropped.
- **Cache**: in-memory LRU (1000 entries), key = (qname, qtype, qclass), expiry = min TTL of the answer (max 1 h); on hit it rewrites the query ID and decreases the TTLs. Cleared on every profile switch. Errors and truncated answers are not cached.
- **Hot profile switch**: the service collects the active profile and swaps the `DnsResolver` without re-creating the TUN.
- **Fallback**: `FallbackPolicy` counts consecutive failures of the chosen DNS (exceptions: timeout, HTTP ≠ 200). Below the threshold (3) the client gets SERVFAIL; at the threshold queries go to a `ChainResolver` over the network DNS servers (IPv4 and global IPv6, router/Pi-hole included, read from `LinkProperties`, 2 s timeout each). In fallback the chosen DNS is probed with a real query at most every 60 s; the first success switches back. Network DNS answers are **not** cached. The `Fallback` state shows on the Home screen (amber banner) and in the notification. Turning off "Automatic fallback" returns to the chosen DNS immediately (SERVFAIL on errors).
- **Network changes**: `registerDefaultNetworkCallback` (the app is excluded from the VPN, so it sees the real network). On new DNS servers, debounced by 1 s: updates the fallback DNS, evicts the DoH connection pool and resets fallback; if the public DNS addresses to intercept change, it calls `establish()` again with the new routes, moves reading to the new fd and closes the old one.
- **Foreground service**: persistent notification ("FireDNS active · AdGuard DNS") with a "Turn off" action; `foregroundServiceType="specialUse"` for Android 14+.
- **Boot and updates**: `BootReceiver` on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`. Turns the VPN back on if the user left it on (`vpnDesiredOn`); at boot only with "Start on boot" enabled, after an update always (`Autostart.shouldStart`). Requires a still-valid VPN consent (`VpnService.prepare() == null`). `vpnDesiredOn` also becomes `false` on "Turn off" from the notification and when another VPN takes over (`onRevoke`).
- **Revocation / another VPN**: `onRevoke()` → "Another VPN took over" error on the Home screen.
- **Extra DNS servers added by Fire OS** (found by the spike): Fire OS also adds the Wi-Fi's static DNS servers (e.g. `8.8.8.8`, `fe80::1`) to the VPN network, next to the virtual DNS. The resolver always uses the first one (20/20 queries went through the tunnel), but if the virtual DNS failed, queries would bypass the app. **Decision**: `DnsVpnService` reads the network's IPv4 DNS servers before `establish()` and routes a `/32` into the TUN for every **public** address; the forwarder answers on behalf of the queried address. Local network addresses (router, Pi-hole: `isSiteLocalAddress`) stay **outside**, because routing them would also swallow all other traffic to them: if the system falls back to the router, the query leaves from there (accepted leak, equivalent to a fallback). Link-local IPv6 (`fe80::/10`) cannot be routed.
- **Non-UDP traffic to the virtual DNS** (found by the spike): TCP packets (`proto=6`, DNS over TCP / DoT probes) and ICMP (`proto=1`, probably port unreachable for answers arriving after the client timeout) reach the TUN. **Decision**: TCP gets a RST so the system does not wait for a timeout; ICMP is silently dropped.

### Built-in profiles
| Profile | DoH URL | Bootstrap IPs |
|---|---|---|
| Cloudflare | `https://cloudflare-dns.com/dns-query` | 1.1.1.1, 1.0.0.1 |
| Google | `https://dns.google/dns-query` | 8.8.8.8, 8.8.4.4 |
| AdGuard DNS | `https://dns.adguard-dns.com/dns-query` | 94.140.14.14, 94.140.15.15 |
| Quad9 | `https://dns.quad9.net/dns-query` | 9.9.9.9, 149.112.112.112 |

The bootstrap IPs feed a custom `okhttp3.Dns`: the DoH hostname resolves without any DNS lookup. For custom DoH URLs the hostname is resolved with the system DNS (the app is excluded from the tunnel).

### Code layout (single `app` module)
```
dev.downlevel.firedns          (applicationId; debug: dev.downlevel.firedns.debug)
├── data/        DnsProfile, DnsAddress, Presets, Settings, SettingsSerializer, ProfileRepository, SettingsRepository
├── dns/         Ipv4, DnsMessage, Bytes, DnsCache, DnsForwarder, FallbackPolicy
│   └── resolver/  DnsResolver, DohResolver, UdpResolver, ChainResolver, ResolverFactory
├── vpn/         DnsVpnService, VpnController/VpnState, AndroidVpnController, FakeVpnController, BootReceiver, Autostart
├── ui/          FireDnsRoot, navigation/, theme/, components/, home/, editor/, settings/, onboarding/, Previews
└── AppContainer.kt, FireDnsApp.kt, MainActivity.kt
```

## Data schema
DataStore JSON (`firedns_settings.json`):
```json
{
  "schemaVersion": 1,
  "activeProfileId": "preset:adguard",
  "vpnDesiredOn": true,
  "autostartOnBoot": true,
  "fallbackEnabled": true,
  "onboardingDone": true,
  "customProfiles": [
    {
      "id": "uuid",
      "name": "NextDNS home",
      "protocol": "DOH",            // DOH | UDP
      "dohUrl": "https://dns.nextdns.io/abc123",
      "servers": []                  // for UDP: ["192.168.1.2"]
    }
  ]
}
```
- Built-in profiles have ids `preset:<name>` and live in code (`Presets.kt`).
- Custom input validation: IPv4/IPv6 → UDP; `https://` + valid host → DoH; anything else → validation error. Purely syntactic, no DNS lookups.
- `schemaVersion` for future migrations; a corrupted file is replaced with defaults.

## Deploy
- **Build types**: `debug` (applicationId suffix `.debug`, installable side by side, signed with the committed `debug.keystore`) and `release` (R8 minify + resource shrinking + signing).
- **GitHub Actions pipelines**:
  - `ci.yml` — on push/PR: `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`, debug APK as artifact.
  - `release.yml` — on `v*` tags: decodes and checks the keystore from secrets, `testReleaseUnitTest assembleRelease`, renames the output to `firedns.apk` and creates a GitHub Release with generated notes.
- **Secrets**: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- **Stable Downloader URL**: `https://github.com/downlevel/firedns/releases/latest/download/firedns.apk` (works only with a public repository); a short Downloader code can point to it.
- **Versioning**: SemVer; `versionName` from the tag (`v1.2.3` → `1.2.3`, otherwise `0.1.0-dev`), `versionCode` = `GITHUB_RUN_NUMBER` (1 locally).
- **Signing**: `signingConfigs.release` is created only when `FIREDNS_KEYSTORE` is set; locally `assembleRelease` produces an unsigned APK.
- **TV manifest**: `LEANBACK_LAUNCHER` on a dedicated `activity-alias`, 320×180 `android:banner`, `uses-feature android.software.leanback required=false`, `android.hardware.touchscreen required=false`.
- **Launcher icon on Fire OS 7** (verified on Fire TV Stick HD): for sideloaded apps the Amazon launcher ignores `android:banner` and the alias icon, and shows the application icon centered on a `#5A5A5A` gray tile. That is why `ic_launcher` is a PNG flame on a transparent background (the same technique Kodi uses). The launcher **caches icons by package name, even across uninstalls**: icon changes show up late (first on Home, then in "Your apps"); when testing, reboot and be patient rather than drawing conclusions from the first attempt.

## Testing & observability
- **Unit tests** (JVM, 61): IPv4/UDP/TCP packet builder and parser with checksums, DNS messages, DnsCache (TTL, ID rewrite, LRU), DnsForwarder (cache, SERVFAIL, RST, fallback), FallbackPolicy, resolvers (DoH with MockWebServer, UDP with a local socket, chain), profile input validation, DataStore serialization and repositories, editor form, navigation, autostart rules.
- **UI**: Compose previews for every state in `ui/Previews.kt`; no automated UI tests yet.
- **Manual device tests**: checklist in TASKS.md; done on Fire OS 7 (Fire TV Stick HD). Still to do: Fire OS 8 (4K Max 2nd gen), network change.
- **Observability**: no telemetry or remote crash reporting (privacy choice). Errors are logged to logcat with the `FireDNS` tag.

## Open questions
- IPv6: the tunnel intercepts IPv4 DNS only; on IPv6-only networks queries to IPv6 DNS servers would not go through the app — TBD (add an IPv6 virtual DNS).
- Does Fire OS honor `addDisallowedApplication` and partial routes on every model? Confirmed on Fire OS 7 (spike and app), to be verified on Fire OS 8.
- `foregroundServiceType` behavior on Fire OS 14 — to be verified.
