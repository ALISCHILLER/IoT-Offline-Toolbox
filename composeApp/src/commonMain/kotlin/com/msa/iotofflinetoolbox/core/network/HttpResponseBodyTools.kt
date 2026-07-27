package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.payload.Base64Codec

/** Bounded response-body classification and display helpers for text and binary HTTP payloads. */
object HttpResponseBodyTools {
    data class DecodedBody(
        val text: String,
        val isText: Boolean,
    )

    fun decode(bytes: ByteArray, contentType: String?): DecodedBody {
        if (bytes.isEmpty()) return DecodedBody("", true)
        val contentTypeIsText = isTextualContentType(contentType)
        val contentTypeIsBinary = isExplicitlyBinaryContentType(contentType)
        val utf8 = isValidUtf8(bytes)
        val likelyText = utf8 && printableRatio(bytes) >= MIN_PRINTABLE_RATIO
        val isText = when {
            contentTypeIsBinary -> false
            contentTypeIsText -> utf8
            else -> likelyText
        }
        return DecodedBody(
            text = if (isText) bytes.decodeToString() else "",
            isText = isText,
        )
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") {
        it.toUByte().toString(16).uppercase().padStart(2, '0')
    }

    fun toBase64(bytes: ByteArray): String = Base64Codec.encode(bytes)

    fun isTextualContentType(contentType: String?): Boolean {
        val mediaType = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (mediaType.startsWith("text/")) return true
        return mediaType == "application/json" ||
            mediaType.endsWith("+json") ||
            mediaType == "application/xml" ||
            mediaType.endsWith("+xml") ||
            mediaType == "application/javascript" ||
            mediaType == "application/x-javascript" ||
            mediaType == "application/x-www-form-urlencoded" ||
            mediaType == "application/graphql" ||
            mediaType == "application/yaml" ||
            mediaType == "application/x-yaml" ||
            mediaType == "application/sql" ||
            mediaType == "image/svg+xml"
    }


    fun isExplicitlyBinaryContentType(contentType: String?): Boolean {
        val mediaType = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (mediaType.isEmpty() || isTextualContentType(mediaType)) return false
        return mediaType.startsWith("image/") ||
            mediaType.startsWith("audio/") ||
            mediaType.startsWith("video/") ||
            mediaType.startsWith("font/") ||
            mediaType == "application/octet-stream" ||
            mediaType == "application/pdf" ||
            mediaType == "application/zip" ||
            mediaType == "application/gzip" ||
            mediaType == "application/x-gzip" ||
            mediaType == "application/x-7z-compressed" ||
            mediaType == "application/x-rar-compressed" ||
            mediaType == "application/wasm" ||
            mediaType == "application/protobuf" ||
            mediaType == "application/x-protobuf" ||
            mediaType == "application/msgpack"
    }

    internal fun isValidUtf8(bytes: ByteArray): Boolean {
        var index = 0
        while (index < bytes.size) {
            val first = bytes[index].toInt() and 0xFF
            when {
                first <= 0x7F -> index += 1
                first in 0xC2..0xDF -> {
                    if (!hasContinuation(bytes, index + 1, 1)) return false
                    index += 2
                }
                first == 0xE0 -> {
                    if (index + 2 >= bytes.size) return false
                    val second = bytes[index + 1].toInt() and 0xFF
                    val third = bytes[index + 2].toInt() and 0xFF
                    if (second !in 0xA0..0xBF || third !in 0x80..0xBF) return false
                    index += 3
                }
                first in 0xE1..0xEC || first in 0xEE..0xEF -> {
                    if (!hasContinuation(bytes, index + 1, 2)) return false
                    index += 3
                }
                first == 0xED -> {
                    if (index + 2 >= bytes.size) return false
                    val second = bytes[index + 1].toInt() and 0xFF
                    val third = bytes[index + 2].toInt() and 0xFF
                    if (second !in 0x80..0x9F || third !in 0x80..0xBF) return false
                    index += 3
                }
                first == 0xF0 -> {
                    if (index + 3 >= bytes.size) return false
                    val second = bytes[index + 1].toInt() and 0xFF
                    if (second !in 0x90..0xBF || !hasContinuation(bytes, index + 2, 2)) return false
                    index += 4
                }
                first in 0xF1..0xF3 -> {
                    if (!hasContinuation(bytes, index + 1, 3)) return false
                    index += 4
                }
                first == 0xF4 -> {
                    if (index + 3 >= bytes.size) return false
                    val second = bytes[index + 1].toInt() and 0xFF
                    if (second !in 0x80..0x8F || !hasContinuation(bytes, index + 2, 2)) return false
                    index += 4
                }
                else -> return false
            }
        }
        return true
    }

    private fun hasContinuation(bytes: ByteArray, start: Int, count: Int): Boolean {
        if (start < 0 || start + count > bytes.size) return false
        repeat(count) { offset ->
            val value = bytes[start + offset].toInt() and 0xFF
            if (value !in 0x80..0xBF) return false
        }
        return true
    }

    private fun printableRatio(bytes: ByteArray): Double {
        var printable = 0
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            if (value >= 0x20 || value == 0x09 || value == 0x0A || value == 0x0D) printable++
        }
        return printable.toDouble() / bytes.size.toDouble()
    }

    private const val MIN_PRINTABLE_RATIO = 0.85
}
