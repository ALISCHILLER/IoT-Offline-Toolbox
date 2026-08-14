package com.msa.iotofflinetoolbox.core.network


import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class JavaNetworkProbeTest {
    private val probe = JavaNetworkProbe("Desktop test")

    @Test
    fun tcpExchangeSendsAndReceivesPayload() = runBlocking {
        val server = ServerSocket(0)
        val serverThread = thread(name = "tcp-test-server") {
            server.use {
                it.accept().use { client ->
                    val request = ByteArray(64)
                    client.getInputStream().read(request)
                    client.getOutputStream().apply {
                        write("tcp-ok".encodeToByteArray())
                        flush()
                    }
                }
            }
        }

        val result = probe.tcpExchange("127.0.0.1", server.localPort, "hello".encodeToByteArray(), 1_500, 4096)

        assertNull(result.error)
        assertEquals("tcp-ok", result.responseText)
        assertEquals(6, result.bytesReceived)
        serverThread.join()
    }

    @Test
    fun udpExchangeSendsAndReceivesDatagram() = runBlocking {
        val server = DatagramSocket(0)
        val serverThread = thread(name = "udp-test-server") {
            server.use {
                val requestBuffer = ByteArray(64)
                val request = DatagramPacket(requestBuffer, requestBuffer.size)
                it.receive(request)
                val response = "udp-ok".encodeToByteArray()
                it.send(DatagramPacket(response, response.size, request.address, request.port))
            }
        }

        val result = probe.udpExchange("127.0.0.1", server.localPort, "hello".encodeToByteArray(), 1_500, 4096)

        assertNull(result.error)
        assertEquals("udp-ok", result.responseText)
        assertEquals(6, result.bytesReceived)
        serverThread.join()
    }
    @Test
    fun udpTimeoutIsReportedAsFailure() = runBlocking {
        val server = DatagramSocket(0)
        val serverThread = thread(name = "udp-timeout-test-server") {
            server.use {
                val request = DatagramPacket(ByteArray(64), 64)
                it.receive(request)
                Thread.sleep(500)
            }
        }

        val result = probe.udpExchange("127.0.0.1", server.localPort, "hello".encodeToByteArray(), 100, 4096)

        assertNotNull(result.error)
        assertEquals("Timed out waiting for a UDP response", result.error)
        assertEquals(0, result.bytesReceived)
        serverThread.join()
    }

    @Test
    fun tcpReadTimeoutWithoutBytesIsReportedAsFailure() = runBlocking {
        val server = ServerSocket(0)
        val serverThread = thread(name = "tcp-timeout-test-server") {
            server.use {
                it.accept().use { Thread.sleep(500) }
            }
        }

        val result = probe.tcpExchange("127.0.0.1", server.localPort, byteArrayOf(), 100, 4096)

        assertEquals("Timed out waiting for a TCP response", result.error)
        assertEquals(0, result.bytesReceived)
        serverThread.join()
    }

    @Test
    fun portScanRejectsUnboundedDirectInput() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            probe.scanPorts("127.0.0.1", List(4_097) { (it % 65_535) + 1 }, 100, 32)
        }
    }

    @Test
    fun tcpTruncationIsReportedOnlyWhenResponseExceedsLimit() = runBlocking {
        suspend fun exchange(response: ByteArray): SocketExchangeResult {
            val server = ServerSocket(0)
            val serverThread = thread(name = "tcp-boundary-test-server") {
                server.use {
                    it.accept().use { client ->
                        client.getOutputStream().apply {
                            write(response)
                            flush()
                        }
                    }
                }
            }
            val result = probe.tcpExchange("127.0.0.1", server.localPort, byteArrayOf(), 1_500, 1_024)
            serverThread.join()
            return result
        }

        val exactPayload = "1".repeat(1_024)
        val exact = exchange(exactPayload.encodeToByteArray())
        assertEquals(exactPayload, exact.responseText)
        assertFalse(exact.truncated)

        val oversized = exchange("2".repeat(1_025).encodeToByteArray())
        assertEquals("2".repeat(1_024), oversized.responseText)
        assertTrue(oversized.truncated)
    }

    @Test
    fun udpTruncationUsesAnExtraCaptureByteWhenAvailable() = runBlocking {
        val server = DatagramSocket(0)
        val serverThread = thread(name = "udp-boundary-test-server") {
            server.use {
                val request = DatagramPacket(ByteArray(64), 64)
                it.receive(request)
                val response = "3".repeat(1_025).encodeToByteArray()
                it.send(DatagramPacket(response, response.size, request.address, request.port))
            }
        }

        val result = probe.udpExchange("127.0.0.1", server.localPort, byteArrayOf(), 1_500, 1_024)
        serverThread.join()
        assertEquals("3".repeat(1_024), result.responseText)
        assertTrue(result.truncated)
    }

}
