package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.data.SecretRedactor
import com.msa.iotofflinetoolbox.core.model.HttpAuthType
import com.msa.iotofflinetoolbox.core.model.HttpBodyType
import com.msa.iotofflinetoolbox.core.model.HttpRequestInput
import com.msa.iotofflinetoolbox.core.model.HttpResponseResult
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlin.time.TimeSource

expect fun createPlatformHttpClient(): HttpClient

/**
 * Bounded, cross-platform HTTP workbench client.
 *
 * Automatic engine redirects remain disabled. Redirects are followed explicitly so every target is
 * validated against the cleartext policy and credentials cannot silently cross an origin boundary.
 */
class HttpToolClient(
    baseClient: HttpClient = createPlatformHttpClient(),
) {
    private val client = baseClient.config { install(HttpTimeout) }

    suspend fun execute(
        input: HttpRequestInput,
        maxResponseBytes: Int,
        allowPublicCleartext: Boolean = false,
    ): HttpResponseResult {
        require(maxResponseBytes in 1_024..1_048_576) {
            "Maximum response bytes must be between 1024 and 1048576"
        }
        HttpRequestValidator.requireValid(
            input = input,
            policy = HttpRequestValidationPolicy(allowPublicCleartext = allowPublicCleartext),
        )
        val normalizedUrl = validateTarget(input.url, allowPublicCleartext)
        val initialMethod = input.method.toHttpMethod()
        val parsedHeaders = HeaderParser.parse(input.headersText)
        val requestUrl = URLBuilder(normalizedUrl).apply {
            KeyValueLineParser.parse(input.queryText).forEach { (key, value) ->
                parameters.append(key, value)
            }
        }.buildString()
        val securityWarning = TransportSecurity.warningForUrl(requestUrl)
        val mark = TimeSource.Monotonic.markNow()
        var currentUrl = requestUrl
        var currentMethod = initialMethod
        var includeBody = true
        var redirectCount = 0

        return try {
            while (true) {
                val timeoutBudget = calculateHttpTimeoutBudget(
                    totalTimeoutMillis = input.timeoutMillis,
                    connectTimeoutMillis = input.connectTimeoutMillis,
                    socketTimeoutMillis = input.socketTimeoutMillis,
                    elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                )
                val response = client.request {
                    url(currentUrl)
                    method = currentMethod
                    headers {
                        parsedHeaders.forEach { (key, value) -> append(key, value) }
                        val cookies = KeyValueLineParser.parse(input.cookieText)
                        if (cookies.isNotEmpty()) {
                            append(HttpHeaders.Cookie, cookies.joinToString("; ") { (key, value) -> "$key=$value" })
                        }
                    }
                    when (input.authType) {
                        HttpAuthType.NONE -> Unit
                        HttpAuthType.BASIC -> basicAuth(input.authUsername, input.authPassword)
                        HttpAuthType.BEARER -> bearerAuth(input.bearerToken)
                        HttpAuthType.API_KEY -> headers { append(input.apiKeyName.trim(), input.apiKeyValue) }
                    }
                    if (includeBody) applyBody(input, currentMethod)
                    timeout {
                        requestTimeoutMillis = timeoutBudget.requestMillis
                        connectTimeoutMillis = timeoutBudget.connectMillis
                        socketTimeoutMillis = timeoutBudget.socketMillis
                    }
                }

                val location = response.headers[HttpHeaders.Location]
                val isRedirect = response.status.value in REDIRECT_STATUS_CODES && !location.isNullOrBlank()
                if (input.followRedirects && isRedirect) {
                    require(redirectCount < MAX_REDIRECTS) { "HTTP redirect limit exceeded ($MAX_REDIRECTS)" }
                    val nextUrl = validateTarget(resolveRedirect(currentUrl, requireNotNull(location)), allowPublicCleartext)
                    val rewriteToGet = shouldRewriteToGet(response.status.value, currentMethod)
                    val forwardsBody = includeBody && !rewriteToGet && input.bodyType != HttpBodyType.NONE && input.body.isNotBlank()
                    require(!crossesOriginWithSensitiveData(currentUrl, nextUrl, input, parsedHeaders, forwardsBody)) {
                        "Cross-origin redirect was blocked because the request would forward credentials, cookies or a request body"
                    }
                    response.bodyAsChannel().cancel(null)
                    redirectCount += 1
                    currentUrl = nextUrl
                    if (rewriteToGet) {
                        currentMethod = HttpMethod.Get
                        includeBody = false
                    }
                    continue
                }

                val channel = response.bodyAsChannel()
                val raw = try {
                    channel.readBuffer(maxResponseBytes + 1).readByteArray()
                } finally {
                    if (!channel.isClosedForRead) channel.cancel(null)
                }
                val bodyBytes = raw.copyOf(minOf(raw.size, maxResponseBytes))
                val contentType = response.headers[HttpHeaders.ContentType]
                val decodedBody = HttpResponseBodyTools.decode(bodyBytes, contentType)
                return HttpResponseResult(
                    statusCode = response.status.value,
                    statusText = response.status.description,
                    headers = SecretRedactor.redactHeaders(
                        response.headers.entries().associate { it.key to it.value.joinToString(", ") },
                    ),
                    body = decodedBody.text,
                    bodyBytes = bodyBytes,
                    bodyIsText = decodedBody.isText,
                    elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                    bytesReceived = bodyBytes.size,
                    truncated = raw.size > bodyBytes.size,
                    securityWarning = TransportSecurity.warningForUrl(currentUrl) ?: securityWarning,
                    requestMethod = initialMethod.value,
                    requestUrl = requestUrl,
                    finalUrl = currentUrl,
                    contentType = contentType,
                    redirected = redirectCount > 0,
                )
            }
            @Suppress("UNREACHABLE_CODE")
            error("Unreachable HTTP execution state")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Exception) {
            HttpResponseResult(
                statusCode = 0,
                statusText = "Request failed",
                headers = emptyMap(),
                body = "",
                elapsedMillis = mark.elapsedNow().inWholeMilliseconds,
                error = SecretRedactor.redact(
                    throwable.message ?: throwable::class.simpleName ?: "Unknown error",
                ),
                securityWarning = TransportSecurity.warningForUrl(currentUrl) ?: securityWarning,
                requestMethod = initialMethod.value,
                requestUrl = requestUrl,
                finalUrl = currentUrl,
                redirected = redirectCount > 0,
            )
        }
    }

    fun close() {
        client.close()
    }

    private fun validateTarget(rawUrl: String, allowPublicCleartext: Boolean): String {
        val normalized = TransportSecurity.requireScheme(
            rawUrl = rawUrl,
            allowedSchemes = setOf("http", "https"),
            label = "HTTP URL",
        )
        val url = Url(normalized)
        require(url.host.isNotBlank()) { "HTTP URL host cannot be empty" }
        require(url.protocol == URLProtocol.HTTP || url.protocol == URLProtocol.HTTPS) {
            "HTTP URL must use http:// or https://"
        }
        require(allowPublicCleartext || !TransportSecurity.isPublicCleartext(normalized)) {
            "Public cleartext HTTP is blocked by default"
        }
        return normalized
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyBody(
        input: HttpRequestInput,
        method: HttpMethod,
    ) {
        require(method != HttpMethod.Head || input.bodyType == HttpBodyType.NONE) {
            "HEAD requests cannot contain a request body"
        }
        when (input.bodyType) {
            HttpBodyType.NONE -> Unit
            HttpBodyType.RAW, HttpBodyType.JSON -> {
                val type = input.contentType.trim().ifBlank {
                    if (input.bodyType == HttpBodyType.JSON) ContentType.Application.Json.toString()
                    else ContentType.Text.Plain.toString()
                }
                setBody(TextContent(input.body, ContentType.parse(type)))
            }
            HttpBodyType.FORM_URLENCODED -> {
                val parameters = Parameters.build {
                    KeyValueLineParser.parse(input.body).forEach { (key, value) -> append(key, value) }
                }
                setBody(FormDataContent(parameters))
            }
        }
    }

    private fun shouldRewriteToGet(statusCode: Int, method: HttpMethod): Boolean = when (statusCode) {
        303 -> method != HttpMethod.Head
        301, 302 -> method == HttpMethod.Post
        else -> false
    }

    private fun crossesOriginWithSensitiveData(
        currentUrl: String,
        nextUrl: String,
        input: HttpRequestInput,
        headers: Map<String, String>,
        forwardsBody: Boolean,
    ): Boolean {
        if (originOf(currentUrl).equals(originOf(nextUrl), ignoreCase = true)) return false
        val sensitiveHeaders = headers.keys.any(SecretRedactor::isSensitiveHeaderName)
        return sensitiveHeaders || input.authType != HttpAuthType.NONE || input.cookieText.isNotBlank() || forwardsBody
    }

    private fun resolveRedirect(currentUrl: String, location: String): String {
        val target = location.trim()
        require(target.isNotEmpty()) { "Redirect Location header is empty" }
        if (target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)) {
            return target
        }
        val current = Url(currentUrl)
        val origin = originOf(currentUrl)
        return when {
            target.startsWith("//") -> "${current.protocol.name}:$target"
            target.startsWith("/") -> origin + target
            target.startsWith("?") -> currentUrl.substringBefore('?').substringBefore('#') + target
            target.startsWith("#") -> currentUrl.substringBefore('#') + target
            else -> {
                val currentPath = current.encodedPath.ifBlank { "/" }
                val directory = if (currentPath.endsWith('/')) {
                    currentPath
                } else {
                    currentPath.substringBeforeLast('/', missingDelimiterValue = "") + "/"
                }
                normalizeRelativeUrl(origin, directory + target)
            }
        }
    }

    private fun normalizeRelativeUrl(origin: String, pathAndSuffix: String): String {
        val suffixIndex = pathAndSuffix.indexOfFirst { it == '?' || it == '#' }
        val rawPath = if (suffixIndex >= 0) pathAndSuffix.substring(0, suffixIndex) else pathAndSuffix
        val suffix = if (suffixIndex >= 0) pathAndSuffix.substring(suffixIndex) else ""
        val segments = mutableListOf<String>()
        rawPath.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return "$origin/${segments.joinToString("/")}$suffix"
    }

    private fun originOf(rawUrl: String): String {
        val url = Url(rawUrl)
        val host = if (':' in url.host && !url.host.startsWith('[')) "[${url.host}]" else url.host
        val defaultPort = when (url.protocol) {
            URLProtocol.HTTP -> 80
            URLProtocol.HTTPS -> 443
            else -> -1
        }
        val port = if (url.port == defaultPort) "" else ":${url.port}"
        return "${url.protocol.name}://$host$port"
    }

    private companion object {
        const val MAX_REDIRECTS = 10
        val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    }
}

private fun String.toHttpMethod(): HttpMethod = when (trim().uppercase()) {
    "GET" -> HttpMethod.Get
    "POST" -> HttpMethod.Post
    "PUT" -> HttpMethod.Put
    "PATCH" -> HttpMethod.Patch
    "DELETE" -> HttpMethod.Delete
    "HEAD" -> HttpMethod.Head
    "OPTIONS" -> HttpMethod.Options
    else -> throw IllegalArgumentException("Unsupported HTTP method: $this")
}
