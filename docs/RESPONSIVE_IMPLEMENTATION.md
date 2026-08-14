# Responsive implementation — 3.3.0

Date: 2026-07-15

> **3.4.0 extension:** the window policy below remains current. Version 3.4.0 adds the shared design system, adaptive status/action composition, selectable evidence surfaces, icon-based navigation, and screen-level UI/UX redesign documented in `UI_UX_DEEP_REDESIGN.md`.

## Scope

This release replaces the previous single width breakpoint with a shared responsive layout contract for mobile portrait, phone landscape, tablets, resizable desktop windows, and low-height windows. The implementation is shared in `commonMain`; platform-specific shells keep the same navigation and business behavior.

## Window model

`ResponsiveLayoutInfo` is calculated from the actual Compose window constraints and exposes:

- width class: compact, medium, expanded;
- orientation and low-height state;
- navigation mode: drawer, rail, sidebar;
- content, card, and page spacing;
- maximum readable content width;
- minimum field width used by responsive grids.

Current thresholds:

| Condition | Result |
|---|---|
| width below 600 dp | compact width |
| width 600–999 dp | medium width |
| width 1000 dp or greater | expanded width |
| height below 520 dp | low-height density |
| width at least 1100 dp and height at least 620 dp | persistent sidebar |
| width at least 600 dp | navigation rail |
| landscape width at least 560 dp | compact navigation rail |
| otherwise | modal navigation drawer |

The thresholds are implementation policy, not device-name detection. Foldables, split-screen windows, Stage Manager, DeX, and desktop resize are handled according to their current window bounds.

## Navigation behavior

### Compact portrait

- modal drawer;
- standard top app bar;
- full-width primary actions;
- single-column forms and cards when space is insufficient.

### Tablet and regular landscape

- persistent navigation rail;
- scrollable rail destinations;
- field/card grids calculated from available content width;
- master/detail-style panes when both panes satisfy their minimum width.

### Low-height landscape

- 72 dp icon-first navigation rail;
- scrollable destinations so no item is clipped;
- compact 48 dp top bar when the drawer shell is used;
- reduced vertical padding, card spacing, and multiline editor height;
- request/result panes placed side by side only when their real minimum widths fit.

### Large windows

- 264 dp persistent sidebar only when height is sufficient;
- rail fallback for short desktop windows;
- content centered and bounded to 1440 dp on ultra-wide displays;
- Desktop minimum window size of 560 × 360.

Android rail/sidebar shells apply `WindowInsets.safeDrawing`, preventing edge-to-edge content from being obscured by system bars or display cutouts. The mobile Scaffold and drawer retain their Material window-inset behavior. The shared adaptive content frame applies IME padding so focused fields and scrollable forms can move above the on-screen keyboard.

## Responsive primitives

Shared components in `ui/components/CommonComponents.kt` provide:

- `responsiveContentPadding`;
- `ResponsiveFields`;
- `ResponsiveButtonGrid`;
- `ResponsiveCardGrid`;
- `ResponsivePanes`;
- `ResponsiveActions`;
- adaptive page/section headers;
- low-height multiline editor sizing.

Column counts are derived from each container's actual width, not only from the global window width. This prevents a tablet navigation rail or desktop sidebar from causing fields to overflow the remaining content area.

## Screens covered

- Dashboard
- Discovery / LAN / SSDP / mDNS
- Network tools
- HTTP workbench
- WebSocket / MQTT / CoAP
- Payload tools
- Profiles / backup / history
- Saved devices
- Logs
- Guide
- Settings

Scrollable tab rows replace fixed tab rows on screens whose translated labels can exceed compact widths. Long forms and result collections remain vertically scrollable in all shell modes.

## Accessibility behavior

- icon-only low-height rail items retain semantic labels;
- menu controls retain at least 48 dp touch targets;
- hidden decorative glyph semantics are cleared;
- headings and live operation feedback remain exposed;
- navigation lists scroll instead of clipping when text scaling or window height reduces available space.

## Automated evidence

`ResponsiveLayoutTest` covers:

- 390 × 844 compact portrait;
- 844 × 390 low-height phone landscape;
- 600 × 960 tablet portrait;
- 1440 × 900 desktop sidebar;
- 1440 × 480 short desktop rail fallback;
- 2560 × 1440 ultra-wide content bounding.

The actual `ResponsiveLayout.kt` calculation source was also compiled and executed in an isolated JVM harness.

Result:

```text
RESPONSIVE_LAYOUT_RUNTIME_HARNESS_PASSED
```

Harness SHA-256:

```text
0b824d1b52016c89e47f36b9d2f933bb81ce4b132938ff9f83a1500a0b33b980
```

This verifies responsive policy calculations only. It is not Compose rendering, screenshot, Android instrumented, iOS simulator, browser, or physical-device evidence.

## Required visual/runtime matrix

Before release, capture screenshots or test records at a minimum for:

| Scenario | Suggested viewport |
|---|---:|
| small Android portrait | 360 × 800 dp |
| regular Android portrait | 390 × 844 dp |
| phone landscape, low height | 844 × 390 dp |
| small split-screen landscape | 600 × 400 dp |
| tablet portrait | 600 × 960 dp |
| tablet landscape | 1024 × 768 dp |
| short desktop window | 1440 × 480 dp |
| regular desktop | 1280 × 820 dp |
| ultra-wide desktop | 2560 × 1440 dp |

Repeat critical cases with Persian/RTL, dark theme, 200% text scaling where supported, keyboard/IME visible, and system display cutouts/insets.

## Verification status

```text
Responsive policy source execution: Verified
Static responsive contract audit: Verified
Compose type-safe compilation: Blocked by delivery-environment DNS
Android visual/runtime verification: Not Executed
iOS visual/runtime verification: Not Executed
Desktop Compose rendering verification: Not Executed
JS/Wasm browser rendering verification: Not Executed
```

## Rollback

The responsive transformation is isolated primarily to `ui/layout`, shared UI components, screen composition, and Desktop window defaults. Reverting the 3.3.0 responsive commit restores the previous 3.2.0 shell without changing persistence schema, endpoint contracts, protocol behavior, package identifiers, or application IDs.
