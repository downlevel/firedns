# Design — FireDNS

A "10-foot" TV interface driven by the Fire TV remote (D-pad, OK, Back, Home, Menu). Reference canvas: **960×540 dp** (1080p at xhdpi).

## Structure and navigation
### Screens
- **Onboarding** — first launch only: explains what FireDNS does and why the VPN permission is needed, then opens the system prompt.
- **Home** — current state, big ON/OFF button, row of profile cards (built-in + custom + "Add").
- **Profile editor** — create/edit a custom profile (name + IP address or DoH URL).
- **Settings** — start on boot, fallback, link to About.
- **About** — version, repository URL, license, lawful-use disclaimer.

### Entry points
There is no side menu: the Home screen is the hub.
- Gear icon (top right on Home) → Settings
- "+ Add DNS" card → Profile editor (new)
- Remote **Menu** key (☰) or long press on a custom card → context menu: Edit / Delete
- Settings → "About" row → About

### Sitemap
```
Onboarding (first launch only)
      ↓
Home
 ├── Profile editor (new / edit)
 └── Settings
      └── About
```

### Main flows
- **First launch**: Onboarding → "Continue" → system VPN dialog → (allow) → Home with the VPN on, using the default profile (Cloudflare).
- **Turn on/off**: Home → initial focus on the ON/OFF button → OK.
- **Switch DNS**: Home → ↓ to the profile row → ←/→ → OK on a card → that profile becomes active (applied immediately if the VPN is on).
- **Add a custom DNS**: Home → "+ Add DNS" card → Editor → name, address → "Save" → back to Home.
- **Edit/delete**: Home → focus a custom card → Menu key → Edit (Editor) / Delete (confirmation dialog).
- **Fallback**: the upstream stops answering → amber banner on Home + notification → the banner disappears when it is back.
- **Permission denied / another VPN**: Home shows the error state with a "Retry" button (opens the permission prompt again).

## Visual style
- **Palette** (dark theme only):
  - Background: graphite `#121315`
  - Surface (cards, panels): `#1E2024`; raised/focused surface: `#2A2D32`
  - Primary "fire" accent: orange `#FF7A1A` (text on the accent: `#1A0E05`)
  - Secondary accent (ON state, focused primary button): `#FFB347`
  - Text primary `#F2F2F3`, secondary `#A9ADB4`, disabled `#6B6F76`
  - Warning/fallback: amber `#F5B400`; error: `#FF5A5F`; success, used sparingly for the "active" check: `#4CD08A`
- **Typography**: Roboto (Fire OS system font) through the `tv-material` type scale, with TV minimums:
  - Display (state "Active/Off"): 40 sp, SemiBold
  - Section titles: 22–24 sp, Medium
  - Card title: 20 sp, Medium; card subtitle: 16 sp
  - Body: 18 sp; never below 14 sp
- **Spacing and grid**: 4/8/12/16/24/32/48 dp scale; overscan safe margins **48 dp horizontal, 27 dp vertical**; 16 dp gap between cards.
- **Theme**: dark only.
- **Design system**: Material 3 for TV (`androidx.tv.material3`), customized with the palette above. **Minimal and technical** tone: few words, simple icons, no decoration.
- **App icon**: orange flame. On the Fire OS launcher it is a flame on a transparent background (the launcher draws its own gray tile); the 16:9 banner is flame + "Fire**DNS**" wordmark on graphite.

## Components and layout
### Main screen layouts
- **Onboarding**: centered panel (~620 dp wide); flame logo on top, title "Faster, private DNS in one click", 3 bullets (what it does, why the VPN, "no data leaves your device except DNS queries to the provider you choose"), primary "Continue" button with initial focus.
- **Home**:
  - Top: flame + "FireDNS" on the left; settings gear on the right.
  - **State block**: big circular ON/OFF button (160 dp) on the left; on the right the state ("Active" / "Off" / "Starting…" / "Fallback" / "Error"), the active profile name, a protocol badge (DoH / UDP) and its host. When ON the button is orange; when OFF it has a gray outline.
  - **Banner** (conditional) below the state: fallback (amber) or error (red) with a short message and an action.
  - **"Profiles" row**: section title + horizontal `LazyRow` of cards (200×116 dp). Order: built-in (Cloudflare, Google, AdGuard, Quad9), custom, "+ Add DNS" card.
- **Profile editor**: two columns. Left: the form (Name, Address with hint "IP (e.g. 192.168.1.2) or DoH URL (https://…)", live detected-protocol badge, validation message), "Save" (primary) and "Cancel" at the bottom. Right: a short help panel with examples (NextDNS, Pi-hole). Inputs use the Fire OS on-screen keyboard.
- **Settings**: full-width vertical rows with a switch on the right: "Start on boot", "Automatic fallback"; "About" row with a chevron.
- **About**: name, version, repository URL, license, disclaimer ("FireDNS is meant for privacy, ad blocking and speed. Use it in compliance with the laws of your country.").

### Recurring components
- **PowerToggle** — big ON/OFF button with off / starting (spinner) / on / error states.
- **StatusHeader** — state text + profile + protocol badge + host.
- **ProfileCard** — colored initial avatar, protocol badge, name, host or IP; "active" indicator (orange border + green check).
- **AddProfileCard** — dashed card with "+"; in the empty state it also shows "NextDNS, Pi-hole…".
- **ProtocolBadge** — "DoH" chip (soft orange) / "UDP" chip (gray).
- **Banner** — warning/error variants with an optional action.
- **SettingsRow / FireSwitch** — row with title, description and a switch or chevron.
- **ProfileMenuDialog** — Edit / Delete, opened with the Menu key or a long press.
- **ConfirmDialog** — TV dialog (delete profile), initial focus on "Cancel".
- **FireButton** — primary / secondary / danger pill buttons.
- **FireTextField** — TV-style text field (tv-material has none).
- **Focus state** (every focusable element): 1.05 scale, 3 dp orange border, `#2A2D32` surface.

### Responsive
16:9 TV only. dp layout that scales from 720p to 4K; no phone/tablet variants. Text and cards stay within the overscan safe area. With many profiles the row scrolls horizontally keeping the focused card visible.

## Accessibility and content
- **Accessibility**:
  - Everything reachable with the D-pad in a predictable focus order (top→bottom, left→right); initial focus on the PowerToggle.
  - Focus is always visible and does not rely on color alone (scale + border).
  - At least WCAG AA contrast (4.5:1 for text, 3:1 for components).
  - `contentDescription`/semantics on buttons, cards and switches, compatible with **VoiceView**.
  - Back always returns to the previous screen; on Home it exits the app (the VPN stays on).
- **Microcopy / tone**: short, direct, technical but clear. EN + IT. Examples: "FireDNS active · AdGuard DNS", "DNS unreachable — using the network DNS", "Another VPN took over". No references to bypassing blocks.
- **UI states**:
  - *Loading*: PowerToggle with a spinner, "Starting…"; the editor validates instantly (no network calls).
  - *Empty*: no custom profiles → only the "+ Add DNS" card, with the "NextDNS, Pi-hole…" subtitle.
  - *Error*: VPN permission denied; VPN revoked by another app; invalid address in the editor (inline message below the field).
  - *Fallback*: amber banner + "Fallback" state in the StatusHeader + system notification.

## Open questions
- Provider logos on the cards: official logos (check usage licenses) or colored initials (current) — TBD.
- Position and shape of the quick toggle (v1.1) — TBD.
- QR code to the repository on the About screen — nice-to-have.
