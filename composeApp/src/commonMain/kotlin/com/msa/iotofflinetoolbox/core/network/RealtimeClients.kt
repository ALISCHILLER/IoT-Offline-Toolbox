package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.policy.validateWebSocketHeaders
import com.msa.iotofflinetoolbox.core.policy.remainingReceiveWindowMillis
import com.msa.iotofflinetoolbox.core.policy.mqttRequiresReceiveLoop

import com.msa.iotofflinetoolbox.core.policy.HeaderParser
import com.msa.iotofflinetoolbox.core.policy.TransportSecurity

import com.msa.iotofflinetoolbox.core.security.SecretRedactor
import com.msa.iotofflinetoolbox.core.model.MessageDirection
import com.msa.iotofflinetoolbox.core.model.MqttMessage
import com.msa.iotofflinetoolbox.core.model.MqttResult
import com.msa.iotofflinetoolbox.core.model.MqttWebSocketInput
import com.msa.iotofflinetoolbox.core.model.PayloadEncoding
import com.msa.iotofflinetoolbox.core.model.RealtimeMessage
import com.msa.iotofflinetoolbox.core.model.WebSocketRequestInput
import com.msa.iotofflinetoolbox.core.model.WebSocketResult
import com.msa.iotofflinetoolbox.core.payload.PayloadCodec
import com.msa.iotofflinetoolbox.core.payload.toDisplayHex
import com.msa.iotofflinetoolbox.core.port.MqttOperationClient
import com.msa.iotofflinetoolbox.core.port.WebSocketOperationClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.TimeSource

