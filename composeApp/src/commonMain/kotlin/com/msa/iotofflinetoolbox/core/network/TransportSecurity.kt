package com.msa.iotofflinetoolbox.core.network

/** Security classification and normalization for user-supplied HTTP and WebSocket endpoints. */
object TransportSecurity {
    fun normalizeUrl(rawUrl: String): String = rawUrl.trim()

    fun hasScheme(rawUrl: String, vararg allowedSchemes: String): Boolean {
        val actual = schemeOf(rawUrl)
        return actual.isNotEmpty() && allowedSchemes.any { it.equals(actual, ignoreCase = true) }
    }

    fun requireScheme(
        rawUrl: String,
        allowedSchemes: Set<String>,
        label: String,
    ): String {
        val normalized = normalizeUrl(rawUrl)
        require(normalized.length <= MAX_URL_LENGTH) { "$label exceeds the 8192 character safety limit" }
        val actual = schemeOf(normalized)
        require(actual in allowedSchemes.map(String::lowercase)) {
            "$label must start with ${allowedSchemes.joinToString(" or ") { "$it://" }}"
        }
        val canonical = actual + normalized.substring(normalized.indexOf("://"))
        val authority = authorityOf(canonical)
        require(authority != null && parseAuthority(canonical) != null) {
            "$label must include a valid host and optional port"
        }
        require('@' !in authority) {
            "$label cannot include embedded credentials; use the dedicated authentication fields"
        }
        return canonical
    }

    fun warningForUrl(rawUrl: String): String? {
        val normalized = normalizeUrl(rawUrl)
        val scheme = schemeOf(normalized)
        if (scheme !in setOf("http", "ws")) return null
        val host = extractHost(normalized)
        return if (isLocalHost(host)) {
            "Cleartext ${scheme.uppercase()} is being used for a local IoT endpoint. Payloads and headers are not encrypted on the local network."
        } else {
            "Cleartext ${scheme.uppercase()} to a non-local host can expose payloads, headers and credentials. Prefer HTTPS/WSS or use a trusted private network."
        }
    }

    fun isPublicCleartext(rawUrl: String): Boolean {
        val normalized = normalizeUrl(rawUrl)
        return schemeOf(normalized) in setOf("http", "ws") && !isLocalHost(extractHost(normalized))
    }

    internal fun extractHost(rawUrl: String): String = parseAuthority(normalizeUrl(rawUrl))?.host.orEmpty()

    private data class ParsedAuthority(val host: String, val port: Int?)

    internal fun hasEmbeddedCredentials(rawUrl: String): Boolean = authorityOf(normalizeUrl(rawUrl))?.contains('@') == true

    private fun authorityOf(rawUrl: String): String? {
        val schemeDelimiter = rawUrl.indexOf("://")
        if (schemeDelimiter <= 0) return null
        return rawUrl.substring(schemeDelimiter + 3)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    }

    private fun parseAuthority(rawUrl: String): ParsedAuthority? {
        val authority = authorityOf(rawUrl) ?: return null
        if (authority.isBlank() || authority.any { it.isWhitespace() || it.code < 0x20 || it == '\\' }) return null

        val hostAndPort = authority.substringAfterLast('@')
        if (hostAndPort.isBlank()) return null
        val host: String
        val portText: String?
        if (hostAndPort.startsWith('[')) {
            val closing = hostAndPort.indexOf(']')
            if (closing <= 1) return null
            host = hostAndPort.substring(1, closing)
            val remainder = hostAndPort.substring(closing + 1)
            portText = when {
                remainder.isEmpty() -> null
                remainder.startsWith(':') -> remainder.drop(1)
                else -> return null
            }
        } else {
            if (hostAndPort.count { it == ':' } > 1) return null // IPv6 URL literals must be bracketed.
            val delimiter = hostAndPort.lastIndexOf(':')
            if (delimiter >= 0) {
                host = hostAndPort.substring(0, delimiter)
                portText = hostAndPort.substring(delimiter + 1)
            } else {
                host = hostAndPort
                portText = null
            }
        }
        if (host.isBlank() || host.any { it.isWhitespace() || it.code < 0x20 || it in "[]/@\\" }) return null
        val port = portText?.let {
            if (it.isBlank() || !it.all(Char::isDigit)) return null
            it.toIntOrNull()?.takeIf { value -> value in 1..65_535 } ?: return null
        }
        return ParsedAuthority(host.trim().lowercase().trimEnd('.'), port)
    }

    internal fun isLocalHost(rawHost: String): Boolean {
        val host = rawHost.trim().lowercase().trimEnd('.')
        if (host.isBlank()) return false
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return true

        val ipv6Host = host.substringBefore('%')
        if (ipv6Host == "::1") return true
        if (':' in ipv6Host) {
            val firstHextet = ipv6Host.substringBefore(':').toIntOrNull(16) ?: return false
            return firstHextet in 0xFC00..0xFDFF || firstHextet in 0xFE80..0xFEBF
        }

        val parts = host.split('.')
        if (parts.size == 4) {
            val values = parts.map { it.toIntOrNull() ?: return false }
            if (values.any { it !in 0..255 }) return false
            return values[0] == 10 ||
                values[0] == 127 ||
                (values[0] == 169 && values[1] == 254) ||
                (values[0] == 172 && values[1] in 16..31) ||
                (values[0] == 192 && values[1] == 168)
        }

        // A single-label name is ambiguous: DNS search domains can resolve it outside the LAN.
        // Require the operator to explicitly allow public cleartext for these names.
        return false
    }

    private fun schemeOf(rawUrl: String): String = normalizeUrl(rawUrl)
        .substringBefore("://", missingDelimiterValue = "")
        .lowercase()

    private const val MAX_URL_LENGTH = 8_192
}
