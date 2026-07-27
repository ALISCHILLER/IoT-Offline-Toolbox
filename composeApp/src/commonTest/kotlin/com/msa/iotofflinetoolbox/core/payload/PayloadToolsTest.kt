package com.msa.iotofflinetoolbox.core.payload

import com.msa.iotofflinetoolbox.core.model.PayloadEncoding
import com.msa.iotofflinetoolbox.core.model.PayloadOperation
import com.msa.iotofflinetoolbox.core.payload.Base64Codec
import com.msa.iotofflinetoolbox.core.payload.PayloadTools
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PayloadToolsTest {
    @Test
    fun base64RoundTripPreservesUtf8() {
        val text = "MSA IoT ابزار"
        assertEquals(text, Base64Codec.decode(Base64Codec.encode(text.encodeToByteArray())).decodeToString())
    }

    @Test
    fun jsonFormattingAndValidationWork() {
        val formatted = PayloadTools.transform("{\"ok\":true,\"value\":2}", PayloadOperation.FORMAT_JSON)
        assertTrue(formatted.valid)
        assertTrue(formatted.output.contains("\n"))
        assertFalse(PayloadTools.transform("{bad}", PayloadOperation.VALIDATE_JSON).valid)
    }

    @Test
    fun variablesCanBeExpanded() {
        assertEquals("device-42", PayloadTools.expandVariables("device-{{id}}", mapOf("id" to "42")))
    }

    @Test
    fun crc32MatchesKnownVector() {
        assertEquals("CBF43926", PayloadTools.transform("123456789", PayloadOperation.CRC32).output)
    }
    @Test
    fun textPayloadsAreBoundedByEncodedByteSize() {
        assertFailsWith<IllegalArgumentException> {
            PayloadCodec.decode("é".repeat(600_000), PayloadEncoding.TEXT)
        }
    }

    @Test
    fun variableExpansionRejectsOutputAmplification() {
        val input = "{{large}}".repeat(70)
        val variables = mapOf("large" to "x".repeat(65_536))
        assertFailsWith<IllegalArgumentException> {
            PayloadTools.expandVariables(input, variables)
        }
    }

    @Test
    fun variableExpansionPreservesUnknownTokensAndReplacesKnownTokens() {
        val output = PayloadTools.expandVariables("a={{known}} b={{unknown}}", mapOf("known" to "ok"))
        assertEquals("a=ok b={{unknown}}", output)
    }

    @Test
    fun hexPrefixesAreOnlyAcceptedAtTokenBoundaries() {
        assertEquals("DE AD", PayloadCodec.decodeHex("0xDE 0xAD").toDisplayHex())
        assertFailsWith<IllegalArgumentException> { PayloadCodec.decodeHex("10x2") }
    }

}

class Base64ValidationTest {
    @Test
    fun rejectsMalformedPaddingAndNonCanonicalUnusedBits() {
        listOf("====", "A===", "AA=A", "AA==AAAA", "AB==", "AAB=").forEach { invalid ->
            assertFailsWith<IllegalArgumentException>(invalid) { Base64Codec.decode(invalid) }
        }
    }

    @Test
    fun acceptsCanonicalKnownVectorsAndWhitespace() {
        assertEquals("M", Base64Codec.decode("TQ==").decodeToString())
        assertEquals("Ma", Base64Codec.decode("TWE=").decodeToString())
        assertEquals("Man", Base64Codec.decode("T W F u\n").decodeToString())
    }

    @Test
    fun rejectsOversizedEncodedInputBeforeAllocation() {
        val oversized = "A".repeat(1_398_104)
        assertFailsWith<IllegalArgumentException> { Base64Codec.decode(oversized) }
    }
}
