package com.msa.iotofflinetoolbox.core.policy

import com.msa.iotofflinetoolbox.core.model.HttpAuthType
import com.msa.iotofflinetoolbox.core.model.HttpBodyType
import com.msa.iotofflinetoolbox.core.model.HttpRequestInput
import kotlinx.serialization.json.Json

/** Platform policy used to keep request validation identical in UI, store and transport layers. */
data class HttpRequestValidationPolicy(
    val allowPublicCleartext: Boolean = false,
    val manualRedirects: Boolean = true,
    val manualCookieHeader: Boolean = true,
    val browserManagedRequestHeaders: Boolean = false,
)

enum class HttpRequestValidationIssue {
    INVALID_URL,
    PUBLIC_CLEARTEXT_BLOCKED,
    UNSUPPORTED_METHOD,
    INVALID_TIMEOUT,
    REDIRECT_UNSUPPORTED,
    INVALID_HEADERS,
    RESTRICTED_HEADERS,
    BROWSER_FORBIDDEN_HEADERS,
    COOKIE_UNSUPPORTED,
    DUPLICATE_COOKIE,
    DUPLICATE_CONTENT_TYPE,
    INVALID_QUERY,
    INVALID_COOKIES,
    INVALID_AUTH,
    INVALID_BODY,
    REQUEST_BODY_TOO_LARGE,
}

object HttpRequestValidator {
    const val MAX_REQUEST_BODY_BYTES: Int = 1_048_576

    private val supportedMethods = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
    private val restrictedRequestHeaders = setOf("host", "content-length", "transfer-encoding", "connection")
    private val headerName = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

    fun firstIssue(
        input: HttpRequestInput,
        policy: HttpRequestValidationPolicy = HttpRequestValidationPolicy(),
    ): HttpRequestValidationIssue? {
        val normalizedUrl = runCatching {
            TransportSecurity.requireScheme(
                rawUrl = input.url,
                allowedSchemes = setOf("http", "https"),
                label = "HTTP URL",
            )
        }.getOrElse { return HttpRequestValidationIssue.INVALID_URL }

        if (!policy.allowPublicCleartext && TransportSecurity.isPublicCleartext(normalizedUrl)) {
            return HttpRequestValidationIssue.PUBLIC_CLEARTEXT_BLOCKED
        }
        if (input.method.trim().uppercase() !in supportedMethods) {
            return HttpRequestValidationIssue.UNSUPPORTED_METHOD
        }
        if (input.timeoutMillis !in 100..120_000 ||
            input.connectTimeoutMillis !in 100..120_000 ||
            input.socketTimeoutMillis !in 100..120_000
        ) {
            return HttpRequestValidationIssue.INVALID_TIMEOUT
        }
        if (input.followRedirects && !policy.manualRedirects) {
            return HttpRequestValidationIssue.REDIRECT_UNSUPPORTED
        }
        if (input.body.encodeToByteArray().size > MAX_REQUEST_BODY_BYTES) {
            return HttpRequestValidationIssue.REQUEST_BODY_TOO_LARGE
        }

        val headers = runCatching { HeaderParser.parse(input.headersText) }
            .getOrElse { return HttpRequestValidationIssue.INVALID_HEADERS }
        val normalizedHeaders = headers.keys.map { it.lowercase() }.toSet()
        if (normalizedHeaders.any { it in restrictedRequestHeaders }) {
            return HttpRequestValidationIssue.RESTRICTED_HEADERS
        }
        if (policy.browserManagedRequestHeaders && normalizedHeaders.any(::isBrowserForbiddenRequestHeader)) {
            return HttpRequestValidationIssue.BROWSER_FORBIDDEN_HEADERS
        }

        val manualCookieRequested = input.cookieText.isNotBlank() || "cookie" in normalizedHeaders
        if (manualCookieRequested && !policy.manualCookieHeader) {
            return HttpRequestValidationIssue.COOKIE_UNSUPPORTED
        }
        if (input.cookieText.isNotBlank() && "cookie" in normalizedHeaders) {
            return HttpRequestValidationIssue.DUPLICATE_COOKIE
        }
        if (input.bodyType != HttpBodyType.NONE && "content-type" in normalizedHeaders) {
            return HttpRequestValidationIssue.DUPLICATE_CONTENT_TYPE
        }

        if (runCatching { KeyValueLineParser.parse(input.queryText) }.isFailure) {
            return HttpRequestValidationIssue.INVALID_QUERY
        }
        if (runCatching { KeyValueLineParser.parse(input.cookieText) }.isFailure) {
            return HttpRequestValidationIssue.INVALID_COOKIES
        }

        val hasAuthorization = "authorization" in normalizedHeaders
        when (input.authType) {
            HttpAuthType.NONE -> Unit
            HttpAuthType.BASIC -> {
                if (hasAuthorization || input.authUsername.isBlank()) return HttpRequestValidationIssue.INVALID_AUTH
            }
            HttpAuthType.BEARER -> {
                if (hasAuthorization || input.bearerToken.isBlank()) return HttpRequestValidationIssue.INVALID_AUTH
            }
            HttpAuthType.API_KEY -> {
                val name = input.apiKeyName.trim()
                if (!headerName.matches(name) ||
                    name.lowercase() in restrictedRequestHeaders ||
                    input.apiKeyValue.isBlank() ||
                    normalizedHeaders.any { it.equals(name, ignoreCase = true) } ||
                    (name.equals("content-type", ignoreCase = true) && input.bodyType != HttpBodyType.NONE) ||
                    (name.equals("cookie", ignoreCase = true) && input.cookieText.isNotBlank()) ||
                    (policy.browserManagedRequestHeaders && isBrowserForbiddenRequestHeader(name))
                ) {
                    return HttpRequestValidationIssue.INVALID_AUTH
                }
            }
        }

        if (input.method.trim().equals("HEAD", ignoreCase = true) && input.bodyType != HttpBodyType.NONE) {
            return HttpRequestValidationIssue.INVALID_BODY
        }
        when (input.bodyType) {
            HttpBodyType.NONE -> Unit
            HttpBodyType.RAW -> if (!isValidContentType(input.contentType)) {
                return HttpRequestValidationIssue.INVALID_BODY
            }
            HttpBodyType.JSON -> {
                if (!isValidContentType(input.contentType) ||
                    runCatching { Json.parseToJsonElement(input.body) }.isFailure
                ) {
                    return HttpRequestValidationIssue.INVALID_BODY
                }
            }
            HttpBodyType.FORM_URLENCODED -> if (runCatching { KeyValueLineParser.parse(input.body) }.isFailure) {
                return HttpRequestValidationIssue.INVALID_BODY
            }
        }
        return null
    }

