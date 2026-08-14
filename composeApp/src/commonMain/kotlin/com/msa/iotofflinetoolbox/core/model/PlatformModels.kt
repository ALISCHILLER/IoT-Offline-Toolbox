package com.msa.iotofflinetoolbox.core.model


enum class ToolScreen {
    DASHBOARD,
    DISCOVERY,
    NETWORK_TOOLS,
    HTTP_CLIENT,
    REALTIME,
    PAYLOAD_TOOLS,
    PROFILES,
    DEVICES,
    LOGS,
    GUIDE,
    SETTINGS,
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
