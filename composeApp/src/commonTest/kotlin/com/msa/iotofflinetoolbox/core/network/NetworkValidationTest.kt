package com.msa.iotofflinetoolbox.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NetworkValidationTest {
    @Test
    fun receiveWindowUsesOneTotalBudget() {
        assertEquals(250, remainingReceiveWindowMillis(1_000, 750))
        assertTrue(remainingReceiveWindowMillis(1_000, 1_001) <= 0)
    }

    @Test
    fun websocketEngineManagedHeadersAreRejected() {
        listOf("Host", "Connection", "Upgrade", "Sec-WebSocket-Protocol").forEach { name ->
            assertTrue(isWebSocketEngineManagedHeader(name))
            assertFailsWith<IllegalArgumentException> {
                validateWebSocketHeaders(mapOf(name to "value"))
            }
        }
        validateWebSocketHeaders(mapOf("Authorization" to "Bearer token", "X-Trace-Id" to "123"))
    }
}
