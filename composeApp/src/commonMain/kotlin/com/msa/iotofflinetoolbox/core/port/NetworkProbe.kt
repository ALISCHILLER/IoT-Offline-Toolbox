package com.msa.iotofflinetoolbox.core.port


import com.msa.iotofflinetoolbox.core.model.DiscoveredDevice
import com.msa.iotofflinetoolbox.core.model.MdnsService
import com.msa.iotofflinetoolbox.core.model.PingResult
import com.msa.iotofflinetoolbox.core.model.PlatformCapabilities
import com.msa.iotofflinetoolbox.core.model.PortScanResult
import com.msa.iotofflinetoolbox.core.model.SocketExchangeResult
import com.msa.iotofflinetoolbox.core.model.SsdpDevice

/** Platform-neutral application port for bounded local-network diagnostics. */
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
