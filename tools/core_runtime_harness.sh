#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/msa-iot-core-harness"
rm -rf "$WORK"
mkdir -p "$WORK/com/msa/iotofflinetoolbox/core/model" "$WORK/com/msa/iotofflinetoolbox/core/network" "$WORK/com/msa/iotofflinetoolbox/core/data" "$WORK/com/msa/iotofflinetoolbox/core/payload"

cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/TransportSecurity.kt" "$WORK/com/msa/iotofflinetoolbox/core/network/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/HeaderParser.kt" "$WORK/com/msa/iotofflinetoolbox/core/network/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/IpTools.kt" "$WORK/com/msa/iotofflinetoolbox/core/network/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/NetworkValidation.kt" "$WORK/com/msa/iotofflinetoolbox/core/network/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/HttpResponseBodyTools.kt" "$WORK/com/msa/iotofflinetoolbox/core/network/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/data/SecretRedactor.kt" "$WORK/com/msa/iotofflinetoolbox/core/data/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/payload/Base64Codec.kt" "$WORK/com/msa/iotofflinetoolbox/core/payload/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/payload/PayloadCodec.kt" "$WORK/com/msa/iotofflinetoolbox/core/payload/"

cat > "$WORK/com/msa/iotofflinetoolbox/core/model/SubnetResult.kt" <<'KOTLIN'
package com.msa.iotofflinetoolbox.core.model

enum class PayloadEncoding { TEXT, HEX, BASE64 }

data class SubnetResult(
    val input: String,
    val networkAddress: String,
    val broadcastAddress: String,
    val subnetMask: String,
    val firstHost: String,
    val lastHost: String,
    val totalAddresses: Long,
    val usableAddresses: Long,
    val prefixLength: Int,
)
KOTLIN

cat > "$WORK/Main.kt" <<'KOTLIN'
package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.data.SecretRedactor
import com.msa.iotofflinetoolbox.core.payload.Base64Codec
import com.msa.iotofflinetoolbox.core.payload.PayloadCodec

private fun checkValue(condition: Boolean, message: String) {
    check(condition) { message }
}

