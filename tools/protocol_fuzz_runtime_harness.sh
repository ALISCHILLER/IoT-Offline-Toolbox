#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/msa-iot-protocol-harness"

# Reuse the exact isolated parser source set prepared by the deterministic
# protocol harness, then replace only its executable entry point.
PROTOCOL_HARNESS_PREPARE_ONLY=1 bash "$ROOT/tools/protocol_runtime_harness.sh"

cat > "$WORK/Main.kt" <<'KOTLIN'
package com.msa.iotofflinetoolbox.core.network

import kotlin.random.Random

private fun unexpected(name: String, input: ByteArray, error: Throwable): Nothing =
    error("$name unexpected ${error::class.qualifiedName}: ${error.message}; input=${input.joinToString("") { "%02X".format(it) }}")

private inline fun fuzzBytes(
    name: String,
    iterations: Int,
    maxSize: Int,
    seed: Int,
    crossinline block: (ByteArray) -> Unit,
) {
    val random = Random(seed)
    repeat(iterations) {
        val input = random.nextBytes(random.nextInt(maxSize + 1))
        try {
            block(input)
        } catch (_: IllegalArgumentException) {
            // Controlled parser rejection is expected for malformed input.
        } catch (_: IllegalStateException) {
            // Controlled parser rejection is expected for malformed input.
        } catch (error: Throwable) {
            unexpected(name, input, error)
        }
    }
}

fun main() {
    fuzzBytes("MQTT", 30_000, 512, 0x51A7) { MqttCodec.parsePackets(it) }
    fuzzBytes("CoAP", 30_000, 512, 0xC0A9) { CoapCodec.parse(it) }
    fuzzBytes("DNS", 30_000, 512, 0xD05) { DnsSdCodec.parse(it) }

    val random = Random(0xBEEF)
    repeat(20_000) {
        val chars = CharArray(random.nextInt(2_048)) { random.nextInt(0, 128).toChar() }.concatToString()
        try {
            SsdpParser.parse(chars)
        } catch (_: IllegalArgumentException) {
        } catch (_: IllegalStateException) {
        } catch (error: Throwable) {
            error("SSDP unexpected ${error::class.qualifiedName}: ${error.message}")
        }
    }
    println("PROTOCOL_FUZZ_HARNESS_PASSED cases=110000 seed=deterministic")
}
KOTLIN

mapfile -t SOURCES < <(find "$WORK" -name '*.kt' -print | sort)
kotlinc "${SOURCES[@]}" -include-runtime -d "$WORK/protocol-fuzz-harness.jar"
java -Xmx256m -jar "$WORK/protocol-fuzz-harness.jar"
