package com.msa.iotofflinetoolbox.core.model

import kotlinx.serialization.Serializable

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
