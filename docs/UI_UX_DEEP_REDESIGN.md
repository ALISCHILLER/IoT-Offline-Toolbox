# UI/UX deep redesign — 3.4.0

Date: 2026-07-15

## Scope

Version 3.4.0 performs a shared Compose Multiplatform UI transformation without changing protocol contracts, persistence schema, application identifiers, navigation destinations, or the established UDF store. The work targets visual hierarchy, operator efficiency, responsive composition, state communication, accessibility semantics, Persian/RTL behavior, dark theme, and consistency across Android, iOS, Desktop, JS, and Wasm source sets.

## Design principles

1. **Evidence before decoration** — network results expose status, endpoint, timing, truncation, acknowledgement, and security evidence before raw payloads.
2. **Progressive disclosure** — primary actions and essential fields appear first; technical detail is grouped in selectable code surfaces and structured key/value rows.
3. **One interaction language** — the same section, status, metric, action, empty-state, and result patterns are reused across workbenches.
4. **Responsive by available container** — fields, cards, buttons, and request/result panes adapt to the actual remaining width after drawer, rail, sidebar, split-screen, and window resize.
5. **Local-first trust** — privacy, credential lifetime, transport warnings, destructive restore behavior, and platform capability limits are visible at the point of action.
6. **No fake affordances** — non-interactive metadata uses non-clickable information pills; controls that look actionable always have an action.

## Shared design system

`ui/theme/ToolboxTheme.kt` now provides:

- a complete light and dark Material 3 color scheme;
- MSA green as the primary identity color, supported by blue and amber semantic accents;
- explicit success, warning, and information tokens for both themes;
- a coherent typography hierarchy from display through labels;
- centralized rounded shapes for fields, cards, banners, navigation, and surfaces.

The deterministic `tools/ui_quality_audit.py` reads the actual source colors and computes WCAG contrast. Forty-four foreground/background pairs currently meet or exceed 4.5:1.

## Shared UI primitives

`ui/components/CommonComponents.kt` centralizes:

- adaptive page headers and action headers;
- metric cards and workflow action cards;
- section cards with compact action fallback;
- responsive status banners that stack actions on narrow containers;
- capability badges and non-interactive information pills;
- selectable key/value evidence rows;
- vertically and horizontally scrollable selectable code surfaces;
- consistent empty states and log-level badges;
- responsive fields, actions, button grids, card grids, and dual panes;
- scrollable translated tab rows.

Raw screen-level Material `Card` usage is intentionally prohibited by the UI audit so visual behavior cannot silently diverge from the shared system.

## Application shell and navigation

The shell now uses one grouped information architecture:

- **Tools** — dashboard, discovery, network, HTTP, realtime, and payload workbenches;
- **Workspace** — saved devices, profiles, history, and logs;
- **Support** — guide and settings.

Material icons replace text glyphs. Drawer, rail, and desktop sidebar share selection colors, brand treatment, version/privacy footer, and destination semantics. In low-height rail mode, labels are visually omitted but remain available to accessibility services. Non-blocking notices use Snackbar; blocking operation failures use a dedicated error dialog. Active operations expose cancellation and live status.

## Screen transformations

### Dashboard

- changed from a button list into an operator command center;
- added product hero, workspace metrics, recommended workflows, runtime capabilities, recent activity, and local-first privacy guidance;
- actions are scannable cards rather than ambiguous buttons.

### Discovery

- capability limitations are shown before execution;
- LAN, SSDP, and mDNS inputs use bounded validation and consistent call-to-action placement;
- results use responsive cards, structured endpoint evidence, service metadata, and explicit save actions;
- informational service labels are non-clickable pills rather than no-op chips.

### Network tools

- reachability, port scan, TCP, UDP, subnet, and DNS flows use consistent request/result hierarchy;
- invalid ports, ranges, timeouts, and CIDR inputs are exposed near the relevant field;
- long text and HEX output is selectable and independently scrollable;
- result success/error state is visually separated from protocol evidence.

### HTTP

- profile selection, method/URL, headers, payload, timeout, and unsafe-cleartext override are grouped by intent;
- response status, transport warning, elapsed time, size, truncation, headers, and body are presented as distinct evidence;
- request and response become parallel panes when their minimum widths fit.

### WebSocket, MQTT, and CoAP

- transport configuration and operation payloads are separated;
- memory-only MQTT credentials and clean-session/retain behavior are explained at the control;
- CONNACK, SUBACK, PUBACK, message ID, token, and correlated response evidence is visible;
- transcripts and payloads use selectable bounded surfaces;
- unsupported platform capability is communicated before execution.

