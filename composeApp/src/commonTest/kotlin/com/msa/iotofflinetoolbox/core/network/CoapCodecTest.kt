package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.CoapMethod
import com.msa.iotofflinetoolbox.core.model.CoapRequestInput
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoapCodecTest {
    @Test
    fun requestRoundTripPreservesPathQueryAndPayload() {
        val token = byteArrayOf(1, 2, 3, 4)
        val encoded = CoapCodec.encodeRequest(
            CoapRequestInput(
                host = "127.0.0.1",
                method = CoapMethod.POST,
                path = "/sensors/temperature",
                query = "unit=c&verbose=true",
                payload = "23.5",
                contentFormat = 0,
            ),
            messageId = 0x1234,
            token = token,
        )
        val parsed = CoapCodec.parse(encoded)
        assertEquals(0x1234, parsed.messageId)
        assertEquals(CoapMethod.POST.code, parsed.code)
        assertContentEquals(token, parsed.token)
        assertEquals(listOf("sensors", "temperature"), parsed.options[11]?.map { it.decodeToString() })
        assertEquals(listOf("unit=c", "verbose=true"), parsed.options[15]?.map { it.decodeToString() })
        assertEquals("23.5", parsed.payload.decodeToString())
        assertEquals(0, CoapCodec.optionUInt(parsed, 12))
    }

    @Test
    fun malformedVersionIsRejected() {
        assertFailsWith<IllegalArgumentException> { CoapCodec.parse(byteArrayOf(0, 1, 0, 1)) }
    }
    @Test
    fun requestLimitsPreventOversizedUdpAllocation() {
        assertFailsWith<IllegalArgumentException> {
            CoapCodec.encodeRequest(
                CoapRequestInput(host = "127.0.0.1", payload = "x".repeat(60_001)),
                messageId = 1,
                token = byteArrayOf(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CoapCodec.encodeRequest(
                CoapRequestInput(host = "127.0.0.1", path = "/" + "a/".repeat(129)),
                messageId = 1,
                token = byteArrayOf(),
            )
        }
    }

    @Test
    fun serviceUnavailableCodeHasSingleCanonicalMapping() {
        assertEquals("5.03 Service Unavailable", CoapCodec.responseCodeText(163))
    }

    @Test
    fun emptyMessageCannotContainTokenOptionsOrPayload() {
        assertFailsWith<IllegalArgumentException> {
            CoapCodec.parse(byteArrayOf(0x41, 0x00, 0x00, 0x01, 0x7F))
        }
        assertFailsWith<IllegalArgumentException> {
            CoapCodec.parse(byteArrayOf(0x40, 0x00, 0x00, 0x01, 0x10))
        }
    }

    @Test
    fun optionNumberOverflowIsRejected() {
        val packet = byteArrayOf(
            0x40,
            0x00,
            0x00,
            0x01,
            0xE0.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
        )
        assertFailsWith<IllegalArgumentException> { CoapCodec.parse(packet) }
    }

    @Test
    fun oversizedDatagramsAndOptionFloodsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            CoapCodec.parse(ByteArray(65_508).also {
                it[0] = 0x40
                it[1] = 0x01
            })
        }
        val optionFlood = ByteArray(4 + 129)
        optionFlood[0] = 0x40
        optionFlood[1] = 0x01
        for (index in 4 until optionFlood.size) optionFlood[index] = 0x10
        assertFailsWith<IllegalArgumentException> { CoapCodec.parse(optionFlood) }
    }

    @Test
    fun requestRejectsTooManyCombinedOptions() {
        val path = "/" + (0 until 65).joinToString("/") { "p$it" }
        val query = (0 until 64).joinToString("&") { "q$it=v" }
        assertFailsWith<IllegalArgumentException> {
            CoapCodec.encodeRequest(
                CoapRequestInput(
                    host = "127.0.0.1",
                    path = path,
                    query = query,
                    contentFormat = 0,
                ),
                messageId = 1,
                token = byteArrayOf(),
            )
        }
    }

    @Test
    fun integerOptionRangesAreValidatedBeforeEncoding() {
        assertFailsWith<IllegalArgumentException> {
            CoapCodec.encodeRequest(
                CoapRequestInput(host = "127.0.0.1", contentFormat = -1),
                messageId = 1,
                token = byteArrayOf(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CoapCodec.encodeRequest(
                CoapRequestInput(host = "127.0.0.1", accept = 65_536),
                messageId = 1,
                token = byteArrayOf(),
            )
        }
    }


    @Test
    fun contentFormatMapsTextAndBinaryRepresentations() {
        assertEquals("text/plain", CoapCodec.contentTypeForFormat(0))
        assertEquals("application/json", CoapCodec.contentTypeForFormat(50))
        assertEquals("application/cbor", CoapCodec.contentTypeForFormat(60))
        assertEquals("application/octet-stream", CoapCodec.contentTypeForFormat(999))
        assertEquals(null, CoapCodec.contentTypeForFormat(null))
    }

}
