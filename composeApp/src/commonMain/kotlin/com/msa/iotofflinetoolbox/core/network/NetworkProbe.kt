package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.*

/** Platform network capabilities used by the shared workbench. */
interface NetworkProbe {
    val capabilities: PlatformCapabilities

    suspend fun ping(host: String, timeoutMillis: Int): PingResult
    suspend fun resolve(host: String): List<String>

    suspend fun tcpExchange(
        host: String,
        port: Int,
        payload: ByteArray,
        timeoutMillis: Int,
        maxResponseBytes: Int,
    ): SocketExchangeResult

    suspend fun udpExchange(
        host: String,
        port: Int,
        payload: ByteArray,
        timeoutMillis: Int,
        maxResponseBytes: Int,
    ): SocketExchangeResult

    suspend fun scanPorts(
        host: String,
        ports: List<Int>,
        timeoutMillis: Int,
        concurrency: Int,
    ): List<PortScanResult>

    suspend fun discover(
        cidr: String,
        timeoutMillis: Int,
        concurrency: Int,
        maxHosts: Int,
    ): List<DiscoveredDevice>

    suspend fun discoverSsdp(timeoutMillis: Int, maxResults: Int): List<SsdpDevice>
    suspend fun discoverMdns(timeoutMillis: Int, maxResults: Int): List<MdnsService>

    fun close() = Unit
}

expect fun createPlatformNetworkProbe(): NetworkProbe
