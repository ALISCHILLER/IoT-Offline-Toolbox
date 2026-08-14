#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/msa-iot-protocol-harness"
rm -rf "$WORK"
mkdir -p "$WORK/com/msa/iotofflinetoolbox/core/network" "$WORK/com/msa/iotofflinetoolbox/core/policy" "$WORK/com/msa/iotofflinetoolbox/core/model" "$WORK/com/msa/iotofflinetoolbox/core/payload"

cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/MqttCodec.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/network/MqttCodec.kt"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/policy/HttpResponseBodyTools.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/policy/HttpResponseBodyTools.kt"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/payload/Base64Codec.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/payload/Base64Codec.kt"
sed -i '/import kotlin.time.Clock/d; s/Clock.System.now().toEpochMilliseconds()/0L/g' \
  "$WORK/com/msa/iotofflinetoolbox/core/network/MqttCodec.kt"

cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/DnsSdCodec.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/network/DnsSdCodec.kt"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/SsdpParser.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/network/SsdpParser.kt"

awk '/\/\*\* Executes one bounded CoAP exchange/{exit} {print}' \
  "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/CoapCodec.kt" \
  > "$WORK/com/msa/iotofflinetoolbox/core/network/CoapCodec.kt"
sed -i \
  -e '/import com.msa.iotofflinetoolbox.core.model.CoapResponseResult/d' \
  -e '/import com.msa.iotofflinetoolbox.core.port.CoapOperationClient/d' \
  -e '/import com.msa.iotofflinetoolbox.core.port.NetworkProbe/d' \
  -e '/import com.msa.iotofflinetoolbox.core.payload.PayloadCodec/d' \
  -e '/import com.msa.iotofflinetoolbox.core.payload.toDisplayHex/d' \
  -e '/import kotlin.time.TimeSource/d' \
  "$WORK/com/msa/iotofflinetoolbox/core/network/CoapCodec.kt"

cat > "$WORK/com/msa/iotofflinetoolbox/core/model/ModelStubs.kt" <<'KOTLIN'
package com.msa.iotofflinetoolbox.core.model

data class MqttMessage(
    val topic: String,
    val payload: String,
    val retained: Boolean,
    val duplicate: Boolean,
    val qos: Int,
    val timestampMillis: Long,
    val binary: Boolean = false,
    val truncated: Boolean = false,
)

