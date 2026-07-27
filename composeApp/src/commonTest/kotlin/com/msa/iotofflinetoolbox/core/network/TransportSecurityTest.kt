package com.msa.iotofflinetoolbox.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransportSecurityTest {
    @Test
    fun secureSchemesDoNotProduceWarnings() {
        assertNull(TransportSecurity.warningForUrl("https://example.com"))
        assertNull(TransportSecurity.warningForUrl("wss://broker.example/mqtt"))
    }

    @Test
    fun schemesAreTrimmedAndComparedCaseInsensitively() {
        assertTrue(TransportSecurity.hasScheme("  WSS://broker.example/mqtt  ", "ws", "wss"))
        assertTrue(TransportSecurity.hasScheme("HTTPS://example.com", "http", "https"))
        assertFalse(TransportSecurity.hasScheme("ftp://example.com", "http", "https"))
        assertEquals(
            "wss://broker.example/mqtt",
            TransportSecurity.requireScheme(
                rawUrl = "  WSS://broker.example/mqtt  ",
                allowedSchemes = setOf("ws", "wss"),
                label = "WebSocket URL",
            ),
        )
    }

    @Test
    fun localCleartextIsAllowedButVisible() {
        assertNotNull(TransportSecurity.warningForUrl("http://192.168.1.10/status"))
        assertFalse(TransportSecurity.isPublicCleartext("http://192.168.1.10?mode=diag"))
        assertFalse(TransportSecurity.isPublicCleartext("ws://sensor.local/socket"))
    }

    @Test
    fun publicCleartextIsClassifiedAsHighRisk() {
        assertTrue(TransportSecurity.isPublicCleartext("http://example.com/api"))
        assertTrue(TransportSecurity.warningForUrl("ws://example.com/socket")!!.contains("non-local"))
    }

    @Test
    fun dnsNamesStartingWithIpv6PrefixesAreNotMisclassified() {
        assertFalse(TransportSecurity.isLocalHost("fcdn.example.com"))
        assertFalse(TransportSecurity.isLocalHost("fdexample.com"))
        assertTrue(TransportSecurity.isPublicCleartext("http://fcdn.example.com/api"))
    }

    @Test
    fun ipv6LocalRangesUseActualHextets() {
        assertTrue(TransportSecurity.isLocalHost("::1"))
        assertTrue(TransportSecurity.isLocalHost("fc00::1"))
        assertTrue(TransportSecurity.isLocalHost("fd12:3456::1"))
        assertTrue(TransportSecurity.isLocalHost("fe80::1"))
        assertTrue(TransportSecurity.isLocalHost("febf::1%en0"))
        assertFalse(TransportSecurity.isLocalHost("fec0::1"))
        assertFalse(TransportSecurity.isLocalHost("2001:db8::1"))
    }

    @Test
    fun hostExtractionSupportsCredentialsPortsAndBracketedIpv6() {
        assertEquals("example.com", TransportSecurity.extractHost("https://user:pass@example.com:8443/api"))
        assertEquals("::1", TransportSecurity.extractHost("http://[::1]:8080/status"))
        assertEquals("fe80::1%25en0", TransportSecurity.extractHost("http://[fe80::1%25en0]:8080/status"))
    }

    @Test
    fun singleLabelHostsRequireExplicitCleartextOverride() {
        assertFalse(TransportSecurity.isLocalHost("plc-controller"))
        assertTrue(TransportSecurity.isPublicCleartext("http://plc-controller/status"))
    }

    @Test
    fun oversizedUrlsAreRejectedBeforeParsing() {
        assertFailsWith<IllegalArgumentException> {
            TransportSecurity.requireScheme(
                "https://example.com/" + "a".repeat(8_192),
                setOf("http", "https"),
                "HTTP URL",
            )
        }
    }

    @Test
    fun missingHostOrUnsupportedSchemeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            TransportSecurity.requireScheme("wss://", setOf("ws", "wss"), "WebSocket URL")
        }
        assertFailsWith<IllegalArgumentException> {
            TransportSecurity.requireScheme("ftp://example.com", setOf("http", "https"), "HTTP URL")
        }
    }
    @Test
    fun malformedAuthoritiesAreRejectedCentrally() {
        listOf(
            "https://example.com:bad/path",
            "https://example.com:0/path",
            "https://2001:db8::1/path",
            "https://example.com\\attacker.test/path",
            "https://user@/path",
        ).forEach { url ->
            assertFailsWith<IllegalArgumentException>(url) {
                TransportSecurity.requireScheme(url, setOf("http", "https"), "HTTP URL")
            }
        }
    }

    @Test
    fun embeddedCredentialsAreRejectedByAllValidatedTransportUrls() {
        listOf(
            "https://user:password@example.com/status" to setOf("http", "https"),
            "wss://user:password@example.com/socket" to setOf("ws", "wss"),
        ).forEach { (url, schemes) ->
            assertFailsWith<IllegalArgumentException>(url) {
                TransportSecurity.requireScheme(url, schemes, "Endpoint URL")
            }
            assertTrue(TransportSecurity.hasEmbeddedCredentials(url))
        }
    }

}
