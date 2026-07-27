package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.data.SecretRedactor
import com.msa.iotofflinetoolbox.core.model.HttpAuthType
import com.msa.iotofflinetoolbox.core.model.HttpBodyType
import com.msa.iotofflinetoolbox.core.model.HttpRequestInput
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.Json

object KeyValueLineParser {
    fun parse(
        text: String,
        maximumEntries: Int = 200,
        allowEmptyValues: Boolean = true,
    ): List<Pair<String, String>> {
        require(maximumEntries in 1..2_000) { "Maximum entry count must be between 1 and 2000" }
        require(text.length <= 65_536) { "Key/value block exceeds 65536 characters" }
        require('\u0000' !in text) { "Key/value block contains a null character" }
        val result = mutableListOf<Pair<String, String>>()
        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith('#')) return@forEachIndexed
            require(result.size < maximumEntries) { "A maximum of $maximumEntries entries is supported" }
            val separator = line.indexOf('=')
            require(separator > 0) { "Invalid key=value entry at line ${index + 1}" }
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            require(key.isNotBlank()) { "Entry key cannot be empty at line ${index + 1}" }
            require(key.length <= 1_024) { "Entry key is too long at line ${index + 1}" }
            require(value.length <= 16_384) { "Entry value is too long at line ${index + 1}" }
            require(allowEmptyValues || value.isNotEmpty()) { "Entry value cannot be empty at line ${index + 1}" }
            require(key.none(Char::isISOControl) && value.none { it == '\r' || it == '\n' || it == '\u0000' }) {
                "Entry contains an invalid control character at line ${index + 1}"
            }
            result += key to value
        }
        return result
    }
}

fun isBrowserForbiddenRequestHeader(name: String): Boolean {
    val normalized = name.trim().lowercase()
    return normalized in BROWSER_FORBIDDEN_REQUEST_HEADERS ||
        normalized.startsWith("proxy-") ||
        normalized.startsWith("sec-")
}

private val BROWSER_FORBIDDEN_REQUEST_HEADERS = setOf(
    "accept-charset",
    "accept-encoding",
    "access-control-request-headers",
    "access-control-request-method",
    "connection",
    "content-length",
    "cookie",
    "cookie2",
    "date",
    "dnt",
    "expect",
    "host",
    "keep-alive",
    "origin",
    "permissions-policy",
    "referer",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
    "via",
)

enum class CurlShell { POSIX, POWERSHELL }

object HttpRequestPreview {
    fun curl(
        input: HttpRequestInput,
        redactSecrets: Boolean = true,
        shell: CurlShell = CurlShell.POSIX,
    ): String {
        val executable = if (shell == CurlShell.POWERSHELL) "curl.exe" else "curl"
        val lines = mutableListOf("$executable --request ${input.method.trim().uppercase()}")
        val url = URLBuilder(input.url.trim()).apply {
            KeyValueLineParser.parse(input.queryText).forEach { (key, value) ->
                val safeValue = if (redactSecrets && SecretRedactor.isSensitiveFieldName(key)) {
                    "<REDACTED>"
                } else {
                    value
                }
                parameters.append(key, safeValue)
            }
        }.buildString()
        lines += "  --url ${quote(url, shell)}"

        val parsedHeaders = HeaderParser.parse(input.headersText)
        parsedHeaders.forEach { (name, value) ->
            val safe = if (redactSecrets && SecretRedactor.isSensitiveHeaderName(name)) "<REDACTED>" else value
            lines += "  --header ${quote("$name: $safe", shell)}"
        }
        if (input.cookieText.isNotBlank()) {
            val cookies = KeyValueLineParser.parse(input.cookieText)
                .joinToString("; ") { (key, value) -> "$key=${if (redactSecrets) "<REDACTED>" else value}" }
            lines += "  --header ${quote("Cookie: $cookies", shell)}"
        }
        when (input.authType) {
            HttpAuthType.NONE -> Unit
            HttpAuthType.BASIC -> lines += "  --user ${quote("${input.authUsername}:${if (redactSecrets) "<REDACTED>" else input.authPassword}", shell)}"
            HttpAuthType.BEARER -> lines += "  --header ${quote("Authorization: Bearer ${if (redactSecrets) "<REDACTED>" else input.bearerToken}", shell)}"
            HttpAuthType.API_KEY -> lines += "  --header ${quote("${input.apiKeyName}: ${if (redactSecrets) "<REDACTED>" else input.apiKeyValue}", shell)}"
        }
        lines += "  --connect-timeout ${formatSeconds(input.connectTimeoutMillis)}"
        lines += "  --max-time ${formatSeconds(input.timeoutMillis)}"
        if (input.followRedirects) lines += "  --location"
        when (input.bodyType) {
            HttpBodyType.NONE -> Unit
            HttpBodyType.FORM_URLENCODED -> KeyValueLineParser.parse(input.body).forEach { (key, value) ->
                val safeValue = if (redactSecrets && SecretRedactor.isSensitiveFieldName(key)) "<REDACTED>" else value
                lines += "  --data-urlencode ${quote("$key=$safeValue", shell)}"
            }
            HttpBodyType.JSON, HttpBodyType.RAW -> {
                lines += "  --header ${quote("Content-Type: ${input.contentType}", shell)}"
                val safeBody = if (redactSecrets) SecretRedactor.redact(input.body) else input.body
                lines += "  --data-raw ${quote(safeBody, shell)}"
            }
        }
        val continuation = if (shell == CurlShell.POWERSHELL) " `\n" else " \\\n"
        return lines.joinToString(continuation)
    }

    fun prettyBody(body: String, contentType: String?): String {
        val looksJson = contentType?.contains("json", ignoreCase = true) == true ||
            body.trimStart().startsWith('{') || body.trimStart().startsWith('[')
        if (!looksJson || body.isBlank()) return body
        return runCatching {
            PRETTY_JSON.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), Json.parseToJsonElement(body))
        }.getOrDefault(body)
    }

    private fun formatSeconds(milliseconds: Int): String =
        (milliseconds / 1000.0).toString().trimEnd('0').trimEnd('.')

    private fun quote(value: String, shell: CurlShell): String = when (shell) {
        CurlShell.POSIX -> "'${value.replace("'", "'\\''")}'"
        CurlShell.POWERSHELL -> "'${value.replace("'", "''")}'"
    }

    private val PRETTY_JSON = Json { prettyPrint = true }
}
