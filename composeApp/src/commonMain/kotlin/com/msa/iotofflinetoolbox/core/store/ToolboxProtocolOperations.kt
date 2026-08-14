package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.CoapRequestInput
import com.msa.iotofflinetoolbox.core.model.HistoryProtocol
import com.msa.iotofflinetoolbox.core.model.HttpRequestInput
import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.model.MessageDirection
import com.msa.iotofflinetoolbox.core.model.MqttWebSocketInput
import com.msa.iotofflinetoolbox.core.model.WebSocketRequestInput
import com.msa.iotofflinetoolbox.core.policy.HttpRequestPreview
import com.msa.iotofflinetoolbox.core.policy.HttpRequestValidationPolicy
import com.msa.iotofflinetoolbox.core.policy.HttpRequestValidator
import com.msa.iotofflinetoolbox.core.port.CoapOperationClient
import com.msa.iotofflinetoolbox.core.port.HttpOperationClient
import com.msa.iotofflinetoolbox.core.port.MqttOperationClient
import com.msa.iotofflinetoolbox.core.port.WebSocketOperationClient
import com.msa.iotofflinetoolbox.core.security.SecretRedactor

/** Application service for HTTP, WebSocket, MQTT-over-WebSocket and CoAP workflows. */
internal class ToolboxProtocolOperations(
    private val httpClient: HttpOperationClient,
    private val webSocketClient: WebSocketOperationClient,
    private val mqttClient: MqttOperationClient,
    private val coapClient: CoapOperationClient,
    private val runner: ToolboxOperationRunner,
) : ProtocolActions {
    override fun executeHttp(input: HttpRequestInput) {
        val snapshot = runner.snapshot()
        if (!runner.requireCapability(snapshot.capabilities.httpClient, "HTTP")) return

        val capabilities = snapshot.capabilities
        val effectiveCleartext = snapshot.settings.allowPublicCleartext && capabilities.publicCleartextOverride
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
            runner.validationFailure("HTTP", runner.httpValidationIssue(validationIssue))
            return
        }

        runner.launch("${input.method} ${SecretRedactor.redact(input.url)}") {
            val result = httpClient.execute(
                input,
                runner.snapshot().settings.maxResponseBytes,
                effectiveCleartext,
            )
            runner.update { copy(httpResult = result) }
            val success = result.error == null && result.statusCode in HTTP_SUCCESS_CODES
            result.securityWarning?.let { runner.log(LogLevel.WARNING, "HTTP security", it) }
            runner.log(
                if (success) LogLevel.SUCCESS else LogLevel.ERROR,
                "HTTP",
                result.error ?: text(
                    "${input.method} ${input.url} returned ${result.statusCode} in ${result.elapsedMillis} ms",
                    "درخواست ${input.method} به ${input.url} با وضعیت ${result.statusCode} در ${result.elapsedMillis} میلی‌ثانیه پایان یافت",
                ),
            )
            runner.addHistory(
                HistoryProtocol.HTTP,
                input.url,
                result.error ?: "${result.statusCode} ${result.statusText}${if (result.truncated) " (truncated)" else ""}",
                success,
                request = HttpRequestPreview.curl(input, redactSecrets = true),
                response = if (result.bodyIsText) {
                    result.body
                } else {
                    text(
                        "Binary response (${result.bytesReceived} bytes, ${result.contentType ?: "unknown content type"})",
                        "پاسخ باینری (${result.bytesReceived} بایت، ${result.contentType ?: "نوع محتوای نامشخص"})",
                    )
                },
                durationMillis = result.elapsedMillis,
            )
        }
    }

    override fun executeWebSocket(input: WebSocketRequestInput) {
        val snapshot = runner.snapshot()
        if (!runner.requireCapability(snapshot.capabilities.webSocketClient, "WebSocket")) return
        if (input.headersText.isNotBlank() && !snapshot.capabilities.customWebSocketHeaders) {
            runner.validationFailure(
                "WebSocket",
                text(
                    "Browser WebSocket does not support arbitrary custom HTTP headers.",
                    "WebSocket مرورگر از Headerهای HTTP سفارشی پشتیبانی نمی‌کند.",
                ),
            )
            return
        }
        if (!runner.allowEndpoint(input.url, "WebSocket")) return

        runner.launch("WebSocket ${SecretRedactor.redact(input.url)}") {
            val current = runner.snapshot()
            val result = webSocketClient.execute(
                input.copy(maxMessageBytes = minOf(input.maxMessageBytes, current.settings.maxResponseBytes)),
                current.settings.allowPublicCleartext && current.capabilities.publicCleartextOverride,
            )
            runner.update { copy(webSocketResult = result) }
            val success = result.error == null && result.connected
            result.securityWarning?.let { runner.log(LogLevel.WARNING, "WebSocket security", it) }
            runner.log(
                if (success) LogLevel.SUCCESS else LogLevel.ERROR,
                "WebSocket",
                result.error ?: text(
                    "Received ${result.messages.count { it.direction == MessageDirection.RECEIVED }} messages",
                    "${result.messages.count { it.direction == MessageDirection.RECEIVED }} پیام دریافت شد",
                ),
            )
            runner.addHistory(
                HistoryProtocol.WEBSOCKET,
                input.url,
                result.error ?: text("WebSocket exchange completed", "تبادل WebSocket کامل شد"),
                success,
                request = "${input.headersText}\n${input.message}",
                response = result.messages.joinToString("\n") { "${it.direction}: ${it.payload}" },
                durationMillis = result.elapsedMillis,
            )
        }
    }

    override fun executeMqtt(input: MqttWebSocketInput) {
        val snapshot = runner.snapshot()
        if (!runner.requireCapability(snapshot.capabilities.mqttWebSocketClient, "MQTT")) return
        if (!runner.allowEndpoint(input.url, "MQTT WebSocket")) return

        runner.launch("MQTT ${SecretRedactor.redact(input.url)}") {
            val current = runner.snapshot()
            val result = mqttClient.execute(
                input.copy(maxMessageBytes = minOf(input.maxMessageBytes, current.settings.maxResponseBytes)),
                current.settings.allowPublicCleartext && current.capabilities.publicCleartextOverride,
            )
            runner.update { copy(mqttResult = result) }
            val success = result.operationSuccessful
            result.securityWarning?.let { runner.log(LogLevel.WARNING, "MQTT security", it) }
            runner.log(
                if (success) LogLevel.SUCCESS else LogLevel.ERROR,
                "MQTT",
                result.error ?: text(
                    "MQTT session completed with ${result.messages.size} publications",
                    "نشست MQTT با ${result.messages.size} پیام انتشار کامل شد",
                ),
            )
            runner.addHistory(
                HistoryProtocol.MQTT,
                input.url,
                result.error ?: text("MQTT WebSocket session completed", "نشست MQTT روی WebSocket کامل شد"),
                success,
                request = "SUB ${input.subscribeTopic}\nPUB ${input.publishTopic}\n${input.payload}",
                response = result.messages.joinToString("\n") { "${it.topic}: ${it.payload}" },
                durationMillis = result.elapsedMillis,
            )
        }
    }

    override fun executeCoap(input: CoapRequestInput) {
        if (!runner.requireCapability(runner.snapshot().capabilities.coapClient, "CoAP")) return
        runner.launch("CoAP ${input.host}:${input.port}${input.path}") {
            val result = coapClient.execute(input, runner.snapshot().settings.maxResponseBytes)
            runner.update { copy(coapResult = result) }
            val success = result.error == null && result.responseCode in COAP_SUCCESS_CODES
            runner.log(if (success) LogLevel.SUCCESS else LogLevel.ERROR, "CoAP", result.error ?: result.responseCodeText)
            runner.addHistory(
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

    private fun text(english: String, persian: String): String = runner.text(english, persian)

    private companion object {
        val HTTP_SUCCESS_CODES = 200..299
        val COAP_SUCCESS_CODES = 64..95
    }
}
