# Claude Design prompt — FireDNS

> The prompt used at kickoff to prototype the interface in [Claude Design](https://claude.ai/design), before any UI code existed. Kept for reference; the implemented design is described in [DESIGN.md](DESIGN.md).

---

Create an interface prototype for **FireDNS**, an Android app for **Fire TV** that changes the DNS in one click from the remote (through a local VPN), choosing among privacy, ad-blocking and speed oriented providers (Cloudflare, Google, AdGuard DNS, Quad9) or a custom DNS. A "10-foot" TV interface, 1920×1080 canvas (960×540 dp), navigated **only with the remote's D-pad**.

**Screens to prototype** (in priority order):
1. **Home** — "FireDNS" logo top left, gear icon top right. Upper half: big ON/OFF button (pill or ~160 dp circle) and next to it the state ("Active" at 40 sp), the active profile name ("AdGuard DNS") and a protocol chip ("DoH"). Lower half: "Profiles" section with a horizontal row of cards (~200×112 dp): Cloudflare, Google, AdGuard DNS, Quad9, two custom profiles ("NextDNS home", "Pi-hole") and a dashed "+ Add DNS" card. The active card has an orange border and a check; the focused card is scaled to 1.05.
2. **Home — state variants**: VPN off (gray outline button, "Off"); fallback (amber banner "DNS unreachable — using the network DNS"); error (red banner "Another VPN took over" with a "Retry" button); starting (spinner in the button, "Starting…").
3. **Custom profile editor** — two columns: on the left the "Name" and "Address" fields (hint: "IP (e.g. 192.168.1.2) or DoH URL (https://…)"), an automatically detected protocol chip (UDP/DoH), an inline validation message; on the right a short help box with NextDNS and Pi-hole examples. "Save" (primary) and "Cancel" at the bottom.
4. **Onboarding (first launch)** — centered panel: logo, title "Faster, private DNS in one click", three bullets (what it does; why the VPN permission is needed; "no data leaves your device except DNS queries to the provider you choose"), focused "Continue" button.
5. **Settings** — full-width vertical list: "Start on boot" (switch), "Automatic fallback to the network DNS" (switch), "About" (chevron).
6. **About** — name and version, QR code to the GitHub repository with the short URL, license, disclaimer "Meant for privacy, ad blocking and speed. Use it in compliance with the laws of your country."
7. **Context menu and dialog** — Edit/Delete menu on a custom card; delete confirmation dialog.

**Navigation**: no side menu. Home is the hub: the gear opens Settings (and from there About), the "+ Add" card opens the Editor, the remote's Menu key on a custom card opens Edit/Delete. Back always returns to the previous screen. Initial focus on Home is the ON/OFF button.

**Visual style**:
- Palette: graphite background `#121315`; surfaces `#1E2024`, focused `#2A2D32`; "fire" orange accent `#FF7A1A` (secondary `#FFB347` for the ON state glow); text `#F2F2F3` / secondary `#A9ADB4`; amber warning `#F5B400`; error `#FF5A5F`; success `#4CD08A` only for the "active" check.
- Typography: Roboto; display 40 sp, section titles 24 sp, card titles 20 sp, body 18 sp, never below 14 sp.
- Theme: dark only.
- Visual tone: minimal and technical, simple line icons, no decoration.
- Reference design system: Material 3 for TV (Compose for TV), customized with the palette.

**Recurring components**: PowerToggle (off / starting / on / error), StatusHeader with protocol chip, ProfileCard, dashed AddProfileCard, Banner (warning/error), SettingsRow with switch, ContextMenu, ConfirmDialog. Uniform focus state: 1.05 scale, 3 dp orange border, lighter surface.

**Responsive**: 16:9 TV only; respect overscan safe margins of 48 dp horizontal and 27 dp vertical; the profile row scrolls horizontally.

**States to show**: empty (no custom profiles → only the "+ Add DNS" card with the subtitle "Add NextDNS, Pi-hole…"), loading (Starting…), error (VPN permission denied, VPN revoked, invalid address in the editor), fallback.

**Accessibility**: everything reachable with the D-pad in a predictable focus order; visible focus not based on color alone; WCAG AA contrast; text readable from 3 meters.

Generate high-fidelity screens, consistent with each other and ready to iterate.
