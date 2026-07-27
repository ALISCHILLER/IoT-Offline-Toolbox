package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.data.SecretRedactor
import com.msa.iotofflinetoolbox.core.data.ToolboxRepository
import com.msa.iotofflinetoolbox.core.model.AppLanguage
import com.msa.iotofflinetoolbox.core.model.AppSettings
import com.msa.iotofflinetoolbox.core.model.AppState
import com.msa.iotofflinetoolbox.core.model.BackupImportMode
import com.msa.iotofflinetoolbox.core.model.BackupInspection
import com.msa.iotofflinetoolbox.core.model.CoapRequestInput
import com.msa.iotofflinetoolbox.core.model.EndpointProfile
import com.msa.iotofflinetoolbox.core.model.HistoryEntry
import com.msa.iotofflinetoolbox.core.model.HistoryProtocol
import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.model.MessageDirection
import com.msa.iotofflinetoolbox.core.model.MqttWebSocketInput
import com.msa.iotofflinetoolbox.core.model.PayloadEncoding
import com.msa.iotofflinetoolbox.core.model.PayloadOperation
import com.msa.iotofflinetoolbox.core.model.PayloadTemplate
import com.msa.iotofflinetoolbox.core.model.SavedDevice
import com.msa.iotofflinetoolbox.core.model.SsdpDevice
import com.msa.iotofflinetoolbox.core.model.TOOLBOX_BACKUP_VERSION
import com.msa.iotofflinetoolbox.core.model.ToolLog
import com.msa.iotofflinetoolbox.core.model.ToolScreen
import com.msa.iotofflinetoolbox.core.model.WebSocketRequestInput
import com.msa.iotofflinetoolbox.core.network.CidrCalculator
import com.msa.iotofflinetoolbox.core.network.CoapClient
import com.msa.iotofflinetoolbox.core.network.DnsSdCodec
import com.msa.iotofflinetoolbox.core.network.HttpRequestPreview
import com.msa.iotofflinetoolbox.core.network.HttpRequestValidationIssue
import com.msa.iotofflinetoolbox.core.network.HttpRequestValidationPolicy
import com.msa.iotofflinetoolbox.core.network.HttpRequestValidator
import com.msa.iotofflinetoolbox.core.network.HttpToolClient
import com.msa.iotofflinetoolbox.core.network.MqttWebSocketClient
import com.msa.iotofflinetoolbox.core.network.NetworkProbe
import com.msa.iotofflinetoolbox.core.network.PortSpecParser
import com.msa.iotofflinetoolbox.core.network.SsdpParser
import com.msa.iotofflinetoolbox.core.network.TransportSecurity
import com.msa.iotofflinetoolbox.core.network.WebSocketToolClient
import com.msa.iotofflinetoolbox.core.network.createPlatformNetworkProbe
import com.msa.iotofflinetoolbox.core.payload.PayloadCodec
import com.msa.iotofflinetoolbox.core.payload.PayloadTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import kotlin.time.Clock

private fun localized(language: AppLanguage, english: String, persian: String): String =
    if (language == AppLanguage.PERSIAN) persian else english

