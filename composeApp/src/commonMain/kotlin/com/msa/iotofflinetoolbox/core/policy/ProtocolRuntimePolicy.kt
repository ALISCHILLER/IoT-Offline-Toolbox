package com.msa.iotofflinetoolbox.core.policy

/** Pure timeout and handshake validation shared by network clients and dependency-free harnesses. */
internal data class HttpTimeoutBudget(
    val requestMillis: Long,
    val connectMillis: Long,
    val socketMillis: Long,
)

internal fun calculateHttpTimeoutBudget(
    totalTimeoutMillis: Int,
    connectTimeoutMillis: Int,
    socketTimeoutMillis: Int,
    elapsedMillis: Long,
): HttpTimeoutBudget {
    val remaining = totalTimeoutMillis.toLong() - elapsedMillis.coerceAtLeast(0L)
    require(remaining > 0L) { "HTTP total request timeout exceeded" }
    return HttpTimeoutBudget(
        requestMillis = remaining,
        connectMillis = minOf(connectTimeoutMillis.toLong(), remaining),
        socketMillis = minOf(socketTimeoutMillis.toLong(), remaining),
    )
}

internal fun remainingReceiveWindowMillis(totalMillis: Int, elapsedMillis: Long): Long =
    totalMillis.toLong() - elapsedMillis.coerceAtLeast(0L)

/**
 * Returns whether the MQTT client still has protocol work that requires an inbound frame
 * after CONNACK. Connection-only sessions and QoS 0 publish-only sessions can finish
 * immediately; subscriptions need SUBACK/messages and QoS 1 publishes need PUBACK.
 */
internal fun mqttRequiresReceiveLoop(
    subscribeTopic: String,
    publishTopic: String,
    publishQos: Int,
): Boolean {
    require(publishQos in 0..1) { "Publish QoS must be 0 or 1" }
    return subscribeTopic.isNotBlank() || (publishTopic.isNotBlank() && publishQos == 1)
}

internal fun isWebSocketEngineManagedHeader(name: String): Boolean {
    val normalized = name.trim().lowercase()
    return normalized in WEBSOCKET_ENGINE_MANAGED_HEADERS || normalized.startsWith("sec-websocket-")
}

internal fun validateWebSocketHeaders(headers: Map<String, String>) {
    val restricted = headers.keys.filter(::isWebSocketEngineManagedHeader)
    require(restricted.isEmpty()) {
        "These WebSocket headers are managed by the engine and cannot be set manually: ${restricted.sorted().joinToString()}"
    }
}

private val WEBSOCKET_ENGINE_MANAGED_HEADERS = setOf(
    "connection",
    "content-length",
    "host",
    "transfer-encoding",
    "upgrade",
)
