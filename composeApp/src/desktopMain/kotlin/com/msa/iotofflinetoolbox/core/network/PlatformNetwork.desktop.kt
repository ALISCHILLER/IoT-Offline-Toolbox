package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.*
import com.msa.iotofflinetoolbox.core.payload.toDisplayHex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.ConnectException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException

/** JVM-backed network implementation used by Desktop JVM. */
internal class JavaNetworkProbe(private val platform: String) : NetworkProbe {
    override val capabilities = PlatformCapabilities(
        platformName = platform,
        ping = true,
        tcpPortScan = true,
        hostDiscovery = true,
        ssdpDiscovery = true,
        mdnsDiscovery = true,
        dnsLookup = true,
        tcpClient = true,
        udpClient = true,
        coapClient = true,
        notes = "Raw TCP/UDP diagnostics, bounded reachability probes, SSDP and DNS-SD/mDNS are enabled. ICMP availability still depends on the operating system.",
    )

    override suspend fun ping(host: String, timeoutMillis: Int): PingResult = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "Host cannot be empty" }
        val boundedTimeout = timeoutMillis.coerceIn(50, 60_000)
        val address = InetAddress.getByName(host.trim())
        val started = System.nanoTime()
        val deadlineNanos = started + boundedTimeout * 1_000_000L
        val icmpBudget = (boundedTimeout / 2).coerceIn(50, boundedTimeout)
        val icmpReachable = runCatching { address.isReachable(icmpBudget) }.getOrDefault(false)
        val tcpReachable = if (icmpReachable) {
            false
        } else {
            COMMON_REACHABILITY_PORTS.any { port ->
                val remainingMillis = ((deadlineNanos - System.nanoTime()) / 1_000_000L).toInt()
                remainingMillis > 0 && canConnect(address.hostAddress, port, remainingMillis.coerceAtMost(750))
            }
        }
        val reachable = icmpReachable || tcpReachable
        val elapsed = elapsedMillis(started)
        PingResult(
            host = host.trim(),
            reachable = reachable,
            latencyMillis = elapsed.takeIf { reachable },
            resolvedAddress = address.hostAddress,
            message = when {
                icmpReachable -> "$host answered a platform reachability probe in $elapsed ms"
                tcpReachable -> "$host accepted a bounded TCP reachability probe in $elapsed ms"
                else -> "$host did not answer a reachability probe within $boundedTimeout ms"
            },
        )
    }

    override suspend fun resolve(host: String): List<String> = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "Host cannot be empty" }
        InetAddress.getAllByName(host.trim()).mapNotNull { it.hostAddress }.distinct()
    }

    override suspend fun tcpExchange(
        host: String,
        port: Int,
        payload: ByteArray,
        timeoutMillis: Int,
        maxResponseBytes: Int,
    ): SocketExchangeResult {
        validateEndpoint(host, port, timeoutMillis, maxResponseBytes)
        require(payload.size <= MAX_REQUEST_BYTES) { "TCP payload exceeds $MAX_REQUEST_BYTES bytes" }
        val started = System.nanoTime()
        return try {
            useCancellableIo(Socket()) { socket ->
                socket.connect(InetSocketAddress(host.trim(), port), timeoutMillis)
                socket.soTimeout = timeoutMillis
                if (payload.isNotEmpty()) {
                    socket.getOutputStream().apply { write(payload); flush() }
                }
                val response = readBounded(socket, maxResponseBytes)
                SocketExchangeResult(
                    protocol = "TCP",
                    endpoint = "$host:$port",
                    responseText = HttpResponseBodyTools.decode(response.bytes, contentType = null).text,
                    responseHex = response.bytes.toDisplayHex(),
                    bytesReceived = response.bytes.size,
                    elapsedMillis = elapsedMillis(started),
                    truncated = response.truncated || response.timedOut,
                    error = if (response.timedOut && response.bytes.isEmpty()) "Timed out waiting for a TCP response" else null,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SocketExchangeResult(
                protocol = "TCP",
                endpoint = "$host:$port",
                responseText = "",
                responseHex = "",
                bytesReceived = 0,
                elapsedMillis = elapsedMillis(started),
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
        val started = System.nanoTime()
        return try {
            useCancellableIo(DatagramSocket()) { socket ->
                socket.soTimeout = timeoutMillis
                socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName(host.trim()), port))
                val responseLimit = maxResponseBytes.coerceAtMost(MAX_UDP_PAYLOAD_BYTES)
                val captureCapacity = minOf(responseLimit + 1, MAX_UDP_PAYLOAD_BYTES)
                val buffer = ByteArray(captureCapacity)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val response = buffer.copyOf(minOf(packet.length, responseLimit))
                SocketExchangeResult(
                    protocol = "UDP",
                    endpoint = "${packet.address.hostAddress}:${packet.port}",
                    responseText = HttpResponseBodyTools.decode(response, contentType = null).text,
                    responseHex = response.toDisplayHex(),
                    bytesReceived = response.size,
                    elapsedMillis = elapsedMillis(started),
                    truncated = packet.length > response.size,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SocketExchangeResult(
                protocol = "UDP",
                endpoint = "$host:$port",
                responseText = "",
                responseHex = "",
                bytesReceived = 0,
                elapsedMillis = elapsedMillis(started),
                error = if (error is SocketTimeoutException) "Timed out waiting for a UDP response" else error.message ?: error::class.simpleName,
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
        val boundedTimeout = timeoutMillis.coerceIn(50, 60_000)
        val semaphore = Semaphore(concurrency.coerceIn(1, 256))
        ports.distinct().map { port ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    probePort(host.trim(), port, boundedTimeout)
                }
            }
        }.awaitAll().sortedBy { it.port }
    }

    override suspend fun discover(
        cidr: String,
        timeoutMillis: Int,
        concurrency: Int,
        maxHosts: Int,
    ): List<DiscoveredDevice> = coroutineScope {
        val hosts = CidrCalculator.hostAddresses(cidr, maxHosts)
        val semaphore = Semaphore(concurrency.coerceIn(1, 256))
        hosts.map { host ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val result = ping(host, timeoutMillis)
                    if (!result.reachable) null else DiscoveredDevice(
                        host = host,
                        resolvedName = runCatching { InetAddress.getByName(host).canonicalHostName }
                            .getOrNull()
                            ?.takeIf { it != host },
                        latencyMillis = result.latencyMillis,
                    )
                }
            }
        }.awaitAll().filterNotNull().sortedBy { Ipv4.parse(it.host) }
    }

    override suspend fun discoverSsdp(timeoutMillis: Int, maxResults: Int): List<SsdpDevice> = withContext(Dispatchers.IO) {
        val boundedTimeout = timeoutMillis.coerceIn(250, 15_000)
        val boundedResults = maxResults.coerceIn(1, 500)
        val request = SsdpParser.searchRequest(mxSeconds = (boundedTimeout / 1_000).coerceIn(1, 5)).encodeToByteArray()
        val devices = linkedMapOf<String, SsdpDevice>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = DISCOVERY_RECEIVE_TIMEOUT
            socket.send(DatagramPacket(request, request.size, InetAddress.getByName(SsdpParser.ADDRESS), SsdpParser.PORT))
            val deadline = System.nanoTime() + boundedTimeout.toLong() * 1_000_000L
            while (System.nanoTime() < deadline && devices.size < boundedResults) {
                val buffer = ByteArray(DISCOVERY_PACKET_BYTES)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.soTimeout = remainingSocketTimeoutMillis(deadline)
                    socket.receive(packet)
                    val response = buffer.copyOf(packet.length).decodeToString()
                    SsdpParser.parse(response, packet.address.hostAddress)?.let { device ->
                        devices[device.usn.ifBlank { device.location.ifBlank { packet.address.hostAddress } }] = device
                    }
                } catch (_: SocketTimeoutException) {
                    // Continue until the bounded deadline.
                }
            }
        }
        devices.values.sortedWith(compareBy({ it.server.orEmpty() }, { it.usn }))
    }

    override suspend fun discoverMdns(timeoutMillis: Int, maxResults: Int): List<MdnsService> = withContext(Dispatchers.IO) {
        val boundedTimeout = timeoutMillis.coerceIn(500, 20_000)
        val boundedResults = maxResults.coerceIn(1, 500)
        val socket = openMdnsSocket()
        socket.use {
            val operationDeadline = System.nanoTime() + boundedTimeout.toLong() * 1_000_000L
            val firstBudget = (boundedTimeout / 3).coerceIn(250, boundedTimeout)
            val firstPhase = queryMdns(socket, DnsSdCodec.SERVICE_ENUMERATION, firstBudget)
            val discoveredTypes = DnsSdCodec.serviceTypes(firstPhase)
            val serviceTypes = (discoveredTypes + COMMON_MDNS_SERVICES).distinct().take(MAX_MDNS_SERVICE_TYPES)
            val allMessages = firstPhase.toMutableList()
            for ((index, serviceType) in serviceTypes.withIndex()) {
                if (DnsSdCodec.services(allMessages).size >= boundedResults) break
                val remainingMillis = remainingDeadlineMillis(operationDeadline)
                if (remainingMillis <= 0) break
                val remainingTypes = (serviceTypes.size - index).coerceAtLeast(1)
                val perTypeTimeout = (remainingMillis / remainingTypes).coerceIn(1, 1_500)
                allMessages += queryMdns(socket, serviceType, perTypeTimeout)
            }
            DnsSdCodec.services(allMessages).take(boundedResults)
        }
    }

    private fun openMdnsSocket(): MulticastSocket {
        val group = InetAddress.getByName(DnsSdCodec.ADDRESS)
        val socket = MulticastSocket(null)
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(DnsSdCodec.PORT))
            socket.soTimeout = DISCOVERY_RECEIVE_TIMEOUT
            val interfaces = NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.filter { networkInterface ->
                    runCatching {
                        networkInterface.isUp &&
                            !networkInterface.isLoopback &&
                            networkInterface.supportsMulticast()
                    }.getOrDefault(false)
                }
                ?.toList()
                .orEmpty()
            val joined = interfaces.any { networkInterface ->
                runCatching {
                    socket.joinGroup(InetSocketAddress(group, DnsSdCodec.PORT), networkInterface)
                    true
                }.getOrDefault(false)
            }
            require(joined) { "No active multicast-capable network interface is available for mDNS" }
            return socket
        } catch (error: Exception) {
            socket.close()
            throw error
        }
    }

    private fun queryMdns(socket: MulticastSocket, name: String, timeoutMillis: Int): List<DnsSdCodec.DnsMessage> {
        val query = DnsSdCodec.query(name)
        val messages = mutableListOf<DnsSdCodec.DnsMessage>()
        socket.send(DatagramPacket(query, query.size, InetAddress.getByName(DnsSdCodec.ADDRESS), DnsSdCodec.PORT))
        val deadline = System.nanoTime() + timeoutMillis.toLong() * 1_000_000L
        while (System.nanoTime() < deadline) {
            val buffer = ByteArray(DISCOVERY_PACKET_BYTES)
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.soTimeout = remainingSocketTimeoutMillis(deadline)
                socket.receive(packet)
                runCatching { DnsSdCodec.parse(buffer.copyOf(packet.length)) }.getOrNull()?.let(messages::add)
            } catch (_: SocketTimeoutException) {
                // Continue until the bounded query deadline.
            }
        }
        return messages
    }

    private fun remainingDeadlineMillis(deadlineNanos: Long): Int {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0L) return 0
        return ((remainingNanos + 999_999L) / 1_000_000L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun remainingSocketTimeoutMillis(deadlineNanos: Long): Int =
        remainingDeadlineMillis(deadlineNanos).coerceIn(1, DISCOVERY_RECEIVE_TIMEOUT)

    private fun validateEndpoint(host: String, port: Int, timeoutMillis: Int, maxResponseBytes: Int) {
        require(host.isNotBlank()) { "Host cannot be empty" }
        require(port in 1..65_535) { "Port must be between 1 and 65535" }
        require(timeoutMillis in 50..60_000) { "Timeout must be between 50 and 60000 ms" }
        require(maxResponseBytes in 1_024..1_048_576) { "Maximum response bytes must be between 1024 and 1048576" }
    }

    private fun readBounded(socket: Socket, maxBytes: Int): BoundedBytes {
        val input = socket.getInputStream()
        val captureLimit = maxBytes + 1
        val output = ByteArrayOutputStream(minOf(captureLimit, 16_384))
        val buffer = ByteArray(minOf(8_192, captureLimit))
        var timedOut = false
        while (output.size() < captureLimit) {
            val count = try {
                input.read(buffer, 0, minOf(buffer.size, captureLimit - output.size()))
            } catch (_: SocketTimeoutException) {
                timedOut = true
                break
            }
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
        }
        val captured = output.toByteArray()
        return BoundedBytes(
            bytes = captured.copyOf(minOf(captured.size, maxBytes)),
            truncated = captured.size > maxBytes,
            timedOut = timedOut,
        )
    }

    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / 1_000_000

    private fun probePort(host: String, port: Int, timeoutMillis: Int): PortScanResult {
        val started = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMillis)
            }
            PortScanResult(
                port = port,
                isOpen = true,
                latencyMillis = elapsedMillis(started),
                serviceHint = serviceHint(port),
                state = PortState.OPEN,
            )
        } catch (error: SocketTimeoutException) {
            PortScanResult(
                port = port,
                isOpen = false,
                latencyMillis = elapsedMillis(started),
                serviceHint = serviceHint(port),
                error = "Connection timed out or was filtered",
                state = PortState.FILTERED,
            )
        } catch (error: ConnectException) {
            PortScanResult(
                port = port,
                isOpen = false,
                latencyMillis = elapsedMillis(started),
                serviceHint = serviceHint(port),
                error = error.message ?: "Connection refused",
                state = PortState.CLOSED,
            )
        } catch (error: Exception) {
            PortScanResult(
                port = port,
                isOpen = false,
                latencyMillis = elapsedMillis(started),
                serviceHint = serviceHint(port),
                error = error.message ?: error::class.simpleName ?: "Connection failed",
                state = PortState.ERROR,
            )
        }
    }

    private fun canConnect(host: String, port: Int, timeoutMillis: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMillis.coerceIn(50, 60_000))
            true
        }
    }.getOrDefault(false)

    private suspend fun <T, C : java.io.Closeable> useCancellableIo(
        resource: C,
        block: (C) -> T,
    ): T = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val worker = kotlinx.coroutines.CoroutineScope(continuation.context).launch(
            context = Dispatchers.IO,
            start = kotlinx.coroutines.CoroutineStart.LAZY,
        ) {
            try {
                val value = resource.use(block)
                if (continuation.isActive) continuation.resumeWith(Result.success(value))
            } catch (cancelled: CancellationException) {
                if (continuation.isActive) continuation.cancel(cancelled)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }
        }
        continuation.invokeOnCancellation {
            runCatching { resource.close() }
            worker.cancel()
        }
        worker.start()
    }

    private data class BoundedBytes(val bytes: ByteArray, val truncated: Boolean, val timedOut: Boolean)

    private companion object {
        const val MAX_REQUEST_BYTES = 1_048_576
        const val MAX_PORT_SCAN_ITEMS = 4_096
        const val MAX_UDP_PAYLOAD_BYTES = 65_507
        const val DISCOVERY_PACKET_BYTES = 65_535
        const val DISCOVERY_RECEIVE_TIMEOUT = 150
        const val MAX_MDNS_SERVICE_TYPES = 32
        val COMMON_REACHABILITY_PORTS = listOf(80, 443, 22, 1883, 8883, 5683)
        val COMMON_MDNS_SERVICES = listOf(
            "_http._tcp.local",
            "_https._tcp.local",
            "_mqtt._tcp.local",
            "_mqtts._tcp.local",
            "_coap._udp.local",
            "_ipp._tcp.local",
            "_hap._tcp.local",
            "_googlecast._tcp.local",
            "_airplay._tcp.local",
            "_raop._tcp.local",
        )
    }
}

actual fun createPlatformNetworkProbe(): NetworkProbe = JavaNetworkProbe("Desktop JVM")
