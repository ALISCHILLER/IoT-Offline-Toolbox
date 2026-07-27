#!/usr/bin/env python3
"""Dependency-free structural and release-coherence audit for MSA IoT Offline Toolbox."""

from __future__ import annotations

import hashlib
import json
import re
import sys
import tomllib
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []
WARNINGS: list[str] = []

FORBIDDEN = {
    "TODO(": "executable TODO",
    "NotImplementedError": "not implemented placeholder",
    "UnsupportedOperationException": "raw unsupported exception",
    "// existing code": "omitted code marker",
    "// unchanged code": "omitted code marker",
    "// بقیه کد": "omitted code marker",
}
FORBIDDEN_REPOSITORIES = ("maven.aliyun.com", "jcenter()", "repo1.maven.org/maven2")
SENSITIVE_NAMES = {"local.properties", "keystore.properties", ".env"}
SENSITIVE_SUFFIXES = {".jks", ".keystore", ".p12", ".mobileprovision"}
OFFICIAL_EMAIL = "solimaniali90@gmail.com"
OFFICIAL_GITHUB = "https://github.com/ALISCHILLER"
OFFICIAL_AUTHOR = "ALISCHILLER"
OFFICIAL_BRAND = "MSA"


def relative(path: Path) -> str:
    return str(path.relative_to(ROOT)).replace("\\", "/")


def fail(path: Path | str, message: str) -> None:
    ERRORS.append(f"{path}: {message}")


def warn(path: Path | str, message: str) -> None:
    WARNINGS.append(f"{path}: {message}")


def strip_kotlin_literals(source: str) -> str:
    # Strings are removed before line comments so URLs such as https:// do not
    # accidentally turn the rest of a Kotlin line into a comment in the audit.
    source = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), source, flags=re.S)
    source = re.sub(r'""".*?"""', lambda m: "\n" * m.group(0).count("\n"), source, flags=re.S)
    source = re.sub(r'"(?:\\.|[^"\\])*"', '""', source)
    source = re.sub(r"'(?:\\.|[^'\\])'", "''", source)
    source = re.sub(r"//[^\n]*", "", source)
    return source


def check_balanced(path: Path, source: str) -> None:
    cleaned = strip_kotlin_literals(source)
    pairs = {"{": "}", "(": ")", "[": "]"}
    closing = {value: key for key, value in pairs.items()}
    stack: list[tuple[str, int]] = []
    for line_no, line in enumerate(cleaned.splitlines(), start=1):
        for char in line:
            if char in pairs:
                stack.append((char, line_no))
            elif char in closing:
                if not stack or stack[-1][0] != closing[char]:
                    fail(relative(path), f"unbalanced {char!r} near line {line_no}")
                    return
                stack.pop()
    if stack:
        opener, line_no = stack[-1]
        fail(relative(path), f"unclosed {opener!r} opened near line {line_no}")


