package com.msa.iotofflinetoolbox.core.policy

import com.msa.iotofflinetoolbox.core.policy.CidrCalculator
import com.msa.iotofflinetoolbox.core.policy.Ipv4
import com.msa.iotofflinetoolbox.core.policy.PortSpecParser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IpToolsTest {
    @Test
    fun ipv4RoundTrip() {
        val value = Ipv4.parse("192.168.1.42")
        assertEquals("192.168.1.42", Ipv4.format(value))
    }

    @Test
    fun cidrCalculatesBoundaries() {
        val result = CidrCalculator.calculate("192.168.10.25/24")
        assertEquals("192.168.10.0", result.networkAddress)
        assertEquals("192.168.10.255", result.broadcastAddress)
        assertEquals("255.255.255.0", result.subnetMask)
        assertEquals(254, result.usableAddresses)
    }

    @Test
    fun invalidAddressIsRejected() {
        assertFailsWith<IllegalArgumentException> { Ipv4.parse("192.168.1.999") }
    }

    @Test
    fun portExpressionsAreDeduplicatedAndSorted() {
        assertEquals(listOf(22, 80, 81, 82, 443), PortSpecParser.parse("443,80-82,22,80"))
    }

    @Test
    fun invalidPortLimitsAreRejectedBeforeExpansion() {
        assertFailsWith<IllegalArgumentException> { PortSpecParser.parse("80", maximumPorts = 0) }
        assertFailsWith<IllegalArgumentException> { PortSpecParser.parse("80", maximumPorts = 65_536) }
    }
    @Test
    fun serviceAliasesAndExclusionsAreExpanded() {
        assertEquals(
            listOf(80, 443, 1883, 1884, 1886),
            PortSpecParser.parse("web,mqtt,1883-1886,!8080,!1885,!8000,!8008,!8081,!8443,!8888,!9000"),
        )
    }

    @Test
    fun presetExpansionHonorsConfiguredSafetyLimit() {
        assertFailsWith<IllegalArgumentException> {
            PortSpecParser.parse("well-known", maximumPorts = 100)
        }
    }

    @Test
    fun professionalServiceAliasesExpandDeterministically() {
        assertEquals(listOf(389, 445, 4840, 5671, 5672, 9092), PortSpecParser.parse("ldap,smb,opcua,amqp,kafka"))
        assertTrue(1433 in PortSpecParser.parse("databases"))
        assertTrue(8883 in PortSpecParser.parse("messaging"))
        assertTrue(993 in PortSpecParser.parse("mail"))
    }

    @Test
    fun repeatedDuplicateRangesAreBoundedByExpansionWorkLimit() {
        assertFailsWith<IllegalArgumentException> {
            PortSpecParser.parse(List(17) { "1-4096" }.joinToString(","))
        }
    }

}
