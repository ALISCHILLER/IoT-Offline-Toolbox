package com.msa.iotofflinetoolbox.core.network

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MqttCodecTest {
    @Test
    fun connectPacketUsesMqtt311AndCleanSession() {
        val packet = MqttCodec.connectPacket("client", "", "", 30, cleanSession = true)
        assertEquals(0x10, packet.first().toInt() and 0xFF)
        assertTrue(packet.toList().contains(4.toByte()))
        assertTrue(packet.toList().contains(2.toByte()))
    }

    @Test
    fun qosOnePublishCanBeParsed() {
        val packet = MqttCodec.publishPacket("sensors/temperature", "21.5".encodeToByteArray(), retain = true, qos = 1, packetId = 77)
        val parsed = MqttCodec.parsePackets(packet).single()
        val (message, truncated) = MqttCodec.parsePublish(parsed, 1024)
        assertEquals("sensors/temperature", message.topic)
        assertEquals("21.5", message.payload)
        assertEquals(1, message.qos)
        assertEquals(77, MqttCodec.parsePacketIdentifier(parsed))
        assertTrue(message.retained)
        assertTrue(!truncated)
    }

    @Test
    fun fragmentedTransportBytesRemainBuffered() {
        val packet = MqttCodec.publishPacket("a/b", "payload".encodeToByteArray(), false)
        val first = MqttCodec.extractCompletePackets(packet.copyOfRange(0, 3))
        assertTrue(first.packets.isEmpty())
        val second = MqttCodec.extractCompletePackets(first.remaining + packet.copyOfRange(3, packet.size))
        assertEquals(1, second.packets.size)
        assertContentEquals(byteArrayOf(), second.remaining)
    }

    @Test
    fun passwordWithoutUsernameIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            MqttCodec.connectPacket("client", "", "secret", 30)
        }
    }

    @Test
    fun subscriptionQosTwoIsRejectedBecauseHandshakeIsNotImplemented() {
        assertFailsWith<IllegalArgumentException> {
            MqttCodec.subscribePacket("sensors/#", qos = 2)
        }
    }

    @Test
    fun invalidPublishWildcardIsRejected() {
        assertFailsWith<IllegalArgumentException> { MqttCodec.publishPacket("sensors/+", byteArrayOf(1), false) }
    }

    @Test
    fun invalidHashPlacementIsRejected() {
        assertFailsWith<IllegalArgumentException> { MqttCodec.subscribePacket("sensors/#/tail") }
    }
    @Test
    fun subAckParsesGrantedQosAndPacketId() {
        val packet = MqttCodec.parsePackets(byteArrayOf(0x90.toByte(), 0x03, 0x00, 0x01, 0x01)).single()
        val ack = MqttCodec.parseSubAck(packet)
        assertEquals(1, ack.packetId)
        assertEquals(listOf(1), ack.returnCodes)
    }

    @Test
    fun rejectedSubscriptionReturnCodeIsExposed() {
        val packet = MqttCodec.parsePackets(byteArrayOf(0x90.toByte(), 0x03, 0x00, 0x01, 0x80.toByte())).single()
        val ack = MqttCodec.parseSubAck(packet)
        assertEquals(0x80, ack.returnCodes.single())
    }

    @Test
    fun pubAckRequiresExpectedShapeAndNonZeroIdentifier() {
        val packet = MqttCodec.parsePackets(byteArrayOf(0x40, 0x02, 0x00, 0x4D)).single()
        assertEquals(77, MqttCodec.parsePubAck(packet))
        val invalid = MqttCodec.parsePackets(byteArrayOf(0x40, 0x02, 0x00, 0x00)).single()
        assertFailsWith<IllegalArgumentException> { MqttCodec.parsePubAck(invalid) }
    }

    @Test
    fun zeroPacketIdentifierIsRejectedForInboundQosOnePublish() {
        val bytes = byteArrayOf(
            0x32,
            0x07,
            0x00,
            0x03,
            'a'.code.toByte(),
            '/'.code.toByte(),
            'b'.code.toByte(),
            0x00,
            0x00,
        )
        val packet = MqttCodec.parsePackets(bytes).single()
        assertFailsWith<IllegalArgumentException> { MqttCodec.parsePublish(packet, 1024) }
        assertFailsWith<IllegalArgumentException> { MqttCodec.parsePacketIdentifier(packet) }
    }

    @Test
    fun pubAckEncoderRejectsZeroPacketIdentifier() {
        assertFailsWith<IllegalArgumentException> { MqttCodec.pubAckPacket(0) }
    }

    @Test
    fun reservedPacketTypesAndInvalidFixedHeaderFlagsAreRejected() {
        assertFailsWith<IllegalArgumentException> { MqttCodec.parsePackets(byteArrayOf(0x00, 0x00)) }
        assertFailsWith<IllegalArgumentException> { MqttCodec.parsePackets(byteArrayOf(0xF0.toByte(), 0x00)) }
        assertFailsWith<IllegalArgumentException> { MqttCodec.parsePackets(byteArrayOf(0x81.toByte(), 0x00)) }
        assertFailsWith<IllegalArgumentException> { MqttCodec.parsePackets(byteArrayOf(0x36, 0x00)) }
    }

    @Test
    fun connAckRequiresZeroFixedHeaderFlags() {
        assertFailsWith<IllegalArgumentException> {
            MqttCodec.parsePackets(byteArrayOf(0x21, 0x02, 0x00, 0x00))
        }
    }

    @Test
    fun refusedConnAckCannotClaimAnExistingSession() {
        val packet = MqttCodec.parsePackets(byteArrayOf(0x20, 0x02, 0x01, 0x05)).single()
        assertFailsWith<IllegalArgumentException> { MqttCodec.parseConnAck(packet) }
    }

    @Test
    fun mqttUtf8StringsRejectNullCharacters() {
        assertFailsWith<IllegalArgumentException> {
            MqttCodec.connectPacket("client\u0000id", "", "", 30)
        }
    }

    @Test
    fun nonCanonicalRemainingLengthIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            MqttCodec.parsePackets(byteArrayOf(0xC0.toByte(), 0x80.toByte(), 0x00))
        }
    }

    @Test
    fun publishParserRequiresPositivePayloadLimit() {
        val packet = MqttCodec.parsePackets(
            MqttCodec.publishPacket("sensors/value", "1".encodeToByteArray(), retain = false),
        ).single()
        assertFailsWith<IllegalArgumentException> { MqttCodec.parsePublish(packet, 0) }
    }

    @Test
    fun qosZeroPublishCannotSetDupFlag() {
        assertFailsWith<IllegalArgumentException> {
            MqttCodec.parsePackets(byteArrayOf(0x38, 0x00))
        }
    }

    @Test
    fun remainingLengthCannotUseMoreThanFourBytes() {
        assertFailsWith<IllegalArgumentException> {
            MqttCodec.parsePackets(
                byteArrayOf(
                    0xC0.toByte(),
                    0x80.toByte(),
                    0x80.toByte(),
                    0x80.toByte(),
                    0x80.toByte(),
                    0x00,
                ),
            )
        }
    }

    @Test
    fun mqttUtf8RejectsUnpairedSurrogatesAndNonCharacters() {
        assertFailsWith<IllegalArgumentException> {
            MqttCodec.connectPacket("client\uD800", "", "", 30)
        }
        assertFailsWith<IllegalArgumentException> {
            MqttCodec.publishPacket("sensors/\uFDD0", byteArrayOf(), retain = false)
        }
    }

    @Test
    fun publishParserRejectsPayloadBoundsAboveSafetyLimit() {
        val packet = MqttCodec.parsePackets(
            MqttCodec.publishPacket("sensors/value", "1".encodeToByteArray(), retain = false),
        ).single()
        assertFailsWith<IllegalArgumentException> { MqttCodec.parsePublish(packet, 1_048_577) }
    }


    @Test
    fun binaryPublishPreservesBoundedHexEvidence() {
        val packet = MqttCodec.parsePackets(
            MqttCodec.publishPacket(
                topic = "sensors/raw",
                payload = byteArrayOf(0x00, 0xFF.toByte(), 0x01, 0x02),
                retain = false,
            ),
        ).single()

        val (message, truncated) = MqttCodec.parsePublish(packet, maxPayloadBytes = 3)

        assertTrue(message.binary)
        assertTrue(message.truncated)
        assertTrue(truncated)
        assertEquals("00 FF 01", message.payload)
    }

}
