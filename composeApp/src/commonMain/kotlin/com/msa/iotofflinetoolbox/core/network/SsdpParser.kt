package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.SsdpDevice

object SsdpParser {
    const val ADDRESS: String = "239.255.255.250"
    const val PORT: Int = 1900

    fun searchRequest(searchTarget: String = "ssdp:all", mxSeconds: Int = 2): String {
        val target = searchTarget.ifBlank { "ssdp:all" }.trim()
        require(target.length <= MAX_HEADER_VALUE_CHARS) { "SSDP search target is too long" }
        require('\r' !in target && '\n' !in target && '\u0000' !in target) {
            "SSDP search target contains an invalid control character"
        }
        return buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $ADDRESS:$PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: ${mxSeconds.coerceIn(1, 5)}\r\n")
            append("ST: $target\r\n")
            append("\r\n")
        }
    }

    fun parse(response: String, remoteAddress: String? = null): SsdpDevice? {
        require(response.length <= MAX_RESPONSE_CHARS) { "SSDP response exceeds the safety limit" }
        require('\u0000' !in response) { "SSDP response contains a null character" }
        val normalized = response.replace("\r\n", "\n").replace('\r', '\n')
        val headerBlock = normalized.substringBefore("\n\n")
        val lines = headerBlock.lines()
        require(lines.size <= MAX_HEADER_LINES + 1) { "SSDP response contains too many header lines" }
        val statusLine = lines.firstOrNull()?.trim().orEmpty()
        if (!STATUS_LINE.matches(statusLine)) return null
        val headers = linkedMapOf<String, String>()
        lines.drop(1).filter(String::isNotBlank).forEach { line ->
            require(line.length <= MAX_HEADER_LINE_CHARS) { "SSDP header line is too long" }
            val delimiter = line.indexOf(':')
            require(delimiter > 0) { "Malformed SSDP header line" }
            val name = line.substring(0, delimiter).trim()
            val value = line.substring(delimiter + 1).trim()
            require(HEADER_NAME.matches(name)) { "SSDP header name contains invalid characters" }
            require(value.length <= MAX_HEADER_VALUE_CHARS) { "SSDP header value is too long" }
            require(value.none { it.code < 0x20 && it != '\t' }) { "SSDP header value contains a control character" }
            headers.putIfAbsent(name.uppercase(), value)
        }
        val location = headers["LOCATION"].orEmpty()
        val usn = headers["USN"] ?: location.ifBlank { remoteAddress.orEmpty() }
        if (usn.isBlank() && location.isBlank()) return null
        return SsdpDevice(
            usn = usn,
            location = location,
            server = headers["SERVER"],
            searchTarget = headers["ST"],
            cacheControl = headers["CACHE-CONTROL"],
            remoteAddress = remoteAddress,
            headers = headers,
        )
    }

    private val STATUS_LINE = Regex("^HTTP/1\\.[01]\\s+200(?:\\s+.*)?$", RegexOption.IGNORE_CASE)
    private val HEADER_NAME = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

    private const val MAX_RESPONSE_CHARS = 65_535
    private const val MAX_HEADER_LINES = 256
    private const val MAX_HEADER_LINE_CHARS = 8_192
    private const val MAX_HEADER_VALUE_CHARS = 4_096
}