fun main() {
    checkValue(TransportSecurity.hasScheme("  WSS://broker.example/mqtt  ", "ws", "wss"), "case-insensitive scheme")
    checkValue(TransportSecurity.extractHost("https://user:pass@example.com:8443/api") == "example.com", "userinfo host")
    checkValue(TransportSecurity.extractHost("http://[::1]:8080/status") == "::1", "bracketed IPv6 host")
    checkValue(TransportSecurity.isLocalHost("fc00::1"), "IPv6 ULA")
    checkValue(TransportSecurity.isLocalHost("febf::1%en0"), "IPv6 link-local")
    checkValue(!TransportSecurity.isLocalHost("fec0::1"), "deprecated site-local must not be treated as ULA/link-local")
    checkValue(!TransportSecurity.isLocalHost("fcdn.example.com"), "DNS fc prefix false positive")
    checkValue(!TransportSecurity.isLocalHost("fdexample.com"), "DNS fd prefix false positive")
    checkValue(TransportSecurity.isPublicCleartext("http://fcdn.example.com/api"), "public cleartext classification")
    checkValue(!TransportSecurity.isLocalHost("plc-controller"), "single-label host is ambiguous")
    checkValue(TransportSecurity.isPublicCleartext("http://plc-controller/status"), "single-label cleartext requires override")
    runCatching { TransportSecurity.requireScheme("wss://user:password@example.com/socket", setOf("ws", "wss"), "WebSocket URL") }
        .onSuccess { error("embedded WebSocket credentials accepted") }
    listOf("https://example.com:bad/path", "https://2001:db8::1/path", "https://example.com\\attacker.test/path").forEach { invalid ->
        runCatching { TransportSecurity.requireScheme(invalid, setOf("http", "https"), "HTTP URL") }
            .onSuccess { error("malformed URL authority accepted: $invalid") }
    }

    checkValue(HeaderParser.parse("Accept: application/json\nX-Test: yes").size == 2, "header parsing")
    runCatching { HeaderParser.parse("X-Test: yes", maximumHeaders = 0) }
        .onSuccess { error("invalid header limit accepted") }
    runCatching { HeaderParser.parse("Authorization: one\nauthorization: two") }
        .onSuccess { error("case-insensitive duplicate headers accepted") }
    runCatching { HeaderParser.parse("X-Test: before\u0001after") }
        .onSuccess { error("header control character accepted") }

    checkValue(PortSpecParser.parse("443,80-82,22,80") == listOf(22, 80, 81, 82, 443), "port parsing")
    checkValue(PortSpecParser.parse("iot,!53").containsAll(listOf(502, 1883, 8883)) && 53 !in PortSpecParser.parse("iot,!53"), "port alias and exclusion")
    checkValue(PortSpecParser.parse("ldap,smb,opcua,amqp,kafka") == listOf(389, 445, 4840, 5671, 5672, 9092), "professional service aliases")
    runCatching { PortSpecParser.parse("80", maximumPorts = 0) }
        .onSuccess { error("invalid port limit accepted") }
    runCatching { PortSpecParser.parse(List(17) { "1-4096" }.joinToString(",")) }
        .onSuccess { error("repeated duplicate port expansion was not bounded") }

    val timeoutBudget = calculateHttpTimeoutBudget(1_000, 800, 600, 750)
    checkValue(timeoutBudget.requestMillis == 250L, "HTTP total timeout budget")
    checkValue(timeoutBudget.connectMillis == 250L && timeoutBudget.socketMillis == 250L, "HTTP child timeout caps")
    checkValue(remainingReceiveWindowMillis(1_000, 750) == 250L, "realtime total receive window")
    runCatching { validateWebSocketHeaders(mapOf("Sec-WebSocket-Protocol" to "mqtt")) }
        .onSuccess { error("engine-managed WebSocket header accepted") }

    val subnet = CidrCalculator.calculate("192.168.10.25/24")
    checkValue(subnet.networkAddress == "192.168.10.0", "CIDR network")
    checkValue(subnet.broadcastAddress == "192.168.10.255", "CIDR broadcast")

    val redacted = SecretRedactor.redact("Authorization: Bearer abc.def.ghi\npassword=secret-value")
    checkValue("abc.def.ghi" !in redacted && "secret-value" !in redacted, "secret redaction")
    checkValue(SecretRedactor.isSensitiveFieldName("api_key"), "sensitive query/form key classification")

    checkValue(Base64Codec.decode("TQ==").decodeToString() == "M", "Base64 one-byte vector")
    checkValue(Base64Codec.decode("TWE=").decodeToString() == "Ma", "Base64 two-byte vector")
    checkValue(Base64Codec.decode("T W F u\n").decodeToString() == "Man", "Base64 whitespace")
    checkValue(PayloadCodec.decodeHex("0xDE 0xAD").contentEquals(byteArrayOf(0xDE.toByte(), 0xAD.toByte())), "HEX token prefixes")
    val binary = byteArrayOf(0x00, 0xFF.toByte(), 0x80.toByte(), 0x01)
    val decodedBinary = HttpResponseBodyTools.decode(binary, "application/octet-stream")
    checkValue(!decodedBinary.isText && decodedBinary.text.isEmpty(), "binary HTTP response classification")
    checkValue(HttpResponseBodyTools.toHex(binary) == "00 FF 80 01", "binary HTTP HEX rendering")
    checkValue(HttpResponseBodyTools.toBase64(binary) == "AP+AAQ==", "binary HTTP Base64 rendering")
    val decodedText = HttpResponseBodyTools.decode("حسگر".encodeToByteArray(), null)
    checkValue(decodedText.isText && decodedText.text == "حسگر", "UTF-8 HTTP response inference")
    runCatching { PayloadCodec.decodeHex("10x2") }
        .onSuccess { error("embedded HEX prefix accepted") }

    listOf("====", "A===", "AA=A", "AA==AAAA", "AB==", "AAB=").forEach { invalid ->
        runCatching { Base64Codec.decode(invalid) }
            .onSuccess { error("malformed Base64 accepted: $invalid") }
    }

    runCatching { PortSpecParser.parse("1,".repeat(8_193)) }
        .onSuccess { error("oversized port token list accepted") }

    println("FINAL_CORE_RUNTIME_HARNESS_PASSED")
}
KOTLIN

mapfile -t SOURCES < <(find "$WORK" -name '*.kt' -print | sort)
kotlinc "${SOURCES[@]}" -include-runtime -d "$WORK/core-harness.jar"
java -jar "$WORK/core-harness.jar"
