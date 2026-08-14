package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.policy.HttpResponseBodyTools

import com.msa.iotofflinetoolbox.core.model.MqttMessage
import kotlin.time.Clock

/** MQTT 3.1.1 packet encoder/decoder used by the WebSocket transport. */
internal object MqttCodec {
    data class ConnAck(val sessionPresent: Boolean, val returnCode: Int)
    data class SubAck(val packetId: Int, val returnCodes: List<Int>)
    data class Packet(val type: Int, val flags: Int, val body: ByteArray)

    fun connectPacket(
        clientId: String,
        username: String,
        password: String,
        keepAliveSeconds: Int,
        cleanSession: Boolean = true,
    ): ByteArray {
        require(clientId.isNotBlank()) { "MQTT client ID cannot be empty" }
        require(password.isEmpty() || username.isNotEmpty()) { "MQTT password requires a username" }
        require(keepAliveSeconds in 0..65_535) { "MQTT keep-alive must be between 0 and 65535 seconds" }
        val variableHeader = mutableListOf<Byte>()
        variableHeader += utf8("MQTT").toList()
        variableHeader += 4.toByte()
        var flags = if (cleanSession) 0x02 else 0
        if (username.isNotEmpty()) flags = flags or 0x80
        if (password.isNotEmpty()) flags = flags or 0x40
        variableHeader += flags.toByte()
        val keepAlive = keepAliveSeconds
        variableHeader += (keepAlive shr 8).toByte()
        variableHeader += keepAlive.toByte()

        val payload = mutableListOf<Byte>()
        payload += utf8(clientId).toList()
        if (username.isNotEmpty()) payload += utf8(username).toList()
        if (password.isNotEmpty()) payload += utf8(password).toList()
        return packet(0x10, (variableHeader + payload).toByteArray())
    }

    fun subscribePacket(topicFilter: String, qos: Int = 0, packetId: Int = 1): ByteArray {
        validateTopicFilter(topicFilter)
        require(qos in 0..1) { "This workbench supports MQTT subscription QoS 0 or 1" }
        require(packetId in 1..65_535) { "MQTT packet ID must be between 1 and 65535" }
        val body = mutableListOf<Byte>()
        body += (packetId shr 8).toByte()
        body += packetId.toByte()
        body += utf8(topicFilter).toList()
        body += qos.toByte()
        return packet(0x82, body.toByteArray())
    }

    fun publishPacket(
        topic: String,
        payload: ByteArray,
        retain: Boolean,
        qos: Int = 0,
        packetId: Int = 2,
    ): ByteArray {
        validateTopicName(topic)
        require(qos in 0..1) { "This workbench supports MQTT publish QoS 0 or 1" }
        if (qos > 0) require(packetId in 1..65_535) { "MQTT packet ID must be between 1 and 65535" }
        val body = mutableListOf<Byte>()
        body += utf8(topic).toList()
        if (qos > 0) {
            body += (packetId shr 8).toByte()
            body += packetId.toByte()
        }
        body += payload.toList()
        val header = 0x30 or (qos shl 1) or if (retain) 1 else 0
        return packet(header, body.toByteArray())
    }

    fun pubAckPacket(packetId: Int): ByteArray {
        require(packetId in 1..65_535) { "MQTT packet ID must be between 1 and 65535" }
        return byteArrayOf(0x40, 0x02, (packetId shr 8).toByte(), packetId.toByte())
    }
    fun pingRequest(): ByteArray = byteArrayOf(0xC0.toByte(), 0)
    fun disconnectPacket(): ByteArray = byteArrayOf(0xE0.toByte(), 0)

    fun parsePackets(bytes: ByteArray): List<Packet> {
        val result = extractCompletePackets(bytes)
        require(result.remaining.isEmpty()) { "Incomplete MQTT packet" }
        return result.packets
    }

    data class Extraction(val packets: List<Packet>, val remaining: ByteArray)

    fun extractCompletePackets(bytes: ByteArray): Extraction {
        val packets = mutableListOf<Packet>()
        var cursor = 0
        while (cursor < bytes.size) {
            val packetStart = cursor
            if (cursor >= bytes.size) break
            val header = bytes[cursor++].toInt() and 0xFF
            val decoded = decodeRemainingLengthOrNull(bytes, cursor) ?: return Extraction(packets, bytes.copyOfRange(packetStart, bytes.size))
            cursor = decoded.nextIndex
            require(decoded.value <= MAX_MQTT_PACKET_BYTES) { "MQTT packet exceeds the 1 MiB safety limit" }
            if (cursor + decoded.value > bytes.size) return Extraction(packets, bytes.copyOfRange(packetStart, bytes.size))
            val type = header shr 4
            val flags = header and 0x0F
            validateFixedHeader(type, flags)
            packets += Packet(type, flags, bytes.copyOfRange(cursor, cursor + decoded.value))
            cursor += decoded.value
        }
        return Extraction(packets, byteArrayOf())
    }

