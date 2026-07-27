package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.*

/** Browser-safe capability adapter for platforms without raw socket access. */
internal class LimitedNetworkProbe(private val platform: String, private val details: String) : NetworkProbe {
    override val capabilities = PlatformCapabilities(
        platformName = platform,
        ping = false,
        tcpPortScan = false,
        hostDiscovery = false,
        ssdpDiscovery = false,
        mdnsDiscovery = false,
        dnsLookup = false,
        tcpClient = false,
        udpClient = false,
        httpClient = true,
        httpConnectTimeout = false,
        httpSocketTimeout = false,
        webSocketClient = true,
        mqttWebSocketClient = true,
        coapClient = false,
        manualCookieHeader = false,
        customWebSocketHeaders = false,
        browserManagedRequestHeaders = true,
        manualHttpRedirects = false,
        publicCleartextOverride = false,
        notes = details,
    )

    override suspend fun ping(host: String, timeoutMillis: Int): PingResult = PingResult(
        host = host,
        reachable = false,
        latencyMillis = null,
        resolvedAddress = null,
        message = "Raw reachability is unavailable on $platform; use HTTP, WebSocket or MQTT over WebSocket instead.",
    )

    override suspend fun resolve(host: String): List<String> = emptyList()

    override suspend fun tcpExchange(
        host: String,
        port: Int,
        payload: ByteArray,
        timeoutMillis: Int,
        maxResponseBytes: Int,
    ): SocketExchangeResult = unsupportedExchange("TCP", host, port)

    override suspend fun udpExchange(
        host: String,
        port: Int,
        payload: ByteArray,
        timeoutMillis: Int,
        maxResponseBytes: Int,
    ): SocketExchangeResult = unsupportedExchange("UDP", host, port)

    override suspend fun scanPorts(
        host: String,
        ports: List<Int>,
        timeoutMillis: Int,
        concurrency: Int,
    ): List<PortScanResult> = ports.map { port ->
        PortScanResult(
            port = port,
            isOpen = false,
            latencyMillis = null,
            serviceHint = serviceHint(port),
            error = "Raw TCP sockets are unavailable on $platform",
            state = PortState.ERROR,
        )
    }

    override suspend fun discover(
        cidr: String,
        timeoutMillis: Int,
        concurrency: Int,
        maxHosts: Int,
    ): List<DiscoveredDevice> = emptyList()

    override suspend fun discoverSsdp(timeoutMillis: Int, maxResults: Int): List<SsdpDevice> = emptyList()

    override suspend fun discoverMdns(timeoutMillis: Int, maxResults: Int): List<MdnsService> = emptyList()

    private fun unsupportedExchange(protocol: String, host: String, port: Int) = SocketExchangeResult(
        protocol = protocol,
        endpoint = "$host:$port",
        responseText = "",
        responseHex = "",
        bytesReceived = 0,
        elapsedMillis = 0,
        error = "$protocol sockets are unavailable on $platform",
    )
}

actual fun createPlatformNetworkProbe(): NetworkProbe = LimitedNetworkProbe(
    platform = "Browser Wasm",
    details = "Browser sandboxing blocks raw ICMP, DNS, UDP and arbitrary TCP sockets. HTTP, WebSocket, MQTT over WebSocket and all offline tools remain available subject to CORS and endpoint policy.",
)
