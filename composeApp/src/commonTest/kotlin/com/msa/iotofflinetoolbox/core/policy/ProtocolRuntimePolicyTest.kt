package com.msa.iotofflinetoolbox.core.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProtocolRuntimePolicyTest {
    @Test
    fun receiveWindowUsesOneTotalBudget() {
        assertEquals(250, remainingReceiveWindowMillis(1_000, 750))
        assertTrue(remainingReceiveWindowMillis(1_000, 1_001) <= 0)
    }

    @Test
    fun mqttReceiveLoopRunsOnlyWhenInboundProtocolWorkRemains() {
        assertEquals(false, mqttRequiresReceiveLoop("", "", publishQos = 0))
        assertEquals(false, mqttRequiresReceiveLoop("", "devices/relay", publishQos = 0))
        assertEquals(true, mqttRequiresReceiveLoop("", "devices/relay", publishQos = 1))
        assertEquals(true, mqttRequiresReceiveLoop("devices/#", "", publishQos = 0))
        assertFailsWith<IllegalArgumentException> {
            mqttRequiresReceiveLoop("", "devices/relay", publishQos = 2)
        }
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
