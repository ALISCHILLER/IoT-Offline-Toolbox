package com.msa.iotofflinetoolbox.core.payload

/** Strict, bounded RFC 4648 Base64 codec used by payload tools and protocol helpers. */
object Base64Codec {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val MAX_DECODED_BYTES = 1_048_576
    private const val MAX_ENCODED_CHARS = ((MAX_DECODED_BYTES + 2) / 3) * 4

    fun encode(input: ByteArray): String {
        require(input.size <= MAX_DECODED_BYTES) { "Base64 input exceeds the 1 MiB safety limit" }
        if (input.isEmpty()) return ""
        val output = StringBuilder(((input.size + 2) / 3) * 4)
        var index = 0
        while (index < input.size) {
            val first = input[index++].toInt() and 0xFF
            val second = if (index < input.size) input[index++].toInt() and 0xFF else -1
            val third = if (index < input.size) input[index++].toInt() and 0xFF else -1
            output.append(ALPHABET[first shr 2])
            output.append(ALPHABET[((first and 0x03) shl 4) or if (second >= 0) second shr 4 else 0])
            output.append(if (second >= 0) ALPHABET[((second and 0x0F) shl 2) or if (third >= 0) third shr 6 else 0] else '=')
            output.append(if (third >= 0) ALPHABET[third and 0x3F] else '=')
        }
        return output.toString()
    }

    fun decode(input: String): ByteArray {
        require(input.length <= MAX_ENCODED_CHARS + 16_384) { "Base64 input exceeds the safety limit" }
        val normalized = input.filterNot(Char::isWhitespace)
        require(normalized.length <= MAX_ENCODED_CHARS) { "Decoded Base64 payload would exceed 1 MiB" }
        require(normalized.length % 4 == 0) { "Base64 length must be divisible by four" }
        if (normalized.isEmpty()) return ByteArray(0)

        val outputSize = normalized.length / 4 * 3 - when {
            normalized.endsWith("==") -> 2
            normalized.endsWith('=') -> 1
            else -> 0
        }
        require(outputSize <= MAX_DECODED_BYTES) { "Decoded Base64 payload would exceed 1 MiB" }
        val output = ByteArray(outputSize)
        var outputIndex = 0

        normalized.chunked(4).forEachIndexed { chunkIndex, chunk ->
            val isLast = chunkIndex == normalized.length / 4 - 1
            require(chunk[0] != '=' && chunk[1] != '=') { "Base64 padding cannot appear in the first two positions" }
            require(isLast || '=' !in chunk) { "Base64 padding is only allowed in the final quartet" }
            require(chunk[2] != '=' || chunk[3] == '=') { "Invalid Base64 padding" }

            val values = IntArray(4) { index ->
                val char = chunk[index]
                if (char == '=') -1 else ALPHABET.indexOf(char).also {
                    require(it >= 0) { "Invalid Base64 character: $char" }
                }
            }

            if (chunk[2] == '=') {
                require((values[1] and 0x0F) == 0) { "Non-zero unused bits in Base64 input" }
            } else if (chunk[3] == '=') {
                require((values[2] and 0x03) == 0) { "Non-zero unused bits in Base64 input" }
            }

            output[outputIndex++] = ((values[0] shl 2) or (values[1] shr 4)).toByte()
            if (values[2] >= 0) {
                output[outputIndex++] = (((values[1] and 0x0F) shl 4) or (values[2] shr 2)).toByte()
            }
            if (values[3] >= 0) {
                output[outputIndex++] = (((values[2] and 0x03) shl 6) or values[3]).toByte()
            }
        }
        return output
    }
}