data class SsdpDevice(
    val usn: String,
    val location: String,
    val server: String? = null,
    val searchTarget: String? = null,
    val cacheControl: String? = null,
    val remoteAddress: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class MdnsService(
    val serviceType: String,
    val instanceName: String,
    val host: String?,
    val port: Int?,
    val addresses: List<String>,
    val txt: Map<String, String>,
    val ttlSeconds: Long,
)

enum class CoapMethod(val code: Int) { GET(1), POST(2), PUT(3), DELETE(4) }
enum class CoapMessageType(val value: Int) { CON(0), NON(1) }

data class CoapRequestInput(
    val host: String,
    val port: Int = 5683,
    val method: CoapMethod = CoapMethod.GET,
    val path: String = "/",
    val query: String = "",
    val payload: String = "",
    val contentFormat: Int? = null,
    val accept: Int? = null,
    val messageType: CoapMessageType = CoapMessageType.CON,
    val timeoutMillis: Int = 3_000,
)
KOTLIN

cat > "$WORK/Main.kt" <<'KOTLIN'
package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.CoapRequestInput

private fun verify(condition: Boolean, message: String) = check(condition) { message }
private fun expectFailure(message: String, block: () -> Unit) {
    check(runCatching(block).isFailure) { message }
}

private fun encodeName(name: String): ByteArray {
    val output = mutableListOf<Byte>()
    name.split('.').forEach { label ->
        output += label.length.toByte()
        output += label.encodeToByteArray().toList()
    }
    output += 0
    return output.toByteArray()
}

fun main() {
    val publish = MqttCodec.publishPacket("sensors/temperature", "21.5".encodeToByteArray(), retain = true, qos = 1, packetId = 77)
    val packet = MqttCodec.parsePackets(publish).single()
    val parsed = MqttCodec.parsePublish(packet, 1_024).first
    verify(parsed.topic == "sensors/temperature" && parsed.payload == "21.5", "MQTT publish round trip")
    val binaryPublish = MqttCodec.publishPacket("sensors/raw", byteArrayOf(0x00, 0xFF.toByte(), 0x01), retain = false, qos = 0)
    val binaryPacket = MqttCodec.parsePackets(binaryPublish).single()
    val (binaryMessage, binaryTruncated) = MqttCodec.parsePublish(binaryPacket, 1_024)
    verify(binaryMessage.binary && !binaryTruncated, "binary MQTT payload was not classified safely")
    verify(binaryMessage.payload == "00 FF 01", "binary MQTT payload did not preserve HEX evidence")
    verify(MqttCodec.parsePacketIdentifier(packet) == 77, "MQTT packet identifier")
    expectFailure("reserved MQTT type accepted") { MqttCodec.parsePackets(byteArrayOf(0x00, 0x00)) }
    expectFailure("invalid MQTT flags accepted") { MqttCodec.parsePackets(byteArrayOf(0x81.toByte(), 0x00)) }
    expectFailure("MQTT null string accepted") { MqttCodec.connectPacket("bad\u0000id", "", "", 30) }
    expectFailure("non-canonical MQTT remaining length accepted") {
        MqttCodec.parsePackets(byteArrayOf(0xC0.toByte(), 0x80.toByte(), 0x00))
    }
    expectFailure("zero MQTT payload bound accepted") { MqttCodec.parsePublish(packet, 0) }
    expectFailure("oversized MQTT payload bound accepted") { MqttCodec.parsePublish(packet, 1_048_577) }
    expectFailure("QoS 0 MQTT PUBLISH accepted DUP") { MqttCodec.parsePackets(byteArrayOf(0x38, 0x00)) }
    expectFailure("five-byte MQTT remaining length accepted") {
        MqttCodec.parsePackets(byteArrayOf(0xC0.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x00))
    }
    expectFailure("unpaired MQTT UTF-16 surrogate accepted") { MqttCodec.connectPacket("bad\uD800", "", "", 30) }
    expectFailure("MQTT Unicode non-character accepted") { MqttCodec.publishPacket("sensors/\uFDD0", byteArrayOf(), false) }
    expectFailure("refused MQTT connection claimed an existing session") {
        val refused = MqttCodec.parsePackets(byteArrayOf(0x20, 0x02, 0x01, 0x05)).single()
        MqttCodec.parseConnAck(refused)
    }

    verify(CoapCodec.responseCodeText(163) == "5.03 Service Unavailable", "CoAP response mapping")
    val coap = CoapCodec.encodeRequest(CoapRequestInput(host = "127.0.0.1", path = "/sensor", payload = "ok"), 1, byteArrayOf(1))
    verify(CoapCodec.parse(coap).payload.decodeToString() == "ok", "CoAP round trip")
    expectFailure("oversized CoAP payload accepted") {
        CoapCodec.encodeRequest(CoapRequestInput(host = "127.0.0.1", payload = "x".repeat(60_001)), 1, byteArrayOf())
    }
    expectFailure("CoAP option number overflow accepted") {
        CoapCodec.parse(byteArrayOf(0x40, 0x00, 0x00, 0x01, 0xE0.toByte(), 0xFF.toByte(), 0xFF.toByte()))
    }
    expectFailure("non-empty CoAP empty message accepted") {
        CoapCodec.parse(byteArrayOf(0x40, 0x00, 0x00, 0x01, 0x10))
    }
    expectFailure("oversized CoAP datagram accepted") {
        CoapCodec.parse(ByteArray(65_508).also { it[0] = 0x40; it[1] = 0x01 })
    }
    expectFailure("combined CoAP option limit bypass accepted") {
        val path = "/" + (0 until 65).joinToString("/") { "p$it" }
        val query = (0 until 64).joinToString("&") { "q$it=v" }
        CoapCodec.encodeRequest(CoapRequestInput(host = "127.0.0.1", path = path, query = query, contentFormat = 0), 1, byteArrayOf())
    }
    expectFailure("negative CoAP Content-Format accepted") {
        CoapCodec.encodeRequest(CoapRequestInput(host = "127.0.0.1", contentFormat = -1), 1, byteArrayOf())
    }
    expectFailure("CoAP option flood accepted") {
        val optionFloodPacket = ByteArray(4 + 129)
        optionFloodPacket[0] = 0x40
        optionFloodPacket[1] = 0x01
        for (index in 4 until optionFloodPacket.size) optionFloodPacket[index] = 0x10
        CoapCodec.parse(optionFloodPacket)
    }

    val excessiveDnsCounts = byteArrayOf(
        0, 0, 0, 0,
        0, 65, 0, 0, 0, 0, 0, 0,
    )
    expectFailure("unbounded DNS question count accepted") { DnsSdCodec.parse(excessiveDnsCounts) }

    val owner = encodeName("device._http._tcp.local")
    val malformedTxt = byteArrayOf(5, 'a'.code.toByte(), 'b'.code.toByte())
    val dnsPacket = byteArrayOf(
        0, 0, 0x84.toByte(), 0,
        0, 0, 0, 1, 0, 0, 0, 0,
    ) + owner + byteArrayOf(
        0, 16, 0, 1,
        0, 0, 0, 120,
        0, malformedTxt.size.toByte(),
    ) + malformedTxt
    expectFailure("malformed DNS TXT accepted") { DnsSdCodec.parse(dnsPacket) }

    val ptrOwner = encodeName("_services._dns-sd._udp.local")
    val ptrTarget = encodeName("_http._tcp.local")
    val shortPtr = byteArrayOf(
        0, 0, 0x84.toByte(), 0,
        0, 0, 0, 1, 0, 0, 0, 0,
    ) + ptrOwner + byteArrayOf(
        0, 12, 0, 1,
        0, 0, 0, 120,
        0, 1,
    ) + ptrTarget
    expectFailure("DNS PTR RDATA boundary bypass accepted") { DnsSdCodec.parse(shortPtr) }
    expectFailure("zero DNS query type accepted") { DnsSdCodec.query("_http._tcp.local", 0) }
    expectFailure("oversized DNS datagram accepted") { DnsSdCodec.parse(ByteArray(65_508)) }
    expectFailure("DNS trailing bytes accepted") {
        DnsSdCodec.parse(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x7F))
    }

    verify(SsdpParser.parse("HTTP/1.1 200 OK\r\nUSN: uuid:test\r\n\r\n")?.usn == "uuid:test", "SSDP success response")
    verify(SsdpParser.parse("HTTP/1.1 404 Not Found\r\nUSN: uuid:test\r\n\r\n") == null, "SSDP status filtering")
    expectFailure("SSDP search-target injection accepted") { SsdpParser.searchRequest("ssdp:all\r\nX-Evil: yes") }
    expectFailure("malformed SSDP header accepted") { SsdpParser.parse("HTTP/1.1 200 OK\r\nBroken header\r\n\r\n") }

    println("PROTOCOL_RUNTIME_HARNESS_PASSED")
}
KOTLIN

if [[ "${PROTOCOL_HARNESS_PREPARE_ONLY:-0}" == "1" ]]; then
  exit 0
fi

mapfile -t SOURCES < <(find "$WORK" -name '*.kt' -print | sort)
kotlinc "${SOURCES[@]}" -include-runtime -d "$WORK/protocol-harness.jar"
java -jar "$WORK/protocol-harness.jar"
