#!/usr/bin/env bash
set -euo pipefail

run_audit() {
  python3 tools/static_audit.py
  python3 tools/deep_quality_audit.py
  python3 tools/ui_quality_audit.py
  bash -n tools/*.sh
  if command -v shellcheck >/dev/null 2>&1; then
    shellcheck tools/*.sh
  fi
}

run_runtime_harnesses() {
  if ! command -v kotlinc >/dev/null 2>&1; then
    echo "kotlinc is required for dependency-free runtime harnesses." >&2
    return 4
  fi
  bash tools/core_runtime_harness.sh
  bash tools/secret_redactor_runtime_harness.sh
  bash tools/operation_coordinator_runtime_harness.sh
  bash tools/protocol_runtime_harness.sh
  bash tools/responsive_runtime_harness.sh
  bash tools/desktop_network_runtime_harness.sh
}

MODE="${1:---static}"
case "$MODE" in
  --audit)
    run_audit
    echo "Verification Status: Statically Reviewed (dependency-free audit)"
    ;;
  --static)
    run_audit
    run_runtime_harnesses
    echo "Verification Status: Statically Reviewed with independent Kotlin runtime harnesses"
    ;;
  --jvm-android)
    ./gradlew \
      :composeApp:compileKotlinDesktop \
      :composeApp:desktopTest \
      :composeApp:testAndroidHostTest \
      :androidApp:assembleDebug \
      :androidApp:assembleRelease \
      :androidApp:lintDebug \
      :androidApp:lintRelease \
      --stacktrace
    echo "Verification Status: JVM and Android Gradle checks executed"
    ;;
  --web)
    ./gradlew \
      :composeApp:jsBrowserTest \
      :composeApp:wasmJsBrowserTest \
      :composeApp:jsBrowserProductionWebpack \
      :composeApp:wasmJsBrowserProductionWebpack \
      --stacktrace
    echo "Verification Status: JS and Wasm Gradle checks executed"
    ;;
  --ios)
    if [[ "$(uname -s)" != "Darwin" ]]; then
      echo "iOS verification requires macOS and Xcode." >&2
      exit 3
    fi
    ./gradlew \
      :composeApp:iosSimulatorArm64Test \
      :composeApp:linkReleaseFrameworkIosSimulatorArm64 \
      --stacktrace
    echo "Verification Status: iOS simulator checks executed"
    ;;
  --all)
    "$0" --static
    "$0" --jvm-android
    "$0" --web
    if [[ "$(uname -s)" == "Darwin" ]]; then "$0" --ios; fi
    ;;
  *)
    echo "Usage: $0 [--audit|--static|--jvm-android|--web|--ios|--all]" >&2
    exit 2
    ;;
esac
