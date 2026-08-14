package com.msa.iotofflinetoolbox.core.policy

import com.msa.iotofflinetoolbox.core.policy.HttpResponseBodyTools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpResponseBodyToolsTest {
    @Test
    fun declaredTextAndValidUtf8AreRenderedAsText() {
        val json = "{\"name\":\"حسگر\"}".encodeToByteArray()
        val declared = HttpResponseBodyTools.decode(json, "application/json; charset=utf-8")
        assertTrue(declared.isText)
        assertEquals(json.decodeToString(), declared.text)

        val inferred = HttpResponseBodyTools.decode("plain response".encodeToByteArray(), null)
        assertTrue(inferred.isText)
        assertEquals("plain response", inferred.text)
    }

    @Test
    fun invalidUtf8BinaryPayloadIsNotRenderedAsText() {
        val bytes = byteArrayOf(0x00, 0xFF.toByte(), 0x80.toByte(), 0x01, 0x7F)
        val decoded = HttpResponseBodyTools.decode(bytes, "application/octet-stream")
        assertFalse(decoded.isText)
        assertEquals("", decoded.text)
        assertEquals("00 FF 80 01 7F", HttpResponseBodyTools.toHex(bytes))
        assertEquals("AP+AAX8=", HttpResponseBodyTools.toBase64(bytes))
    }

    @Test
    fun malformedAndOutOfRangeUtf8SequencesAreRejected() {
        val overlong = byteArrayOf(0xC0.toByte(), 0xAF.toByte())
        val surrogate = byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte())
        val aboveUnicode = byteArrayOf(0xF4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte())
        assertFalse(HttpResponseBodyTools.isValidUtf8(overlong))
        assertFalse(HttpResponseBodyTools.isValidUtf8(surrogate))
        assertFalse(HttpResponseBodyTools.isValidUtf8(aboveUnicode))
    }
    @Test
    fun explicitBinaryMediaTypeOverridesAsciiHeuristics() {
        val asciiBytes = "MZ-compatible-binary-header".encodeToByteArray()
        val decoded = HttpResponseBodyTools.decode(asciiBytes, "application/octet-stream")
        assertFalse(decoded.isText)
        assertEquals("", decoded.text)
    }

    @Test
    fun declaredTextWithInvalidUtf8FallsBackToBinaryInspection() {
        val invalid = byteArrayOf(0xC3.toByte(), 0x28)
        val decoded = HttpResponseBodyTools.decode(invalid, "text/plain; charset=utf-8")
        assertFalse(decoded.isText)
        assertEquals("", decoded.text)
    }

}
