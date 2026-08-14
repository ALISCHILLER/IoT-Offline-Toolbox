package com.msa.iotofflinetoolbox.core.policy

/** Parses an operator-entered `Header-Name: value` block with RFC-compatible name checks. */
object HeaderParser {
    private val token = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

    fun parse(text: String, maximumHeaders: Int = 100): Map<String, String> {
        require(maximumHeaders in 1..1_000) { "Maximum header count must be between 1 and 1000" }
        require(text.length <= MAX_HEADER_BLOCK_LENGTH) { "Header block exceeds $MAX_HEADER_BLOCK_LENGTH characters" }
        require('\u0000' !in text) { "Header block contains a null character" }
        require(!BARE_CARRIAGE_RETURN.containsMatchIn(text)) {
            "Header block contains a carriage return that is not part of CRLF"
        }
        val result = linkedMapOf<String, String>()
        val normalizedNames = mutableSetOf<String>()
        text.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEachIndexed { index, line ->
                require(result.size < maximumHeaders) { "A maximum of $maximumHeaders headers is supported" }
                val separator = line.indexOf(':')
                require(separator > 0) { "Invalid header at line ${index + 1}: $line" }
                val name = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                require(token.matches(name)) { "Invalid header name at line ${index + 1}: $name" }
                val normalizedName = name.lowercase()
                require(normalizedNames.add(normalizedName)) { "Duplicate header name at line ${index + 1}: $name" }
                require(value.none { it == '\r' || it == '\n' || (it.code < 0x20 && it != '\t') || it.code == 0x7F }) {
                    "Header value contains an invalid control character"
                }
                require(value.length <= MAX_HEADER_VALUE_LENGTH) { "Header value is too long at line ${index + 1}" }
                result[name] = value
            }
        return result
    }

    private val BARE_CARRIAGE_RETURN = Regex("\r(?!\n)")
    private const val MAX_HEADER_BLOCK_LENGTH = 65_536
    private const val MAX_HEADER_VALUE_LENGTH = 16_384
}