### Payload tools

- local-only processing is made explicit;
- input/variables and transformed output use adaptive panes;
- templates are responsive cards with clear apply/delete actions;
- technical output preserves monospaced selection and scrolling.

### Profiles, backup, and history

- endpoint profiles are searchable and visually distinguish protocol and security state;
- passwords remain operation-scoped and are not presented as durable profile fields;
- backup export, inspection, merge, and destructive replace are separated with explicit risk messaging;
- history exposes method/protocol, endpoint, timestamp, elapsed time, status, and bounded response evidence.

### Saved devices and logs

- both collections are searchable and have meaningful empty states;
- device metadata is grouped into endpoint, observation, note, addresses, ports, services, and tags;
- log level, timestamp, category, and selectable message are visually distinct;
- destructive clear/delete actions use explicit styling.

### Settings and guide

- unsaved, invalid, and saved-state behavior is visible;
- language, theme, limits, retention, and unsafe transport policy are grouped by concern;
- save is disabled until values are valid and changed;
- the guide is organized around workflows, runtime capability boundaries, safety, and release verification rather than a flat help page.

## Responsive and low-height behavior

The 3.3.0 responsive architecture remains the foundation and is extended by the new design system. The same UI supports:

- compact portrait drawer;
- phone landscape and split-screen rail;
- tablet rail and adaptive grids;
- regular desktop sidebar;
- short desktop rail fallback;
- ultra-wide centered content capped at 1440 dp;
- edge-to-edge safe drawing and IME padding.

Status actions, page-header actions, section actions, technical panes, and metadata rows now also have local narrow-container fallbacks instead of relying only on the global window class.

## Accessibility and localization

Source-level improvements include:

- semantic headings for page and section hierarchy;
- semantic destination names for icon-only navigation;
- at least Material-standard interactive targets;
- no clickable appearance for static metadata;
- non-color-only status through icon, title, text, and state labels;
- selectable technical evidence;
- localized English/Persian primary UI strings and effective RTL direction;
- scrollable navigation and tabs to reduce clipping under text scaling;
- strong light/dark semantic contrast verified by source-level WCAG calculations.

TalkBack, VoiceOver, keyboard focus order, 200% platform text scaling, switch control, browser accessibility tree, and screenshot comparison still require runtime evidence.

## Automated source evidence

Commands:

```bash
python3 tools/ui_quality_audit.py
python3 tools/static_audit.py
bash tools/run_quality_checks.sh --static
```

Current deterministic UI result:

```text
Contrast pairs checked: 44
Source contracts checked: 9
Errors: 0
UI_QUALITY_AUDIT_PASSED
```

The actual `ResponsiveLayout.kt` policy was also compiled with minimal temporary runtime/unit stubs and executed across mobile, low-height landscape, split-screen, tablet, sidebar boundary, short desktop, and ultra-wide cases:

```text
RESPONSIVE_LAYOUT_RUNTIME_HARNESS_PASSED
```

These checks verify source contracts and pure layout policy. They do not render Compose pixels and do not replace Gradle compilation, screenshot tests, emulator/simulator tests, or assistive-technology testing.

## Required visual release matrix

Before store or public release, retain screenshot/runtime evidence for at least:

| Scenario | Viewport / condition |
|---|---|
| small phone portrait | 360 × 800 dp |
| regular phone portrait | 390 × 844 dp |
| phone landscape | 844 × 390 dp |
| split-screen | 600 × 400 dp |
| tablet portrait | 600 × 960 dp |
| tablet landscape | 1024 × 768 dp |
| short desktop | 1440 × 480 dp |
| regular desktop | 1280 × 820 dp |
| ultra-wide | 2560 × 1440 dp |
| accessibility | English and Persian, LTR/RTL, light/dark, 200% text, keyboard/IME, TalkBack/VoiceOver |

Every operational screen must be checked for loading, disabled, validation, empty, success, warning, error, long-content, and cancellation states.

## Verification classification

```text
UI source architecture: Statically Reviewed
WCAG source color pairs: Verified by deterministic execution
Responsive policy calculation: Verified by isolated execution
Compose type-safe build: Blocked by delivery-environment DNS
Rendered visual matrix: Not Executed
Assistive technology runtime: Not Executed
```

## Rollback

The redesign is isolated to shared theme, icon mapping, UI components, screen composition, shell presentation, UI audits, documentation, and release version metadata. Reverting the 3.4.0 UI commit restores the 3.3.0 responsive presentation without changing persistence schema, protocol codecs, network contracts, package names, application ID, Bundle ID, or license.