/** Bounded WebSocket request/response workbench shared by every HTTP-capable target. */
class WebSocketToolClient(
    private val client: HttpClient = createPlatformHttpClient().config {
        install(WebSockets) { maxFrameSize = MAX_WEBSOCKET_FRAME_BYTES }
    },
) : WebSocketOperationClient {
    override suspend fun execute(
        input: WebSocketRequestInput,
        allowPublicCleartext: Boolean,
    ): WebSocketResult {
        val normalizedUrl = TransportSecurity.requireScheme(
            rawUrl = input.url,
            allowedSchemes = setOf("ws", "wss"),
            label = "WebSocket URL",
        )
        val displayUrl = SecretRedactor.redact(normalizedUrl)
        require(input.receiveTimeoutMillis in 100..120_000) {
            "Receive timeout must be between 100 and 120000 ms"
        }
        require(input.maxMessages in 1..100) { "Maximum message count must be between 1 and 100" }
        require(input.maxMessageBytes in 1_024..1_048_576) {
            "Maximum frame size must be between 1024 and 1048576 bytes"
        }
        require(allowPublicCleartext || !TransportSecurity.isPublicCleartext(normalizedUrl)) {
            "Public cleartext WebSocket transport is blocked by default"
        }

        val parsedHeaders = HeaderParser.parse(input.headersText)
        validateWebSocketHeaders(parsedHeaders)
        val securityWarning = TransportSecurity.warningForUrl(normalizedUrl)
        val mark = TimeSource.Monotonic.markNow()
        val transcript = mutableListOf<RealtimeMessage>()
        var session: DefaultClientWebSocketSession? = null
        return try {
            session = client.webSocketSession {
                url(normalizedUrl)
                parsedHeaders.forEach { (name, value) -> header(name, value) }
            }
            transcript += RealtimeMessage(now(), MessageDirection.SYSTEM, "Connected to $displayUrl")
            securityWarning?.let { transcript += RealtimeMessage(now(), MessageDirection.SYSTEM, it) }

            if (input.message.isNotEmpty()) {
                val payload = PayloadCodec.decode(input.message, input.messageEncoding)
                require(payload.size <= input.maxMessageBytes) {
                    "Outbound WebSocket frame exceeds the configured size limit"
                }
                if (input.messageEncoding == PayloadEncoding.TEXT) {
                    session.send(Frame.Text(input.message))
                } else {
                    session.send(Frame.Binary(fin = true, data = payload))
                }
                transcript += RealtimeMessage(
                    timestampMillis = now(),
                    direction = MessageDirection.SENT,
                    payload = if (input.messageEncoding == PayloadEncoding.TEXT) input.message else payload.toDisplayHex(),
                    binary = input.messageEncoding != PayloadEncoding.TEXT,
                )
            }

            val receiveMark = TimeSource.Monotonic.markNow()
            var receivedCount = 0
            var receiving = true
            while (receiving && receivedCount < input.maxMessages) {
                val remainingMillis = remainingReceiveWindowMillis(
                    input.receiveTimeoutMillis,
                    receiveMark.elapsedNow().inWholeMilliseconds,
                )
                if (remainingMillis <= 0L) {
                    transcript += RealtimeMessage(now(), MessageDirection.SYSTEM, "Receive window completed")
                    break
                }
                val result = withTimeoutOrNull(remainingMillis) {
                    session.incoming.receiveCatching()
                }
                when {
                    result == null -> {
                        transcript += RealtimeMessage(now(), MessageDirection.SYSTEM, "Receive window completed")
                        receiving = false
                    }
                    result.isClosed -> {
                        transcript += RealtimeMessage(
                            now(),
                            MessageDirection.SYSTEM,
                            SecretRedactor.redact(result.exceptionOrNull()?.message ?: "Remote endpoint closed the WebSocket"),
                        )
                        receiving = false
                    }
                    else -> {
                        when (val frame = result.getOrThrow()) {
                            is Frame.Text -> {
                                val bytes = frame.readText().encodeToByteArray()
                                val truncated = bytes.size > input.maxMessageBytes
                                val visible = bytes.copyOf(minOf(bytes.size, input.maxMessageBytes)).decodeToString()
                                transcript += RealtimeMessage(
                                    now(),
                                    MessageDirection.RECEIVED,
                                    visible,
                                    truncated = truncated,
                                )
                                receivedCount++
                            }
                            is Frame.Binary -> {
                                val bytes = frame.readBytes()
                                val truncated = bytes.size > input.maxMessageBytes
                                transcript += RealtimeMessage(
                                    now(),
                                    MessageDirection.RECEIVED,
                                    bytes.copyOf(minOf(bytes.size, input.maxMessageBytes)).toDisplayHex(),
                                    binary = true,
                                    truncated = truncated,
                                )
                                receivedCount++
                            }
                            is Frame.Ping -> transcript += RealtimeMessage(
                                now(),
                                MessageDirection.SYSTEM,
                                "PING (${frame.data.size} bytes)",
                            )
                            is Frame.Pong -> transcript += RealtimeMessage(
                                now(),
                                MessageDirection.SYSTEM,
                                "PONG (${frame.data.size} bytes)",
                            )
                            is Frame.Close -> {
                                transcript += RealtimeMessage(
                                    now(),
                                    MessageDirection.SYSTEM,
                                    "Remote endpoint requested close",
                                )
                                receiving = false
                            }
                        }
                    }
                }
            }

            WebSocketResult(
                endpoint = displayUrl,
                connected = true,
                messages = transcript,
                elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                closeReason = "Completed",
                securityWarning = securityWarning,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            transcript += RealtimeMessage(
                now(),
                MessageDirection.SYSTEM,
                SecretRedactor.redact(error.message ?: "WebSocket operation failed"),
            )
            WebSocketResult(
                endpoint = displayUrl,
                connected = false,
                messages = transcript,
                elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                error = SecretRedactor.redact(error.message ?: error::class.simpleName ?: "WebSocket operation failed"),
                securityWarning = securityWarning,
            )
        } finally {
            session.closeQuietly("Toolbox request completed")
        }
    }

    override fun close() = client.close()
}

/** MQTT 3.1.1 client over RFC 6455 WebSocket transport. */
class MqttWebSocketClient(
    private val client: HttpClient = createPlatformHttpClient().config {
        install(WebSockets) { maxFrameSize = MAX_WEBSOCKET_FRAME_BYTES }
    },
) : MqttOperationClient {
    override suspend fun execute(
        input: MqttWebSocketInput,
        allowPublicCleartext: Boolean,
    ): MqttResult {
        val normalizedUrl = TransportSecurity.requireScheme(
            rawUrl = input.url,
            allowedSchemes = setOf("ws", "wss"),
            label = "MQTT WebSocket URL",
        )
        val displayUrl = SecretRedactor.redact(normalizedUrl)
        require(input.receiveTimeoutMillis in 100..120_000) {
            "Receive timeout must be between 100 and 120000 ms"
        }
        require(input.maxMessages in 1..100) { "Maximum message count must be between 1 and 100" }
        require(input.maxMessageBytes in 1_024..1_048_576) {
            "Maximum MQTT message size must be between 1024 and 1048576 bytes"
        }
        require(input.keepAliveSeconds in 0..65_535) {
            "MQTT keep-alive must be between 0 and 65535 seconds"
        }
        require(input.subscribeQos in 0..1) { "This workbench supports subscribe QoS 0 or 1" }
        require(input.publishQos in 0..1) { "Publish QoS must be 0 or 1" }
        require(input.password.isEmpty() || input.username.isNotEmpty()) {
            "MQTT password requires a username"
        }
        if (input.subscribeTopic.isNotBlank()) MqttCodec.validateTopicFilter(input.subscribeTopic)
        if (input.publishTopic.isNotBlank()) MqttCodec.validateTopicName(input.publishTopic)
        require(allowPublicCleartext || !TransportSecurity.isPublicCleartext(normalizedUrl)) {
            "Public cleartext MQTT WebSocket transport is blocked by default"
        }

        val securityWarning = TransportSecurity.warningForUrl(normalizedUrl)
        val mark = TimeSource.Monotonic.markNow()
        val events = mutableListOf<String>()
        val messages = mutableListOf<MqttMessage>()
        var connAck = MqttCodec.ConnAck(false, -1)
        var pending = byteArrayOf()
        var session: DefaultClientWebSocketSession? = null
        var transportConnected = false
        var subscribed = input.subscribeTopic.isBlank()
        var publishAcknowledged = input.publishTopic.isBlank() || input.publishQos == 0

        return try {
            session = client.webSocketSession {
                url(normalizedUrl)
                header("Sec-WebSocket-Protocol", "mqtt")
            }
            events += "WebSocket transport connected"
            securityWarning?.let(events::add)
            session.send(
                Frame.Binary(
                    fin = true,
                    data = MqttCodec.connectPacket(
                        input.clientId,
                        input.username,
                        input.password,
                        input.keepAliveSeconds,
                        input.cleanSession,
                    ),
                ),
            )
            events += "CONNECT sent for client ${input.clientId}"

            val receiveMark = TimeSource.Monotonic.markNow()
            while (!transportConnected) {
                val remainingMillis = remainingReceiveWindowMillis(
                    input.receiveTimeoutMillis,
                    receiveMark.elapsedNow().inWholeMilliseconds,
                )
                require(remainingMillis > 0L) { "Timed out waiting for MQTT CONNACK" }
                val result = withTimeoutOrNull(remainingMillis) {
                    session.incoming.receiveCatching()
                } ?: error("Timed out waiting for MQTT CONNACK")
                require(!result.isClosed) {
                    result.exceptionOrNull()?.message ?: "WebSocket closed before MQTT CONNACK"
                }
                pending += frameBytes(result.getOrThrow())
                require(pending.size <= MAX_MQTT_PENDING_BYTES) {
                    "Buffered MQTT transport data exceeds the safety limit"
                }
                val extraction = MqttCodec.extractCompletePackets(pending)
                pending = extraction.remaining
                extraction.packets.forEach { packet ->
                    if (packet.type == 2) {
                        connAck = MqttCodec.parseConnAck(packet)
                        require(connAck.returnCode == 0) {
                            "Broker rejected MQTT connection with code ${connAck.returnCode}"
                        }
                        transportConnected = true
                    }
                }
            }
            events += "CONNACK accepted${if (connAck.sessionPresent) " (existing session)" else ""}"

            if (input.subscribeTopic.isNotBlank()) {
                session.send(
                    Frame.Binary(
                        true,
                        MqttCodec.subscribePacket(
                            input.subscribeTopic,
                            input.subscribeQos,
                            SUBSCRIBE_PACKET_ID,
                        ),
                    ),
                )
                events += "SUBSCRIBE sent for ${SecretRedactor.redact(input.subscribeTopic)} at QoS ${input.subscribeQos}"
            }
            if (input.publishTopic.isNotBlank()) {
                val payload = input.payload.encodeToByteArray()
                require(payload.size <= input.maxMessageBytes) {
                    "MQTT publish payload exceeds the configured size limit"
                }
                session.send(
                    Frame.Binary(
                        true,
                        MqttCodec.publishPacket(
                            input.publishTopic,
                            payload,
                            input.retain,
                            input.publishQos,
                            PUBLISH_PACKET_ID,
                        ),
                    ),
                )
                events += "Published ${payload.size} bytes to ${SecretRedactor.redact(input.publishTopic)} at QoS ${input.publishQos}"
            }

            val pingIntervalMillis = if (input.keepAliveSeconds == 0) {
                input.receiveTimeoutMillis.toLong()
            } else {
                (input.keepAliveSeconds * 500L).coerceIn(1_000L, 60_000L)
            }
            var receiving = mqttRequiresReceiveLoop(
                subscribeTopic = input.subscribeTopic,
                publishTopic = input.publishTopic,
                publishQos = input.publishQos,
            )
            while (receiving && messages.size < input.maxMessages) {
                val remainingMillis = remainingReceiveWindowMillis(
                    input.receiveTimeoutMillis,
                    receiveMark.elapsedNow().inWholeMilliseconds,
                )
                if (remainingMillis <= 0L) break
                val waitMillis = minOf(remainingMillis, pingIntervalMillis)
                val result = withTimeoutOrNull(waitMillis) { session.incoming.receiveCatching() }
                when {
                    result == null && remainingMillis > waitMillis && input.keepAliveSeconds > 0 -> {
                        session.send(Frame.Binary(true, MqttCodec.pingRequest()))
                        events += "PINGREQ sent"
                    }
                    result == null -> receiving = false
                    result.isClosed -> {
                        events += SecretRedactor.redact(result.exceptionOrNull()?.message ?: "MQTT WebSocket closed by remote endpoint")
                        receiving = false
                    }
                    else -> {
                        pending += frameBytes(result.getOrThrow())
                        require(pending.size <= MAX_MQTT_PENDING_BYTES) {
                            "Buffered MQTT transport data exceeds the safety limit"
                        }
                        val extraction = MqttCodec.extractCompletePackets(pending)
                        pending = extraction.remaining
                        extraction.packets.forEach { packet ->
                            when (packet.type) {
                                3 -> {
                                    val (message, truncated) = MqttCodec.parsePublish(packet, input.maxMessageBytes)
                                    require(message.qos in 0..1) {
                                        "Inbound MQTT QoS 2 is not supported because the PUBREC/PUBREL/PUBCOMP handshake is not implemented"
                                    }
                                    messages += message
                                    if (truncated) events += "Incoming MQTT payload on ${SecretRedactor.redact(message.topic)} was truncated"
                                    if (message.qos == 1) {
                                        val packetId = requireNotNull(MqttCodec.parsePacketIdentifier(packet)) {
                                            "Inbound QoS 1 publication is missing a packet identifier"
                                        }
                                        session.send(Frame.Binary(true, MqttCodec.pubAckPacket(packetId)))
                                        events += "PUBACK $packetId sent for inbound publication"
                                    }
                                }
                                4 -> {
                                    val packetId = MqttCodec.parsePubAck(packet)
                                    require(packetId == PUBLISH_PACKET_ID) {
                                        "Received PUBACK $packetId but expected $PUBLISH_PACKET_ID"
                                    }
                                    publishAcknowledged = true
                                    events += "PUBACK $packetId received"
                                }
                                9 -> {
                                    val subAck = MqttCodec.parseSubAck(packet)
                                    require(subAck.packetId == SUBSCRIBE_PACKET_ID) {
                                        "Received SUBACK ${subAck.packetId} but expected $SUBSCRIBE_PACKET_ID"
                                    }
                                    require(subAck.returnCodes.size == 1) { "Broker returned an unexpected number of SUBACK codes" }
                                    val grantedQos = subAck.returnCodes.single()
                                    require(grantedQos != 0x80) { "Broker rejected subscription to ${SecretRedactor.redact(input.subscribeTopic)}" }
                                    require(grantedQos in 0..1) { "Broker granted unsupported subscription QoS $grantedQos" }
                                    subscribed = true
                                    events += "SUBACK ${subAck.packetId} accepted at QoS $grantedQos"
                                }
                                13 -> events += "PINGRESP received"
                                else -> events += "MQTT packet type ${packet.type} received"
                            }
                        }
                        if (input.subscribeTopic.isBlank() && publishAcknowledged) {
                            // A connection-only or publish-only session has no further inbound work.
                            receiving = false
                        }
                    }
                }
            }

            if (pending.isNotEmpty()) {
                events += "The receive window ended with ${pending.size} incomplete MQTT transport bytes"
            }
            require(subscribed) { "Timed out waiting for SUBACK for ${input.subscribeTopic}" }
            require(publishAcknowledged) { "Timed out waiting for PUBACK for ${input.publishTopic}" }
            session.send(Frame.Binary(true, MqttCodec.disconnectPacket()))
            events += "DISCONNECT sent"
            MqttResult(
                endpoint = displayUrl,
                connected = transportConnected,
                sessionPresent = connAck.sessionPresent,
                returnCode = connAck.returnCode,
                messages = messages,
                events = events,
                elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                subscribed = subscribed,
                publishAcknowledged = publishAcknowledged,
                operationSuccessful = transportConnected && subscribed && publishAcknowledged,
                securityWarning = securityWarning,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            events += SecretRedactor.redact(error.message ?: "MQTT operation failed")
            MqttResult(
                endpoint = displayUrl,
                connected = transportConnected,
                sessionPresent = connAck.sessionPresent,
                returnCode = connAck.returnCode,
                messages = messages,
                events = events,
                elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                subscribed = subscribed,
                publishAcknowledged = publishAcknowledged,
                operationSuccessful = false,
                error = SecretRedactor.redact(error.message ?: error::class.simpleName ?: "MQTT operation failed"),
                securityWarning = securityWarning,
            )
        } finally {
            session.closeQuietly("MQTT toolbox session completed")
        }
    }

    private fun frameBytes(frame: Frame): ByteArray = when (frame) {
        is Frame.Binary -> frame.readBytes()
        is Frame.Text -> error("MQTT WebSocket transport requires binary frames")
        else -> byteArrayOf()
    }

    override fun close() = client.close()

    private companion object {
        const val SUBSCRIBE_PACKET_ID = 1
        const val PUBLISH_PACKET_ID = 2
    }
}

private suspend fun DefaultClientWebSocketSession?.closeQuietly(reason: String) {
    val activeSession = this ?: return
    try {
        activeSession.close(CloseReason(CloseReason.Codes.NORMAL, reason.take(123)))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Closing is best-effort; the operation result remains authoritative.
    }
}

private const val MAX_WEBSOCKET_FRAME_BYTES: Long = 1_048_576L
private const val MAX_MQTT_PENDING_BYTES: Int = 1_048_576

private fun now(): Long = Clock.System.now().toEpochMilliseconds()
