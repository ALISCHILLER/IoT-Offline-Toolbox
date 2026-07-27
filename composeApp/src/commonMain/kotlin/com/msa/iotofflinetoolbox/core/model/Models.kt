package com.msa.iotofflinetoolbox.core.model

import kotlinx.serialization.Serializable

const val APP_VERSION: String = "3.6.2"
const val TOOLBOX_BACKUP_VERSION: Int = 4

@Serializable
enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }

@Serializable
data class ToolLog(
    val id: String,
    val timestampMillis: Long,
    val level: LogLevel,
    val source: String,
    val message: String,
)

@Serializable
data class SavedDevice(
    val id: String,
    val host: String,
    val displayName: String,
    val addresses: List<String> = emptyList(),
    val openPorts: List<Int> = emptyList(),
    val services: List<String> = emptyList(),
    val note: String = "",
    val tags: List<String> = emptyList(),
    val lastSeenMillis: Long,
)

@Serializable
enum class AppLanguage { ENGLISH, PERSIAN }

@Serializable
data class AppSettings(
    val darkMode: Boolean = false,
    val useSystemTheme: Boolean = true,
    val compactMode: Boolean = false,
    val rtl: Boolean = false,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val lastScreen: ToolScreen = ToolScreen.DASHBOARD,
    val defaultTimeoutMillis: Int = 1_500,
    /** Total HTTP request deadline. */
    val defaultHttpTimeoutMillis: Int = 10_000,
    /** Time allowed to establish the HTTP connection when the platform engine supports it. */
    val defaultHttpConnectTimeoutMillis: Int = 5_000,
    /** Maximum HTTP socket inactivity when the platform engine supports it. */
    val defaultHttpSocketTimeoutMillis: Int = 10_000,
    val defaultFollowRedirects: Boolean = false,
    val scanConcurrency: Int = 32,
    val maxPortScanItems: Int = 4_096,
    val maxDiscoveryHosts: Int = 256,
    val maxResponseBytes: Int = 65_536,
    val retainLogs: Int = 300,
    val retainHistory: Int = 100,
    val showAdvancedOptions: Boolean = true,
    /** Explicit opt-in for public HTTP/WS where the platform can enforce the policy. */
    val allowPublicCleartext: Boolean = false,
)

@Serializable
data class PingResult(
    val host: String,
    val reachable: Boolean,
    val latencyMillis: Long?,
    val resolvedAddress: String?,
    val message: String,
)

@Serializable
enum class PortState { OPEN, CLOSED, FILTERED, ERROR }

@Serializable
data class PortScanResult(
    val port: Int,
    val isOpen: Boolean,
    val latencyMillis: Long?,
    val serviceHint: String,
    val error: String? = null,
    val state: PortState = if (isOpen) PortState.OPEN else PortState.CLOSED,
)

@Serializable
data class DiscoveredDevice(
    val host: String,
    val resolvedName: String? = null,
    val latencyMillis: Long? = null,
)

