package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.policy.HttpResponseBodyTools
import com.msa.iotofflinetoolbox.core.port.CoapOperationClient
import com.msa.iotofflinetoolbox.core.port.NetworkProbe

import com.msa.iotofflinetoolbox.core.model.CoapRequestInput
import com.msa.iotofflinetoolbox.core.model.CoapResponseResult
import com.msa.iotofflinetoolbox.core.payload.PayloadCodec
import com.msa.iotofflinetoolbox.core.payload.toDisplayHex
import kotlin.random.Random
import kotlin.time.TimeSource

/** Minimal RFC 7252 CoAP codec for UDP request/response diagnostics. */
object CoapCodec {
    private const val VERSION = 1
    private const val PAYLOAD_MARKER = 0xFF
    private const val MAX_PATH_OR_QUERY_CHARS = 4_096
    private const val MAX_OPTION_ITEMS = 128
    private const val MAX_COAP_PAYLOAD_BYTES = 60_000
    private const val MAX_UDP_DATAGRAM_BYTES = 65_507

    data class ParsedMessage(
        val version: Int,
        val type: Int,
        val token: ByteArray,
        val code: Int,
        val messageId: Int,
        val options: Map<Int, List<ByteArray>>,
        val payload: ByteArray,
    )

    fun encodeRequest(input: CoapRequestInput, messageId: Int, token: ByteArray): ByteArray {
        require(input.host.isNotBlank()) { "CoAP host cannot be empty" }
        require(input.port in 1..65_535) { "CoAP port must be between 1 and 65535" }
        require(input.timeoutMillis in 100..120_000) { "CoAP timeout must be between 100 and 120000 ms" }
        require(token.size in 0..8) { "CoAP token length must be between 0 and 8 bytes" }
        require(messageId in 0..65_535) { "CoAP message ID must be between 0 and 65535" }
        require(input.path.length <= MAX_PATH_OR_QUERY_CHARS) { "CoAP path exceeds the safety limit" }
        require(input.query.length <= MAX_PATH_OR_QUERY_CHARS) { "CoAP query exceeds the safety limit" }
        val payloadBytes = input.payload.encodeToByteArray()
        require(payloadBytes.size <= MAX_COAP_PAYLOAD_BYTES) { "CoAP payload exceeds the UDP safety limit" }

        val result = mutableListOf<Byte>()
        val first = (VERSION shl 6) or (input.messageType.value shl 4) or token.size
        result += first.toByte()
        result += input.method.code.toByte()
        result += (messageId shr 8).toByte()
        result += messageId.toByte()
        result += token.toList()

        val options = mutableListOf<Pair<Int, ByteArray>>()
        val pathSegments = input.path.trim().trim('/').split('/').filter(String::isNotBlank)
        require(pathSegments.size <= MAX_OPTION_ITEMS) { "CoAP path contains too many segments" }
        pathSegments.forEach {
            options += 11 to it.encodeToByteArray()
        }
        input.contentFormat?.let {
            require(it in 0..65_535) { "CoAP Content-Format must be between 0 and 65535" }
            options += 12 to encodeUInt(it)
        }
        val queryItems = input.query.trim().removePrefix("?").split('&').filter(String::isNotBlank)
        require(queryItems.size <= MAX_OPTION_ITEMS) { "CoAP query contains too many items" }
        queryItems.forEach {
            options += 15 to it.encodeToByteArray()
        }
        input.accept?.let {
            require(it in 0..65_535) { "CoAP Accept must be between 0 and 65535" }
            options += 17 to encodeUInt(it)
        }
        require(options.size <= MAX_OPTION_ITEMS) { "CoAP request contains too many options" }

        var previousNumber = 0
        options.sortedBy { it.first }.forEach { (number, value) ->
            val delta = number - previousNumber
            val deltaField = encodeOptionField(delta)
            val lengthField = encodeOptionField(value.size)
            result += ((deltaField.nibble shl 4) or lengthField.nibble).toByte()
            result += deltaField.extended.toList()
            result += lengthField.extended.toList()
            result += value.toList()
            previousNumber = number
        }

        if (payloadBytes.isNotEmpty()) {
            result += PAYLOAD_MARKER.toByte()
            result += payloadBytes.toList()
        }
        require(result.size <= MAX_UDP_DATAGRAM_BYTES) { "Encoded CoAP request exceeds the UDP datagram limit" }
        return result.toByteArray()
    }

