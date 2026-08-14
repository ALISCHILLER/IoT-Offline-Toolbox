#!/usr/bin/env bash
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/msa-operation-coordinator-harness"
KOTLIN_HOME="${KOTLIN_HOME:-$(CDPATH= cd -- "$(dirname -- "$(command -v kotlinc)")/.." && pwd)}"
COROUTINES_JAR="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
[[ -f "$COROUTINES_JAR" ]] || { echo "Missing coroutines runtime" >&2; exit 4; }
rm -rf "$WORK"
mkdir -p "$WORK/com/msa/iotofflinetoolbox/core/store"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/OperationCoordinator.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/store/OperationCoordinator.kt"
cat > "$WORK/Main.kt" <<'KOTLIN'
package com.msa.iotofflinetoolbox.core.store

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

fun main() = runBlocking {
    val events = mutableListOf<String>()
    val firstStarted = CompletableDeferred<Unit>()
    val coordinator = OperationCoordinator(this)
    coordinator.launch(
        onStart = { events += "first-start"; firstStarted.complete(Unit) },
        onFailure = { error("unexpected first failure: $it") },
        onFinish = { events += "first-finish" },
    ) {
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                delay(75)
                events += "first-cleanup"
            }
        }
    }
    firstStarted.await()
    check(coordinator.cancelCurrent())
    check(!coordinator.cancelCurrent())

    val secondFinished = CompletableDeferred<Unit>()
    coordinator.launch(
        onStart = { events += "second-start" },
        onFailure = { error("unexpected second failure: $it") },
        onFinish = { events += "second-finish"; secondFinished.complete(Unit) },
    ) { events += "second-body" }
    secondFinished.await()

    check(events.indexOf("first-cleanup") < events.indexOf("second-start")) { events }
    check("first-finish" !in events) { events }
    check(events.takeLast(3) == listOf("second-start", "second-body", "second-finish")) { events }
    coordinator.close()
    println("OPERATION_COORDINATOR_RUNTIME_HARNESS_PASSED")
}
KOTLIN
mapfile -t SOURCES < <(find "$WORK" -name '*.kt' -print | sort)
kotlinc "${SOURCES[@]}" -cp "$COROUTINES_JAR" -include-runtime -d "$WORK/operation-coordinator.jar"
java -cp "$WORK/operation-coordinator.jar:$COROUTINES_JAR" com.msa.iotofflinetoolbox.core.store.MainKt