@Serializable
data class SsdpDevice(
    val usn: String,
    val location: String,
    val server: String? = null,
    val searchTarget: String? = null,
    val cacheControl: String? = null,
    val remoteAddress: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class MdnsService(
    val serviceType: String,
    val instanceName: String,
    val host: String? = null,
    val port: Int? = null,
    val addresses: List<String> = emptyList(),
    val txt: Map<String, String> = emptyMap(),
    val ttlSeconds: Long? = null,
)

@Serializable
enum class PayloadEncoding { TEXT, HEX, BASE64 }

@Serializable
enum class HttpAuthType { NONE, BASIC, BEARER, API_KEY }

@Serializable
enum class HttpBodyType { NONE, RAW, JSON, FORM_URLENCODED }

@Serializable
data class SocketExchangeResult(
    val protocol: String,
    val endpoint: String,
    val responseText: String,
    val responseHex: String = "",
    val bytesReceived: Int,
    val elapsedMillis: Long,
    val truncated: Boolean = false,
    val error: String? = null,
)

@Serializable
data class HttpRequestInput(
    val method: String = "GET",
    val url: String = "",
    val queryText: String = "",
    val headersText: String = "Accept: application/json",
    val cookieText: String = "",
    val body: String = "",
    val bodyType: HttpBodyType = HttpBodyType.NONE,
    val contentType: String = "application/json",
    val authType: HttpAuthType = HttpAuthType.NONE,
    val authUsername: String = "",
    val authPassword: String = "",
    val bearerToken: String = "",
    val apiKeyName: String = "X-API-Key",
    val apiKeyValue: String = "",
    val timeoutMillis: Int = 10_000,
    val connectTimeoutMillis: Int = 5_000,
    val socketTimeoutMillis: Int = 10_000,
    val followRedirects: Boolean = false,
)

@Serializable
data class HttpResponseResult(
    val statusCode: Int,
    val statusText: String,
    val headers: Map<String, String>,
    val body: String,
    /** Exact bounded response bytes retained for on-demand HEX/Base64 inspection. */
    val bodyBytes: ByteArray = byteArrayOf(),
    /** True when the response was classified as readable text. */
    val bodyIsText: Boolean = true,
    val elapsedMillis: Long,
    val bytesReceived: Int = 0,
    val truncated: Boolean = false,
    val error: String? = null,
    val securityWarning: String? = null,
    val requestMethod: String = "",
    val requestUrl: String = "",
    val finalUrl: String = "",
    val contentType: String? = null,
    val redirected: Boolean = false,
)

@Serializable
data class WebSocketRequestInput(
    val url: String = "",
    val headersText: String = "",
    val message: String = "Hello from MSA IoT Offline Toolbox",
    val messageEncoding: PayloadEncoding = PayloadEncoding.TEXT,
    val receiveTimeoutMillis: Int = 5_000,
    val maxMessages: Int = 10,
    val maxMessageBytes: Int = 65_536,
)

@Serializable
data class RealtimeMessage(
    val timestampMillis: Long,
    val direction: MessageDirection,
    val payload: String,
    val binary: Boolean = false,
    val truncated: Boolean = false,
)

@Serializable
enum class MessageDirection { SENT, RECEIVED, SYSTEM }

@Serializable
data class WebSocketResult(
    val endpoint: String,
    val connected: Boolean,
    val messages: List<RealtimeMessage>,
    val elapsedMillis: Long,
    val closeReason: String? = null,
    val error: String? = null,
    val securityWarning: String? = null,
)

@Serializable
data class MqttWebSocketInput(
    val url: String = "",
    val clientId: String = "msa-iot-toolbox",
    val username: String = "",
    val password: String = "",
    val subscribeTopic: String = "msa/toolbox/#",
    val subscribeQos: Int = 0,
    val publishTopic: String = "msa/toolbox/test",
    val publishQos: Int = 0,
    val payload: String = "{\"source\":\"MSA IoT Offline Toolbox\"}",
    val retain: Boolean = false,
    val cleanSession: Boolean = true,
    val keepAliveSeconds: Int = 30,
    val receiveTimeoutMillis: Int = 5_000,
    val maxMessages: Int = 20,
    val maxMessageBytes: Int = 65_536,
)

@Serializable
data class MqttMessage(
    val topic: String,
    /** Safe text when the payload is valid UTF-8, otherwise a bounded HEX representation. */
    val payload: String,
    val retained: Boolean,
    val duplicate: Boolean,
    val qos: Int,
    val timestampMillis: Long,
    val binary: Boolean = false,
    val truncated: Boolean = false,
)

@Serializable
data class MqttResult(
    val endpoint: String,
    val connected: Boolean,
    val sessionPresent: Boolean,
    val returnCode: Int,
    val messages: List<MqttMessage>,
    val events: List<String>,
    val elapsedMillis: Long,
    val subscribed: Boolean = false,
    val publishAcknowledged: Boolean = false,
    val operationSuccessful: Boolean = false,
    val error: String? = null,
    val securityWarning: String? = null,
)

@Serializable
enum class CoapMethod(val code: Int) {
    GET(1),
    POST(2),
    PUT(3),
    DELETE(4),
}

@Serializable
enum class CoapMessageType(val value: Int) {
    CONFIRMABLE(0),
    NON_CONFIRMABLE(1),
}

@Serializable
data class CoapRequestInput(
    val host: String = "",
    val port: Int = 5683,
    val method: CoapMethod = CoapMethod.GET,
    val messageType: CoapMessageType = CoapMessageType.CONFIRMABLE,
    val path: String = "/.well-known/core",
    val query: String = "",
    val payload: String = "",
    val contentFormat: Int? = null,
    val accept: Int? = null,
    val timeoutMillis: Int = 3_000,
)

@Serializable
data class CoapResponseResult(
    val endpoint: String,
    val messageId: Int,
    val tokenHex: String,
    val responseCode: Int,
    val responseCodeText: String,
    val payloadText: String,
    val payloadHex: String,
    val contentFormat: Int? = null,
    val elapsedMillis: Long,
    val truncated: Boolean = false,
    val error: String? = null,
)

@Serializable
enum class EndpointProtocol { HTTP, HTTPS, TCP, UDP, WEBSOCKET, MQTT_WEBSOCKET, COAP }

@Serializable
data class EndpointProfile(
    val id: String,
    val name: String,
    val protocol: EndpointProtocol,
    val hostOrUrl: String,
    val port: Int? = null,
    val username: String = "",
    val defaultTopic: String = "",
    val headersText: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Serializable
data class PayloadTemplate(
    val id: String,
    val name: String,
    val content: String,
    val contentType: String = "application/json",
    val notes: String = "",
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Serializable
enum class HistoryProtocol { PING, DNS, PORT_SCAN, LAN_DISCOVERY, SSDP, MDNS, TCP, UDP, HTTP, WEBSOCKET, MQTT, COAP }

@Serializable
data class HistoryEntry(
    val id: String,
    val timestampMillis: Long,
    val protocol: HistoryProtocol,
    val target: String,
    val summary: String,
    val successful: Boolean,
    val requestPreview: String = "",
    val responsePreview: String = "",
    val durationMillis: Long? = null,
)

@Serializable
data class BackupMetadata(
    val applicationVersion: String = APP_VERSION,
    val exportedBy: String = "MSA / ALISCHILLER",
    val secretsRedacted: Boolean = true,
    val deviceCount: Int = 0,
    val profileCount: Int = 0,
    val templateCount: Int = 0,
    val historyCount: Int = 0,
    val logCount: Int = 0,
)

@Serializable
data class ToolboxBackup(
    val schemaVersion: Int = TOOLBOX_BACKUP_VERSION,
    val exportedAtMillis: Long,
    val settings: AppSettings,
    val devices: List<SavedDevice>,
    val profiles: List<EndpointProfile>,
    val templates: List<PayloadTemplate>,
    val history: List<HistoryEntry>,
    val logs: List<ToolLog> = emptyList(),
    val metadata: BackupMetadata? = null,
)

@Serializable
enum class BackupImportMode { MERGE, REPLACE }

data class BackupInspection(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val applicationVersion: String?,
    val devices: Int,
    val profiles: Int,
    val templates: Int,
    val history: Int,
    val logs: Int,
    val compatible: Boolean,
    val message: String,
)

data class ImportSummary(
    val devices: Int,
    val profiles: Int,
    val templates: Int,
    val history: Int,
    val logs: Int,
    val mode: BackupImportMode,
)

data class SubnetResult(
    val input: String,
    val networkAddress: String,
    val broadcastAddress: String,
    val subnetMask: String,
    val firstHost: String,
    val lastHost: String,
    val totalAddresses: Long,
    val usableAddresses: Long,
    val prefixLength: Int,
)

enum class PayloadOperation(val title: String) {
    FORMAT_JSON("Format JSON"),
    MINIFY_JSON("Minify JSON"),
    VALIDATE_JSON("Validate JSON"),
    TEXT_TO_HEX("Text → HEX"),
    HEX_TO_TEXT("HEX → Text"),
    TEXT_TO_BASE64("Text → Base64"),
    BASE64_TO_TEXT("Base64 → Text"),
    EXPAND_VARIABLES("Expand variables"),
    CRC32("CRC32"),
}

data class PayloadToolResult(
    val operation: PayloadOperation,
    val output: String,
    val valid: Boolean = true,
    val message: String = "",
)

enum class ToolScreen(val title: String, val symbol: String) {
    DASHBOARD("Dashboard", "⌂"),
    DISCOVERY("Discovery", "⌁"),
    NETWORK_TOOLS("Network Tools", "⇄"),
    HTTP_CLIENT("HTTP Client", "↗"),
    REALTIME("Realtime Protocols", "◉"),
    PAYLOAD_TOOLS("Payload Tools", "{}"),
    PROFILES("Profiles & Backup", "★"),
    DEVICES("Saved Devices", "▣"),
    LOGS("Activity Logs", "≡"),
    GUIDE("Operator Guide", "?"),
    SETTINGS("Settings", "⚙"),
}

data class PlatformCapabilities(
    val platformName: String,
    val ping: Boolean,
    val tcpPortScan: Boolean,
    val hostDiscovery: Boolean,
    val ssdpDiscovery: Boolean,
    val mdnsDiscovery: Boolean,
    val dnsLookup: Boolean,
    val tcpClient: Boolean,
    val udpClient: Boolean,
    val httpClient: Boolean = true,
    /** Ktor engine supports a dedicated connection timeout. */
    val httpConnectTimeout: Boolean = true,
    /** Ktor engine supports a dedicated socket inactivity timeout. */
    val httpSocketTimeout: Boolean = true,
    val webSocketClient: Boolean = true,
    val mqttWebSocketClient: Boolean = true,
    val coapClient: Boolean = udpClient,
    /** Browser Fetch does not allow callers to set Cookie directly. */
    val manualCookieHeader: Boolean = true,
    /** Browser WebSocket constructors do not accept arbitrary HTTP headers. */
    val customWebSocketHeaders: Boolean = true,
    /** Browser Fetch owns a wider set of forbidden request headers than native engines. */
    val browserManagedRequestHeaders: Boolean = false,
    /** Whether the client can inspect and follow every redirect itself. */
    val manualHttpRedirects: Boolean = true,
    /** Whether the public-cleartext override can be enforced by this platform build. */
    val publicCleartextOverride: Boolean = true,
    val notes: String,
)

data class AppState(
    val currentScreen: ToolScreen = ToolScreen.DASHBOARD,
    val isBusy: Boolean = false,
    val operationTitle: String? = null,
    val settings: AppSettings = AppSettings(),
    val capabilities: PlatformCapabilities,
    val pingResult: PingResult? = null,
    val dnsResults: List<String> = emptyList(),
    val socketResult: SocketExchangeResult? = null,
    val portResults: List<PortScanResult> = emptyList(),
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val ssdpDevices: List<SsdpDevice> = emptyList(),
    val mdnsServices: List<MdnsService> = emptyList(),
    val httpResult: HttpResponseResult? = null,
    val webSocketResult: WebSocketResult? = null,
    val mqttResult: MqttResult? = null,
    val coapResult: CoapResponseResult? = null,
    val subnetResult: SubnetResult? = null,
    val payloadResult: PayloadToolResult? = null,
    val savedDevices: List<SavedDevice> = emptyList(),
    val profiles: List<EndpointProfile> = emptyList(),
    val templates: List<PayloadTemplate> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val logs: List<ToolLog> = emptyList(),
    val backupText: String = "",
    val backupInspection: BackupInspection? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
)
