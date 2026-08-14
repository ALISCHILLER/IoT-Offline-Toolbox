package com.msa.iotofflinetoolbox.core.payload

import com.msa.iotofflinetoolbox.core.model.PayloadOperation
import com.msa.iotofflinetoolbox.core.model.PayloadToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.random.Random
import kotlin.time.Clock

object PayloadTools {
    private const val MAX_PAYLOAD_INPUT_CHARS = 2_097_152
    private const val MAX_PAYLOAD_OUTPUT_CHARS = 4_194_304
    private const val MAX_VARIABLES = 256
    private const val MAX_VARIABLE_KEY_CHARS = 128
    private const val MAX_VARIABLE_VALUE_CHARS = 65_536
    private val compactJson = Json { prettyPrint = false }
    private val prettyJson = Json { prettyPrint = true }

    fun transform(
        input: String,
        operation: PayloadOperation,
        variables: Map<String, String> = emptyMap(),
    ): PayloadToolResult = runCatching {
        require(input.length <= MAX_PAYLOAD_INPUT_CHARS) { "Payload input exceeds the 2 MiB character safety limit" }
        validateExpansionInput(input, variables)
        val output = when (operation) {
            PayloadOperation.FORMAT_JSON -> prettyJson.encodeToString(JsonElement.serializer(), compactJson.parseToJsonElement(input))
            PayloadOperation.MINIFY_JSON -> compactJson.encodeToString(JsonElement.serializer(), compactJson.parseToJsonElement(input))
            PayloadOperation.VALIDATE_JSON -> {
                compactJson.parseToJsonElement(input)
                "Valid JSON"
            }
            PayloadOperation.TEXT_TO_HEX -> PayloadCodec.encode(input.encodeToByteArray(), com.msa.iotofflinetoolbox.core.model.PayloadEncoding.HEX)
            PayloadOperation.HEX_TO_TEXT -> PayloadCodec.decodeHex(input).decodeToString()
            PayloadOperation.TEXT_TO_BASE64 -> Base64Codec.encode(input.encodeToByteArray())
            PayloadOperation.BASE64_TO_TEXT -> Base64Codec.decode(input).decodeToString()
            PayloadOperation.EXPAND_VARIABLES -> expandVariables(input, variables)
            PayloadOperation.CRC32 -> crc32(PayloadCodec.decode(input, com.msa.iotofflinetoolbox.core.model.PayloadEncoding.TEXT)).toString(16).uppercase().padStart(8, '0')
        }
        require(output.length <= MAX_PAYLOAD_OUTPUT_CHARS) { "Payload output exceeds the 4 MiB character safety limit" }
        PayloadToolResult(operation = operation, output = output, valid = true, message = successMessage(operation))
    }.getOrElse { error ->
        PayloadToolResult(
            operation = operation,
            output = "",
            valid = false,
            message = error.message ?: "Payload conversion failed",
        )
    }

    fun expandVariables(input: String, variables: Map<String, String> = emptyMap()): String {
        validateExpansionInput(input, variables)
        val now = Clock.System.now().toEpochMilliseconds()
        val replacements = mapOf(
            "timestamp" to now.toString(),
            "timestamp_seconds" to (now / 1_000L).toString(),
            "uuid" to pseudoUuid(),
            "random" to Random.nextInt().toString(),
            "random_6" to Random.nextInt(100_000, 1_000_000).toString(),
        ) + variables

        val output = StringBuilder(minOf(input.length, MAX_PAYLOAD_OUTPUT_CHARS))
        var cursor = 0
        while (cursor < input.length) {
            val tokenStart = input.indexOf("{{", cursor)
            if (tokenStart < 0) {
                output.appendBounded(input, cursor, input.length)
                break
            }
            output.appendBounded(input, cursor, tokenStart)
            val tokenEnd = input.indexOf("}}", tokenStart + 2)
            if (tokenEnd < 0) {
                output.appendBounded(input, tokenStart, input.length)
                break
            }
            val key = input.substring(tokenStart + 2, tokenEnd)
            val replacement = replacements[key]
            if (replacement == null) {
                output.appendBounded(input, tokenStart, tokenEnd + 2)
            } else {
                output.appendBounded(replacement)
            }
            cursor = tokenEnd + 2
        }
        return output.toString()
    }

    private fun validateExpansionInput(input: String, variables: Map<String, String>) {
        require(input.length <= MAX_PAYLOAD_INPUT_CHARS) { "Payload input exceeds the 2 MiB character safety limit" }
        require(variables.size <= MAX_VARIABLES) { "A maximum of 256 variables is supported" }
        require(variables.all { (key, value) ->
            key.isNotEmpty() && key.length <= MAX_VARIABLE_KEY_CHARS &&
                value.length <= MAX_VARIABLE_VALUE_CHARS &&
                key.none { it == '{' || it == '}' }
        }) { "Variable name or value exceeds the safety limit" }
    }

    private fun StringBuilder.appendBounded(value: String) {
        require(value.length <= MAX_PAYLOAD_OUTPUT_CHARS - length) { "Expanded payload exceeds the 4 MiB safety limit" }
        append(value)
    }

    private fun StringBuilder.appendBounded(value: String, startIndex: Int, endIndex: Int) {
        val segmentLength = endIndex - startIndex
        require(segmentLength <= MAX_PAYLOAD_OUTPUT_CHARS - length) { "Expanded payload exceeds the 4 MiB safety limit" }
        append(value, startIndex, endIndex)
    }

    private fun crc32(bytes: ByteArray): UInt {
        var crc = 0xFFFFFFFFu
        bytes.forEach { byte ->
            crc = crc xor byte.toUByte().toUInt()
            repeat(8) {
                crc = if ((crc and 1u) != 0u) (crc shr 1) xor 0xEDB88320u else crc shr 1
            }
        }
        return crc xor 0xFFFFFFFFu
    }

    private fun pseudoUuid(): String {
        val chars = "0123456789abcdef"
        val groups = listOf(8, 4, 4, 4, 12)
        return groups.joinToString("-") { length -> buildString(length) { repeat(length) { append(chars.random()) } } }
    }

    private fun successMessage(operation: PayloadOperation): String = when (operation) {
        PayloadOperation.VALIDATE_JSON -> "The payload is valid JSON."
        PayloadOperation.CRC32 -> "CRC32 calculated from UTF-8 bytes."
        PayloadOperation.EXPAND_VARIABLES -> "Supported variables: {{timestamp}}, {{timestamp_seconds}}, {{uuid}}, {{random}}, {{random_6}}."
        else -> "Conversion completed."
    }
}