    fun parseConnAck(packet: Packet): ConnAck {
        require(packet.type == 2 && packet.flags == 0 && packet.body.size == 2) { "Expected a valid two-byte MQTT CONNACK packet" }
        val flags = packet.body[0].toInt() and 0xFF
        require(flags and 0xFE == 0) { "Malformed MQTT CONNACK flags" }
        val returnCode = packet.body[1].toInt() and 0xFF
        require(returnCode in 0..5) { "Invalid MQTT CONNACK return code: $returnCode" }
        val sessionPresent = (flags and 0x01) != 0
        require(returnCode == 0 || !sessionPresent) {
            "MQTT CONNACK cannot report an existing session when the connection is refused"
        }
        return ConnAck(sessionPresent, returnCode)
    }

    fun parsePubAck(packet: Packet): Int {
        require(packet.type == 4 && packet.flags == 0) { "Expected MQTT PUBACK" }
        require(packet.body.size == 2) { "MQTT PUBACK must contain exactly one packet identifier" }
        return packetIdentifier(packet.body, 0)
    }

    fun parseSubAck(packet: Packet): SubAck {
        require(packet.type == 9 && packet.flags == 0) { "Expected MQTT SUBACK" }
        require(packet.body.size >= 3) { "MQTT SUBACK must contain a packet identifier and return code" }
        val packetId = packetIdentifier(packet.body, 0)
        val codes = packet.body.copyOfRange(2, packet.body.size).map { it.toInt() and 0xFF }
        require(codes.all { it in setOf(0, 1, 2, 0x80) }) { "MQTT SUBACK contains an invalid return code" }
        return SubAck(packetId, codes)
    }

    fun parsePacketIdentifier(packet: Packet): Int? {
        return when (packet.type) {
            4, 9 -> if (packet.body.size >= 2) packetIdentifier(packet.body, 0) else null
            3 -> {
                val qos = (packet.flags shr 1) and 0x03
                if (qos == 0 || packet.body.size < 2) {
                    null
                } else {
                    val topicLength = ((packet.body[0].toInt() and 0xFF) shl 8) or (packet.body[1].toInt() and 0xFF)
                    val index = 2 + topicLength
                    if (index + 1 >= packet.body.size) null else packetIdentifier(packet.body, index)
                }
            }
            else -> null
        }
    }

    fun parsePublish(packet: Packet, maxPayloadBytes: Int): Pair<MqttMessage, Boolean> {
        require(maxPayloadBytes in 1..MAX_MQTT_PACKET_BYTES) {
            "MQTT maximum payload size must be between 1 and $MAX_MQTT_PACKET_BYTES bytes"
        }
        require(packet.type == 3) { "Expected MQTT PUBLISH packet" }
        require(packet.body.size >= 2) { "MQTT PUBLISH packet is too short" }
        val topicLength = ((packet.body[0].toInt() and 0xFF) shl 8) or (packet.body[1].toInt() and 0xFF)
        require(topicLength > 0 && 2 + topicLength <= packet.body.size) { "Invalid MQTT topic length" }
        val topicBytes = packet.body.copyOfRange(2, 2 + topicLength)
        val topic = topicBytes.decodeToString(throwOnInvalidSequence = true)
        validateTopicName(topic)
        val qos = (packet.flags shr 1) and 0x03
        require(qos in 0..2) { "Invalid MQTT QoS" }
        if (qos > 0) packetIdentifier(packet.body, 2 + topicLength)
        val payloadStart = 2 + topicLength + if (qos > 0) 2 else 0
        require(payloadStart <= packet.body.size) { "Invalid MQTT PUBLISH packet identifier" }
        val fullPayload = packet.body.copyOfRange(payloadStart, packet.body.size)
        val truncated = fullPayload.size > maxPayloadBytes
        val payload = fullPayload.copyOf(minOf(fullPayload.size, maxPayloadBytes))
        val decoded = HttpResponseBodyTools.decode(payload, contentType = null)
        return MqttMessage(
            topic = topic,
            payload = if (decoded.isText) decoded.text else HttpResponseBodyTools.toHex(payload),
            retained = (packet.flags and 0x01) != 0,
            duplicate = (packet.flags and 0x08) != 0,
            qos = qos,
            timestampMillis = Clock.System.now().toEpochMilliseconds(),
            binary = !decoded.isText,
            truncated = truncated,
        ) to truncated
    }

    fun validateTopicName(topic: String) {
        require(topic.isNotBlank()) { "MQTT topic cannot be empty" }
        require(topic.encodeToByteArray().size <= 65_535) { "MQTT topic is too long" }
        require('#' !in topic && '+' !in topic) { "Publish topics cannot contain MQTT wildcards" }
        validateMqttUtf8(topic, "MQTT topic")
    }

