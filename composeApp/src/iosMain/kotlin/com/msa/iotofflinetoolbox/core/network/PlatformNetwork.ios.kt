package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.*
import com.msa.iotofflinetoolbox.core.payload.toDisplayHex
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.flush
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.time.TimeSource

/**
 * Native iOS implementation backed by Ktor Network.
 *
 * Direct TCP/UDP operations are enabled. Bonjour and SSDP remain capability-gated because raw
 * multicast distribution requires Apple entitlements and must be validated on a signed device.
 */
internal class IosNetworkProbe : NetworkProbe {
    private val selector = SelectorManager(Dispatchers.Default)

    override val capabilities = PlatformCapabilities(
        platformName = "iOS",
        ping = true,
        tcpPortScan = true,
        hostDiscovery = true,
        ssdpDiscovery = false,
        mdnsDiscovery = false,
        dnsLookup = false,
        tcpClient = true,
        udpClient = true,
        httpClient = true,
        httpConnectTimeout = false,
        httpSocketTimeout = true,
        webSocketClient = true,
        mqttWebSocketClient = true,
        coapClient = true,
        publicCleartextOverride = false,
        notes = "Native TCP/UDP diagnostics, CoAP and bounded LAN probing are enabled. Raw Bonjour/SSDP require Apple multicast entitlement, and public cleartext transport remains governed by ATS rather than an in-app switch.",
    )

    override suspend fun ping(host: String, timeoutMillis: Int): PingResult {
        require(host.isNotBlank()) { "Host cannot be empty" }
        val boundedTimeout = timeoutMillis.coerceIn(50, 60_000)
        val mark = TimeSource.Monotonic.markNow()
        val reachable = withTimeoutOrNull(boundedTimeout.toLong()) {
            COMMON_REACHABILITY_PORTS.any { port ->
                val remainingMillis = boundedTimeout - mark.elapsedNow().inWholeMilliseconds.toInt()
                remainingMillis > 0 && canConnect(host.trim(), port, remainingMillis.coerceAtMost(750))
            }
        } ?: false
        val elapsed = mark.elapsedNow().inWholeMilliseconds
        return PingResult(
            host = host.trim(),
            reachable = reachable,
            latencyMillis = elapsed.takeIf { reachable },
            resolvedAddress = null,
            message = if (reachable) {
                "$host accepted a bounded TCP reachability probe in $elapsed ms"
            } else {
                "$host did not accept a TCP reachability probe within $boundedTimeout ms"
            },
        )
    }

    override suspend fun resolve(host: String): List<String> = emptyList()

    override suspend fun tcpExchange(
        host: String,
        port: Int,
        payload: ByteArray,
        timeoutMillis: Int,
        maxResponseBytes: Int,
    ): SocketExchangeResult {
        validateEndpoint(host, port, timeoutMillis, maxResponseBytes)
        require(payload.size <= MAX_REQUEST_BYTES) { "TCP payload exceeds $MAX_REQUEST_BYTES bytes" }
        val mark = TimeSource.Monotonic.markNow()
        val outcome = runCatching {
            val socket = withTimeoutOrNull(timeoutMillis.toLong()) {
                aSocket(selector).tcp().connect(host.trim(), port)
            } ?: throw IllegalStateException("TCP connection timed out")
            try {
                val output = socket.openWriteChannel(autoFlush = true)
                if (payload.isNotEmpty()) {
                    output.writeByteArray(payload)
                    output.flush()
                }
                val response = readBounded(socket, timeoutMillis, maxResponseBytes)
                SocketExchangeResult(
                    protocol = "TCP",
                    endpoint = "$host:$port",
                    responseText = HttpResponseBodyTools.decode(response.bytes, contentType = null).text,
                    responseHex = response.bytes.toDisplayHex(),
                    bytesReceived = response.bytes.size,
                    elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                    truncated = response.truncated || response.timedOut,
                    error = if (response.timedOut && response.bytes.isEmpty()) "Timed out waiting for a TCP response" else null,
                )
            } finally {
                socket.close()
            }
        }
        return outcome.getOrElse { error ->
            if (error is CancellationException) throw error
            SocketExchangeResult(
                protocol = "TCP",
                endpoint = "$host:$port",
                responseText = "",
                responseHex = "",
                bytesReceived = 0,
                elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                error = error.message ?: error::class.simpleName,
            )
        }
    }

