package com.msa.iotofflinetoolbox.core.policy

import com.msa.iotofflinetoolbox.core.policy.HeaderParser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HeaderParserTest {
    @Test
    fun parsesHeaderLines() {
        assertEquals(mapOf("Accept" to "application/json", "X-Test" to "yes"), HeaderParser.parse("Accept: application/json\nX-Test: yes"))
    }

    @Test
    fun rejectsMalformedHeader() {
        assertFailsWith<IllegalArgumentException> { HeaderParser.parse("Broken header") }
    }
    @Test
    fun rejectsHeaderInjectionAndInvalidNames() {
        assertFailsWith<IllegalArgumentException> { HeaderParser.parse("X-Test: ok\rInjected: value") }
        assertFailsWith<IllegalArgumentException> { HeaderParser.parse("Bad Header: value") }
    }

    @Test
    fun rejectsInvalidHeaderLimits() {
        assertFailsWith<IllegalArgumentException> { HeaderParser.parse("X-Test: yes", maximumHeaders = 0) }
        assertFailsWith<IllegalArgumentException> { HeaderParser.parse("X-Test: yes", maximumHeaders = 1_001) }
    }
    @Test
    fun rejectsCaseInsensitiveDuplicatesAndControlCharacters() {
        assertFailsWith<IllegalArgumentException> {
            HeaderParser.parse("Authorization: one\nauthorization: two")
        }
        assertFailsWith<IllegalArgumentException> {
            HeaderParser.parse("X-Test: before\u0001after")
        }
    }

}