    fun parse(bytes: ByteArray): ParsedMessage {
        require(bytes.size >= 4) { "CoAP packet is shorter than its four-byte header" }
        require(bytes.size <= MAX_UDP_DATAGRAM_BYTES) { "CoAP packet exceeds the UDP datagram limit" }
        val first = bytes[0].toInt() and 0xFF
        val version = first shr 6
        require(version == VERSION) { "Unsupported CoAP version $version" }
        val type = (first shr 4) and 0x03
        val tokenLength = first and 0x0F
        require(tokenLength <= 8 && bytes.size >= 4 + tokenLength) { "Invalid CoAP token length" }
        val code = bytes[1].toInt() and 0xFF
        if (code == 0) {
            require(tokenLength == 0 && bytes.size == 4) {
                "An empty CoAP message must contain only the four-byte header and no token"
            }
        }
        val messageId = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        val token = bytes.copyOfRange(4, 4 + tokenLength)
        var cursor = 4 + tokenLength
        var optionNumber = 0
        val options = linkedMapOf<Int, MutableList<ByteArray>>()
        var payload = byteArrayOf()
        var optionCount = 0

        while (cursor < bytes.size) {
            val header = bytes[cursor++].toInt() and 0xFF
            if (header == PAYLOAD_MARKER) {
                require(cursor < bytes.size) { "CoAP payload marker must be followed by payload data" }
                payload = bytes.copyOfRange(cursor, bytes.size)
                break
            }
            optionCount += 1
            require(optionCount <= MAX_OPTION_ITEMS) { "CoAP packet contains too many options" }
            val delta = decodeOptionField(header shr 4, bytes, cursor)
            cursor = delta.nextIndex
            val length = decodeOptionField(header and 0x0F, bytes, cursor)
            cursor = length.nextIndex
            require(delta.value <= 65_535 - optionNumber) { "CoAP option number exceeds 65535" }
            optionNumber += delta.value
            require(cursor + length.value <= bytes.size) { "CoAP option length exceeds packet size" }
            options.getOrPut(optionNumber) { mutableListOf() } += bytes.copyOfRange(cursor, cursor + length.value)
            cursor += length.value
        }

        return ParsedMessage(version, type, token, code, messageId, options, payload)
    }

    fun responseCodeText(code: Int): String {
        val codeClass = code shr 5
        val detail = code and 0x1F
        val known = when (code) {
            65 -> "Created"
            66 -> "Deleted"
            67 -> "Valid"
            68 -> "Changed"
            69 -> "Content"
            95 -> "Continue"
            128 -> "Bad Request"
            129 -> "Unauthorized"
            130 -> "Bad Option"
            131 -> "Forbidden"
            132 -> "Not Found"
            133 -> "Method Not Allowed"
            134 -> "Not Acceptable"
            136 -> "Request Entity Incomplete"
            140 -> "Precondition Failed"
            141 -> "Request Entity Too Large"
            143 -> "Unsupported Content-Format"
            160 -> "Internal Server Error"
            161 -> "Not Implemented"
            162 -> "Bad Gateway"
            163 -> "Service Unavailable"
            164 -> "Gateway Timeout"
            165 -> "Proxying Not Supported"
            else -> null
        }
        return "$codeClass.${detail.toString().padStart(2, '0')}${known?.let { " $it" }.orEmpty()}"
    }

    fun optionUInt(message: ParsedMessage, number: Int): Int? {
        val value = message.options[number]?.firstOrNull() ?: return null
        require(value.size <= 4) { "CoAP integer option is too large" }
        val decoded = value.fold(0L) { accumulator, byte ->
            (accumulator shl 8) or (byte.toLong() and 0xFF)
        }
        require(decoded <= Int.MAX_VALUE) { "CoAP integer option exceeds the supported signed range" }
        return decoded.toInt()
    }

    /** Common CoAP Content-Format registry mappings used for safe payload rendering. */
    fun contentTypeForFormat(format: Int?): String? = when (format) {
        0 -> "text/plain; charset=utf-8"
        40 -> "application/link-format"
        41 -> "application/xml"
        42 -> "application/octet-stream"
        47 -> "application/exi"
        50 -> "application/json"
        60 -> "application/cbor"
        else -> null
    }

    private data class EncodedField(val nibble: Int, val extended: ByteArray)
    private data class DecodedField(val value: Int, val nextIndex: Int)

    private fun encodeOptionField(value: Int): EncodedField = when {
        value in 0..12 -> EncodedField(value, byteArrayOf())
        value in 13..268 -> EncodedField(13, byteArrayOf((value - 13).toByte()))
        value in 269..65_804 -> EncodedField(14, byteArrayOf(((value - 269) shr 8).toByte(), (value - 269).toByte()))
        else -> error("CoAP option delta/length is too large")
    }

