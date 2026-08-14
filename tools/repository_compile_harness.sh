#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/msa-iot-repository-compile"
rm -rf "$WORK"
mkdir -p \
  "$WORK/com/msa/iotofflinetoolbox/core/data" \
  "$WORK/com/msa/iotofflinetoolbox/core/model" \
  "$WORK/com/msa/iotofflinetoolbox/core/port" \
  "$WORK/com/msa/iotofflinetoolbox/core/security" \
  "$WORK/com/russhwolf/settings" \
  "$WORK/kotlinx/serialization/json" \
  "$WORK/kotlinx/serialization"

cp "$ROOT"/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/model/*.kt \
  "$WORK/com/msa/iotofflinetoolbox/core/model/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/port/ToolboxPersistence.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/port/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/security/SecretRedactor.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/security/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/data/ToolboxRepository.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/data/"

# The host compiler in this dependency-free harness is Kotlin 1.9. Production uses Kotlin 2.4,
# where kotlin.time.Clock is available. Replace only the default clock expression so the harness
# can still compile the real repository structure and catch unresolved project symbols/imports.
python3 - "$WORK/com/msa/iotofflinetoolbox/core/data/ToolboxRepository.kt" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1])
s=p.read_text()
s=s.replace('{ kotlin.time.Clock.System.now().toEpochMilliseconds() }', '{ 0L }')
p.write_text(s)
PY

cat > "$WORK/kotlinx/serialization/Serializable.kt" <<'KT'
package kotlinx.serialization

@Target(AnnotationTarget.CLASS)
annotation class Serializable

inline fun <reified T> kotlinx.serialization.json.Json.decodeFromString(value: String): T =
    error("compile-only serialization stub")

inline fun <reified T> kotlinx.serialization.json.Json.encodeToString(value: T): String = "{}"
KT

cat > "$WORK/kotlinx/serialization/json/Json.kt" <<'KT'
package kotlinx.serialization.json

class Json {
    constructor(builder: JsonBuilder.() -> Unit = {}) { JsonBuilder().builder() }
    constructor(from: Json, builder: JsonBuilder.() -> Unit = {}) { JsonBuilder().builder() }
}

class JsonBuilder {
    var ignoreUnknownKeys: Boolean = false
    var encodeDefaults: Boolean = false
    var prettyPrint: Boolean = false
    var explicitNulls: Boolean = true
}
KT

cat > "$WORK/com/russhwolf/settings/Settings.kt" <<'KT'
package com.russhwolf.settings

class Settings {
    private val values = mutableMapOf<String, String>()
    internal fun read(key: String): String? = values[key]
    internal fun write(key: String, value: String) { values[key] = value }
}

operator fun Settings.get(key: String): String? = read(key)
operator fun Settings.set(key: String, value: String) = write(key, value)
KT

kotlinc "$WORK" -d "$WORK/repository.jar"
echo REPOSITORY_COMPILE_HARNESS_PASSED
