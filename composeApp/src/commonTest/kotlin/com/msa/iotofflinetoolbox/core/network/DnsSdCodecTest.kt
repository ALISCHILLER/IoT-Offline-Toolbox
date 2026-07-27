package com.msa.iotofflinetoolbox.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DnsSdCodecTest {
    @Test
    fun queryUsesMdnsHeaderAndPtrQuestion() {
        val query = DnsSdCodec.query(DnsSdCodec.SERVICE_ENUMERATION)
        assertEquals(0, query[0].toInt())
        assertEquals(1, query[5].toInt())
        val queryType = ((query[query.size - 4].toInt() and 0xFF) shl 8) or
            (query[query.size - 3].toInt() and 0xFF)
        assertEquals(12, queryType)
    }

    @Test
    fun uncompressedPtrResponseIsParsed() {
        val owner = encodeName("_services._dns-sd._udp.local")
        val target = encodeName("_http._tcp.local")
        val packet = byteArrayOf(
            0, 0, (-124).toByte(), 0, // response flags
            0, 0, 0, 1, 0, 0, 0, 0,
        ) + owner + byteArrayOf(
            0, 12, 0, 1,
            0, 0, 0, 120,
            ((target.size shr 8) and 0xFF).toByte(), (target.size and 0xFF).toByte(),
        ) + target
        val message = DnsSdCodec.parse(packet)
        assertEquals(listOf("_http._tcp.local"), DnsSdCodec.serviceTypes(listOf(message)))
        assertTrue(message.records.single().ttlSeconds == 120L)
    }

    @Test
    fun malformedTxtLengthIsRejectedInsteadOfSilentlyTruncated() {
        val owner = encodeName("device._http._tcp.local")
        val malformedTxt = byteArrayOf(5, 'a'.code.toByte(), 'b'.code.toByte())
        val packet = byteArrayOf(
            0, 0, (-124).toByte(), 0,
            0, 0, 0, 1, 0, 0, 0, 0,
        ) + owner + byteArrayOf(
            0, 16, 0, 1,
            0, 0, 0, 120,
            0, malformedTxt.size.toByte(),
        ) + malformedTxt

        assertFailsWith<IllegalArgumentException> { DnsSdCodec.parse(packet) }
    }

    @Test
    fun goodbyePtrRecordsAreNotReturnedAsActiveServices() {
        val owner = encodeName("_services._dns-sd._udp.local")
        val target = encodeName("_http._tcp.local")
        val packet = byteArrayOf(
            0, 0, (-124).toByte(), 0,
            0, 0, 0, 1, 0, 0, 0, 0,
        ) + owner + byteArrayOf(
            0, 12, 0, 1,
            0, 0, 0, 0,
            ((target.size shr 8) and 0xFF).toByte(), (target.size and 0xFF).toByte(),
        ) + target

        assertTrue(DnsSdCodec.serviceTypes(listOf(DnsSdCodec.parse(packet))).isEmpty())
    }

    @Test
    fun ptrNameMustFitInsideItsRdataLength() {
        val owner = encodeName("_services._dns-sd._udp.local")
        val target = encodeName("_http._tcp.local")
        val packet = byteArrayOf(
            0, 0, (-124).toByte(), 0,
            0, 0, 0, 1, 0, 0, 0, 0,
        ) + owner + byteArrayOf(
            0, 12, 0, 1,
            0, 0, 0, 120,
            0, 1,
        ) + target

        assertFailsWith<IllegalArgumentException> { DnsSdCodec.parse(packet) }
    }

    @Test
    fun addressRecordsRequireExactRdataLength() {
        val owner = encodeName("device.local")
        val packet = byteArrayOf(
            0, 0, (-124).toByte(), 0,
            0, 0, 0, 1, 0, 0, 0, 0,
        ) + owner + byteArrayOf(
            0, 1, 0, 1,
            0, 0, 0, 120,
            0, 3,
            127, 0, 0,
        )

        assertFailsWith<IllegalArgumentException> { DnsSdCodec.parse(packet) }
    }

    @Test
    fun invalidQueryTypesAndOversizedPacketsAreRejected() {
        assertFailsWith<IllegalArgumentException> { DnsSdCodec.query("_http._tcp.local", type = 0) }
        assertFailsWith<IllegalArgumentException> {
            DnsSdCodec.parse(ByteArray(65_508).also { packet ->
                packet[5] = 0
            })
        }
    }

    @Test
    fun declaredRecordsCannotLeaveTrailingPacketBytes() {
        val packet = byteArrayOf(
            0, 0, (-124).toByte(), 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0x7F,
        )
        assertFailsWith<IllegalArgumentException> { DnsSdCodec.parse(packet) }
    }

    @Test
    fun ptrRdataCannotContainTrailingGarbage() {
        val owner = encodeName("_services._dns-sd._udp.local")
        val target = encodeName("_http._tcp.local")
        val rdata = target + byteArrayOf(0)
        val packet = byteArrayOf(
            0, 0, (-124).toByte(), 0,
            0, 0, 0, 1, 0, 0, 0, 0,
        ) + owner + byteArrayOf(
            0, 12, 0, 1,
            0, 0, 0, 120,
            ((rdata.size shr 8) and 0xFF).toByte(), (rdata.size and 0xFF).toByte(),
        ) + rdata
        assertFailsWith<IllegalArgumentException> { DnsSdCodec.parse(packet) }
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
}
