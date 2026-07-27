package com.msa.iotofflinetoolbox.core.payload

import com.msa.iotofflinetoolbox.core.model.PayloadEncoding

/** Converts operator-entered payloads to and from deterministic byte representations. */
object PayloadCodec {
    private const val MAX_PAYLOAD_BYTES = 1_048_576
    private val HEX_PREFIX_AT_TOKEN_BOUNDARY = Regex("(?i)(^|[\\s:_-]+)0x")
    private const val MAX_HEX_INPUT_CHARS = MAX_PAYLOAD_BYTES * 2

    fun decode(value: String, encoding: PayloadEncoding): ByteArray {
        val decoded = when (encoding) {
            PayloadEncoding.TEXT -> value.encodeToByteArray()
            PayloadEncoding.HEX -> decodeHex(value)
            PayloadEncoding.BASE64 -> Base64Codec.decode(value)
        }
        require(decoded.size <= MAX_PAYLOAD_BYTES) { "Payload exceeds the 1 MiB safety limit" }
        return decoded
    }

    fun encode(bytes: ByteArray, encoding: PayloadEncoding): String {
        require(bytes.size <= MAX_PAYLOAD_BYTES) { "Payload exceeds the 1 MiB safety limit" }
        return when (encoding) {
            PayloadEncoding.TEXT -> bytes.decodeToString()
            PayloadEncoding.HEX -> bytes.toHex()
            PayloadEncoding.BASE64 -> Base64Codec.encode(bytes)
        }
    }

    fun decodeHex(value: String): ByteArray {
        require(value.length <= MAX_HEX_INPUT_CHARS) { "HEX input exceeds the 1 MiB decoded safety limit" }
        val withoutPrefixes = HEX_PREFIX_AT_TOKEN_BOUNDARY.replace(value) { match -> match.groupValues[1] }
        val normalized = withoutPrefixes
            .filterNot(Char::isWhitespace)
            .replace(":", "")
            .replace("-", "")
            .replace("_", "")
        require(normalized.isNotEmpty()) { "HEX input is empty" }
        require(normalized.length % 2 == 0) { "HEX input must contain an even number of digits" }
        require(normalized.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "HEX input contains invalid characters"
        }
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    fun ByteArray.toHex(): String = joinToString(separator = " ") { byte ->
        byte.toUByte().toString(16).uppercase().padStart(2, '0')
    }
}

/** Upper-case, space-separated hexadecimal display representation. */
fun ByteArray.toDisplayHex(): String = PayloadCodec.run { toHex() }
