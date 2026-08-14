package com.msa.iotofflinetoolbox.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SsdpParserTest {
    @Test
    fun requestContainsRequiredHeaders() {
        val request = SsdpParser.searchRequest()
        assertTrue(request.startsWith("M-SEARCH * HTTP/1.1"))
        assertTrue(request.contains("MAN: \"ssdp:discover\""))
        assertTrue(request.endsWith("\r\n\r\n"))
    }

    @Test
    fun responseIsParsedCaseInsensitively() {
        val response = """HTTP/1.1 200 OK\r
CACHE-CONTROL: max-age=1800\r
LOCATION: http://192.168.1.2/device.xml\r
SERVER: Linux/1.0 UPnP/1.1\r
ST: upnp:rootdevice\r
USN: uuid:device-1::upnp:rootdevice\r
\r
""".replace("\\r", "\r")
        val parsed = assertNotNull(SsdpParser.parse(response, "192.168.1.2"))
        assertEquals("http://192.168.1.2/device.xml", parsed.location)
        assertEquals("uuid:device-1::upnp:rootdevice", parsed.usn)
    }
    @Test
    fun searchTargetRejectsHeaderInjection() {
        assertFailsWith<IllegalArgumentException> {
            SsdpParser.searchRequest("ssdp:all\r\nX-Injected: yes")
        }
    }

    @Test
    fun onlySuccessfulHttpResponsesAreAccepted() {
        assertNull(SsdpParser.parse("NOTIFY * HTTP/1.1\r\nUSN: uuid:test\r\n\r\n"))
        assertNull(SsdpParser.parse("HTTP/1.1 404 Not Found\r\nUSN: uuid:test\r\n\r\n"))
    }

    @Test
    fun malformedOrInvalidHeaderNamesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            SsdpParser.parse("HTTP/1.1 200 OK\r\nBroken header\r\n\r\n")
        }
        assertFailsWith<IllegalArgumentException> {
            SsdpParser.parse("HTTP/1.1 200 OK\r\nBad Header: value\r\n\r\n")
        }
    }

    @Test
    fun responseHeaderCountIsBounded() {
        val response = buildString {
            append("HTTP/1.1 200 OK\r\n")
            repeat(257) { append("X-$it: value\r\n") }
            append("\r\n")
        }
        assertFailsWith<IllegalArgumentException> { SsdpParser.parse(response) }
    }

}
