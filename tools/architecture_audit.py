#!/usr/bin/env python3
"""Dependency-direction and application-architecture audit for the shared KMP source set."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMMON = ROOT / "composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox"
ERRORS: list[str] = []


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT)).replace("\\", "/")


def fail(path: Path | str, message: str) -> None:
    ERRORS.append(f"{path}: {message}")


def imports(path: Path) -> list[str]:
    source = path.read_text(encoding="utf-8")
    return re.findall(r"(?m)^import\s+([^\s]+)", source)


def reject_prefixes(root: Path, forbidden: tuple[str, ...], reason: str) -> None:
    for path in sorted(root.rglob("*.kt")):
        for imported in imports(path):
            if imported.startswith(forbidden):
                fail(rel(path), f"{reason}: {imported}")


def check_model_is_pure() -> None:
    root = COMMON / "core/model"
    allowed = ("kotlin.", "kotlinx.serialization.")
    for path in sorted(root.rglob("*.kt")):
        for imported in imports(path):
            if not imported.startswith(allowed):
                fail(rel(path), f"domain model imports a non-domain dependency: {imported}")


def check_dependency_direction() -> None:
    reject_prefixes(
        COMMON / "core/port",
        (
            "com.msa.iotofflinetoolbox.core.data",
            "com.msa.iotofflinetoolbox.core.network",
            "com.msa.iotofflinetoolbox.core.store",
            "com.msa.iotofflinetoolbox.ui",
        ),
        "application ports must remain adapter and UI independent",
    )
    reject_prefixes(
        COMMON / "core/policy",
        (
            "com.msa.iotofflinetoolbox.core.data",
            "com.msa.iotofflinetoolbox.core.network",
            "com.msa.iotofflinetoolbox.core.store",
            "com.msa.iotofflinetoolbox.ui",
            "io.ktor.",
            "android.",
            "platform.",
            "java.",
        ),
        "shared policy must remain adapter, UI and platform independent",
    )
    reject_prefixes(
        COMMON / "ui",
        (
            "com.msa.iotofflinetoolbox.core.data",
            "com.msa.iotofflinetoolbox.core.network",
        ),
        "presentation must depend on application/model/policy contracts, not infrastructure adapters",
    )
    reject_prefixes(
        COMMON / "core/security",
        (
            "com.msa.iotofflinetoolbox.core.data",
            "com.msa.iotofflinetoolbox.core.network",
            "com.msa.iotofflinetoolbox.core.store",
            "com.msa.iotofflinetoolbox.ui",
        ),
        "security policy must remain independent",
    )
    reject_prefixes(
        COMMON / "core/data",
        (
            "com.msa.iotofflinetoolbox.core.network",
            "com.msa.iotofflinetoolbox.core.store",
            "com.msa.iotofflinetoolbox.ui",
        ),
        "data adapter points inward only",
    )
    reject_prefixes(
        COMMON / "core/network",
        (
            "com.msa.iotofflinetoolbox.core.data",
            "com.msa.iotofflinetoolbox.core.store",
            "com.msa.iotofflinetoolbox.ui",
        ),
        "network adapter must not depend on data, application store or UI",
    )
    reject_prefixes(
        COMMON / "core/store",
        (
            "com.msa.iotofflinetoolbox.core.data",
            "com.msa.iotofflinetoolbox.core.network",
            "com.msa.iotofflinetoolbox.ui",
            "android.",
            "androidx.compose.",
            "platform.UIKit",
            "java.",
        ),
        "application orchestration must depend on ports/policy/model, never adapters or UI",
    )


def check_composition_root() -> None:
    composition = COMMON / "ToolboxComposition.kt"
    store = COMMON / "core/store/ToolboxStore.kt"
    if not composition.exists():
        fail(rel(composition), "production composition root is missing")
        return
    composition_source = composition.read_text(encoding="utf-8")
    for token in (
        "ToolboxRepository(",
        "createPlatformNetworkProbe()",
        "HttpToolClient()",
        "WebSocketToolClient()",
        "MqttWebSocketClient()",
        "CoapClient(networkProbe)",
    ):
        if token not in composition_source:
            fail(rel(composition), f"composition root missing production adapter: {token}")

    store_source = store.read_text(encoding="utf-8")
    for concrete in (
        "ToolboxRepository",
        "HttpToolClient",
        "WebSocketToolClient",
        "MqttWebSocketClient",
        "CoapClient",
        "createPlatformNetworkProbe",
    ):
        if re.search(rf"\b{re.escape(concrete)}\b", store_source):
            fail(rel(store), f"state holder must not construct or import concrete adapter: {concrete}")
    for contract in (
        "ToolboxPersistence",
        "NetworkProbe",
        "HttpOperationClient",
        "WebSocketOperationClient",
        "MqttOperationClient",
        "CoapOperationClient",
    ):
        if contract not in store_source:
            fail(rel(store), f"state holder dependency contract missing: {contract}")


def check_state_holder_cohesion() -> None:
    required = {
        "facade": COMMON / "core/store/ToolboxStore.kt",
        "feedback model": COMMON / "core/store/AppFeedback.kt",
        "navigation manager": COMMON / "core/store/ToolboxNavigationManager.kt",
        "operation runner": COMMON / "core/store/ToolboxOperationRunner.kt",
        "diagnostics operations": COMMON / "core/store/ToolboxDiagnosticsOperations.kt",
        "protocol operations": COMMON / "core/store/ToolboxProtocolOperations.kt",
        "payload operations": COMMON / "core/store/ToolboxPayloadOperations.kt",
        "activity journal": COMMON / "core/store/ToolboxActivityJournal.kt",
        "inventory context": COMMON / "core/store/ToolboxInventoryContext.kt",
        "device inventory": COMMON / "core/store/ToolboxDeviceInventory.kt",
        "profile inventory": COMMON / "core/store/ToolboxProfileInventory.kt",
        "template inventory": COMMON / "core/store/ToolboxTemplateInventory.kt",
        "settings manager": COMMON / "core/store/ToolboxSettingsManager.kt",
        "state loader": COMMON / "core/store/ToolboxStateLoader.kt",
        "message policy": COMMON / "core/store/ToolboxMessages.kt",
        "runtime boundary": COMMON / "core/store/ToolboxRuntime.kt",
        "action contracts": COMMON / "core/store/ToolboxActions.kt",
    }
    for label, path in required.items():
        if not path.exists():
            fail(rel(path), f"required {label} component is missing")

    limits = {
        "facade": 220,
        "navigation manager": 80,
        "operation runner": 160,
        "diagnostics operations": 300,
        "protocol operations": 250,
        "payload operations": 80,
        "inventory context": 80,
        "device inventory": 180,
        "profile inventory": 130,
        "template inventory": 120,
    }
    for label, maximum in limits.items():
        path = required[label]
        if path.exists():
            line_count = len(path.read_text(encoding="utf-8").splitlines())
            if line_count > maximum:
                fail(rel(path), f"{label} exceeds {maximum} lines: {line_count}")

    store = required["facade"]
    if store.exists():
        source = store.read_text(encoding="utf-8")
        for token in (
            "ToolboxNavigationManager(",
            "ToolboxOperationRunner(",
            "ToolboxDiagnosticsOperations(",
            "ToolboxProtocolOperations(",
            "ToolboxPayloadOperations(",
            "ToolboxInventoryContext(",
            "ToolboxDeviceInventory(",
            "ToolboxProfileInventory(",
            "ToolboxTemplateInventory(",
            "ToolboxSettingsManager(",
            "ToolboxStateLoader(",
        ):
            if token not in source:
                fail(rel(store), f"facade delegation boundary missing: {token}")
        if "_state.update" not in source:
            fail(rel(store), "StateFlow mutations must use atomic update")
        if "_state.value = _state.value.copy" in source:
            fail(rel(store), "non-atomic read/copy/write state mutation remains")
        if "Clock.System" in source or "Random." in source:
            fail(rel(store), "clock and randomness must enter through ToolboxRuntime")

    persistence_port = COMMON / "core/port/ToolboxPersistence.kt"
    if persistence_port.exists():
        source = persistence_port.read_text(encoding="utf-8")
        for contract in (
            "SettingsPersistence",
            "DevicePersistence",
            "ProfilePersistence",
            "TemplatePersistence",
            "HistoryPersistence",
            "LogPersistence",
            "BackupPersistence",
            "SettingsBackupPersistence",
            "ToolboxSnapshotReader",
        ):
            if f"interface {contract}" not in source:
                fail(rel(persistence_port), f"segregated persistence port missing: {contract}")

    narrow_persistence_contracts = {
        "ToolboxActivityJournal.kt": "ActivityPersistence",
        "ToolboxDeviceInventory.kt": "DevicePersistence",
        "ToolboxProfileInventory.kt": "ProfilePersistence",
        "ToolboxTemplateInventory.kt": "TemplatePersistence",
        "ToolboxSettingsManager.kt": "SettingsBackupPersistence",
        "ToolboxStateLoader.kt": "ToolboxSnapshotReader",
        "ToolboxNavigationManager.kt": "SettingsPersistence",
    }
    for filename, contract in narrow_persistence_contracts.items():
        path = COMMON / "core/store" / filename
        if not path.exists():
            continue
        source = path.read_text(encoding="utf-8")
        if contract not in source:
            fail(rel(path), f"service must depend on its narrow persistence contract: {contract}")
        if "ToolboxPersistence" in source:
            fail(rel(path), "application service must not depend on the full persistence aggregate")

    feedback_model = required["feedback model"]
    app_state = COMMON / "core/store/AppState.kt"
    if feedback_model.exists() and app_state.exists():
        feedback_source = feedback_model.read_text(encoding="utf-8")
        state_source = app_state.read_text(encoding="utf-8")
        for token in ("enum class FeedbackKind", "data class AppFeedback", "val source: String?"):
            if token not in feedback_source:
                fail(rel(feedback_model), f"typed feedback contract missing: {token}")
        if "val feedback: AppFeedback?" not in state_source:
            fail(rel(app_state), "AppState must expose typed AppFeedback")
        if "errorMessage: String?" in state_source or "noticeMessage: String?" in state_source:
            fail(rel(app_state), "ambiguous String? feedback fields must not return")

    actions = required["action contracts"]
    if actions.exists():
        source = actions.read_text(encoding="utf-8")
        for token in ("DiagnosticsActions", "ProtocolActions", "PayloadActions", "OperationActions", "DeviceInventoryActions", "ProfileInventoryActions", "TemplateInventoryActions"):
            if token not in source:
                fail(rel(actions), f"feature intent segregation missing: {token}")
        if "NetworkToolActions" in source:
            fail(rel(actions), "monolithic NetworkToolActions contract must not return")

    navigation = required["navigation manager"]
    if navigation.exists():
        source = navigation.read_text(encoding="utf-8")
        for token in ("SettingsPersistence", "saveSettings", "persistenceFailure"):
            if token not in source:
                fail(rel(navigation), f"navigation persistence boundary missing: {token}")

    runner = required["operation runner"]
    if runner.exists():
        source = runner.read_text(encoding="utf-8")
        for token in ("OperationCoordinator", "requireCapability", "TransportSecurity.isPublicCleartext", "SecretRedactor.redact"):
            if token not in source:
                fail(rel(runner), f"shared operation lifecycle/security policy missing: {token}")

    diagnostics = required["diagnostics operations"]
    if diagnostics.exists():
        source = diagnostics.read_text(encoding="utf-8")
        for token in ("DiagnosticsActions", "NetworkProbe", "PortSpecParser", "DiscoveryEndpoints"):
            if token not in source:
                fail(rel(diagnostics), f"diagnostics use-case boundary missing: {token}")

    protocol = required["protocol operations"]
    if protocol.exists():
        source = protocol.read_text(encoding="utf-8")
        for token in ("ProtocolActions", "HttpOperationClient", "HttpRequestValidator.firstIssue", "result.statusCode in HTTP_SUCCESS_CODES"):
            if token not in source:
                fail(rel(protocol), f"protocol use-case boundary missing: {token}")

    journal = required["activity journal"]
    if journal.exists():
        source = journal.read_text(encoding="utf-8")
        for token in ("SecretRedactor.redact", "retainLogs", "retainHistory", "persistenceFailure"):
            if token not in source:
                fail(rel(journal), f"activity journal policy missing: {token}")

    inventory_context = required["inventory context"]
    if inventory_context.exists():
        source = inventory_context.read_text(encoding="utf-8")
        for token in ("MAX_PERSISTED_ITEMS", "MAX_TEMPLATE_BYTES", "MAX_HEADER_BLOCK_LENGTH"):
            if token not in source:
                fail(rel(inventory_context), f"shared inventory limit missing: {token}")

    device_inventory = required["device inventory"]
    if device_inventory.exists() and "SecretRedactor.redact" not in device_inventory.read_text(encoding="utf-8"):
        fail(rel(device_inventory), "device inventory must redact durable metadata")

    profile_inventory = required["profile inventory"]
    if profile_inventory.exists() and "removeSensitiveHeaders" not in profile_inventory.read_text(encoding="utf-8"):
        fail(rel(profile_inventory), "profile inventory must remove sensitive headers")

    template_inventory = required["template inventory"]
    if template_inventory.exists():
        source = template_inventory.read_text(encoding="utf-8")
        for token in ("redactedContent", "MAX_TEMPLATE_BYTES"):
            if token not in source:
                fail(rel(template_inventory), f"template inventory policy missing: {token}")

    settings = required["settings manager"]
    if settings.exists():
        source = settings.read_text(encoding="utf-8")
        for token in ("SettingsAndBackupActions", "exportBackup", "importBackup", "trimLogs", "trimHistory"):
            if token not in source:
                fail(rel(settings), f"settings/backup policy missing: {token}")

    loader = required["state loader"]
    if loader.exists():
        source = loader.read_text(encoding="utf-8")
        required_loads = ("loadSettings()", "loadDevices()", "loadProfiles()", "loadTemplates()", "loadHistory()", "loadLogs()")
        drain = source.find("drainRecoveryEvents()")
        if drain < 0 or any(source.find(token) < 0 or source.find(token) > drain for token in required_loads):
            fail(rel(loader), "all durable collections must load before recovery events are drained")
        if "SecretRedactor.redact(event.message)" not in source:
            fail(rel(loader), "recovery evidence must be defensively redacted")

def check_policy_placement() -> None:
    network_root = COMMON / "core/network"
    runtime_policy = COMMON / "core/policy/ProtocolRuntimePolicy.kt"
    if not runtime_policy.exists():
        fail(rel(runtime_policy), "protocol runtime policy must live outside the network adapter")
    elif any(token not in runtime_policy.read_text(encoding="utf-8") for token in ("calculateHttpTimeoutBudget", "mqttRequiresReceiveLoop", "validateWebSocketHeaders")):
        fail(rel(runtime_policy), "protocol runtime policy is incomplete")
    legacy_runtime_policy = network_root / "NetworkValidation.kt"
    if legacy_runtime_policy.exists():
        fail(rel(legacy_runtime_policy), "pure protocol policy must not live inside the infrastructure network package")
    for legacy in (
        "TransportSecurity.kt",
        "HeaderParser.kt",
        "HttpRequestTools.kt",
        "HttpRequestValidation.kt",
        "HttpResponseBodyTools.kt",
        "IpTools.kt",
    ):
        path = network_root / legacy
        if path.exists():
            fail(rel(path), "pure policy must not live in the infrastructure network package")
    policy_root = COMMON / "core/policy"
    for required in (
        "TransportSecurity.kt",
        "HeaderParser.kt",
        "HttpRequestTools.kt",
        "HttpRequestValidation.kt",
        "HttpResponseBodyTools.kt",
        "NetworkInputPolicy.kt",
        "DiscoveryEndpoints.kt",
    ):
        path = policy_root / required
        if not path.exists():
            fail(rel(path), "required shared policy component is missing")


def check_platform_boundaries() -> None:
    for path in sorted((COMMON / "core").rglob("*.kt")):
        source = path.read_text(encoding="utf-8")
        for token in ("android.content.", "android.net.", "platform.UIKit.", "java.net.", "java.io."):
            if token in source:
                fail(rel(path), f"platform API leaked into commonMain: {token}")


def main() -> int:
    check_model_is_pure()
    check_dependency_direction()
    check_composition_root()
    check_state_holder_cohesion()
    check_policy_placement()
    check_platform_boundaries()
    print("Architecture files checked:", len(list(COMMON.rglob("*.kt"))))
    for error in ERRORS:
        print("ERROR:", error)
    print("Errors:", len(ERRORS))
    return 1 if ERRORS else 0


if __name__ == "__main__":
    sys.exit(main())