    override suspend fun udpExchange(
        host: String,
        port: Int,
        payload: ByteArray,
        timeoutMillis: Int,
        maxResponseBytes: Int,
    ): SocketExchangeResult {
        validateEndpoint(host, port, timeoutMillis, maxResponseBytes)
        require(payload.size <= MAX_UDP_PAYLOAD_BYTES) { "UDP payload exceeds $MAX_UDP_PAYLOAD_BYTES bytes" }
        val mark = TimeSource.Monotonic.markNow()
        val outcome = runCatching {
            val address = InetSocketAddress(host.trim(), port)
            val socket = aSocket(selector).udp().bind()
            try {
                val packet = Buffer().apply { write(payload) }
                socket.send(Datagram(packet = packet, address = address))
                val incoming = withTimeoutOrNull(timeoutMillis.toLong()) { socket.receive() }
                    ?: return@runCatching SocketExchangeResult(
                        protocol = "UDP",
                        endpoint = "$host:$port",
                        responseText = "",
                        responseHex = "",
                        bytesReceived = 0,
                        elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                        error = "Timed out waiting for a UDP response",
                    )
                val raw = incoming.packet.readByteArray()
                val response = raw.copyOf(minOf(raw.size, maxResponseBytes))
                SocketExchangeResult(
                    protocol = "UDP",
                    endpoint = incoming.address.toString(),
                    responseText = HttpResponseBodyTools.decode(response, contentType = null).text,
                    responseHex = response.toDisplayHex(),
                    bytesReceived = response.size,
                    elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                    truncated = raw.size > response.size,
                )
            } finally {
                socket.close()
            }
        }
        return outcome.getOrElse { error ->
            if (error is CancellationException) throw error
            SocketExchangeResult(
                protocol = "UDP",
                endpoint = "$host:$port",
                responseText = "",
                responseHex = "",
                bytesReceived = 0,
                elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                error = error.message ?: error::class.simpleName,
            )
        }
    }

    override suspend fun scanPorts(
        host: String,
        ports: List<Int>,
        timeoutMillis: Int,
        concurrency: Int,
    ): List<PortScanResult> = coroutineScope {
        require(host.isNotBlank()) { "Host cannot be empty" }
        require(ports.isNotEmpty()) { "Port list cannot be empty" }
        require(ports.size <= MAX_PORT_SCAN_ITEMS) { "Port scan cannot contain more than $MAX_PORT_SCAN_ITEMS ports" }
        require(ports.all { it in 1..65_535 }) { "Port list contains an invalid port" }
        val semaphore = Semaphore(concurrency.coerceIn(1, 128))
        ports.distinct().map { port ->
            async { semaphore.withPermit { probePort(host.trim(), port, timeoutMillis) } }
        }.awaitAll().sortedBy { it.port }
    }

    override suspend fun discover(
        cidr: String,
        timeoutMillis: Int,
        concurrency: Int,
        maxHosts: Int,
    ): List<DiscoveredDevice> = coroutineScope {
        val semaphore = Semaphore(concurrency.coerceIn(1, 128))
        CidrCalculator.hostAddresses(cidr, maxHosts).map { host ->
            async {
                semaphore.withPermit {
                    val result = ping(host, timeoutMillis)
                    if (result.reachable) DiscoveredDevice(host = host, latencyMillis = result.latencyMillis) else null
                }
            }
        }.awaitAll().filterNotNull().sortedBy { Ipv4.parse(it.host) }
    }