    fun requireValid(
        input: HttpRequestInput,
        policy: HttpRequestValidationPolicy = HttpRequestValidationPolicy(),
    ) {
        val issue = firstIssue(input, policy) ?: return
        throw IllegalArgumentException(issue.englishMessage())
    }

    private fun isValidContentType(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > 1_024 || '\r' in trimmed || '\n' in trimmed) return false
        val segments = trimmed.split(';')
        val mediaType = segments.first().trim()
        val slash = mediaType.indexOf('/')
        if (slash <= 0 || slash != mediaType.lastIndexOf('/') || slash == mediaType.lastIndex) return false
        if (!isHttpToken(mediaType.substring(0, slash)) || !isHttpToken(mediaType.substring(slash + 1))) return false
        return segments.drop(1).all { rawParameter ->
            val parameter = rawParameter.trim()
            val equals = parameter.indexOf('=')
            if (equals <= 0 || equals == parameter.lastIndex) return@all false
            val name = parameter.substring(0, equals).trim()
            val rawValue = parameter.substring(equals + 1).trim()
            isHttpToken(name) && (isHttpToken(rawValue) || isQuotedParameter(rawValue))
        }
    }

    private fun isHttpToken(value: String): Boolean =
        value.isNotEmpty() && value.all { it.isLetterOrDigit() || it in "!#$%&'*+-.^_`|~" }

    private fun isQuotedParameter(value: String): Boolean =
        value.length >= 2 && value.first() == '"' && value.last() == '"' &&
            value.substring(1, value.lastIndex).none { it == '\r' || it == '\n' || it == '\u0000' }
}

fun HttpRequestValidationIssue.englishMessage(): String = when (this) {
    HttpRequestValidationIssue.INVALID_URL -> "HTTP URL is invalid or contains embedded credentials"
    HttpRequestValidationIssue.PUBLIC_CLEARTEXT_BLOCKED -> "Public cleartext HTTP is blocked by default"
    HttpRequestValidationIssue.UNSUPPORTED_METHOD -> "HTTP method is not supported"
    HttpRequestValidationIssue.INVALID_TIMEOUT -> "HTTP timeout values must be between 100 and 120000 ms"
    HttpRequestValidationIssue.REDIRECT_UNSUPPORTED -> "Manual redirect inspection is unavailable on this platform"
    HttpRequestValidationIssue.INVALID_HEADERS -> "HTTP header block is invalid"
    HttpRequestValidationIssue.RESTRICTED_HEADERS -> "One or more HTTP headers are managed by the engine"
    HttpRequestValidationIssue.BROWSER_FORBIDDEN_HEADERS -> "One or more HTTP headers are controlled by the browser"
    HttpRequestValidationIssue.COOKIE_UNSUPPORTED -> "This platform cannot set the Cookie header manually"
    HttpRequestValidationIssue.DUPLICATE_COOKIE -> "Use either the Cookie editor or a manual Cookie header, not both"
    HttpRequestValidationIssue.DUPLICATE_CONTENT_TYPE -> "Use the Content Type field instead of a manual Content-Type header"
    HttpRequestValidationIssue.INVALID_QUERY -> "HTTP query parameters are invalid"
    HttpRequestValidationIssue.INVALID_COOKIES -> "HTTP cookie entries are invalid"
    HttpRequestValidationIssue.INVALID_AUTH -> "HTTP authentication settings are invalid"
    HttpRequestValidationIssue.INVALID_BODY -> "HTTP request body or content type is invalid"
    HttpRequestValidationIssue.REQUEST_BODY_TOO_LARGE -> "HTTP request body exceeds the 1 MiB safety limit"
}
