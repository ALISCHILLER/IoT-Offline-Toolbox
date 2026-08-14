package com.msa.iotofflinetoolbox.core.security

/**
 * Removes credential-shaped values from local logs, history previews, reusable profiles and backups.
 *
 * Redaction is intentionally conservative. A false positive is safer than persisting a token,
 * password, cookie, signature, API key or client secret in operator-visible diagnostics.
 */
object SecretRedactor {
    private const val MASK = "***"
    private const val SECRET_TOKEN =
        "password|passwd|passphrase|credential|token|access[_-]?token|refresh[_-]?token|secret|client[_-]?secret|api[_-]?key|private[_-]?key|session[_-]?id|signature"
    private const val SENSITIVE_NAME =
        "(?:[A-Za-z0-9]+[-_])*(?:$SECRET_TOKEN)(?:[-_][A-Za-z0-9]+)*"

    internal val sensitiveHeaderTokens = setOf(
        "authorization", "proxy-authorization", "cookie", "set-cookie", "api-key", "apikey",
        "auth-token", "access-token", "refresh-token", "client-secret", "private-key",
        "session-id", "credential", "password", "passwd", "passphrase", "token", "secret",
        "signature",
    )

    private val privateKeyBlock = Regex(
        "-----BEGIN(?: [A-Z0-9]+)? PRIVATE KEY-----[\\s\\S]*?-----END(?: [A-Z0-9]+)? PRIVATE KEY-----",
        RegexOption.IGNORE_CASE,
    )
    private val bearerAnywhere = Regex("(?i)\\b(Bearer|Basic)[ \\t]+[A-Za-z0-9._~+/=-]+")
    private val quotedJsonSecret = Regex(
        "(?i)([\\\"']?(?:$SENSITIVE_NAME)[\\\"']?[ \\t]*:[ \\t]*)[\\\"'](?:\\\\.|[^\\\"'\\\\])*[\\\"']",
    )
    private val primitiveJsonSecret = Regex(
        "(?i)([\\\"']?(?:$SENSITIVE_NAME)[\\\"']?[ \\t]*:[ \\t]*)(?![\\\"'])[^\\s,;}]+",
    )
    private val assignmentSecret = Regex("(?i)((?:$SENSITIVE_NAME)[ \\t]*[=:][ \\t]*)[^&\\s,;]+")
    private val querySecret = Regex("(?i)([?&](?:$SENSITIVE_NAME)=)[^&#\\s]+")
    private val commandLineSecret = Regex("(?i)(--?(?:$SENSITIVE_NAME)(?:=|[ \\t]+))[^ \\t\\r\\n]+")
    private val uriUserInfo = Regex("(?i)(://[^/@:\\s]+:)[^@/\\s]+@")
    private val sensitiveFieldName = Regex("(?i)^(?:$SENSITIVE_NAME)$")

    fun redact(value: String): String {
        if (value.isBlank()) return value
        return redactCustomSensitiveHeaderLines(value)
            .replace(privateKeyBlock, "-----BEGIN PRIVATE KEY-----$MASK-----END PRIVATE KEY-----")
            .replace(bearerAnywhere) { match -> match.value.substringBefore(' ') + " " + MASK }
            .replace(quotedJsonSecret) { match -> match.groupValues[1] + "\"$MASK\"" }
            .replace(primitiveJsonSecret) { match -> match.groupValues[1] + MASK }
            .replace(querySecret) { match -> match.groupValues[1] + MASK }
            .replace(assignmentSecret) { match -> match.groupValues[1] + MASK }
            .replace(commandLineSecret) { match -> match.groupValues[1] + MASK }
            .replace(uriUserInfo) { match -> match.groupValues[1] + "$MASK@" }
    }

    internal fun redactCustomSensitiveHeaderLines(value: String): String = value.lineSequence().joinToString("\n") { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) return@joinToString line
        val name = line.substring(0, separator).trim()
        if (isSensitiveHeaderName(name)) line.substring(0, separator + 1) + " " + MASK else line
    }

    /** Removes sensitive HTTP header lines before a reusable profile is persisted. */
    fun removeSensitiveHeaders(value: String): SanitizedHeaders {
        if (value.isBlank()) return SanitizedHeaders("", emptyList())
        val retained = mutableListOf<String>()
        val removed = mutableListOf<String>()
        value.lineSequence().forEach { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) {
                retained += line
            } else {
                val name = line.substring(0, separator).trim()
                if (isSensitiveHeaderName(name)) removed += name else retained += line
            }
        }
        return SanitizedHeaders(retained.joinToString("\n").trim(), removed.distinctBy(String::lowercase))
    }

    fun redactHeaders(headers: Map<String, String>): Map<String, String> = headers.mapValues { (name, value) ->
        if (isSensitiveHeaderName(name)) MASK else redact(value)
    }

    fun isSensitiveHeaderName(name: String): Boolean {
        val normalized = name.trim().lowercase().replace('_', '-')
        if (normalized in sensitiveHeaderTokens) return true
        return sensitiveHeaderTokens.any { token ->
            token.length >= 5 && (
                normalized == token ||
                    normalized.endsWith("-$token") ||
                    normalized.startsWith("$token-") ||
                    normalized.contains("-$token-")
                )
        }
    }

    fun isSensitiveFieldName(name: String): Boolean = sensitiveFieldName.matches(name.trim())

}

data class SanitizedHeaders(
    val value: String,
    val removedNames: List<String>,
)