    private fun decodeOptionField(nibble: Int, bytes: ByteArray, cursor: Int): DecodedField = when (nibble) {
        in 0..12 -> DecodedField(nibble, cursor)
        13 -> {
            require(cursor < bytes.size) { "Incomplete CoAP extended option field" }
            DecodedField(13 + (bytes[cursor].toInt() and 0xFF), cursor + 1)
        }
        14 -> {
            require(cursor + 1 < bytes.size) { "Incomplete CoAP extended option field" }
            val extended = ((bytes[cursor].toInt() and 0xFF) shl 8) or (bytes[cursor + 1].toInt() and 0xFF)
            DecodedField(269 + extended, cursor + 2)
        }
        else -> error("Reserved CoAP option field value 15")
    }

    private fun encodeUInt(value: Int): ByteArray {
        require(value >= 0) { "CoAP integer option cannot be negative" }
        return when {
            value == 0 -> byteArrayOf()
            value <= 0xFF -> byteArrayOf(value.toByte())
            value <= 0xFFFF -> byteArrayOf((value shr 8).toByte(), value.toByte())
            value <= 0xFFFFFF -> byteArrayOf((value shr 16).toByte(), (value shr 8).toByte(), value.toByte())
            else -> byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())
        }
    }
}

/** Executes one bounded CoAP exchange through the platform UDP engine. */
class CoapClient(private val networkProbe: NetworkProbe) : CoapOperationClient {
    override suspend fun execute(input: CoapRequestInput, maxResponseBytes: Int): CoapResponseResult {
        val messageId = Random.nextInt(0, 65_536)
        val token = Random.nextBytes(4)
        val packet = CoapCodec.encodeRequest(input, messageId, token)
        val mark = TimeSource.Monotonic.markNow()
        val exchange = networkProbe.udpExchange(input.host.trim(), input.port, packet, input.timeoutMillis, maxResponseBytes)
        if (exchange.error != null) {
            return CoapResponseResult(
                endpoint = exchange.endpoint,
                messageId = messageId,
                tokenHex = token.toDisplayHex(),
                responseCode = 0,
                responseCodeText = "No response",
                payloadText = "",
                payloadHex = "",
                elapsedMillis = exchange.elapsedMillis,
                truncated = exchange.truncated,
                error = exchange.error,
            )
        }
        if (exchange.responseHex.isBlank()) {
            return CoapResponseResult(
                endpoint = exchange.endpoint,
                messageId = messageId,
                tokenHex = token.toDisplayHex(),
                responseCode = 0,
                responseCodeText = "No response",
                payloadText = "",
                payloadHex = "",
                elapsedMillis = exchange.elapsedMillis,
                truncated = exchange.truncated,
                error = "Timed out waiting for a CoAP response",
            )
        }
        return runCatching {
            val responseBytes = PayloadCodec.decode(exchange.responseHex, com.msa.iotofflinetoolbox.core.model.PayloadEncoding.HEX)
            val parsed = CoapCodec.parse(responseBytes)
            require(parsed.token.contentEquals(token)) { "CoAP response token does not match the request" }
            require(parsed.messageId == messageId || input.messageType.value == 1) { "CoAP response message ID does not match the request" }
            val contentFormat = CoapCodec.optionUInt(parsed, 12)
            val decodedPayload = HttpResponseBodyTools.decode(
                parsed.payload,
                CoapCodec.contentTypeForFormat(contentFormat),
            )
            CoapResponseResult(
                endpoint = exchange.endpoint,
                messageId = parsed.messageId,
                tokenHex = parsed.token.toDisplayHex(),
                responseCode = parsed.code,
                responseCodeText = CoapCodec.responseCodeText(parsed.code),
                payloadText = decodedPayload.text,
                payloadHex = parsed.payload.toDisplayHex(),
                contentFormat = contentFormat,
                elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                truncated = exchange.truncated,
            )
        }.getOrElse { error ->
            CoapResponseResult(
                endpoint = exchange.endpoint,
                messageId = messageId,
                tokenHex = token.toDisplayHex(),
                responseCode = 0,
                responseCodeText = "Malformed response",
                payloadText = exchange.responseText,
                payloadHex = exchange.responseHex,
                elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                truncated = exchange.truncated,
                error = error.message ?: "Unable to parse CoAP response",
            )
        }
    }
}
