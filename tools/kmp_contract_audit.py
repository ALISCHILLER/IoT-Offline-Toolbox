#!/usr/bin/env python3
"""Dependency-free KMP contract and internal-symbol audit.

This intentionally stays conservative: it validates project-owned imports and high-risk
interface/adapter signatures without pretending to replace the Kotlin compiler.
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "composeApp" / "src"
PROJECT_PREFIX = "com.msa.iotofflinetoolbox"
errors: list[str] = []


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def fail(path: str | Path, message: str) -> None:
    errors.append(f"ERROR: {rel(path) if isinstance(path, Path) else path}: {message}")


def strip_literals_and_comments(source: str) -> str:
    """Replace strings/comments with spaces while preserving newlines and brace positions."""
    out = list(source)
    i = 0
    n = len(source)
    state = "code"
    quote = ""
    while i < n:
        if state == "code":
            if source.startswith("//", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "line_comment"
                continue
            if source.startswith("/*", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "block_comment"
                continue
            if source.startswith('"""', i):
                out[i : i + 3] = [" ", " ", " "]
                i += 3
                state = "triple_string"
                continue
            if source[i] in {'"', "'"}:
                quote = source[i]
                out[i] = " "
                i += 1
                state = "string"
                continue
            i += 1
            continue
        if state == "line_comment":
            if source[i] == "\n":
                state = "code"
            else:
                out[i] = " "
            i += 1
            continue
        if state == "block_comment":
            if source.startswith("*/", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "code"
            else:
                if source[i] != "\n":
                    out[i] = " "
                i += 1
            continue
        if state == "triple_string":
            if source.startswith('"""', i):
                out[i : i + 3] = [" ", " ", " "]
                i += 3
                state = "code"
            else:
                if source[i] != "\n":
                    out[i] = " "
                i += 1
            continue
        if state == "string":
            if source[i] == "\\" and i + 1 < n:
                if source[i] != "\n":
                    out[i] = " "
                if source[i + 1] != "\n":
                    out[i + 1] = " "
                i += 2
                continue
            if source[i] == quote:
                out[i] = " "
                i += 1
                state = "code"
            else:
                if source[i] != "\n":
                    out[i] = " "
                i += 1
    return "".join(out)


def package_name(source: str) -> str:
    match = re.search(r"(?m)^\s*package\s+([\w.]+)", source)
    return match.group(1) if match else ""


def top_level_symbols(path: Path) -> set[str]:
    source = path.read_text(encoding="utf-8")
    package = package_name(source)
    if not package:
        return set()
    clean = strip_literals_and_comments(source)
    symbols: set[str] = set()
    depth = 0
    modifiers = r"(?:(?:public|internal|private|protected|expect|actual|data|sealed|enum|value|annotation|open|abstract|final|suspend|inline|tailrec|operator|infix|const|external|lateinit)\s+)*"
    type_re = re.compile(rf"\b{modifiers}(?:class|interface|object|typealias)\s+([A-Za-z_]\w*)")
    fun_re = re.compile(rf"\b{modifiers}fun\s+(?:<[^>]+>\s*)?(?:[^\s(]+\.)?([A-Za-z_]\w*)\s*\(")
    prop_re = re.compile(rf"\b{modifiers}(?:val|var)\s+(?:[^\s:=]+\.)?([A-Za-z_]\w*)\b")
    for line in clean.splitlines():
        if depth == 0:
            for regex in (type_re, fun_re, prop_re):
                match = regex.search(line)
                if match:
                    symbols.add(f"{package}.{match.group(1)}")
        depth += line.count("{") - line.count("}")
    return symbols


def check_internal_imports(paths: list[Path]) -> None:
    symbols: set[str] = set()
    packages: set[str] = set()
    for path in paths:
        source = path.read_text(encoding="utf-8")
        package = package_name(source)
        if package:
            packages.add(package)
        symbols.update(top_level_symbols(path))

    checked = 0
    for path in paths:
        source = path.read_text(encoding="utf-8")
        for match in re.finditer(
            rf"(?m)^\s*import\s+({re.escape(PROJECT_PREFIX)}(?:\.[\w]+)*(?:\.\*)?)(?:\s+as\s+\w+)?\s*$",
            source,
        ):
            target = match.group(1)
            if target.startswith(f"{PROJECT_PREFIX}.resources"):
                continue  # generated by Compose Resources
            checked += 1
            if target.endswith(".*"):
                package = target[:-2]
                if package not in packages:
                    fail(path, f"internal wildcard import points to an unknown project package: {target}")
                continue
            if target in symbols:
                continue
            # Nested type/member imports are valid when a project-owned parent declaration exists.
            parts = target.split(".")
            if any(".".join(parts[:index]) in symbols for index in range(len(parts) - 1, 3, -1)):
                continue
            fail(path, f"internal import does not resolve to a project declaration: {target}")
    print(f"Internal project imports checked: {checked}")


def find_matching(text: str, start: int, opening: str, closing: str) -> int:
    depth = 0
    for index in range(start, len(text)):
        char = text[index]
        if char == opening:
            depth += 1
        elif char == closing:
            depth -= 1
            if depth == 0:
                return index
    return -1


def declaration_block(source: str, kind: str, name: str) -> str:
    clean = strip_literals_and_comments(source)
    match = re.search(rf"\b{kind}\s+{re.escape(name)}\b", clean)
    if not match:
        return ""
    paren = bracket = angle = 0
    brace = -1
    for index in range(match.end(), len(clean)):
        char = clean[index]
        if char == "(":
            paren += 1
        elif char == ")":
            paren = max(0, paren - 1)
        elif char == "[":
            bracket += 1
        elif char == "]":
            bracket = max(0, bracket - 1)
        elif char == "<":
            angle += 1
        elif char == ">":
            angle = max(0, angle - 1)
        elif char == "{" and paren == 0 and bracket == 0 and angle == 0:
            brace = index
            break
    if brace < 0:
        return ""
    end = find_matching(clean, brace, "{", "}")
    return source[match.start() : end + 1] if end >= 0 else ""


@dataclass(frozen=True)
class FunSig:
    suspend: bool
    parameter_types: tuple[str, ...]
    return_type: str | None
    has_defaults: bool


def split_top_level(value: str) -> list[str]:
    parts: list[str] = []
    start = 0
    depths = {"(": 0, "[": 0, "<": 0, "{": 0}
    close_to_open = {")": "(", "]": "[", ">": "<", "}": "{"}
    for index, char in enumerate(value):
        if char in depths:
            depths[char] += 1
        elif char in close_to_open:
            opening = close_to_open[char]
            depths[opening] = max(0, depths[opening] - 1)
        elif char == "," and not any(depths.values()):
            parts.append(value[start:index].strip())
            start = index + 1
    tail = value[start:].strip()
    if tail:
        parts.append(tail)
    return parts


def split_default(value: str) -> tuple[str, bool]:
    depths = {"(": 0, "[": 0, "<": 0, "{": 0}
    close_to_open = {")": "(", "]": "[", ">": "<", "}": "{"}
    for index, char in enumerate(value):
        if char in depths:
            depths[char] += 1
        elif char in close_to_open:
            opening = close_to_open[char]
            depths[opening] = max(0, depths[opening] - 1)
        elif char == "=" and not any(depths.values()):
            return value[:index].strip(), True
    return value.strip(), False


def normalize_type(value: str) -> str:
    return re.sub(r"\s+", "", value)


def function_signature(block: str, name: str, require_override: bool = False) -> FunSig | None:
    clean = strip_literals_and_comments(block)
    prefix = r"\boverride\s+" if require_override else r"\b(?:override\s+)?"
    match = re.search(rf"{prefix}(suspend\s+)?fun\s+{re.escape(name)}\s*\(", clean)
    if not match:
        return None
    open_paren = clean.find("(", match.start())
    close_paren = find_matching(clean, open_paren, "(", ")")
    if close_paren < 0:
        return None
    params_source = block[open_paren + 1 : close_paren]
    types: list[str] = []
    has_defaults = False
    for parameter in split_top_level(params_source):
        base, had_default = split_default(parameter)
        has_defaults = has_defaults or had_default
        # Drop annotations/modifiers and split the declaration at the first top-level colon.
        colon = base.find(":")
        if colon < 0:
            types.append("<missing-type>")
        else:
            types.append(normalize_type(base[colon + 1 :]))
    tail = clean[close_paren + 1 : close_paren + 250]
    return_match = re.match(r"\s*:\s*([^={\n]+)", tail)
    return_type = normalize_type(return_match.group(1)) if return_match else None
    return FunSig(bool(match.group(1)), tuple(types), return_type, has_defaults)




def check_type_collisions(paths: list[Path]) -> None:
    declarations: list[tuple[str, str, str, Path]] = []
    type_re = re.compile(
        r"\b(?P<mods>(?:(?:public|internal|private|protected|expect|actual|data|sealed|enum|value|annotation|open|abstract|final)\s+)*)"
        r"(?:class|interface|object|typealias)\s+(?P<name>[A-Za-z_]\w*)"
    )
    for path in paths:
        source = path.read_text(encoding="utf-8")
        package = package_name(source)
        if not package:
            continue
        source_set_match = re.search(r"composeApp/src/([^/]+)/", rel(path))
        source_set = source_set_match.group(1) if source_set_match else ""
        clean = strip_literals_and_comments(source)
        depth = 0
        for line in clean.splitlines():
            if depth == 0:
                match = type_re.search(line)
                if match:
                    mods = match.group("mods").split()
                    marker = "expect" if "expect" in mods else "actual" if "actual" in mods else "plain"
                    declarations.append((f"{package}.{match.group('name')}", marker, source_set, path))
            depth += line.count("{") - line.count("}")

    closures = {
        "android": {"commonMain", "androidMain"},
        "desktop": {"commonMain", "desktopMain"},
        "ios": {"commonMain", "iosMain"},
        "js": {"commonMain", "webMain", "jsMain"},
        "wasmJs": {"commonMain", "webMain", "wasmJsMain"},
    }
    checked = 0
    for target, source_sets in closures.items():
        by_name: dict[str, list[tuple[str, str, Path]]] = {}
        for fqname, marker, source_set, path in declarations:
            if source_set in source_sets:
                by_name.setdefault(fqname, []).append((marker, source_set, path))
        for fqname, entries in by_name.items():
            if len(entries) <= 1:
                continue
            markers = [marker for marker, _, _ in entries]
            # One common expect plus one target/shared actual is the only intentional duplicate type.
            allowed = (
                len(entries) == 2
                and markers.count("expect") == 1
                and markers.count("actual") == 1
                and any(source_set == "commonMain" and marker == "expect" for marker, source_set, _ in entries)
            )
            if not allowed:
                locations = ", ".join(f"{source_set}:{rel(path)}" for _, source_set, path in entries)
                fail(entries[0][2], f"{target} source-set closure has duplicate type {fqname}: {locations}")
            checked += 1
    print(f"Cross-source-set type collisions checked: {checked}")

def check_protocol_client_contracts() -> None:
    ports_path = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/port/ProtocolClientPorts.kt"
    ports_source = ports_path.read_text(encoding="utf-8")
    adapters = [
        ("HttpOperationClient", "HttpToolClient", ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/HttpToolClient.kt"),
        ("WebSocketOperationClient", "WebSocketToolClient", ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/RealtimeClients.kt"),
        ("MqttOperationClient", "MqttWebSocketClient", ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/RealtimeClients.kt"),
        ("CoapOperationClient", "CoapClient", ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/CoapCodec.kt"),
    ]
    checked = 0
    for interface_name, class_name, adapter_path in adapters:
        interface_block = declaration_block(ports_source, "interface", interface_name)
        adapter_source = adapter_path.read_text(encoding="utf-8")
        class_block = declaration_block(adapter_source, "class", class_name)
        if not interface_block:
            fail(ports_path, f"missing protocol contract {interface_name}")
            continue
        if not class_block:
            fail(adapter_path, f"missing adapter class {class_name}")
            continue
        if not re.search(rf"\)\s*:\s*{re.escape(interface_name)}\b", strip_literals_and_comments(class_block)):
            fail(adapter_path, f"{class_name} must explicitly implement {interface_name}")
        expected = function_signature(interface_block, "execute")
        actual = function_signature(class_block, "execute", require_override=True)
        if expected is None or actual is None:
            fail(adapter_path, f"cannot resolve execute signature for {class_name}/{interface_name}")
            continue
        if actual.has_defaults:
            fail(adapter_path, f"{class_name}.execute must inherit defaults from {interface_name}; overrides cannot redeclare them")
        if (actual.suspend, actual.parameter_types, actual.return_type) != (
            expected.suspend,
            expected.parameter_types,
            expected.return_type,
        ):
            fail(
                adapter_path,
                f"{class_name}.execute signature drift: expected {expected}, actual {actual}",
            )
        checked += 1
    print(f"Protocol adapter signatures checked: {checked}")


def check_network_probe_contracts() -> None:
    port_path = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/port/NetworkProbe.kt"
    source = port_path.read_text(encoding="utf-8")
    interface_block = declaration_block(source, "interface", "NetworkProbe")
    if not interface_block:
        fail(port_path, "NetworkProbe interface missing")
        return
    required_methods = (
        "ping", "resolve", "tcpExchange", "udpExchange", "scanPorts", "discover", "discoverSsdp", "discoverMdns",
    )
    expected = {name: function_signature(interface_block, name) for name in required_methods}
    implementations = [
        (ROOT / "composeApp/src/androidMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.android.kt", "JavaNetworkProbe"),
        (ROOT / "composeApp/src/desktopMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.desktop.kt", "JavaNetworkProbe"),
        (ROOT / "composeApp/src/iosMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.ios.kt", "IosNetworkProbe"),
        (ROOT / "composeApp/src/webMain/kotlin/com/msa/iotofflinetoolbox/core/network/BrowserNetworkProbe.kt", "BrowserNetworkProbe"),
    ]
    checked = 0
    for path, class_name in implementations:
        source = path.read_text(encoding="utf-8") if path.exists() else ""
        block = declaration_block(source, "class", class_name)
        if not block:
            fail(path, f"missing NetworkProbe adapter {class_name}")
            continue
        if ": NetworkProbe" not in block:
            fail(path, f"{class_name} must implement NetworkProbe")
        if not re.search(r"\boverride\s+val\s+capabilities\b", strip_literals_and_comments(block)):
            fail(path, f"{class_name} must expose NetworkProbe.capabilities")
        for name in required_methods:
            actual = function_signature(block, name, require_override=True)
            wanted = expected[name]
            if actual is None or wanted is None:
                fail(path, f"{class_name} is missing override {name}")
                continue
            if actual.has_defaults:
                fail(path, f"{class_name}.{name} must not redeclare interface default values")
            if (actual.suspend, actual.parameter_types, actual.return_type) != (
                wanted.suspend,
                wanted.parameter_types,
                wanted.return_type,
            ):
                fail(path, f"{class_name}.{name} signature drift: expected {wanted}, actual {actual}")
        checked += 1
    print(f"NetworkProbe implementations checked: {checked}")


def check_browser_source_set_contract() -> None:
    shared = ROOT / "composeApp/src/webMain/kotlin/com/msa/iotofflinetoolbox/core/network/BrowserNetworkProbe.kt"
    if not shared.exists():
        fail(shared, "shared browser NetworkProbe implementation is missing")
    for source_set, expected_platform in (("jsMain", "Browser JS"), ("wasmJsMain", "Browser Wasm")):
        path = ROOT / f"composeApp/src/{source_set}/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.{ 'js' if source_set == 'jsMain' else 'wasmJs' }.kt"
        source = path.read_text(encoding="utf-8") if path.exists() else ""
        if "class LimitedNetworkProbe" in source or "class BrowserNetworkProbe" in source:
            fail(path, "browser NetworkProbe implementation must remain consolidated in webMain")
        for token in ("actual fun createPlatformNetworkProbe(): NetworkProbe", "BrowserNetworkProbe(", f'platform = "{expected_platform}"'):
            if token not in source:
                fail(path, f"browser actual contract missing {token}")


def main() -> int:
    kotlin_files = sorted(SRC.rglob("*.kt"))
    check_internal_imports(kotlin_files)
    check_type_collisions(kotlin_files)
    check_protocol_client_contracts()
    check_network_probe_contracts()
    check_browser_source_set_contract()
    print(f"Kotlin files inspected: {len(kotlin_files)}")
    print(f"Errors: {len(errors)}")
    for error in errors:
        print(error)
    if errors:
        return 1
    print("KMP_CONTRACT_AUDIT_PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