    fun validateTopicFilter(topic: String) {
        require(topic.isNotBlank()) { "MQTT topic filter cannot be empty" }
        require(topic.encodeToByteArray().size <= 65_535) { "MQTT topic filter is too long" }
        validateMqttUtf8(topic, "MQTT topic filter")
        val levels = topic.split('/')
        levels.forEachIndexed { index, level ->
            if ('#' in level) require(level == "#" && index == levels.lastIndex) { "# must occupy the final topic level" }
            if ('+' in level) require(level == "+") { "+ must occupy an entire topic level" }
        }
    }


    private fun validateFixedHeader(type: Int, flags: Int) {
        require(type in 1..14) { "MQTT packet type $type is reserved" }
        val expectedFlags = when (type) {
            3 -> null // PUBLISH flags encode DUP, QoS and RETAIN.
            6, 8, 10 -> 0x02
            else -> 0x00
        }
        if (expectedFlags != null) {
            require(flags == expectedFlags) { "Invalid MQTT fixed-header flags for packet type $type" }
        } else {
            val qos = (flags shr 1) and 0x03
            require(qos != 3) { "MQTT PUBLISH cannot use reserved QoS value 3" }
            require(qos != 0 || (flags and 0x08) == 0) { "MQTT QoS 0 PUBLISH cannot set DUP" }
        }
    }

    private fun packetIdentifier(bytes: ByteArray, offset: Int): Int {
        require(offset >= 0 && offset + 1 < bytes.size) { "MQTT packet identifier is missing" }
        val value = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
        require(value in 1..65_535) { "MQTT packet identifier must be non-zero" }
        return value
    }

    private fun packet(header: Int, body: ByteArray): ByteArray = byteArrayOf(header.toByte()) + encodeRemainingLength(body.size) + body

    private fun utf8(value: String): ByteArray {
        validateMqttUtf8(value, "MQTT UTF-8 string")
        val encoded = value.encodeToByteArray()
        require(encoded.size <= 65_535) { "MQTT string exceeds 65535 UTF-8 bytes" }
        return byteArrayOf((encoded.size shr 8).toByte(), encoded.size.toByte()) + encoded
    }


    private fun validateMqttUtf8(value: String, label: String) {
        var index = 0
        while (index < value.length) {
            val first = value[index].code
            require(first != 0) { "$label cannot contain a null character" }
            require(first !in 0xD800..0xDFFF || first in 0xD800..0xDBFF) { "$label contains an unpaired UTF-16 surrogate" }
            val codePoint = if (first in 0xD800..0xDBFF) {
                require(index + 1 < value.length) { "$label contains an unpaired UTF-16 surrogate" }
                val second = value[index + 1].code
                require(second in 0xDC00..0xDFFF) { "$label contains an unpaired UTF-16 surrogate" }
                index += 1
                0x10000 + ((first - 0xD800) shl 10) + (second - 0xDC00)
            } else {
                first
            }
            val lowWord = codePoint and 0xFFFF
            require(codePoint !in 0xFDD0..0xFDEF && lowWord != 0xFFFE && lowWord != 0xFFFF) {
                "$label contains a Unicode non-character"
            }
            index += 1
        }
    }

    private fun encodeRemainingLength(length: Int): ByteArray {
        require(length in 0..268_435_455) { "Invalid MQTT remaining length" }
        var remaining = length
        val bytes = mutableListOf<Byte>()
        do {
            var digit = remaining % 128
            remaining /= 128
            if (remaining > 0) digit = digit or 0x80
            bytes += digit.toByte()
        } while (remaining > 0)
        return bytes.toByteArray()
    }

    private const val MAX_MQTT_PACKET_BYTES = 1_048_576

    private data class RemainingLength(val value: Int, val nextIndex: Int)

    private fun decodeRemainingLengthOrNull(bytes: ByteArray, start: Int): RemainingLength? {
        var multiplier = 1
        var value = 0
        var cursor = start
        var loops = 0
        while (true) {
            if (cursor >= bytes.size) return null
            loops++
            require(loops <= 4) { "Malformed MQTT remaining length" }
            val digit = bytes[cursor++].toInt() and 0xFF
            val contribution = (digit and 127).toLong() * multiplier.toLong()
            require(value.toLong() + contribution <= 268_435_455L) { "Malformed MQTT remaining length" }
            value += contribution.toInt()
            if ((digit and 128) == 0) break
            require(loops < 4) { "Malformed MQTT remaining length" }
            multiplier *= 128
        }
        val minimumCanonicalValue = when (loops) {
            1 -> 0
            2 -> 128
            3 -> 16_384
            4 -> 2_097_152
            else -> error("Malformed MQTT remaining length")
        }
        require(value >= minimumCanonicalValue) { "MQTT remaining length uses a non-canonical encoding" }
        return RemainingLength(value, cursor)
    }
}