    override suspend fun discoverSsdp(timeoutMillis: Int, maxResults: Int): List<SsdpDevice> = emptyList()

    override suspend fun discoverMdns(timeoutMillis: Int, maxResults: Int): List<MdnsService> = emptyList()

    override fun close() {
        selector.close()
    }

    private suspend fun readBounded(socket: Socket, timeoutMillis: Int, maxBytes: Int): BoundedBytes {
        val input = socket.openReadChannel()
        val captureLimit = maxBytes + 1
        val captured = ByteArray(captureLimit)
        var capturedSize = 0
        val completed = withTimeoutOrNull(timeoutMillis.toLong()) {
            val buffer = ByteArray(minOf(8_192, captureLimit))
            while (capturedSize < captureLimit) {
                val count = input.readAvailable(buffer)
                if (count < 0) break
                if (count == 0) continue
                val accepted = minOf(count, captureLimit - capturedSize)
                buffer.copyInto(
                    destination = captured,
                    destinationOffset = capturedSize,
                    startIndex = 0,
                    endIndex = accepted,
                )
                capturedSize += accepted
            }
            true
        } ?: false
        return BoundedBytes(
            bytes = captured.copyOf(minOf(capturedSize, maxBytes)),
            truncated = capturedSize > maxBytes,
            timedOut = !completed,
        )
    }

    private suspend fun probePort(host: String, port: Int, timeoutMillis: Int): PortScanResult {
        val mark = TimeSource.Monotonic.markNow()
        return try {
            val socket = withTimeoutOrNull(timeoutMillis.coerceIn(50, 60_000).toLong()) {
                aSocket(selector).tcp().connect(host, port)
            } ?: return PortScanResult(
                port = port,
                isOpen = false,
                latencyMillis = mark.elapsedNow().inWholeMilliseconds,
                serviceHint = serviceHint(port),
                error = "Connection timed out or was filtered",
                state = PortState.FILTERED,
            )
            socket.close()
            PortScanResult(
                port = port,
                isOpen = true,
                latencyMillis = mark.elapsedNow().inWholeMilliseconds,
                serviceHint = serviceHint(port),
                state = PortState.OPEN,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            PortScanResult(
                port = port,
                isOpen = false,
                latencyMillis = mark.elapsedNow().inWholeMilliseconds,
                serviceHint = serviceHint(port),
                error = error.message ?: error::class.simpleName ?: "Connection failed",
                state = PortState.ERROR,
            )
        }
    }

    private suspend fun canConnect(host: String, port: Int, timeoutMillis: Int): Boolean {
        if (port !in 1..65_535) return false
        return runCatching {
            val socket = withTimeoutOrNull(timeoutMillis.coerceIn(50, 60_000).toLong()) {
                aSocket(selector).tcp().connect(host, port)
            } ?: return false
            socket.close()
            true
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            false
        }
    }

    private fun validateEndpoint(host: String, port: Int, timeoutMillis: Int, maxResponseBytes: Int) {
        require(host.isNotBlank()) { "Host cannot be empty" }
        require(port in 1..65_535) { "Port must be between 1 and 65535" }
        require(timeoutMillis in 50..60_000) { "Timeout must be between 50 and 60000 ms" }
        require(maxResponseBytes in 1_024..1_048_576) { "Maximum response bytes must be between 1024 and 1048576" }
    }

    private data class BoundedBytes(val bytes: ByteArray, val truncated: Boolean, val timedOut: Boolean)

    private companion object {
        const val MAX_REQUEST_BYTES = 1_048_576
        const val MAX_PORT_SCAN_ITEMS = 4_096
        const val MAX_UDP_PAYLOAD_BYTES = 65_507
        val COMMON_REACHABILITY_PORTS = listOf(80, 443, 22, 1883, 8883, 5683)
    }
}

actual fun createPlatformNetworkProbe(): NetworkProbe = IosNetworkProbe()