def check_kotlin(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    rel = relative(path)
    if path.suffix == ".kt":
        package = re.search(r"^package\s+([A-Za-z_][\w.]*)\s*$", source, flags=re.M)
        if not package:
            fail(rel, "missing package declaration")
        elif not package.group(1).startswith("com.msa"):
            fail(rel, f"package must start with com.msa: {package.group(1)}")
        elif "/kotlin/" in rel:
            actual_directory = rel.split("/kotlin/", 1)[1].rsplit("/", 1)[0]
            expected_directory = package.group(1).replace(".", "/")
            if actual_directory != expected_directory:
                fail(rel, f"source path does not match package {package.group(1)}")
    for token, description in FORBIDDEN.items():
        if token in source:
            fail(rel, f"contains {description}: {token}")
    if re.search(r'(?i)(password|secret|api[_-]?key)\s*=\s*"[^"$]{6,}"', source):
        warn(rel, "possible hard-coded credential literal; review manually")
    check_balanced(path, source)


def check_xml(path: Path) -> None:
    try:
        ET.parse(path)
    except ET.ParseError as exc:
        fail(relative(path), f"invalid XML: {exc}")


def check_toml(path: Path) -> None:
    try:
        with path.open("rb") as handle:
            tomllib.load(handle)
    except Exception as exc:  # noqa: BLE001
        fail(relative(path), f"invalid TOML: {exc}")


def check_json(path: Path) -> None:
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001
        fail(relative(path), f"invalid JSON: {exc}")


def check_yaml(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if "\t" in text:
        fail(relative(path), "YAML must not contain tab indentation")
    if not re.search(r"(?m)^\s*(name|on|jobs|version):", text):
        warn(relative(path), "YAML file has no recognized top-level workflow/configuration key")
    for line_no, line in enumerate(text.splitlines(), start=1):
        if re.match(r"^\s*-[^\s-]", line):
            fail(relative(path), f"list marker must be followed by a space at line {line_no}")


def read_gradle_properties() -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result




def check_toolchain_compatibility() -> None:
    catalog_path = ROOT / "gradle/libs.versions.toml"
    with catalog_path.open("rb") as handle:
        catalog = tomllib.load(handle)
    versions = catalog.get("versions", {})
    agp = str(versions.get("agp", ""))
    kotlin = str(versions.get("kotlin", ""))

    wrapper_path = ROOT / "gradle/wrapper/gradle-wrapper.properties"
    wrapper = wrapper_path.read_text(encoding="utf-8")
    match = re.search(r"gradle-([0-9]+\.[0-9]+(?:\.[0-9]+)?)-bin\.zip", wrapper)
    gradle = match.group(1) if match else ""

    if agp != "9.0.0-alpha06":
        fail(relative(catalog_path), f"this Android Studio compatibility build requires AGP 9.0.0-alpha06, found {agp or 'missing'}")
    if gradle != "9.1.0":
        fail(relative(wrapper_path), f"AGP 9.0 compatibility build requires Gradle 9.1.0, found {gradle or 'missing'}")
    if kotlin != "2.4.10":
        fail(relative(catalog_path), f"validated Kotlin version is 2.4.10, found {kotlin or 'missing'}")
    expected_checksum = "a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806"
    if f"distributionSha256Sum={expected_checksum}" not in wrapper:
        fail(relative(wrapper_path), "Gradle 9.1.0 official binary SHA-256 checksum is missing")

def check_version_coherence() -> None:
    props = read_gradle_properties()
    version = props.get("appVersion")
    code = props.get("appVersionCode")
    if not version or not re.fullmatch(r"\d+\.\d+\.\d+", version):
        fail("gradle.properties", "appVersion must be present and use semantic x.y.z format")
        return
    if not code or not code.isdigit() or int(code) <= 0:
        fail("gradle.properties", "appVersionCode must be a positive integer")
    models = (ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/model/Models.kt").read_text()
    model_version = re.search(r'APP_VERSION:\s*String\s*=\s*"([^"]+)"', models)
    if not model_version or model_version.group(1) != version:
        fail("Models.kt", f"APP_VERSION must equal gradle.properties appVersion ({version})")
    ios = (ROOT / "iosApp/Configuration/Config.xcconfig").read_text()
    if f"MARKETING_VERSION={version}" not in ios:
        fail("iosApp/Configuration/Config.xcconfig", f"MARKETING_VERSION must equal {version}")
    if f"CURRENT_PROJECT_VERSION={code}" not in ios:
        fail("iosApp/Configuration/Config.xcconfig", f"CURRENT_PROJECT_VERSION must equal {code}")
    android = (ROOT / "androidApp/build.gradle.kts").read_text()
    if 'providers.gradleProperty("appVersion")' not in android or 'providers.gradleProperty("appVersionCode")' not in android:
        fail("androidApp/build.gradle.kts", "Android version must read central appVersion/appVersionCode properties")
    desktop = (ROOT / "composeApp/build.gradle.kts").read_text()
    if 'packageVersion = providers.gradleProperty("appVersion").get()' not in desktop:
        fail("composeApp/build.gradle.kts", "Desktop packageVersion must read central appVersion")


def check_screen_exhaustiveness() -> None:
    models = (ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/model/Models.kt").read_text()
    app = (ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/App.kt").read_text()
    match = re.search(r"enum class ToolScreen[^\{]*\{(.*?)\n\}", models, flags=re.S)
    if not match:
        fail("Models.kt", "ToolScreen enum not found")
        return
    names = re.findall(r"^\s*([A-Z][A-Z0-9_]*)\s*\(", match.group(1), flags=re.M)
    missing = [name for name in names if f"ToolScreen.{name} ->" not in app]
    if missing:
        fail("App.kt", f"ToolScreen entries not handled: {', '.join(missing)}")


def check_platform_contracts() -> None:
    expected = {
        "androidMain": "PlatformNetwork.android.kt",
        "desktopMain": "PlatformNetwork.desktop.kt",
        "iosMain": "PlatformNetwork.ios.kt",
        "jsMain": "PlatformNetwork.js.kt",
        "wasmJsMain": "PlatformNetwork.wasmJs.kt",
    }
    for source_set, filename in expected.items():
        path = ROOT / f"composeApp/src/{source_set}/kotlin/com/msa/iotofflinetoolbox/core/network/{filename}"
        if not path.exists():
            fail(relative(path), "missing platform NetworkProbe implementation")
        elif "actual fun createPlatformNetworkProbe" not in path.read_text():
            fail(relative(path), "missing actual createPlatformNetworkProbe")


def check_required_files() -> None:
    required = [
        "README.md", "SETUP.md", "ARCHITECTURE.md", "LICENSE.md", "LICENSE",
        "SECURITY.md", "PRIVACY.md", "CONTRIBUTING.md", "CHANGELOG.md",
        "RELEASE_CHECKLIST.md", "THIRD_PARTY_NOTICES.md",
        "docs/PRODUCTION_HARDENING_REPORT.md", "docs/RESPONSIVE_IMPLEMENTATION.md",
        "docs/UI_UX_DEEP_REDESIGN.md", "docs/UI_QUALITY_TESTS.txt",
        "docs/FINAL_AUDIT_REPORT.md", "docs/DEEP_CODE_CONFIG_AUDIT_3.6.2.md",
        "docs/COMPILE_FIX_3.6.2.md",
        "tools/core_runtime_harness.sh", "tools/protocol_runtime_harness.sh",
        "tools/responsive_runtime_harness.sh", "tools/desktop_network_runtime_harness.sh",
        "tools/generate_source_checksums.py",
        "tools/ui_quality_audit.py",
        ".github/workflows/ci.yml", "gradle/wrapper/gradle-wrapper.properties",
    ]
    for item in required:
        if not (ROOT / item).exists():
            fail(item, "required project file is missing")

    license_path = ROOT / "LICENSE"
    license_md_path = ROOT / "LICENSE.md"
    if license_path.exists() and license_md_path.exists():
        license_text = license_path.read_text(encoding="utf-8")
        if license_text != license_md_path.read_text(encoding="utf-8"):
            fail("LICENSE.md", "LICENSE and LICENSE.md must contain the same license text")
        if "END OF TERMS AND CONDITIONS" not in license_text:
            fail("LICENSE", "Apache License 2.0 text is incomplete")


def check_wrapper() -> None:
    path = ROOT / "gradle/wrapper/gradle-wrapper.properties"
    text = path.read_text()
    if "distributionUrl=https\\://services.gradle.org/distributions/" not in text:
        fail(relative(path), "wrapper must use the portable HTTPS Gradle distribution service")
    checksum = re.search(r"^distributionSha256Sum=([0-9a-f]{64})$", text, flags=re.M)
    if not checksum:
        fail(relative(path), "wrapper distributionSha256Sum is missing or invalid")


def check_repositories_and_sensitive_files() -> None:
    for path in ROOT.rglob("*"):
        if any(part in {"build", ".gradle", ".git"} for part in path.parts):
            continue
        if path.is_file() and (path.name in SENSITIVE_NAMES or path.suffix.lower() in SENSITIVE_SUFFIXES):
            fail(relative(path), "sensitive local/signing file must not be packaged")
        if path.is_file() and (path.suffix.lower() == ".pyc" or "__pycache__" in path.parts):
            fail(relative(path), "generated Python cache must not be packaged")
        if path.is_file() and path.suffix.lower() in {".kt", ".kts", ".toml", ".properties", ".yml", ".yaml", ".ps1", ".sh"}:
            text = path.read_text(encoding="utf-8", errors="replace")
            active_lines = []
            for line in text.splitlines():
                stripped = line.strip()
                if stripped.startswith(("//", "#", "*")):
                    continue
                active_lines.append(line)
            active_text = "\n".join(active_lines)
            for repository in FORBIDDEN_REPOSITORIES:
                if repository in active_text:
                    fail(relative(path), f"forbidden or legacy repository reference: {repository}")


def check_identity() -> None:
    emails: dict[str, set[str]] = {}
    for path in ROOT.rglob("*"):
        if not path.is_file() or any(part in {"build", ".gradle", ".git"} for part in path.parts):
            continue
        if path.suffix.lower() not in {".md", ".swift", ".plist", ".xml", ".properties", ".yml", ".yaml"}:
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        found = set(re.findall(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", text))
        if found:
            emails[relative(path)] = found
    for path, values in emails.items():
        unexpected = sorted(value for value in values if value.lower() != OFFICIAL_EMAIL)
        if unexpected:
            fail(path, f"unexpected identity email(s): {', '.join(unexpected)}")

    required_identity_files = ("README.md", "SETUP.md", "ARCHITECTURE.md")
    for item in required_identity_files:
        path = ROOT / item
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        required_values = {
            OFFICIAL_AUTHOR: "official author",
            OFFICIAL_BRAND: "official brand",
            OFFICIAL_EMAIL: "official email",
            OFFICIAL_GITHUB: "official GitHub URL",
        }
        for value, label in required_values.items():
            if value not in text:
                fail(item, f"missing {label}: {value}")




def check_responsive_contract() -> None:
    responsive = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/layout/ResponsiveLayout.kt"
    test = ROOT / "composeApp/src/commonTest/kotlin/com/msa/iotofflinetoolbox/ui/layout/ResponsiveLayoutTest.kt"
    app = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/App.kt"
    if not responsive.exists():
        fail(relative(responsive), "shared responsive layout profile is missing")
        return
    source = responsive.read_text(encoding="utf-8")
    required_tokens = (
        "WindowWidthClass.COMPACT",
        "WindowWidthClass.MEDIUM",
        "WindowWidthClass.EXPANDED",
        "NavigationMode.DRAWER",
        "NavigationMode.RAIL",
        "NavigationMode.SIDEBAR",
        "isLowHeight",
        "maxContentWidth",
    )
    for token in required_tokens:
        if token not in source:
            fail(relative(responsive), f"responsive contract missing {token}")
    if not test.exists():
        fail(relative(test), "responsive breakpoint tests are missing")
    else:
        test_source = test.read_text(encoding="utf-8")
        for scenario in ("compactPortraitUsesDrawer", "phoneLandscapeUsesCompactRailAndLowHeightDensity", "tabletPortraitUsesNavigationRail", "largeWindowUsesSidebarOnlyWithEnoughHeight"):
            if scenario not in test_source:
                fail(relative(test), f"responsive scenario test missing: {scenario}")
    app_source = app.read_text(encoding="utf-8")
    for shell in ("DesktopShell", "NavigationRailShell", "MobileShell"):
        if shell not in app_source:
            fail(relative(app), f"adaptive navigation shell missing: {shell}")
    if app_source.count("windowInsetsPadding(WindowInsets.safeDrawing)") < 2:
        fail(relative(app), "rail/sidebar shells must consume safe-drawing insets for edge-to-edge hosts")
    if ".imePadding()" not in app_source:
        fail(relative(app), "adaptive content frame must account for the on-screen keyboard/IME")
    screens_root = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/screens"
    for screen in screens_root.glob("*.kt"):
        screen_source = screen.read_text(encoding="utf-8")
        if "PaddingValues(24.dp)" in screen_source or ".padding(24.dp)" in screen_source:
            fail(relative(screen), "fixed 24dp page padding bypasses responsive content padding")
    for name in ("DiscoveryScreen.kt", "ProfilesScreen.kt", "RealtimeScreen.kt"):
        path = screens_root / name
        if path.exists():
            tab_source = path.read_text(encoding="utf-8")
            if "ScrollableTabRow" not in tab_source and "ToolTabRow" not in tab_source:
                fail(relative(path), "multi-tab screen must use the shared horizontally scrollable tab row")


def check_ui_quality_contract() -> None:
    store = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/ToolboxStore.kt"
    store_source = store.read_text(encoding="utf-8") if store.exists() else ""
    for token in ("MAX_PERSISTED_ITEMS", "MAX_TEMPLATE_BYTES", "MAX_HEADER_BLOCK_LENGTH", "Device inventory reached"):
        if token not in store_source:
            fail(relative(store), f"direct persistence bound missing {token}")

    build_file = ROOT / "composeApp/build.gradle.kts"
    theme_file = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/theme/ToolboxTheme.kt"
    icons_file = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/theme/ToolboxIcons.kt"
    components_file = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/components/CommonComponents.kt"
    app_file = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/App.kt"

    build_source = build_file.read_text(encoding="utf-8")
    material_icons_dependencies = (
        "implementation(libs.compose.material.icons.extended)",
        "implementation(\"org.jetbrains.compose.material:material-icons-extended:1.7.3\")",
    )
    if not any(dependency in build_source for dependency in material_icons_dependencies):
        fail(relative(build_file), "shared UI must include the explicitly pinned Material icons dependency")

    theme = theme_file.read_text(encoding="utf-8") if theme_file.exists() else ""
    for token in ("lightColorScheme", "darkColorScheme", "ToolboxTypography", "ToolboxShapes", "ToolboxUiTokens"):
        if token not in theme:
            fail(relative(theme_file), f"design system missing {token}")

    if not icons_file.exists() or "val ToolScreen.icon" not in icons_file.read_text(encoding="utf-8"):
        fail(relative(icons_file), "screen icon mapping is missing")

    components = components_file.read_text(encoding="utf-8") if components_file.exists() else ""
    for component in ("StatusBanner", "CodeSurface", "SelectionContainer", "InfoPill", "ToolActionCard", "ResponsiveCardGrid", "ToolTabRow"):
        if component not in components:
            fail(relative(components_file), f"shared UI component missing {component}")

    all_ui = "\n".join(path.read_text(encoding="utf-8") for path in (ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui").rglob("*.kt"))
    if "AssistChip(onClick = {})" in all_ui:
        fail("ui", "static metadata must not be exposed as a no-op interactive AssistChip")

    app = app_file.read_text(encoding="utf-8")
    for token in ("SnackbarHost", "NavigationDrawerItem", "NavigationRailItem", "WindowInsets.safeDrawing", ".imePadding()"):
        if token not in app:
            fail(relative(app_file), f"application shell missing UI contract token {token}")
    if re.search(r'Text\(\s*"[⌂⇄↗☰]', app):
        fail(relative(app_file), "navigation must not use text glyphs as interactive icons")

    screens_root = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/screens"
    for screen in screens_root.glob("*.kt"):
        source = screen.read_text(encoding="utf-8")
        if "PageHeader" not in source:
            fail(relative(screen), "screen must expose a consistent page hierarchy")
        if re.search(r"(?m)^\s*Card\(", source):
            fail(relative(screen), "screen bypasses shared section/card design system")
        if "LazyColumn" in source and "responsiveContentPadding()" not in source:
            fail(relative(screen), "scrolling screen must use responsive content padding")


def check_transport_security_contract() -> None:
    network_root = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network"
    transport = network_root / "TransportSecurity.kt"
    realtime = network_root / "RealtimeClients.kt"
    http_client = network_root / "HttpToolClient.kt"
    realtime_ui = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/screens/RealtimeScreen.kt"
    http_ui = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/screens/HttpScreen.kt"
    test = ROOT / "composeApp/src/commonTest/kotlin/com/msa/iotofflinetoolbox/core/network/TransportSecurityTest.kt"
    harness = ROOT / "tools/core_runtime_harness.sh"

    transport_source = transport.read_text(encoding="utf-8") if transport.exists() else ""
    for token in ("normalizeUrl", "hasScheme", "requireScheme", "firstHextet in 0xFC00..0xFDFF", "firstHextet in 0xFE80..0xFEBF"):
        if token not in transport_source:
            fail(relative(transport), f"transport security contract missing {token}")
    if 'host.startsWith("fc")' in transport_source or 'host.startsWith("fd")' in transport_source:
        fail(relative(transport), "DNS names must not be classified as IPv6 ULA by textual fc/fd prefixes")

    for path in (realtime, realtime_ui):
        source = path.read_text(encoding="utf-8") if path.exists() else ""
        if 'startsWith("ws://")' in source or 'startsWith("wss://")' in source:
            fail(relative(path), "WebSocket schemes must use the shared case-insensitive transport validator")
    for path in (realtime, http_client, realtime_ui, http_ui):
        source = path.read_text(encoding="utf-8") if path.exists() else ""
        if "TransportSecurity" not in source:
            fail(relative(path), "endpoint path bypasses shared transport security validation")

    production_root = ROOT / "composeApp/src"
    for path in production_root.rglob("*.kt"):
        if "Test" in path.parts or "commonTest" in path.parts or "desktopTest" in path.parts:
            continue
        source = path.read_text(encoding="utf-8")
        if re.search(r"catch\s*\([^)]*Throwable[^)]*\)", source):
            fail(relative(path), "production code catches Throwable and may swallow cancellation or fatal errors")

    test_source = test.read_text(encoding="utf-8") if test.exists() else ""
    for scenario in ("dnsNamesStartingWithIpv6PrefixesAreNotMisclassified", "ipv6LocalRangesUseActualHextets", "hostExtractionSupportsCredentialsPortsAndBracketedIpv6", "schemesAreTrimmedAndComparedCaseInsensitively"):
        if scenario not in test_source:
            fail(relative(test), f"transport regression test missing: {scenario}")
    if not harness.exists() or "FINAL_CORE_RUNTIME_HARNESS_PASSED" not in harness.read_text(encoding="utf-8"):
        fail(relative(harness), "independent core runtime harness is missing")

    production_sources = [
        path for path in (ROOT / "composeApp/src").rglob("*.kt")
        if "commonTest" not in path.parts and "desktopTest" not in path.parts
    ]
    for path in production_sources:
        source = path.read_text(encoding="utf-8")
        for public_demo_host in ("httpbin.org", "echo.websocket.events", "broker.emqx.io"):
            if public_demo_host in source:
                fail(relative(path), f"public third-party endpoint must not be a production input default: {public_demo_host}")

    ios_network = ROOT / "composeApp/src/iosMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.ios.kt"
    ios_source = ios_network.read_text(encoding="utf-8") if ios_network.exists() else ""
    if "CancellationException" not in ios_source or ios_source.count("if (error is CancellationException) throw error") < 3:
        fail(relative(ios_network), "iOS suspend socket operations must preserve structured cancellation")

def check_deep_hardening_contract() -> None:
    app = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/App.kt"
    app_source = app.read_text(encoding="utf-8") if app.exists() else ""
    if "SupervisorJob() + Dispatchers.Main" not in app_source:
        fail(relative(app), "ToolboxStore mutations must be serialized on Dispatchers.Main")
    if "SupervisorJob() + Dispatchers.Default" in app_source:
        fail(relative(app), "ToolboxStore must not mutate StateFlow concurrently on Dispatchers.Default")

    http_clients = sorted((ROOT / "composeApp/src").glob("*Main/kotlin/com/msa/iotofflinetoolbox/core/network/HttpClient.*.kt"))
    if len(http_clients) != 5:
        fail("composeApp/src", f"expected five platform HTTP clients, found {len(http_clients)}")
    for client in http_clients:
        source = client.read_text(encoding="utf-8")
        if "followRedirects = false" not in source:
            fail(relative(client), "automatic redirects can bypass cleartext destination validation")

    base64 = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/payload/Base64Codec.kt"
    base64_source = base64.read_text(encoding="utf-8") if base64.exists() else ""
    for token in ("MAX_DECODED_BYTES", "padding is only allowed in the final quartet", "Non-zero unused bits"):
        if token not in base64_source:
            fail(relative(base64), f"strict Base64 contract missing {token}")

    payload_tools = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/payload/PayloadTools.kt"
    if "object Base64Codec" in payload_tools.read_text(encoding="utf-8"):
        fail(relative(payload_tools), "Base64 codec must remain isolated for independent regression execution")

    network_actuals = [
        ROOT / "composeApp/src/androidMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.android.kt",
        ROOT / "composeApp/src/desktopMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.desktop.kt",
        ROOT / "composeApp/src/iosMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.ios.kt",
    ]
    for actual in network_actuals:
        source = actual.read_text(encoding="utf-8") if actual.exists() else ""
        if "Timed out waiting for a UDP response" not in source:
            fail(relative(actual), "UDP receive timeout must be surfaced as an operation error")

    mqtt = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/MqttCodec.kt"
    mqtt_source = mqtt.read_text(encoding="utf-8") if mqtt.exists() else ""
    for token in ("validateFixedHeader(type, flags)", "type in 1..14", "cannot contain a null character"):
        if token not in mqtt_source:
            fail(relative(mqtt), f"MQTT protocol validation missing {token}")

    build_file = ROOT / "composeApp/build.gradle.kts"
    build_source = build_file.read_text(encoding="utf-8") if build_file.exists() else ""
    if "withHostTest" not in build_source:
        fail(relative(build_file), "Android KMP common tests are not enabled as host tests")

    workflow = ROOT / ".github/workflows/ci.yml"
    workflow_source = workflow.read_text(encoding="utf-8") if workflow.exists() else ""
    for token in (":composeApp:testAndroidHostTest", "tools/run_quality_checks.sh --audit", 'sdkmanager "platforms;android-36" "build-tools;36.0.0"'):
        if token not in workflow_source:
            fail(relative(workflow), f"CI coverage contract missing {token}")
    if "cache: gradle" in workflow_source:
        fail(relative(workflow), "setup-java Gradle caching duplicates gradle/actions/setup-gradle caching")

    quality = ROOT / "tools/run_quality_checks.sh"
    quality_source = quality.read_text(encoding="utf-8") if quality.exists() else ""
    for token in ("--audit", "tools/core_runtime_harness.sh", "tools/protocol_runtime_harness.sh", "tools/responsive_runtime_harness.sh", "tools/desktop_network_runtime_harness.sh", ":composeApp:testAndroidHostTest"):
        if token not in quality_source:
            fail(relative(quality), f"quality runner missing {token}")

    protocol_harness = ROOT / "tools/protocol_runtime_harness.sh"
    if not protocol_harness.exists() or "PROTOCOL_RUNTIME_HARNESS_PASSED" not in protocol_harness.read_text(encoding="utf-8"):
        fail(relative(protocol_harness), "reproducible protocol runtime harness is missing")

    responsive_harness = ROOT / "tools/responsive_runtime_harness.sh"
    if not responsive_harness.exists() or "RESPONSIVE_LAYOUT_RUNTIME_HARNESS_PASSED" not in responsive_harness.read_text(encoding="utf-8"):
        fail(relative(responsive_harness), "reproducible responsive runtime harness is missing")


def check_settings_and_protocol_contracts() -> None:
    settings = ROOT / "settings.gradle.kts"
    settings_source = settings.read_text(encoding="utf-8") if settings.exists() else ""
    first_statement = re.search(r"(?m)^\s*(?!//)([A-Za-z_][\w.]*)", settings_source)
    if not first_statement or first_statement.group(1) != "pluginManagement":
        fail(relative(settings), "pluginManagement must be the first settings block")
    for token in ("RepositoriesMode.FAIL_ON_PROJECT_REPOS", "google", "mavenCentral", "gradlePluginPortal"):
        if token not in settings_source:
            fail(relative(settings), f"repository policy missing {token}")

    dns = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/DnsSdCodec.kt"
    dns_source = dns.read_text(encoding="utf-8") if dns.exists() else ""
    if dns_source.count("var nextIndex = -1") != 1:
        fail(relative(dns), "DNS name decoder must contain exactly one nextIndex declaration")
    for token in (
        "MAX_DNS_QUESTIONS",
        "MAX_DNS_RECORDS",
        "DNS section counts are inconsistent with packet size",
        "DNS PTR record contains trailing or truncated name data",
        "DNS SRV record contains trailing or truncated target data",
        "DNS packet contains trailing bytes outside declared sections",
        "it.type == TYPE_SRV && it.ttlSeconds > 0",
        "it.type == TYPE_TXT && it.ttlSeconds > 0",
    ):
        if token not in dns_source:
            fail(relative(dns), f"DNS hardening contract missing {token}")

    mqtt = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/MqttCodec.kt"
    mqtt_source = mqtt.read_text(encoding="utf-8") if mqtt.exists() else ""
    for token in (
        "MQTT remaining length uses a non-canonical encoding",
        "MQTT CONNACK cannot report an existing session when the connection is refused",
        "maxPayloadBytes in 1..MAX_MQTT_PACKET_BYTES",
        "MQTT QoS 0 PUBLISH cannot set DUP",
        "MQTT packet exceeds the 1 MiB safety limit",
    ):
        if token not in mqtt_source:
            fail(relative(mqtt), f"MQTT hardening contract missing {token}")

    coap = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/CoapCodec.kt"
    coap_source = coap.read_text(encoding="utf-8") if coap.exists() else ""
    for token in (
        "CoAP option number exceeds 65535",
        "An empty CoAP message must contain only the four-byte header and no token",
        "MAX_UDP_DATAGRAM_BYTES",
        "CoAP packet contains too many options",
    ):
        if token not in coap_source:
            fail(relative(coap), f"CoAP hardening contract missing {token}")
    if coap_source.count('163 -> "Service Unavailable"') != 1:
        fail(relative(coap), "CoAP 5.03 response mapping must be unique")

    ssdp = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/SsdpParser.kt"
    ssdp_source = ssdp.read_text(encoding="utf-8") if ssdp.exists() else ""
    for token in (
        "STATUS_LINE.matches(statusLine)",
        "HEADER_NAME.matches(name)",
        "SSDP response contains too many header lines",
        "SSDP search target contains an invalid control character",
    ):
        if token not in ssdp_source:
            fail(relative(ssdp), f"SSDP hardening contract missing {token}")

    payload = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/payload/PayloadTools.kt"
    payload_source = payload.read_text(encoding="utf-8") if payload.exists() else ""
    for token in ("MAX_PAYLOAD_OUTPUT_CHARS", "StringBuilder", "Expanded payload exceeds the 4 MiB safety limit", "Payload output exceeds the 4 MiB character safety limit"):
        if token not in payload_source:
            fail(relative(payload), f"bounded payload expansion contract missing {token}")
    if ".replace(" in re.search(r"fun expandVariables.*?\n    }", payload_source, flags=re.S).group(0) if "fun expandVariables" in payload_source else False:
        fail(relative(payload), "variable expansion must not use repeated whole-string replacement")

    for source_set in ("androidMain", "desktopMain", "iosMain"):
        actual = ROOT / f"composeApp/src/{source_set}/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.{source_set.removesuffix('Main')}.kt"
        source = actual.read_text(encoding="utf-8") if actual.exists() else ""
        if "Timed out waiting for a TCP response" not in source:
            fail(relative(actual), "TCP receive timeout must be surfaced as an operation error")

    for source_set in ("androidMain", "desktopMain"):
        suffix = "android" if source_set == "androidMain" else "desktop"
        actual = ROOT / f"composeApp/src/{source_set}/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.{suffix}.kt"
        source = actual.read_text(encoding="utf-8") if actual.exists() else ""
        mdns_match = re.search(r"private fun openMdnsSocket\(\): MulticastSocket \{(.*?)\n    }", source, flags=re.S)
        if not mdns_match or "socket.close()" not in mdns_match.group(1) or "catch (error: Exception)" not in mdns_match.group(1):
            fail(relative(actual), "mDNS socket setup must close the socket when bind/join fails")

    payload_test = ROOT / "composeApp/src/commonTest/kotlin/com/msa/iotofflinetoolbox/core/payload/PayloadToolsTest.kt"
    payload_test_source = payload_test.read_text(encoding="utf-8") if payload_test.exists() else ""
    method_names = re.findall(r"fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(", payload_test_source)
    duplicates = sorted({name for name in method_names if method_names.count(name) > 1})
    if duplicates:
        fail(relative(payload_test), f"duplicate test method names: {', '.join(duplicates)}")


def check_release_configuration_contracts() -> None:
    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    if "foojay-resolver-convention" in settings:
        fail("settings.gradle.kts", "JDK auto-download resolver must not be required for reproducible/offline builds")

    properties = read_gradle_properties()
    if properties.get("org.gradle.configuration-cache") != "false":
        fail("gradle.properties", "configuration cache must remain disabled until every target is build-verified")

    wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
    timeout = re.search(r"^networkTimeout=(\d+)$", wrapper, flags=re.M)
    if not timeout or int(timeout.group(1)) < 60_000:
        fail("gradle/wrapper/gradle-wrapper.properties", "wrapper networkTimeout must be at least 60000 ms")

    pbx = (ROOT / "iosApp/iosApp.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
    release = re.search(r"C01C20738F4CC23C466B4C81 /\* Release \*/.*?name = Release;", pbx, flags=re.S)
    if release and ('CODE_SIGN_IDENTITY = "Apple Development"' in release.group(0) or "ENABLE_PREVIEWS = YES" in release.group(0)):
        fail("iosApp/iosApp.xcodeproj/project.pbxproj", "Release must not force development signing or previews")

    common_bars = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/platform/PlatformSystemBars.kt"
    android_bars = ROOT / "composeApp/src/androidMain/kotlin/com/msa/iotofflinetoolbox/ui/platform/PlatformSystemBars.android.kt"
    app = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/App.kt"
    for path in (common_bars, android_bars):
        if not path.exists():
            fail(relative(path), "platform system-bar integration is missing")
    if app.exists() and "PlatformSystemBars(effectiveDarkMode)" not in app.read_text(encoding="utf-8"):
        fail(relative(app), "Android system-bar icon contrast is not synchronized with app dark mode")

    repository = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/data/ToolboxRepository.kt"
    repository_source = repository.read_text(encoding="utf-8") if repository.exists() else ""
    for token in ("MAX_LOCAL_STORAGE_BYTES", "Serialized value for $key exceeds", "Generated backup exceeds the 8 MiB safety limit", "allowPublicCleartext = false"):
        if token not in repository_source:
            fail(relative(repository), f"bounded persistence contract missing {token}")

    store = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/ToolboxStore.kt"
    store_source = store.read_text(encoding="utf-8") if store.exists() else ""
    for token in (
        'runCatching { repository.exportBackup(includeLogs) }',
        'result.statusCode in 200..299',
        'val redactedContent = SecretRedactor.redact(template.content)',
        'content = redactedContent',
    ):
        if token not in store_source:
            fail(relative(store), f"store release contract missing {token}")



def check_compile_regression_contracts() -> None:
    source_root = ROOT / "composeApp/src"
    for path in source_root.rglob("*.kt"):
        source = path.read_text(encoding="utf-8")
        if "import androidx.compose.foundation.layout.weight" in source:
            fail(relative(path), "scoped Compose weight modifier must not be explicitly imported")

    app = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/App.kt"
    app_source = app.read_text(encoding="utf-8")
    for token in (
        "@OptIn(ExperimentalMaterial3Api::class)",
        "val closeMenuDescription = stringResource(",
        "contentDescription = closeMenuDescription",
    ):
        if token not in app_source:
            fail(relative(app), f"Compose compiler regression contract missing {token}")
    if re.search(r"semantics\s*\{[^}]*tr\(", app_source, flags=re.S):
        fail(relative(app), "composable localization must not be invoked inside a semantics lambda")

    http = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/HttpToolClient.kt"
    http_source = http.read_text(encoding="utf-8")
    for token in (
        "MAX_REDIRECTS",
        "crossesOriginWithSensitiveData",
        "forwardsBody",
        "finalUrl = currentUrl",
        "redirected = redirectCount > 0",
        "validateTarget(resolveRedirect",
    ):
        if token not in http_source:
            fail(relative(http), f"manual redirect hardening contract missing {token}")
    if ".readBuffer(maxResponseBytes + 1)" not in http_source:
        fail(relative(http), "Ktor readBuffer must use the supported Int argument")
    if re.search(r"readBuffer\([^)]*toLong\(", http_source):
        fail(relative(http), "Ktor readBuffer Long overload is incompatible with the selected Ktor API")

    realtime = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/screens/RealtimeScreen.kt"
    realtime_source = realtime.read_text(encoding="utf-8")
    if re.search(r"capabilities\.mqttWebSocket(?!Client)", realtime_source):
        fail(relative(realtime), "MQTT capability must use mqttWebSocketClient")

    components = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/components/CommonComponents.kt"
    components_source = components.read_text(encoding="utf-8")
    if "cards: List<@Composable (Modifier) -> Unit>" not in components_source:
        fail(relative(components), "generated composable cards require a typed List overload")

    devices = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/screens/DevicesScreen.kt"
    payload = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/screens/PayloadScreen.kt"
    for path, marker in ((devices, "cards = filtered.map"), (payload, "cards = state.templates.map")):
        source = path.read_text(encoding="utf-8")
        if marker not in source or "val card: @Composable (Modifier) -> Unit" not in source:
            fail(relative(path), "dynamically generated cards must preserve the @Composable function type")

    properties = read_gradle_properties()
    if properties.get("kotlin.native.ignoreDisabledTargets") != "true":
        fail("gradle.properties", "non-macOS hosts must explicitly ignore disabled native targets")

def check_localization_resource_contracts() -> None:
    english = ROOT / "composeApp/src/commonMain/composeResources/values/strings.xml"
    persian = ROOT / "composeApp/src/commonMain/composeResources/values-fa/strings.xml"
    for path in (english, persian):
        if not path.exists():
            fail(relative(path), "Compose Multiplatform string resource file is missing")
            return

    def resource_names(path: Path) -> list[str]:
        return [node.attrib.get("name", "") for node in ET.parse(path).getroot().findall("string")]

    english_names = resource_names(english)
    persian_names = resource_names(persian)
    for path, names in ((english, english_names), (persian, persian_names)):
        duplicates = sorted({name for name in names if names.count(name) > 1})
        if duplicates:
            fail(relative(path), f"duplicate string resource keys: {', '.join(duplicates[:8])}")
    english_set = set(english_names)
    persian_set = set(persian_names)
    if english_set != persian_set:
        missing_fa = sorted(english_set - persian_set)
        missing_en = sorted(persian_set - english_set)
        if missing_fa:
            fail(relative(persian), f"missing Persian keys: {', '.join(missing_fa[:8])}")
        if missing_en:
            fail(relative(english), f"missing English keys: {', '.join(missing_en[:8])}")
    if len(english_set) < 600:
        fail(relative(english), "localized resource coverage unexpectedly dropped below 600 keys")

    references: set[str] = set()
    for path in (ROOT / "composeApp/src").rglob("*.kt"):
        references.update(re.findall(r"Res\.string\.([A-Za-z0-9_]+)", path.read_text(encoding="utf-8")))
    missing = sorted(references - english_set)
    if missing:
        fail("composeApp/src", f"missing generated string resources: {', '.join(missing[:12])}")

    common_kotlin = ROOT / "composeApp/src/commonMain/kotlin"
    manual_translation_calls = []
    for path in common_kotlin.rglob("*.kt"):
        if re.search(r"\btr\s*\(", path.read_text(encoding="utf-8")):
            manual_translation_calls.append(relative(path))
    if manual_translation_calls:
        fail("composeApp/src/commonMain/kotlin", f"manual tr(en, fa) calls remain: {', '.join(manual_translation_calls[:8])}")

    required_locale_files = (
        "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/localization/AppLocale.kt",
        "composeApp/src/androidMain/kotlin/com/msa/iotofflinetoolbox/ui/localization/AppLocale.android.kt",
        "composeApp/src/desktopMain/kotlin/com/msa/iotofflinetoolbox/ui/localization/AppLocale.desktop.kt",
        "composeApp/src/iosMain/kotlin/com/msa/iotofflinetoolbox/ui/localization/AppLocale.ios.kt",
        "composeApp/src/webMain/kotlin/com/msa/iotofflinetoolbox/ui/localization/AppLocale.web.kt",
    )
    for rel in required_locale_files:
        if not (ROOT / rel).exists():
            fail(rel, "dynamic locale implementation is missing")
    build = (ROOT / "composeApp/build.gradle.kts").read_text(encoding="utf-8")
    for token in (
        "compose.resources",
        'packageOfResClass = "com.msa.iotofflinetoolbox.resources"',
        "generateResClass = always",
    ):
        if token not in build:
            fail("composeApp/build.gradle.kts", f"Compose resources configuration missing {token}")
    web_index = (ROOT / "composeApp/src/webMain/resources/index.html").read_text(encoding="utf-8")
    if "window.__customLocale" not in web_index or "Navigator.prototype" not in web_index:
        fail("composeApp/src/webMain/resources/index.html", "dynamic web locale override is missing")


def check_current_release_documentation() -> None:
    properties = read_gradle_properties()
    version = properties.get("appVersion", "")
    code = properties.get("appVersionCode", "")
    expected_markers = {
        "README.md": f"# IoT Offline Toolbox {version}",
        "docs/VERIFICATION.md": f"{version}",
        "docs/IMPLEMENTATION_REPORT.md": f"{version}",
        "docs/FINAL_AUDIT_REPORT.md": f"{version}",
        "docs/INDEPENDENT_RUNTIME_TESTS.txt": f"{version}",
        "docs/BUILD_ATTEMPT.txt": f"{version}",
        "RELEASE_CHECKLIST.md": f"Version `{version}` and build `{code}`",
    }
    for rel, marker in expected_markers.items():
        path = ROOT / rel
        if path.exists() and marker not in path.read_text(encoding="utf-8"):
            fail(rel, f"current release documentation must reference {marker!r}")


def check_source_checksums() -> None:
    manifest = ROOT / "docs/SOURCE_SHA256SUMS.txt"
    if not manifest.exists():
        fail(relative(manifest), "source checksum manifest is missing")
        return

    expected: dict[str, str] = {}
    for line_no, raw in enumerate(manifest.read_text(encoding="utf-8").splitlines(), start=1):
        if not raw.strip():
            continue
        match = re.fullmatch(r"([0-9a-f]{64})  \./(.+)", raw)
        if not match:
            fail(relative(manifest), f"invalid checksum line {line_no}")
            continue
        expected[match.group(2)] = match.group(1)

    actual_files: dict[str, Path] = {}
    for path in ROOT.rglob("*"):
        if not path.is_file() or any(part in {"build", ".gradle", ".git", ".kotlin", ".idea", "node_modules", "__pycache__"} for part in path.parts):
            continue
        rel = relative(path)
        if rel == "docs/SOURCE_SHA256SUMS.txt":
            continue
        actual_files[rel] = path

    missing = sorted(set(actual_files) - set(expected))
    stale = sorted(set(expected) - set(actual_files))
    if missing:
        fail(relative(manifest), f"files missing from checksum manifest: {', '.join(missing[:8])}")
    if stale:
        fail(relative(manifest), f"stale checksum entries: {', '.join(stale[:8])}")

    for rel, path in actual_files.items():
        expected_hash = expected.get(rel)
        if expected_hash is None:
            continue
        actual_hash = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual_hash != expected_hash:
            fail(rel, "SHA-256 does not match docs/SOURCE_SHA256SUMS.txt")

def main() -> int:
    kotlin_files = [path for path in sorted(ROOT.rglob("*.kt")) + sorted(ROOT.rglob("*.kts")) if not any(part in {"build", ".gradle"} for part in path.parts)]
    for path in kotlin_files:
        check_kotlin(path)
    xml_files = [path for path in ROOT.rglob("*.xml") if "build" not in path.parts]
    toml_files = [path for path in ROOT.rglob("*.toml") if "build" not in path.parts]
    json_files = [path for path in ROOT.rglob("*.json") if "build" not in path.parts]
    yaml_files = [path for extension in ("*.yml", "*.yaml") for path in ROOT.rglob(extension) if "build" not in path.parts]
    for path in xml_files: check_xml(path)
    for path in toml_files: check_toml(path)
    for path in json_files: check_json(path)
    for path in yaml_files: check_yaml(path)
    check_toolchain_compatibility()
    check_version_coherence()
    check_screen_exhaustiveness()
    check_platform_contracts()
    check_required_files()
    check_wrapper()
    check_repositories_and_sensitive_files()
    check_identity()
    check_responsive_contract()
    check_ui_quality_contract()
    check_transport_security_contract()
    check_deep_hardening_contract()
    check_settings_and_protocol_contracts()
    check_release_configuration_contracts()
    check_compile_regression_contracts()
    check_localization_resource_contracts()
    check_current_release_documentation()
    check_source_checksums()

    print(f"Kotlin/Gradle files checked: {len(kotlin_files)}")
    print(f"XML files checked: {len(xml_files)}")
    print(f"TOML files checked: {len(toml_files)}")
    print(f"JSON files checked: {len(json_files)}")
    print(f"YAML files checked: {len(yaml_files)}")
    for item in WARNINGS: print(f"WARNING: {item}")
    for item in ERRORS: print(f"ERROR: {item}")
    print(f"Warnings: {len(WARNINGS)}")
    print(f"Errors: {len(ERRORS)}")
    return 1 if ERRORS else 0


if __name__ == "__main__":
    sys.exit(main())
