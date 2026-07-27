#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/msa-iot-secret-redactor-harness"
rm -rf "$WORK"
mkdir -p "$WORK/com/msa/iotofflinetoolbox/core/data"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/data/SecretRedactor.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/data/"
cat > "$WORK/Main.kt" <<'KT'
import com.msa.iotofflinetoolbox.core.data.SecretRedactor

fun main() {
    val raw = """X-Client-Secret: top-secret
X-Custom-Token: abc123
Content-Type: application/json
Authorization: Bearer secret-token
https://alice:password@example.com/path?api_key=visible&x-client-secret=query-secret
{"x-custom-token":"json-secret"}
x-service-password=form-secret"""
    val redacted = SecretRedactor.redact(raw)
    check("top-secret" !in redacted)
    check("abc123" !in redacted)
    check("secret-token" !in redacted)
    check("alice:password" !in redacted)
    check("visible" !in redacted)
    check("query-secret" !in redacted)
    check("json-secret" !in redacted)
    check("form-secret" !in redacted)
    check(SecretRedactor.isSensitiveFieldName("x-client-secret"))
    check("Content-Type: application/json" in redacted)

    val sanitized = SecretRedactor.removeSensitiveHeaders(raw)
    check("Content-Type: application/json" in sanitized.value)
    check(sanitized.removedNames.any { it.equals("X-Client-Secret", ignoreCase = true) })
    check(sanitized.removedNames.any { it.equals("X-Custom-Token", ignoreCase = true) })
    println("SECRET_REDACTOR_RUNTIME_HARNESS_PASSED")
}
KT
mapfile -t SOURCES < <(find "$WORK" -name '*.kt' -print | sort)
kotlinc "${SOURCES[@]}" -include-runtime -d "$WORK/secret-redactor-harness.jar"
java -jar "$WORK/secret-redactor-harness.jar"
