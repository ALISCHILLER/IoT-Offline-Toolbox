package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.HttpAuthType
import com.msa.iotofflinetoolbox.core.model.HttpBodyType
import com.msa.iotofflinetoolbox.core.model.HttpRequestInput
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpRequestToolsTest {
    @Test
    fun keyValueParserSupportsCommentsAndEmptyValues() {
        assertEquals(
            listOf("page" to "1", "filter" to ""),
            KeyValueLineParser.parse("# query\npage=1\nfilter="),
        )
    }

    @Test
    fun malformedKeyValueLineIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            KeyValueLineParser.parse("missing-separator")
        }
    }

    @Test
    fun curlPreviewEncodesQueryAndRedactsCredentials() {
        val preview = HttpRequestPreview.curl(
            HttpRequestInput(
                method = "POST",
                url = "https://example.test/devices",
                queryText = "name=sensor room",
                authType = HttpAuthType.BEARER,
                bearerToken = "very-secret",
                bodyType = HttpBodyType.JSON,
                body = "{\"enabled\":true}",
            ),
        )
        assertContains(preview, "name=sensor%20room")
        assertContains(preview, "Authorization: Bearer <REDACTED>")
        assertContains(preview, "--data-raw")
    }

    @Test
    fun jsonResponseCanBePrettyPrinted() {
        val pretty = HttpRequestPreview.prettyBody("{\"enabled\":true}", "application/json")
        assertContains(pretty, "\n")
        assertContains(pretty, "\"enabled\"")
    }
    @Test
    fun curlPreviewRedactsSensitiveQueryFormAndJsonValues() {
        val queryPreview = HttpRequestPreview.curl(
            HttpRequestInput(
                url = "https://example.test/status",
                queryText = "api_key=query-secret",
                bodyType = HttpBodyType.FORM_URLENCODED,
                body = "password=form-secret\nname=sensor",
            ),
        )
        assertContains(queryPreview, "api_key=%3CREDACTED%3E")
        assertContains(queryPreview, "password=<REDACTED>")
        check("query-secret" !in queryPreview && "form-secret" !in queryPreview)

        val jsonPreview = HttpRequestPreview.curl(
            HttpRequestInput(
                method = "POST",
                url = "https://example.test/status",
                bodyType = HttpBodyType.JSON,
                body = "{\"token\":\"json-secret\",\"enabled\":true}",
            ),
        )
        check("json-secret" !in jsonPreview)
        assertContains(jsonPreview, "***")
    }

    @Test
    fun curlPreviewUsesUtf8PercentEncoding() {
        val preview = HttpRequestPreview.curl(
            HttpRequestInput(
                url = "https://example.test/status",
                queryText = "name=حسگر",
            ),
        )
        assertContains(preview, "%D8%AD%D8%B3%DA%AF%D8%B1")
    }

    @Test
    fun curlPreviewEmitsIndependentTimeoutOptionsExactlyOnce() {
        val preview = HttpRequestPreview.curl(
            HttpRequestInput(
                url = "https://example.test/status",
                timeoutMillis = 12_500,
                connectTimeoutMillis = 2_500,
                socketTimeoutMillis = 7_500,
            ),
        )
        assertContains(preview, "--connect-timeout 2.5")
        assertContains(preview, "--max-time 12.5")
        assertEquals(1, Regex("--max-time").findAll(preview).count())
    }

    @Test
    fun browserForbiddenHeaderClassifierCoversNamesAndPrefixes() {
        assertEquals(true, isBrowserForbiddenRequestHeader("Host"))
        assertEquals(true, isBrowserForbiddenRequestHeader("Sec-Fetch-Site"))
        assertEquals(true, isBrowserForbiddenRequestHeader("Proxy-Authorization"))
        assertEquals(false, isBrowserForbiddenRequestHeader("X-Trace-Id"))
    }

    @Test
    fun curlPreviewPlacesQueryBeforeFragment() {
        val preview = HttpRequestPreview.curl(
            HttpRequestInput(
                url = "https://example.test/status#details",
                queryText = "name=sensor room",
            ),
        )
        assertContains(preview, "https://example.test/status?name=sensor%20room#details")
    }

    @Test
    fun powershellCurlPreviewUsesNativeExecutableAndSafeQuoting() {
        val preview = HttpRequestPreview.curl(
            input = HttpRequestInput(
                method = "POST",
                url = "https://example.test/devices",
                bodyType = HttpBodyType.RAW,
                contentType = "text/plain",
                body = "sensor's payload",
            ),
            redactSecrets = false,
            shell = CurlShell.POWERSHELL,
        )
        assertTrue(preview.startsWith("curl.exe --request POST"))
        assertContains(preview, " `\n")
        assertContains(preview, "sensor''s payload")
    }

    @Test
    fun centralizedValidatorAppliesSecurityAndPlatformPolicies() {
        assertEquals(
            HttpRequestValidationIssue.PUBLIC_CLEARTEXT_BLOCKED,
            HttpRequestValidator.firstIssue(HttpRequestInput(url = "http://example.com/status")),
        )
        assertNull(HttpRequestValidator.firstIssue(HttpRequestInput(url = "http://192.168.1.20/status")))
        assertEquals(
            HttpRequestValidationIssue.UNSUPPORTED_METHOD,
            HttpRequestValidator.firstIssue(HttpRequestInput(method = "TRACE", url = "https://example.test/status")),
        )
        assertEquals(
            HttpRequestValidationIssue.DUPLICATE_CONTENT_TYPE,
            HttpRequestValidator.firstIssue(
                HttpRequestInput(
                    method = "POST",
                    url = "https://example.test/status",
                    headersText = "Content-Type: application/json",
                    bodyType = HttpBodyType.JSON,
                    body = "{}",
                ),
            ),
        )
        assertEquals(
            HttpRequestValidationIssue.COOKIE_UNSUPPORTED,
            HttpRequestValidator.firstIssue(
                HttpRequestInput(url = "https://example.test/status", cookieText = "session=value"),
                HttpRequestValidationPolicy(manualCookieHeader = false),
            ),
        )
        assertEquals(
            HttpRequestValidationIssue.INVALID_AUTH,
            HttpRequestValidator.firstIssue(
                HttpRequestInput(
                    url = "https://example.test/status",
                    authType = HttpAuthType.API_KEY,
                    apiKeyName = "Host",
                    apiKeyValue = "attacker.test",
                ),
            ),
        )
        assertEquals(
            HttpRequestValidationIssue.INVALID_AUTH,
            HttpRequestValidator.firstIssue(
                HttpRequestInput(
                    method = "POST",
                    url = "https://example.test/status",
                    authType = HttpAuthType.API_KEY,
                    apiKeyName = "Content-Type",
                    apiKeyValue = "not-an-api-key-header",
                    bodyType = HttpBodyType.JSON,
                    body = "{}",
                ),
            ),
        )
    }

    @Test
    fun centralizedValidatorRejectsOversizedBodyBeforeTransport() {
        val issue = HttpRequestValidator.firstIssue(
            HttpRequestInput(
                method = "POST",
                url = "https://example.test/upload",
                bodyType = HttpBodyType.RAW,
                contentType = "application/octet-stream",
                body = "x".repeat(HttpRequestValidator.MAX_REQUEST_BODY_BYTES + 1),
            ),
        )
        assertEquals(HttpRequestValidationIssue.REQUEST_BODY_TOO_LARGE, issue)
    }

}
