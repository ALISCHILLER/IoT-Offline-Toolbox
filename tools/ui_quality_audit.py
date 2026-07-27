#!/usr/bin/env python3
"""Deterministic UI quality checks that do not require Gradle or a renderer.

This script validates source-level UI contracts and computes WCAG contrast for the
actual Material theme values declared in ToolboxTheme.kt. It deliberately does
not claim screenshot, runtime, focus, or platform accessibility verification.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
THEME = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/theme/ToolboxTheme.kt"
UI_ROOT = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui"
APP = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/App.kt"
COMMON = UI_ROOT / "components/CommonComponents.kt"


def extract_parenthesized(text: str, marker: str) -> str:
    start = text.find(marker)
    if start < 0:
        raise ValueError(f"Missing marker: {marker}")
    open_index = text.find("(", start + len(marker))
    if open_index < 0:
        raise ValueError(f"Missing opening parenthesis after: {marker}")
    depth = 0
    for index in range(open_index, len(text)):
        char = text[index]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return text[open_index + 1:index]
    raise ValueError(f"Unclosed block: {marker}")


def parse_arg_block(block: str, constants: dict[str, int]) -> dict[str, int]:
    colors: dict[str, int] = {}
    for raw_line in block.splitlines():
        line = raw_line.strip()
        match = re.match(r"([A-Za-z][A-Za-z0-9_]*)\s*=\s*([^,]+),?$", line)
        if not match:
            continue
        name, expression = match.groups()
        expression = expression.strip()
        value: int | None = None
        color_match = re.fullmatch(r"Color\(0xFF([0-9A-Fa-f]{6})\)", expression)
        if color_match:
            value = int(color_match.group(1), 16)
        elif expression == "Color.White":
            value = 0xFFFFFF
        elif expression == "Color.Black":
            value = 0x000000
        elif expression in constants:
            value = constants[expression]
        if value is not None:
            colors[name] = value
    return colors


def srgb_channel(value: int) -> float:
    channel = value / 255.0
    return channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4


def luminance(rgb: int) -> float:
    r = srgb_channel((rgb >> 16) & 0xFF)
    g = srgb_channel((rgb >> 8) & 0xFF)
    b = srgb_channel(rgb & 0xFF)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(first: int, second: int) -> float:
    high, low = sorted((luminance(first), luminance(second)), reverse=True)
    return (high + 0.05) / (low + 0.05)


def hex_color(value: int) -> str:
    return f"#{value:06X}"


def check_pair(
    failures: list[str],
    report: list[str],
    palette_name: str,
    palette: dict[str, int],
    foreground: str,
    background: str,
    threshold: float = 4.5,
) -> None:
    if foreground not in palette or background not in palette:
        failures.append(f"{palette_name}: missing {foreground}/{background}")
        return
    ratio = contrast(palette[foreground], palette[background])
    report.append(
        f"{palette_name:18} {foreground:22} on {background:24} "
        f"{ratio:5.2f}:1  {hex_color(palette[foreground])}/{hex_color(palette[background])}"
    )
    if ratio + 1e-9 < threshold:
        failures.append(
            f"{palette_name}: {foreground} on {background} is {ratio:.2f}:1; required {threshold:.1f}:1"
        )


def main() -> int:
    failures: list[str] = []
    report: list[str] = []
    theme_text = THEME.read_text(encoding="utf-8")

    constants = {
        match.group(1): int(match.group(2), 16)
        for match in re.finditer(
            r"private\s+val\s+([A-Za-z][A-Za-z0-9_]*)\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)",
            theme_text,
        )
    }
    try:
        light = parse_arg_block(extract_parenthesized(theme_text, "private val LightColors = lightColorScheme"), constants)
        dark = parse_arg_block(extract_parenthesized(theme_text, "private val DarkColors = darkColorScheme"), constants)
    except ValueError as error:
        failures.append(str(error))
        light, dark = {}, {}

    semantic_pairs = [
        ("onPrimary", "primary"),
        ("onPrimaryContainer", "primaryContainer"),
        ("onSecondary", "secondary"),
        ("onSecondaryContainer", "secondaryContainer"),
        ("onTertiary", "tertiary"),
        ("onTertiaryContainer", "tertiaryContainer"),
        ("onBackground", "background"),
        ("onSurface", "surface"),
        ("onSurfaceVariant", "surfaceVariant"),
        ("onSurface", "surfaceContainerLowest"),
        ("onSurface", "surfaceContainerLow"),
        ("onSurface", "surfaceContainer"),
        ("onSurface", "surfaceContainerHigh"),
        ("onSurface", "surfaceContainerHighest"),
        ("onError", "error"),
        ("onErrorContainer", "errorContainer"),
    ]
    for name, palette in (("Light scheme", light), ("Dark scheme", dark)):
        for foreground, background in semantic_pairs:
            check_pair(failures, report, name, palette, foreground, background)

    # Extract the default/light semantic token block and the dark semantic token block.
    try:
        default_tokens = parse_arg_block(
            extract_parenthesized(theme_text, "LocalToolboxUiTokens = staticCompositionLocalOf"),
            constants,
        )
        dark_marker = "val tokens = if (darkMode)"
        dark_tokens = parse_arg_block(extract_parenthesized(theme_text, dark_marker), constants)
    except ValueError as error:
        failures.append(str(error))
        default_tokens, dark_tokens = {}, {}

    token_pairs = [
        ("onSuccess", "success"),
        ("onSuccessContainer", "successContainer"),
        ("onWarning", "warning"),
        ("onWarningContainer", "warningContainer"),
        ("onInfo", "info"),
        ("onInfoContainer", "infoContainer"),
    ]
    for name, palette in (("Light UI tokens", default_tokens), ("Dark UI tokens", dark_tokens)):
        for foreground, background in token_pairs:
            check_pair(failures, report, name, palette, foreground, background)

    app_text = APP.read_text(encoding="utf-8")
    common_text = COMMON.read_text(encoding="utf-8")
    all_ui_text = "\n".join(path.read_text(encoding="utf-8") for path in UI_ROOT.rglob("*.kt"))

    source_contracts = {
        "SnackbarHost is used for non-blocking feedback": "SnackbarHost(" in app_text,
        "safe-drawing insets are applied": "WindowInsets.safeDrawing" in app_text,
        "IME padding is applied": ".imePadding()" in app_text,
        "drawer, rail and sidebar modes exist": all(
            token in app_text for token in ("ModalNavigationDrawer", "NavigationRailShell", "DesktopShell")
        ),
        "shared status banner exists": "fun StatusBanner(" in common_text,
        "shared selectable code surface exists": "SelectionContainer" in common_text and "fun CodeSurface(" in common_text,
        "shared non-interactive information pill exists": "fun InfoPill(" in common_text,
        "no fake no-op AssistChip remains": "AssistChip(onClick = {})" not in all_ui_text,
        "no raw screen Card bypasses the shared design system": not any(
            re.search(r"^\s*Card\(", path.read_text(encoding="utf-8"), flags=re.MULTILINE)
            for path in (UI_ROOT / "screens").glob("*.kt")
        ),
        "known user-facing strings use Compose resources": not any(
            token in all_ui_text
            for token in (
                '"Invalid request"',
                'Text("Payload (',
                'Text("IPv4 CIDR")',
                'label = "DNS lookup"',
                '"TRUNCATED"',
            )
        ),
        "HTTP UI uses the shared request-validation boundary": all(
            token in all_ui_text
            for token in ("HttpRequestValidator.firstIssue", "HttpRequestValidationPolicy(")
        ),
        "HTTP response UI exposes safe text and binary evidence views": all(
            token in all_ui_text
            for token in ("HttpResponseView.HEX", "HttpResponseView.BASE64", "http_binary_body_desc")
        ),
        "HTTP request preview supports Bash and PowerShell": all(
            token in all_ui_text
            for token in ("CurlShell.POSIX", "CurlShell.POWERSHELL", "http_preview_powershell")
        ),
    }
    for description, passed in source_contracts.items():
        report.append(f"SOURCE CONTRACT     {'PASS' if passed else 'FAIL'}  {description}")
        if not passed:
            failures.append(description)

    print("UI quality audit")
    print("=" * 80)
    for line in report:
        print(line)
    print("=" * 80)
    print(f"Contrast pairs checked: {len([line for line in report if ':1' in line])}")
    print(f"Source contracts checked: {len(source_contracts)}")
    print(f"Errors: {len(failures)}")
    if failures:
        for failure in failures:
            print(f"ERROR: {failure}")
        return 1
    print("UI_QUALITY_AUDIT_PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
