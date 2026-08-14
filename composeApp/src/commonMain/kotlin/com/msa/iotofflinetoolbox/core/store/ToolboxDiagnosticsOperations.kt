package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.HistoryProtocol
import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.model.PayloadEncoding
import com.msa.iotofflinetoolbox.core.payload.PayloadCodec
import com.msa.iotofflinetoolbox.core.policy.CidrCalculator
import com.msa.iotofflinetoolbox.core.policy.DiscoveryEndpoints
import com.msa.iotofflinetoolbox.core.policy.PortSpecParser
import com.msa.iotofflinetoolbox.core.port.NetworkProbe

/** Application service for bounded IP diagnostics and local discovery. */
internal class ToolboxDiagnosticsOperations(
    private val networkProbe: NetworkProbe,
    private val runner: ToolboxOperationRunner,
) : DiagnosticsActions {
    override fun calculateSubnet(cidr: String) {
        runCatching { CidrCalculator.calculate(cidr) }
            .onSuccess {
                runner.update { copy(subnetResult = it, feedback = null) }
                runner.log(LogLevel.SUCCESS, "Subnet", text("Calculated $cidr", "محدوده $cidr محاسبه شد"))
            }
            .onFailure { runner.fail("Subnet", it) }
    }

    override fun ping(host: String, timeoutMillis: Int) {
        if (!runner.requireCapability(runner.snapshot().capabilities.ping, "Ping")) return
        runner.launch(text("Probing $host", "در حال بررسی دسترسی $host")) {
            val normalizedHost = requireHost(host)
            val result = networkProbe.ping(normalizedHost, timeoutMillis)
            val displayMessage = if (result.reachable) {
                text(
                    "$normalizedHost answered in ${result.latencyMillis ?: 0} ms",
                    "$normalizedHost در ${result.latencyMillis ?: 0} میلی‌ثانیه پاسخ داد",
                )
            } else {
                text(
                    "$normalizedHost did not answer within $timeoutMillis ms",
                    "$normalizedHost در مهلت $timeoutMillis میلی‌ثانیه پاسخ نداد",
                )
            }
            runner.update { copy(pingResult = result.copy(message = displayMessage)) }
            runner.log(if (result.reachable) LogLevel.SUCCESS else LogLevel.WARNING, "Ping", displayMessage)
            runner.addHistory(
                HistoryProtocol.PING,
                normalizedHost,
                displayMessage,
                result.reachable,
                response = result.resolvedAddress.orEmpty(),
                durationMillis = result.latencyMillis,
            )
        }
    }

    override fun resolve(host: String) {
        if (!runner.requireCapability(runner.snapshot().capabilities.dnsLookup, "DNS")) return
        runner.launch(text("Resolving $host", "در حال یافتن نشانی $host")) {
            val normalizedHost = requireHost(host)
            val addresses = networkProbe.resolve(normalizedHost)
            runner.update { copy(dnsResults = addresses) }
            val success = addresses.isNotEmpty()
            val message = if (success) {
                text(
                    "Resolved $normalizedHost to ${addresses.joinToString()}",
                    "$normalizedHost به ${addresses.joinToString()} تبدیل شد",
                )
            } else {
                text("No addresses returned for $normalizedHost", "هیچ نشانی برای $normalizedHost یافت نشد")
            }
            runner.log(if (success) LogLevel.SUCCESS else LogLevel.WARNING, "DNS", message)
            runner.addHistory(HistoryProtocol.DNS, normalizedHost, message, success, response = addresses.joinToString())
        }
    }

    override fun exchangeTcp(
        host: String,
        port: Int,
        payload: String,
        encoding: PayloadEncoding,
        timeoutMillis: Int,
    ) = exchangeSocket(
        source = "TCP",
        protocol = HistoryProtocol.TCP,
        enabled = runner.snapshot().capabilities.tcpClient,
        host = host,
        port = port,
        payload = payload,
        encoding = encoding,
        timeoutMillis = timeoutMillis,
        exchange = networkProbe::tcpExchange,
    )

    override fun exchangeUdp(
        host: String,
        port: Int,
        payload: String,
        encoding: PayloadEncoding,
        timeoutMillis: Int,
    ) = exchangeSocket(
        source = "UDP",
        protocol = HistoryProtocol.UDP,
        enabled = runner.snapshot().capabilities.udpClient,
        host = host,
        port = port,
        payload = payload,
        encoding = encoding,
        timeoutMillis = timeoutMillis,
        exchange = networkProbe::udpExchange,
    )

    override fun scanPorts(host: String, portSpec: String, timeoutMillis: Int) {
        if (!runner.requireCapability(runner.snapshot().capabilities.tcpPortScan, "Port scanner")) return
        val snapshot = runner.snapshot()
        val ports = runCatching { PortSpecParser.parse(portSpec, snapshot.settings.maxPortScanItems) }
            .getOrElse {
                runner.fail("Port scanner", it)
                return
            }
        runner.launch(text("Scanning ${ports.size} ports on $host", "در حال اسکن ${ports.size} پورت روی $host")) {
            val settings = runner.snapshot().settings
            val results = networkProbe.scanPorts(host.trim(), ports, timeoutMillis, settings.scanConcurrency)
            runner.update { copy(portResults = results) }
            val open = results.filter { it.isOpen }
            val message = text(
                "Completed scan: ${open.size} open of ${results.size}",
                "اسکن کامل شد: ${open.size} پورت باز از ${results.size}",
            )
            runner.log(LogLevel.SUCCESS, "Port scanner", message)
            runner.addHistory(
                HistoryProtocol.PORT_SCAN,
                host,
                message,
                successful = true,
                request = portSpec,
                response = open.joinToString { "${it.port}/${it.serviceHint}" },
            )
        }
    }

    override fun discover(cidr: String, timeoutMillis: Int) {
        if (!runner.requireCapability(runner.snapshot().capabilities.hostDiscovery, "LAN discovery")) return
        runner.launch(text("Discovering hosts in $cidr", "در حال یافتن میزبان‌ها در $cidr")) {
            val settings = runner.snapshot().settings
            val results = networkProbe.discover(
                cidr.trim(),
                timeoutMillis,
                settings.scanConcurrency,
                settings.maxDiscoveryHosts,
            )
            runner.update { copy(discoveredDevices = results) }
            val message = text(
                "Found ${results.size} reachable hosts in $cidr",
                "${results.size} میزبان در $cidr در دسترس بود",
            )
            runner.log(LogLevel.SUCCESS, "Discovery", message)
            runner.addHistory(
                HistoryProtocol.LAN_DISCOVERY,
                cidr,
                message,
                successful = true,
                response = results.joinToString { it.host },
            )
        }
    }

    override fun discoverSsdp(timeoutMillis: Int, maxResults: Int) {
        if (!runner.requireCapability(runner.snapshot().capabilities.ssdpDiscovery, "SSDP")) return
        runner.launch(text("Discovering UPnP/SSDP devices", "در حال کشف دستگاه‌های UPnP/SSDP")) {
            val results = networkProbe.discoverSsdp(timeoutMillis, maxResults.coerceIn(1, MAX_DISCOVERY_RESULTS))
            runner.update { copy(ssdpDevices = results) }
            val message = text("Discovered ${results.size} SSDP services", "${results.size} سرویس SSDP کشف شد")
            runner.log(LogLevel.SUCCESS, "SSDP", message)
            runner.addHistory(
                HistoryProtocol.SSDP,
                DiscoveryEndpoints.SSDP_MULTICAST_ADDRESS,
                message,
                successful = true,
                response = results.joinToString { it.location },
            )
        }
    }

    override fun discoverMdns(timeoutMillis: Int, maxResults: Int) {
        if (!runner.requireCapability(runner.snapshot().capabilities.mdnsDiscovery, "mDNS")) return
        runner.launch(text("Discovering Bonjour/mDNS services", "در حال کشف سرویس‌های Bonjour/mDNS")) {
            val results = networkProbe.discoverMdns(timeoutMillis, maxResults.coerceIn(1, MAX_DISCOVERY_RESULTS))
            runner.update { copy(mdnsServices = results) }
            val message = text("Discovered ${results.size} mDNS services", "${results.size} سرویس mDNS کشف شد")
            runner.log(LogLevel.SUCCESS, "mDNS", message)
            runner.addHistory(
                HistoryProtocol.MDNS,
                DiscoveryEndpoints.MDNS_MULTICAST_ADDRESS,
                message,
                successful = true,
                response = results.joinToString { "${it.instanceName} ${it.host.orEmpty()}:${it.port ?: ""}" },
            )
        }
    }

    private fun exchangeSocket(
        source: String,
        protocol: HistoryProtocol,
        enabled: Boolean,
        host: String,
        port: Int,
        payload: String,
        encoding: PayloadEncoding,
        timeoutMillis: Int,
        exchange: suspend (String, Int, ByteArray, Int, Int) -> com.msa.iotofflinetoolbox.core.model.SocketExchangeResult,
    ) {
        if (!runner.requireCapability(enabled, source)) return
        runner.launch("$source $host:$port") {
            val bytes = PayloadCodec.decode(payload, encoding)
            val result = exchange(
                requireHost(host),
                port,
                bytes,
                timeoutMillis,
                runner.snapshot().settings.maxResponseBytes,
            )
            runner.update { copy(socketResult = result) }
            val success = result.error == null
            runner.log(
                if (success) LogLevel.SUCCESS else LogLevel.ERROR,
                source,
                result.error ?: text(
                    "Received ${result.bytesReceived} bytes from ${result.endpoint}",
                    "${result.bytesReceived} بایت از ${result.endpoint} دریافت شد",
                ),
            )
            runner.addHistory(
                protocol,
                result.endpoint,
                result.error ?: text(
                    "$source exchange completed${if (result.truncated) " (truncated)" else ""}",
                    "تبادل $source کامل شد${if (result.truncated) " (خروجی کوتاه شده)" else ""}",
                ),
                success,
                request = payload,
                response = result.responseText.ifBlank { result.responseHex },
                durationMillis = result.elapsedMillis,
            )
        }
    }

    private fun requireHost(host: String): String = host.trim().also {
        require(it.isNotEmpty()) { text("Host cannot be empty", "میزبان نمی‌تواند خالی باشد") }
    }

    private fun text(english: String, persian: String): String = runner.text(english, persian)

    private companion object {
        const val MAX_DISCOVERY_RESULTS = 500
    }
}