/** Single UDF state holder coordinating persistence, protocol clients and shared Compose UI. */
class ToolboxStore(
    private val repository: ToolboxRepository = ToolboxRepository(),
    private val networkProbe: NetworkProbe = createPlatformNetworkProbe(),
    private val httpClient: HttpToolClient = HttpToolClient(),
    private val webSocketClient: WebSocketToolClient = WebSocketToolClient(),
    private val mqttClient: MqttWebSocketClient = MqttWebSocketClient(),
    private val scope: CoroutineScope,
) {
    private val coapClient = CoapClient(networkProbe)
    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val operationCoordinator = OperationCoordinator(scope)

    fun navigate(screen: ToolScreen) {
        val settings = _state.value.settings.copy(lastScreen = screen)
        val persistenceError = persistenceFailure("Navigation settings") { repository.saveSettings(settings) }
        _state.value = _state.value.copy(
            currentScreen = screen,
            settings = settings,
            errorMessage = persistenceError,
            noticeMessage = null,
        )
        persistenceError?.let(::appendEphemeralStorageLog)
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(noticeMessage = null)
    }

    fun calculateSubnet(cidr: String) {
        runCatching { CidrCalculator.calculate(cidr) }
            .onSuccess {
                _state.value = _state.value.copy(subnetResult = it, errorMessage = null)
                log(LogLevel.SUCCESS, "Subnet", msg("Calculated $cidr", "محدوده $cidr محاسبه شد"))
            }
            .onFailure { fail("Subnet", it) }
    }

    fun ping(host: String, timeoutMillis: Int) {
        if (!requireCapability(_state.value.capabilities.ping, "Ping")) return
        launchOperation(msg("Probing $host", "در حال بررسی دسترسی $host")) {
            val normalizedHost = host.trim().also {
                require(it.isNotEmpty()) { msg("Host cannot be empty", "میزبان نمی‌تواند خالی باشد") }
            }
            val result = networkProbe.ping(normalizedHost, timeoutMillis)
            val displayMessage = if (result.reachable) {
                msg(
                    "$normalizedHost answered in ${result.latencyMillis ?: 0} ms",
                    "$normalizedHost در ${result.latencyMillis ?: 0} میلی‌ثانیه پاسخ داد",
                )
            } else {
                msg(
                    "$normalizedHost did not answer within $timeoutMillis ms",
                    "$normalizedHost در مهلت $timeoutMillis میلی‌ثانیه پاسخ نداد",
                )
            }
            val displayResult = result.copy(message = displayMessage)
            _state.value = _state.value.copy(pingResult = displayResult)
            log(if (result.reachable) LogLevel.SUCCESS else LogLevel.WARNING, "Ping", displayMessage)
            addHistory(HistoryProtocol.PING, normalizedHost, displayMessage, result.reachable, response = result.resolvedAddress.orEmpty(), durationMillis = result.latencyMillis)
        }
    }

    fun resolve(host: String) {
        if (!requireCapability(_state.value.capabilities.dnsLookup, "DNS")) return
        launchOperation(msg("Resolving $host", "در حال یافتن نشانی $host")) {
            val normalizedHost = host.trim().also {
                require(it.isNotEmpty()) { msg("Host cannot be empty", "میزبان نمی‌تواند خالی باشد") }
            }
            val addresses = networkProbe.resolve(normalizedHost)
            _state.value = _state.value.copy(dnsResults = addresses)
            val success = addresses.isNotEmpty()
            val message = if (success) {
                msg("Resolved $normalizedHost to ${addresses.joinToString()}", "$normalizedHost به ${addresses.joinToString()} تبدیل شد")
            } else {
                msg("No addresses returned for $normalizedHost", "هیچ نشانی برای $normalizedHost یافت نشد")
            }
            log(if (success) LogLevel.SUCCESS else LogLevel.WARNING, "DNS", message)
            addHistory(HistoryProtocol.DNS, normalizedHost, message, success, response = addresses.joinToString())
        }
    }

    fun exchangeTcp(host: String, port: Int, payload: String, encoding: PayloadEncoding, timeoutMillis: Int) {
        if (!requireCapability(_state.value.capabilities.tcpClient, "TCP")) return
        launchOperation("TCP $host:$port") {
            val bytes = PayloadCodec.decode(payload, encoding)
            val result = networkProbe.tcpExchange(host.trim(), port, bytes, timeoutMillis, _state.value.settings.maxResponseBytes)
            _state.value = _state.value.copy(socketResult = result)
            val success = result.error == null
            log(if (success) LogLevel.SUCCESS else LogLevel.ERROR, "TCP", result.error ?: msg("Received ${result.bytesReceived} bytes from ${result.endpoint}", "${result.bytesReceived} بایت از ${result.endpoint} دریافت شد"))
            addHistory(
                HistoryProtocol.TCP,
                result.endpoint,
                result.error ?: msg(
                    "TCP exchange completed${if (result.truncated) " (truncated)" else ""}",
                    "تبادل TCP کامل شد${if (result.truncated) " (خروجی کوتاه شده)" else ""}",
                ),
                success,
                request = payload,
                response = if (result.responseText.isNotBlank()) result.responseText else result.responseHex,
                durationMillis = result.elapsedMillis,
            )
        }
    }

    fun exchangeUdp(host: String, port: Int, payload: String, encoding: PayloadEncoding, timeoutMillis: Int) {
        if (!requireCapability(_state.value.capabilities.udpClient, "UDP")) return
        launchOperation("UDP $host:$port") {
            val bytes = PayloadCodec.decode(payload, encoding)
            val result = networkProbe.udpExchange(host.trim(), port, bytes, timeoutMillis, _state.value.settings.maxResponseBytes)
            _state.value = _state.value.copy(socketResult = result)
            val success = result.error == null
            log(if (success) LogLevel.SUCCESS else LogLevel.ERROR, "UDP", result.error ?: msg("Received ${result.bytesReceived} bytes from ${result.endpoint}", "${result.bytesReceived} بایت از ${result.endpoint} دریافت شد"))
            addHistory(
                HistoryProtocol.UDP,
                result.endpoint,
                result.error ?: msg(
                    "UDP exchange completed${if (result.truncated) " (truncated)" else ""}",
                    "تبادل UDP کامل شد${if (result.truncated) " (خروجی کوتاه شده)" else ""}",
                ),
                success,
                request = payload,
                response = if (result.responseText.isNotBlank()) result.responseText else result.responseHex,
                durationMillis = result.elapsedMillis,
            )
        }
    }

    fun executeCoap(input: CoapRequestInput) {
        if (!requireCapability(_state.value.capabilities.coapClient, "CoAP")) return
        launchOperation("CoAP ${input.host}:${input.port}${input.path}") {
            val result = coapClient.execute(input, _state.value.settings.maxResponseBytes)
            _state.value = _state.value.copy(coapResult = result)
            val success = result.error == null && result.responseCode in 64..95
            log(if (success) LogLevel.SUCCESS else LogLevel.ERROR, "CoAP", result.error ?: result.responseCodeText)
            addHistory(
                HistoryProtocol.COAP,
                result.endpoint,
                result.error ?: result.responseCodeText,
                success,
                request = "${input.method} ${input.path}${input.query.takeIf(String::isNotBlank)?.let { "?$it" }.orEmpty()}\n${input.payload}",
                response = result.payloadText.ifBlank { result.payloadHex },
                durationMillis = result.elapsedMillis,
            )
        }
    }

    fun scanPorts(host: String, portSpec: String, timeoutMillis: Int) {
        if (!requireCapability(_state.value.capabilities.tcpPortScan, "Port scanner")) return
        val ports = runCatching { PortSpecParser.parse(portSpec, _state.value.settings.maxPortScanItems) }.getOrElse { fail("Port scanner", it); return }
        launchOperation(msg("Scanning ${ports.size} ports on $host", "در حال اسکن ${ports.size} پورت روی $host")) {
            val results = networkProbe.scanPorts(host.trim(), ports, timeoutMillis, _state.value.settings.scanConcurrency)
            _state.value = _state.value.copy(portResults = results)
            val open = results.filter { it.isOpen }
            val message = msg("Completed scan: ${open.size} open of ${results.size}", "اسکن کامل شد: ${open.size} پورت باز از ${results.size}")
            log(LogLevel.SUCCESS, "Port scanner", message)
            addHistory(HistoryProtocol.PORT_SCAN, host, message, true, request = portSpec, response = open.joinToString { "${it.port}/${it.serviceHint}" })
        }
    }

    fun discover(cidr: String, timeoutMillis: Int) {
        if (!requireCapability(_state.value.capabilities.hostDiscovery, "LAN discovery")) return
        launchOperation(msg("Discovering hosts in $cidr", "در حال یافتن میزبان‌ها در $cidr")) {
            val results = networkProbe.discover(cidr.trim(), timeoutMillis, _state.value.settings.scanConcurrency, _state.value.settings.maxDiscoveryHosts)
            _state.value = _state.value.copy(discoveredDevices = results)
            val message = msg("Found ${results.size} reachable hosts in $cidr", "${results.size} میزبان در $cidr در دسترس بود")
            log(LogLevel.SUCCESS, "Discovery", message)
            addHistory(HistoryProtocol.LAN_DISCOVERY, cidr, message, true, response = results.joinToString { it.host })
        }
    }

    fun discoverSsdp(timeoutMillis: Int, maxResults: Int = 100) {
        if (!requireCapability(_state.value.capabilities.ssdpDiscovery, "SSDP")) return
        launchOperation(msg("Discovering UPnP/SSDP devices", "در حال کشف دستگاه‌های UPnP/SSDP")) {
            val results = networkProbe.discoverSsdp(timeoutMillis, maxResults.coerceIn(1, 500))
            _state.value = _state.value.copy(ssdpDevices = results)
            val message = msg("Discovered ${results.size} SSDP services", "${results.size} سرویس SSDP کشف شد")
            log(LogLevel.SUCCESS, "SSDP", message)
            addHistory(HistoryProtocol.SSDP, SsdpParser.ADDRESS, message, true, response = results.joinToString { it.location })
        }
    }

    fun discoverMdns(timeoutMillis: Int, maxResults: Int = 100) {
        if (!requireCapability(_state.value.capabilities.mdnsDiscovery, "mDNS")) return
        launchOperation(msg("Discovering Bonjour/mDNS services", "در حال کشف سرویس‌های Bonjour/mDNS")) {
            val results = networkProbe.discoverMdns(timeoutMillis, maxResults.coerceIn(1, 500))
            _state.value = _state.value.copy(mdnsServices = results)
            val message = msg("Discovered ${results.size} mDNS services", "${results.size} سرویس mDNS کشف شد")
            log(LogLevel.SUCCESS, "mDNS", message)
            addHistory(HistoryProtocol.MDNS, DnsSdCodec.ADDRESS, message, true, response = results.joinToString { "${it.instanceName} ${it.host.orEmpty()}:${it.port ?: ""}" })
        }
    }

    fun executeHttp(input: com.msa.iotofflinetoolbox.core.model.HttpRequestInput) {
        if (!requireCapability(_state.value.capabilities.httpClient, "HTTP")) return
        val capabilities = _state.value.capabilities
        val effectiveCleartext = _state.value.settings.allowPublicCleartext && capabilities.publicCleartextOverride
        val validationIssue = HttpRequestValidator.firstIssue(
            input = input,
            policy = HttpRequestValidationPolicy(
                allowPublicCleartext = effectiveCleartext,
                manualRedirects = capabilities.manualHttpRedirects,
                manualCookieHeader = capabilities.manualCookieHeader,
                browserManagedRequestHeaders = capabilities.browserManagedRequestHeaders,
            ),
        )
        if (validationIssue != null) {
            return validationFailure("HTTP", localizedHttpValidationIssue(validationIssue))
        }
        launchOperation("${input.method} ${SecretRedactor.redact(input.url)}") {
            val result = httpClient.execute(input, _state.value.settings.maxResponseBytes, effectiveCleartext)
            _state.value = _state.value.copy(httpResult = result)
            val success = result.error == null && result.statusCode in 200..299
            result.securityWarning?.let { log(LogLevel.WARNING, "HTTP security", it) }
            log(
                if (success) LogLevel.SUCCESS else LogLevel.ERROR,
                "HTTP",
                result.error ?: msg(
                    "${input.method} ${input.url} returned ${result.statusCode} in ${result.elapsedMillis} ms",
                    "درخواست ${input.method} به ${input.url} با وضعیت ${result.statusCode} در ${result.elapsedMillis} میلی‌ثانیه پایان یافت",
                ),
            )
            addHistory(
                HistoryProtocol.HTTP,
                input.url,
                result.error ?: "${result.statusCode} ${result.statusText}${if (result.truncated) " (truncated)" else ""}",
                success,
                request = HttpRequestPreview.curl(input, redactSecrets = true),
                response = if (result.bodyIsText) {
                    result.body
                } else {
                    msg(
                        "Binary response (${result.bytesReceived} bytes, ${result.contentType ?: "unknown content type"})",
                        "پاسخ باینری (${result.bytesReceived} بایت، ${result.contentType ?: "نوع محتوای نامشخص"})",
                    )
                },
                durationMillis = result.elapsedMillis,
            )
        }
    }

    fun executeWebSocket(input: WebSocketRequestInput) {
        if (!requireCapability(_state.value.capabilities.webSocketClient, "WebSocket")) return
        val capabilities = _state.value.capabilities
        if (input.headersText.isNotBlank() && !capabilities.customWebSocketHeaders) {
            return validationFailure(
                "WebSocket",
                msg(
                    "Browser WebSocket does not support arbitrary custom HTTP headers.",
                    "WebSocket مرورگر از Headerهای HTTP سفارشی پشتیبانی نمی‌کند.",
                ),
            )
        }
        if (!allowEndpoint(input.url, "WebSocket")) return
        launchOperation("WebSocket ${SecretRedactor.redact(input.url)}") {
            val result = webSocketClient.execute(
                input.copy(maxMessageBytes = minOf(input.maxMessageBytes, _state.value.settings.maxResponseBytes)),
                _state.value.settings.allowPublicCleartext && capabilities.publicCleartextOverride,
            )
            _state.value = _state.value.copy(webSocketResult = result)
            val success = result.error == null && result.connected
            result.securityWarning?.let { log(LogLevel.WARNING, "WebSocket security", it) }
            log(
                if (success) LogLevel.SUCCESS else LogLevel.ERROR,
                "WebSocket",
                result.error ?: msg(
                    "Received ${result.messages.count { it.direction == MessageDirection.RECEIVED }} messages",
                    "${result.messages.count { it.direction == MessageDirection.RECEIVED }} پیام دریافت شد",
                ),
            )
            addHistory(HistoryProtocol.WEBSOCKET, input.url, result.error ?: msg("WebSocket exchange completed", "تبادل WebSocket کامل شد"), success, request = "${input.headersText}\n${input.message}", response = result.messages.joinToString("\n") { "${it.direction}: ${it.payload}" }, durationMillis = result.elapsedMillis)
        }
    }

    fun executeMqtt(input: MqttWebSocketInput) {
        if (!requireCapability(_state.value.capabilities.mqttWebSocketClient, "MQTT")) return
        val capabilities = _state.value.capabilities
        if (!allowEndpoint(input.url, "MQTT WebSocket")) return
        launchOperation("MQTT ${SecretRedactor.redact(input.url)}") {
            val result = mqttClient.execute(
                input.copy(maxMessageBytes = minOf(input.maxMessageBytes, _state.value.settings.maxResponseBytes)),
                _state.value.settings.allowPublicCleartext && capabilities.publicCleartextOverride,
            )
            _state.value = _state.value.copy(mqttResult = result)
            val success = result.operationSuccessful
            result.securityWarning?.let { log(LogLevel.WARNING, "MQTT security", it) }
            log(if (success) LogLevel.SUCCESS else LogLevel.ERROR, "MQTT", result.error ?: msg("MQTT session completed with ${result.messages.size} publications", "نشست MQTT با ${result.messages.size} پیام انتشار کامل شد"))
            addHistory(
                HistoryProtocol.MQTT, input.url,
                result.error ?: msg("MQTT WebSocket session completed", "نشست MQTT روی WebSocket کامل شد"),
                success, request = "SUB ${input.subscribeTopic}\nPUB ${input.publishTopic}\n${input.payload}",
                response = result.messages.joinToString("\n") { "${it.topic}: ${it.payload}" },
                durationMillis = result.elapsedMillis,
            )
        }
    }

    fun transformPayload(input: String, operation: PayloadOperation, variables: Map<String, String> = emptyMap()) {
        val result = PayloadTools.transform(input, operation, variables)
        _state.value = _state.value.copy(payloadResult = result, errorMessage = if (result.valid) null else result.message)
        log(if (result.valid) LogLevel.SUCCESS else LogLevel.ERROR, "Payload", "${operation.title}: ${result.message}")
    }

    fun saveDevice(host: String, displayName: String = host, addresses: List<String> = listOf(host), services: List<String> = emptyList()) {
        val normalizedHost = host.trim()
        if (normalizedHost.isBlank()) return validationFailure("Devices", msg("Device host cannot be empty", "میزبان دستگاه نمی‌تواند خالی باشد"))
        if (normalizedHost.length > MAX_ENDPOINT_LENGTH) return validationFailure("Devices", msg("Device host is too long", "میزبان دستگاه بیش از حد طولانی است"))
        if (displayName.length > MAX_NAME_LENGTH) return validationFailure("Devices", msg("Device name is too long", "نام دستگاه بیش از حد طولانی است"))
        if (addresses.size > MAX_CHILD_ITEMS || services.size > MAX_CHILD_ITEMS) {
            return validationFailure("Devices", msg("Device address or service list exceeds the safety limit", "فهرست نشانی یا سرویس دستگاه از محدودیت ایمنی عبور کرده است"))
        }
        val current = _state.value.savedDevices
        val existing = current.firstOrNull { it.host.equals(normalizedHost, ignoreCase = true) }
        if (existing == null && current.size >= MAX_PERSISTED_ITEMS) {
            return validationFailure("Devices", msg("Device inventory reached the $MAX_PERSISTED_ITEMS item safety limit", "فهرست دستگاه‌ها به سقف ایمنی $MAX_PERSISTED_ITEMS مورد رسیده است"))
        }
        val device = SavedDevice(
            id = existing?.id ?: newId("device"),
            host = normalizedHost,
            displayName = displayName.ifBlank { normalizedHost }.trim().take(MAX_NAME_LENGTH),
            addresses = (existing?.addresses.orEmpty() + addresses)
                .map(String::trim)
                .filter(String::isNotBlank)
                .map { it.take(MAX_ENDPOINT_LENGTH) }
                .distinct()
                .take(MAX_CHILD_ITEMS),
            openPorts = existing?.openPorts.orEmpty().filter { it in 1..65_535 }.distinct().sorted().take(MAX_CHILD_ITEMS),
            services = (existing?.services.orEmpty() + services)
                .map(String::trim)
                .filter(String::isNotBlank)
                .map { SecretRedactor.redact(it).take(MAX_NAME_LENGTH) }
                .distinct()
                .take(MAX_CHILD_ITEMS),
            note = existing?.note.orEmpty().take(MAX_NOTES_LENGTH),
            tags = existing?.tags.orEmpty().take(MAX_CHILD_ITEMS),
            lastSeenMillis = now(),
        )
        val updated = (current.filterNot { it.id == existing?.id } + device).sortedBy { it.displayName.lowercase() }
        persistenceFailure("Device inventory") { repository.saveDevices(updated) }
            ?.let { return reportPersistenceFailure(it) }
        _state.value = _state.value.copy(savedDevices = updated)
        log(LogLevel.SUCCESS, "Devices", msg("Saved ${device.displayName} for offline access", "${device.displayName} برای دسترسی آفلاین ذخیره شد"))
    }

    fun saveSsdpDevice(device: SsdpDevice) {
        val host = extractHost(device.location).ifBlank { device.remoteAddress.orEmpty() }
        if (host.isBlank()) return validationFailure("SSDP", msg("This response does not contain a usable host", "این پاسخ میزبان قابل استفاده‌ای ندارد"))
        saveDevice(host, device.server ?: device.searchTarget ?: host, listOfNotNull(device.remoteAddress, host), listOfNotNull(device.searchTarget, device.server))
    }

    fun updateDevice(device: SavedDevice) {
        if (_state.value.savedDevices.none { it.id == device.id }) return validationFailure("Devices", msg("Saved device no longer exists", "دستگاه ذخیره‌شده دیگر وجود ندارد"))
        if (device.host.isBlank()) return validationFailure("Devices", msg("Device host cannot be empty", "میزبان دستگاه نمی‌تواند خالی باشد"))
        if (device.host.length > MAX_ENDPOINT_LENGTH || device.displayName.length > MAX_NAME_LENGTH) {
            return validationFailure("Devices", msg("Device host or name exceeds the safety limit", "میزبان یا نام دستگاه از محدودیت ایمنی عبور کرده است"))
        }
        if (device.addresses.size > MAX_CHILD_ITEMS || device.services.size > MAX_CHILD_ITEMS || device.tags.size > MAX_CHILD_ITEMS) {
            return validationFailure("Devices", msg("Device metadata exceeds the safety limit", "فراداده دستگاه از محدودیت ایمنی عبور کرده است"))
        }
        val normalized = device.copy(
            host = device.host.trim().take(MAX_ENDPOINT_LENGTH),
            displayName = device.displayName.ifBlank { device.host }.trim().take(MAX_NAME_LENGTH),
            addresses = device.addresses.map(String::trim).filter(String::isNotBlank).map { it.take(MAX_ENDPOINT_LENGTH) }.distinct().take(MAX_CHILD_ITEMS),
            openPorts = device.openPorts.filter { it in 1..65_535 }.distinct().sorted().take(MAX_CHILD_ITEMS),
            services = device.services.map(String::trim).filter(String::isNotBlank).map { SecretRedactor.redact(it).take(MAX_NAME_LENGTH) }.distinct().take(MAX_CHILD_ITEMS),
            note = SecretRedactor.redact(device.note).take(MAX_NOTES_LENGTH),
            tags = device.tags.map(SecretRedactor::redact).map(String::trim).filter(String::isNotBlank).map { it.take(MAX_NAME_LENGTH) }.distinct().take(MAX_CHILD_ITEMS),
        )
        val updated = _state.value.savedDevices.map { if (it.id == normalized.id) normalized else it }
        persistenceFailure("Device inventory") { repository.saveDevices(updated) }
            ?.let { return reportPersistenceFailure(it) }
        _state.value = _state.value.copy(savedDevices = updated)
        log(LogLevel.INFO, "Devices", msg("Updated ${normalized.displayName}", "${normalized.displayName} به‌روزرسانی شد"))
    }

    fun deleteDevice(id: String) {
        val target = _state.value.savedDevices.firstOrNull { it.id == id }
        val updated = _state.value.savedDevices.filterNot { it.id == id }
        persistenceFailure("Device inventory") { repository.saveDevices(updated) }
            ?.let { return reportPersistenceFailure(it) }
        _state.value = _state.value.copy(savedDevices = updated)
        log(LogLevel.WARNING, "Devices", msg("Deleted ${target?.displayName ?: id}", "${target?.displayName ?: id} حذف شد"))
    }

    fun saveProfile(profile: EndpointProfile) {
        if (profile.name.isBlank() || profile.hostOrUrl.isBlank()) return validationFailure("Profiles", msg("Profile name and endpoint are required", "نام پروفایل و Endpoint الزامی هستند"))
        if (profile.name.length > MAX_NAME_LENGTH || profile.hostOrUrl.length > MAX_ENDPOINT_LENGTH) {
            return validationFailure("Profiles", msg("Profile name or endpoint exceeds the safety limit", "نام یا Endpoint پروفایل از محدودیت ایمنی عبور کرده است"))
        }
        if (profile.headersText.length > MAX_HEADER_BLOCK_LENGTH || profile.notes.length > MAX_NOTES_LENGTH || profile.tags.size > MAX_CHILD_ITEMS) {
            return validationFailure("Profiles", msg("Profile headers, notes or tags exceed the safety limit", "Headerها، یادداشت‌ها یا برچسب‌های پروفایل از محدودیت ایمنی عبور کرده‌اند"))
        }
        if (_state.value.profiles.none { it.id == profile.id } && _state.value.profiles.size >= MAX_PERSISTED_ITEMS) {
            return validationFailure("Profiles", msg("Profile inventory reached the $MAX_PERSISTED_ITEMS item safety limit", "فهرست پروفایل‌ها به سقف ایمنی $MAX_PERSISTED_ITEMS مورد رسیده است"))
        }
        val now = now()
        val sanitizedHeaders = SecretRedactor.removeSensitiveHeaders(profile.headersText)
        val normalized = profile.copy(
            id = profile.id.ifBlank { newId("profile") }.take(MAX_ID_LENGTH),
            name = profile.name.trim().take(MAX_NAME_LENGTH),
            hostOrUrl = SecretRedactor.redact(profile.hostOrUrl.trim()).take(MAX_ENDPOINT_LENGTH),
            port = profile.port?.takeIf { it in 1..65_535 },
            username = SecretRedactor.redact(profile.username).take(MAX_NAME_LENGTH),
            defaultTopic = SecretRedactor.redact(profile.defaultTopic).take(MAX_ENDPOINT_LENGTH),
            headersText = sanitizedHeaders.value.take(MAX_HEADER_BLOCK_LENGTH),
            notes = SecretRedactor.redact(profile.notes).take(MAX_NOTES_LENGTH),
            tags = profile.tags.map(SecretRedactor::redact).map(String::trim).filter(String::isNotBlank).map { it.take(MAX_NAME_LENGTH) }.distinct().take(MAX_CHILD_ITEMS),
            createdAtMillis = profile.createdAtMillis.takeIf { it > 0 } ?: now,
            updatedAtMillis = now,
        )
        val updated = (_state.value.profiles.filterNot { it.id == normalized.id } + normalized).sortedBy { it.name.lowercase() }
        persistenceFailure("Endpoint profiles") { repository.saveProfiles(updated) }
            ?.let { return reportPersistenceFailure(it) }
        _state.value = _state.value.copy(
            profiles = updated,
            noticeMessage = if (sanitizedHeaders.removedNames.isEmpty()) {
                msg("Profile saved", "پروفایل ذخیره شد")
            } else {
                msg(
                    "Profile saved; sensitive headers were removed: ${sanitizedHeaders.removedNames.joinToString()}",
                    "پروفایل ذخیره شد؛ Headerهای حساس حذف شدند: ${sanitizedHeaders.removedNames.joinToString()}",
                )
            },
        )
        log(LogLevel.SUCCESS, "Profiles", msg("Saved ${normalized.name}", "پروفایل ${normalized.name} ذخیره شد"))
    }

    fun deleteProfile(id: String) {
        val target = _state.value.profiles.firstOrNull { it.id == id }
        val updated = _state.value.profiles.filterNot { it.id == id }
        persistenceFailure("Endpoint profiles") { repository.saveProfiles(updated) }
            ?.let { return reportPersistenceFailure(it) }
        _state.value = _state.value.copy(profiles = updated)
        log(LogLevel.WARNING, "Profiles", msg("Deleted ${target?.name ?: id}", "پروفایل ${target?.name ?: id} حذف شد"))
    }

    fun saveTemplate(template: PayloadTemplate) {
        if (template.name.isBlank() || template.content.isBlank()) return validationFailure("Templates", msg("Template name and content are required", "نام و محتوای قالب الزامی هستند"))
        if (template.name.length > MAX_NAME_LENGTH || template.contentType.length > MAX_NAME_LENGTH || template.notes.length > MAX_NOTES_LENGTH) {
            return validationFailure("Templates", msg("Template metadata exceeds the safety limit", "فراداده قالب از محدودیت ایمنی عبور کرده است"))
        }
        if (template.content.encodeToByteArray().size > MAX_TEMPLATE_BYTES) {
            return validationFailure("Templates", msg("Template content exceeds the 1 MiB safety limit", "محتوای قالب از سقف ایمنی ۱ مگابایت عبور کرده است"))
        }
        if (_state.value.templates.none { it.id == template.id } && _state.value.templates.size >= MAX_PERSISTED_ITEMS) {
            return validationFailure("Templates", msg("Template inventory reached the $MAX_PERSISTED_ITEMS item safety limit", "فهرست قالب‌ها به سقف ایمنی $MAX_PERSISTED_ITEMS مورد رسیده است"))
        }
        val now = now()
        val redactedContent = SecretRedactor.redact(template.content)
        val normalized = template.copy(
            id = template.id.ifBlank { newId("template") }.take(MAX_ID_LENGTH),
            name = template.name.trim().take(MAX_NAME_LENGTH),
            content = redactedContent,
            contentType = template.contentType.trim().take(MAX_NAME_LENGTH),
            notes = SecretRedactor.redact(template.notes).take(MAX_NOTES_LENGTH),
            createdAtMillis = template.createdAtMillis.takeIf { it > 0 } ?: now,
            updatedAtMillis = now,
        )
        val updated = (_state.value.templates.filterNot { it.id == normalized.id } + normalized).sortedBy { it.name.lowercase() }
        persistenceFailure("Payload templates") { repository.saveTemplates(updated) }
            ?.let { return reportPersistenceFailure(it) }
        _state.value = _state.value.copy(
            templates = updated,
            noticeMessage = if (redactedContent == template.content) {
                msg("Payload template saved", "قالب Payload ذخیره شد")
            } else {
                msg(
                    "Payload template saved; detected secret values were redacted",
                    "قالب Payload ذخیره شد؛ مقادیر محرمانه شناسایی‌شده پاک‌سازی شدند",
                )
            },
        )
        log(LogLevel.SUCCESS, "Templates", msg("Saved ${normalized.name}", "قالب ${normalized.name} ذخیره شد"))
    }

    fun deleteTemplate(id: String) {
        val target = _state.value.templates.firstOrNull { it.id == id }
        val updated = _state.value.templates.filterNot { it.id == id }
        persistenceFailure("Payload templates") { repository.saveTemplates(updated) }
            ?.let { return reportPersistenceFailure(it) }
        _state.value = _state.value.copy(templates = updated)
        log(LogLevel.WARNING, "Templates", msg("Deleted ${target?.name ?: id}", "قالب ${target?.name ?: id} حذف شد"))
    }

    fun exportBackup(includeLogs: Boolean) {
        runCatching { repository.exportBackup(includeLogs) }
            .onSuccess { backup ->
                _state.value = _state.value.copy(
                    backupText = backup,
                    backupInspection = localizedBackupInspection(backup),
                    noticeMessage = msg("Redacted backup JSON generated locally", "نسخه پشتیبان پالایش‌شده به‌صورت محلی تولید شد"),
                    errorMessage = null,
                )
                log(LogLevel.SUCCESS, "Backup", msg("Generated schema v$TOOLBOX_BACKUP_VERSION backup", "نسخه پشتیبان با Schema v$TOOLBOX_BACKUP_VERSION تولید شد"))
            }
            .onFailure { fail("Backup export", it) }
    }

    fun inspectBackup(raw: String) {
        _state.value = _state.value.copy(backupInspection = localizedBackupInspection(raw), errorMessage = null)
    }

    fun importBackup(raw: String, mode: BackupImportMode) {
        runCatching { repository.importBackup(raw, mode) }
            .onSuccess { summary ->
                reloadPersistentState()
                _state.value = _state.value.copy(
                    backupInspection = localizedBackupInspection(raw),
                    noticeMessage = msg(
                        "${summary.mode.name.lowercase().replaceFirstChar { it.uppercase() }} complete: ${summary.devices} devices, ${summary.profiles} profiles, ${summary.templates} templates, ${summary.history} history entries and ${summary.logs} logs",
                        "عملیات ${summary.mode.name} کامل شد: ${summary.devices} دستگاه، ${summary.profiles} پروفایل، ${summary.templates} قالب، ${summary.history} سابقه و ${summary.logs} لاگ",
                    ),
                    errorMessage = null,
                )
                log(LogLevel.SUCCESS, "Backup", msg("Backup ${summary.mode.name.lowercase()} completed", "عملیات نسخه پشتیبان ${summary.mode.name} کامل شد"))
            }
            .onFailure { fail("Backup import", it) }
    }

    fun updateSettings(settings: AppSettings) {
        persistenceFailure("Application settings") { repository.saveSettings(settings) }
            ?.let { return reportPersistenceFailure(it) }
        val normalized = repository.loadSettings()
        _state.value = _state.value.copy(settings = normalized)
        trimLogs()
        trimHistory()
        log(LogLevel.INFO, "Settings", msg("Application settings updated", "تنظیمات برنامه به‌روزرسانی شد"))
    }

    fun clearLogs() {
        persistenceFailure("Activity logs") { repository.saveLogs(emptyList()) }
            ?.let { return reportPersistenceFailure(it) }
        _state.value = _state.value.copy(logs = emptyList())
    }

    fun clearHistory() {
        persistenceFailure("Request history") { repository.saveHistory(emptyList()) }
            ?.let { return reportPersistenceFailure(it) }
        _state.value = _state.value.copy(history = emptyList())
        log(LogLevel.WARNING, "History", msg("Request history cleared", "تاریخچه درخواست‌ها پاک شد"))
    }

    fun cancelOperation() {
        if (!operationCoordinator.cancelCurrent()) return
        _state.value = _state.value.copy(isBusy = false, operationTitle = null)
        log(LogLevel.WARNING, "System", msg("Operation cancelled", "عملیات لغو شد"))
    }

    fun close() {
        operationCoordinator.close()
        runCatching(networkProbe::close)
        runCatching(httpClient::close)
        runCatching(webSocketClient::close)
        runCatching(mqttClient::close)
        scope.cancel()
    }

    private fun loadInitialState(): AppState {
        val settings = repository.loadSettings()
        val devices = repository.loadDevices()
        val profiles = repository.loadProfiles()
        val templates = repository.loadTemplates()
        val history = repository.loadHistory()
        val existingLogs = repository.loadLogs()
        val recoveryEvents = repository.drainRecoveryEvents()
        val recoveryLogs = recoveryEvents.mapIndexed { index, event ->
            ToolLog(
                id = "recovery-${now()}-$index",
                timestampMillis = now(),
                level = LogLevel.WARNING,
                source = localized(settings.language, "Storage recovery", "بازیابی ذخیره‌سازی"),
                message = localized(
                    settings.language,
                    buildString {
                        append("Recovered from unreadable local data at ")
                        append(event.key)
                        event.quarantineKey?.let { append("; snapshot saved as ").append(it) }
                        append(": ").append(event.message)
                    },
                    buildString {
                        append("داده محلی ناخوانا در ").append(event.key).append(" بازیابی شد")
                        event.quarantineKey?.let { append("؛ Snapshot با نام ").append(it).append(" ذخیره شد") }
                        append(": ").append(event.message)
                    },
                ).take(MAX_LOG_MESSAGE),
            )
        }
        val logs = (recoveryLogs + existingLogs).take(settings.retainLogs)
        if (recoveryLogs.isNotEmpty()) runCatching { repository.saveLogs(logs) }
        return AppState(
            currentScreen = settings.lastScreen,
            settings = settings,
            capabilities = networkProbe.capabilities,
            savedDevices = devices,
            profiles = profiles,
            templates = templates,
            history = history,
            logs = logs,
            noticeMessage = recoveryEvents.takeIf { it.isNotEmpty() }?.let {
                localized(
                    settings.language,
                    "Recovered ${it.size} unreadable local data record(s). A diagnostic snapshot was preserved when storage allowed it.",
                    "${it.size} رکورد محلی ناخوانا بازیابی شد و در صورت امکان Snapshot تشخیصی نگه‌داری شد.",
                )
            },
        )
    }

    private fun launchOperation(title: String, block: suspend () -> Unit) {
        operationCoordinator.launch(
            onStart = {
                _state.value = _state.value.copy(
                    isBusy = true,
                    operationTitle = title,
                    errorMessage = null,
                    noticeMessage = null,
                )
            },
            onFailure = { failure -> fail(title, failure) },
            onFinish = {
                _state.value = _state.value.copy(isBusy = false, operationTitle = null)
            },
            block = block,
        )
    }



    private fun allowEndpoint(url: String, source: String): Boolean {
        val capabilities = _state.value.capabilities
        val overrideEnabled = _state.value.settings.allowPublicCleartext && capabilities.publicCleartextOverride
        if (!TransportSecurity.isPublicCleartext(url) || overrideEnabled) return true
        val message = msg(
            "Public cleartext transport is blocked by default. Use HTTPS/WSS or a local/private endpoint.",
            "انتقال عمومی بدون رمزنگاری به‌صورت پیش‌فرض مسدود است؛ از HTTPS/WSS یا مقصد محلی/خصوصی استفاده کنید.",
        )
        _state.value = _state.value.copy(errorMessage = message)
        log(LogLevel.ERROR, "$source security", message)
        return false
    }

    private fun requireCapability(enabled: Boolean, source: String): Boolean {
        if (enabled) return true
        val displaySource = localizedSource(source)
        _state.value = _state.value.copy(
            noticeMessage = msg(
                "$displaySource is unavailable on ${_state.value.capabilities.platformName}. ${_state.value.capabilities.notes}",
                "$displaySource در ${_state.value.capabilities.platformName} در دسترس نیست. ${_state.value.capabilities.notes}",
            ),
        )
        log(LogLevel.WARNING, source, msg("Capability unavailable on ${_state.value.capabilities.platformName}", "این قابلیت در ${_state.value.capabilities.platformName} در دسترس نیست"))
        return false
    }

    private fun fail(source: String, throwable: Throwable) {
        val message = throwable.message ?: throwable::class.simpleName ?: msg("Unknown error", "خطای ناشناخته")
        _state.value = _state.value.copy(errorMessage = SecretRedactor.redact(message))
        log(LogLevel.ERROR, source, message)
    }

    private fun log(level: LogLevel, source: String, message: String) {
        val entry = ToolLog(newId("log"), now(), level, localizedSource(source), SecretRedactor.redact(message).take(MAX_LOG_MESSAGE))
        val updated = (listOf(entry) + _state.value.logs).take(_state.value.settings.retainLogs)
        val persistenceError = persistenceFailure("Activity logs") { repository.saveLogs(updated) }
        _state.value = _state.value.copy(
            logs = updated,
            errorMessage = persistenceError ?: _state.value.errorMessage,
        )
        persistenceError?.let(::appendEphemeralStorageLog)
    }

    private fun addHistory(
        protocol: HistoryProtocol,
        target: String,
        summary: String,
        successful: Boolean,
        request: String = "",
        response: String = "",
        durationMillis: Long? = null,
    ) {
        val item = HistoryEntry(
            id = newId("history"),
            timestampMillis = now(),
            protocol = protocol,
            target = SecretRedactor.redact(target).take(MAX_TARGET),
            summary = SecretRedactor.redact(summary).take(MAX_SUMMARY),
            successful = successful,
            requestPreview = SecretRedactor.redact(request).take(MAX_HISTORY_PREVIEW),
            responsePreview = SecretRedactor.redact(response).take(MAX_HISTORY_PREVIEW),
            durationMillis = durationMillis,
        )
        val updated = (listOf(item) + _state.value.history).take(_state.value.settings.retainHistory)
        val persistenceError = persistenceFailure("Request history") { repository.saveHistory(updated) }
        _state.value = _state.value.copy(
            history = updated,
            errorMessage = persistenceError ?: _state.value.errorMessage,
        )
        persistenceError?.let(::appendEphemeralStorageLog)
    }

    private fun reloadPersistentState() {
        _state.value = _state.value.copy(
            settings = repository.loadSettings(),
            savedDevices = repository.loadDevices(),
            profiles = repository.loadProfiles(),
            templates = repository.loadTemplates(),
            history = repository.loadHistory(),
            logs = repository.loadLogs(),
        )
    }

    private fun trimLogs() {
        val updated = _state.value.logs.take(_state.value.settings.retainLogs)
        val persistenceError = persistenceFailure("Activity logs") { repository.saveLogs(updated) }
        _state.value = _state.value.copy(
            logs = updated,
            errorMessage = persistenceError ?: _state.value.errorMessage,
        )
        persistenceError?.let(::appendEphemeralStorageLog)
    }

    private fun trimHistory() {
        val updated = _state.value.history.take(_state.value.settings.retainHistory)
        val persistenceError = persistenceFailure("Request history") { repository.saveHistory(updated) }
        _state.value = _state.value.copy(
            history = updated,
            errorMessage = persistenceError ?: _state.value.errorMessage,
        )
        persistenceError?.let(::appendEphemeralStorageLog)
    }

    private inline fun persistenceFailure(source: String, operation: () -> Unit): String? = try {
        operation()
        null
    } catch (error: Exception) {
        val detail = SecretRedactor.redact(error.message ?: error::class.simpleName ?: msg("Unknown storage error", "خطای ناشناخته ذخیره‌سازی"))
        localizedSource(source).let { displaySource -> msg("$displaySource could not be saved: $detail", "ذخیره $displaySource انجام نشد: $detail") }
    }

    private fun reportPersistenceFailure(message: String) {
        _state.value = _state.value.copy(errorMessage = message)
        appendEphemeralStorageLog(message)
    }

    private fun appendEphemeralStorageLog(message: String) {
        val entry = ToolLog(
            id = newId("storage-error"),
            timestampMillis = now(),
            level = LogLevel.ERROR,
            source = localizedSource("Storage"),
            message = SecretRedactor.redact(message).take(MAX_LOG_MESSAGE),
        )
        _state.value = _state.value.copy(
            logs = (listOf(entry) + _state.value.logs).take(_state.value.settings.retainLogs),
        )
    }

    private fun validationFailure(source: String, message: String) {
        _state.value = _state.value.copy(errorMessage = message)
        log(LogLevel.ERROR, source, message)
    }


    private fun localizedHttpValidationIssue(issue: HttpRequestValidationIssue): String = when (issue) {
        HttpRequestValidationIssue.INVALID_URL -> msg("HTTP URL is invalid or contains embedded credentials", "نشانی HTTP نامعتبر است یا اطلاعات ورود را داخل URL قرار داده است")
        HttpRequestValidationIssue.PUBLIC_CLEARTEXT_BLOCKED -> msg("Public cleartext HTTP is blocked by default", "HTTP رمزنگاری‌نشده عمومی به‌صورت پیش‌فرض مسدود است")
        HttpRequestValidationIssue.UNSUPPORTED_METHOD -> msg("HTTP method is not supported", "متد HTTP پشتیبانی نمی‌شود")
        HttpRequestValidationIssue.INVALID_TIMEOUT -> msg("HTTP timeout values must be between 100 and 120000 ms", "مقادیر Timeout باید بین ۱۰۰ تا ۱۲۰۰۰۰ میلی‌ثانیه باشند")
        HttpRequestValidationIssue.REDIRECT_UNSUPPORTED -> msg("Manual redirect inspection is unavailable on this platform", "بررسی دستی Redirect در این پلتفرم در دسترس نیست")
        HttpRequestValidationIssue.INVALID_HEADERS -> msg("HTTP header block is invalid", "ساختار Headerهای HTTP نامعتبر است")
        HttpRequestValidationIssue.RESTRICTED_HEADERS -> msg("One or more HTTP headers are managed by the engine", "یک یا چند Header توسط موتور HTTP مدیریت می‌شوند")
        HttpRequestValidationIssue.BROWSER_FORBIDDEN_HEADERS -> msg("One or more HTTP headers are controlled by the browser", "یک یا چند Header توسط مرورگر کنترل می‌شوند")
        HttpRequestValidationIssue.COOKIE_UNSUPPORTED -> msg("This platform cannot set the Cookie header manually", "این پلتفرم امکان تنظیم دستی Header مربوط به Cookie را ندارد")
        HttpRequestValidationIssue.DUPLICATE_COOKIE -> msg("Use either the Cookie editor or a manual Cookie header, not both", "فقط یکی از ویرایشگر Cookie یا Header دستی Cookie را استفاده کنید")
        HttpRequestValidationIssue.DUPLICATE_CONTENT_TYPE -> msg("Use the Content Type field instead of a manual Content-Type header", "به‌جای Header دستی Content-Type از فیلد نوع محتوا استفاده کنید")
        HttpRequestValidationIssue.INVALID_QUERY -> msg("HTTP query parameters are invalid", "پارامترهای Query نامعتبر هستند")
        HttpRequestValidationIssue.INVALID_COOKIES -> msg("HTTP cookie entries are invalid", "مقادیر Cookie نامعتبر هستند")
        HttpRequestValidationIssue.INVALID_AUTH -> msg("HTTP authentication settings are invalid", "تنظیمات احراز هویت HTTP نامعتبر هستند")
        HttpRequestValidationIssue.INVALID_BODY -> msg("HTTP request body or content type is invalid", "بدنه درخواست یا نوع محتوای HTTP نامعتبر است")
        HttpRequestValidationIssue.REQUEST_BODY_TOO_LARGE -> msg("HTTP request body exceeds the 1 MiB safety limit", "بدنه درخواست از سقف ایمنی ۱ مگابایت عبور کرده است")
    }

    private fun localizedBackupInspection(raw: String): BackupInspection {
        val inspection = repository.inspectBackup(raw)
        val message = when {
            inspection.compatible -> msg(
                "Compatible backup. Choose Merge to preserve local records or Replace to overwrite them.",
                "نسخه پشتیبان سازگار است. برای حفظ داده‌های محلی «ادغام» و برای جایگزینی کامل «جایگزینی» را انتخاب کنید.",
            )
            raw.isBlank() -> msg("Backup JSON cannot be empty", "متن JSON نسخه پشتیبان نمی‌تواند خالی باشد")
            else -> msg(
                "Backup is invalid: ${inspection.message}",
                "نسخه پشتیبان نامعتبر است: ${inspection.message}",
            )
        }
        return inspection.copy(message = message)
    }

    private fun localizedSource(source: String): String {
        if (_state.value.settings.language != AppLanguage.PERSIAN) return source
        if (source.endsWith(" security", ignoreCase = true)) {
            return localizedSource(source.dropLast(" security".length)) + " - امنیت"
        }
        return when (source) {
            "Navigation settings" -> "تنظیمات ناوبری"
            "Subnet" -> "زیرشبکه"
            "Ping" -> "پینگ"
            "DNS" -> "DNS"
            "TCP" -> "TCP"
            "UDP" -> "UDP"
            "Port scanner" -> "اسکن پورت"
            "LAN discovery" -> "کشف شبکه محلی"
            "Discovery" -> "کشف دستگاه"
            "SSDP" -> "SSDP"
            "mDNS" -> "mDNS"
            "HTTP" -> "HTTP"
            "WebSocket" -> "WebSocket"
            "MQTT", "MQTT WebSocket" -> "MQTT"
            "CoAP" -> "CoAP"
            "Payload" -> "Payload"
            "Devices" -> "دستگاه‌ها"
            "Device inventory" -> "فهرست دستگاه‌ها"
            "Profiles" -> "پروفایل‌ها"
            "Endpoint profiles" -> "پروفایل‌های Endpoint"
            "Templates" -> "قالب‌ها"
            "Payload templates" -> "قالب‌های Payload"
            "Backup" -> "نسخه پشتیبان"
            "Backup export" -> "خروجی نسخه پشتیبان"
            "Backup import" -> "ورود نسخه پشتیبان"
            "Application settings" -> "تنظیمات برنامه"
            "Settings" -> "تنظیمات"
            "Activity logs" -> "لاگ‌های فعالیت"
            "Request history" -> "تاریخچه درخواست‌ها"
            "History" -> "تاریخچه"
            "System" -> "سیستم"
            "Storage recovery" -> "بازیابی ذخیره‌سازی"
            "Storage" -> "ذخیره‌سازی"
            else -> source
        }
    }

    private fun msg(english: String, persian: String): String =
        localized(_state.value.settings.language, english, persian)

    private fun extractHost(url: String): String = TransportSecurity.extractHost(url)

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
    private fun newId(prefix: String): String = "$prefix-${now()}-${Random.nextInt(100_000, 999_999)}"

    private companion object {
        const val MAX_LOG_MESSAGE = 2_000
        const val MAX_HISTORY_PREVIEW = 4_000
        const val MAX_TARGET = 500
        const val MAX_SUMMARY = 1_000
        const val MAX_PERSISTED_ITEMS = 5_000
        const val MAX_CHILD_ITEMS = 512
        const val MAX_ID_LENGTH = 160
        const val MAX_NAME_LENGTH = 256
        const val MAX_ENDPOINT_LENGTH = 2_048
        const val MAX_HEADER_BLOCK_LENGTH = 65_536
        const val MAX_NOTES_LENGTH = 8_192
        const val MAX_TEMPLATE_BYTES = 1_048_576
    }
}
