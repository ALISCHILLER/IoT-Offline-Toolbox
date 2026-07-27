package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.HttpAuthType
import com.msa.iotofflinetoolbox.core.model.HttpBodyType
import com.msa.iotofflinetoolbox.core.model.HttpRequestInput
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpToolClientTest {
    @Test
    fun secureRequestUsesMockEngineAndReturnsBoundedResult() = runTest {
        val engine = MockEngine {
            respond(
                content = "response-body",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            val result = client.execute(
                input = HttpRequestInput(url = "https://example.test/status"),
                maxResponseBytes = 1_024,
            )
            assertEquals(200, result.statusCode)
            assertEquals("response-body", result.body)
            assertNull(result.securityWarning)
            assertNull(result.error)
        } finally {
            client.close()
        }
    }

    @Test
    fun publicCleartextIsBlockedBeforeNetworkExecution() = runTest {
        var executed = false
        val engine = MockEngine {
            executed = true
            respond("unexpected")
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            assertFailsWith<IllegalArgumentException> {
                client.execute(
                    input = HttpRequestInput(url = "http://example.com/status"),
                    maxResponseBytes = 1_024,
                )
            }
            assertTrue(!executed)
        } finally {
            client.close()
        }
    }

    @Test
    fun explicitPublicCleartextOverrideRemainsVisibleInResult() = runTest {
        val engine = MockEngine { respond("ok") }
        val client = HttpToolClient(HttpClient(engine))
        try {
            val result = client.execute(
                input = HttpRequestInput(url = "http://example.com/status"),
                maxResponseBytes = 1_024,
                allowPublicCleartext = true,
            )
            assertEquals(200, result.statusCode)
            assertTrue(result.securityWarning?.contains("non-local") == true)
        } finally {
            client.close()
        }
    }
    @Test
    fun oversizedRequestBodyIsRejectedBeforeNetworkExecution() = runTest {
        var executed = false
        val engine = MockEngine {
            executed = true
            respond("unexpected")
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            assertFailsWith<IllegalArgumentException> {
                client.execute(
                    input = HttpRequestInput(
                        url = "https://example.test/upload",
                        method = "POST",
                        body = "x".repeat(1_048_577),
                    ),
                    maxResponseBytes = 1_024,
                )
            }
            assertTrue(!executed)
        } finally {
            client.close()
        }
    }

    @Test
    fun sensitiveResponseHeadersAreRedactedBeforeEnteringState() = runTest {
        val engine = MockEngine {
            respond(
                content = "ok",
                status = HttpStatusCode.OK,
                headers = headersOf("Set-Cookie", "session=secret-cookie"),
            )
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            val result = client.execute(HttpRequestInput(url = "https://example.test/status"), 1_024)
            assertEquals("***", result.headers["Set-Cookie"])
        } finally {
            client.close()
        }
    }

    @Test
    fun queryHeadersAndJsonBodyAreApplied() = runTest {
        var capturedUrl = ""
        var capturedAuthorization = ""
        var capturedBody = ""
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            capturedAuthorization = request.headers[HttpHeaders.Authorization].orEmpty()
            capturedBody = (request.body as TextContent).text
            respond("ok", status = HttpStatusCode.Created)
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            val result = client.execute(
                input = HttpRequestInput(
                    method = "POST",
                    url = "https://example.test/devices",
                    queryText = "site=plant one",
                    authType = HttpAuthType.BEARER,
                    bearerToken = "token-value",
                    bodyType = HttpBodyType.JSON,
                    body = "{\"enabled\":true}",
                ),
                maxResponseBytes = 1_024,
            )
            assertEquals(201, result.statusCode)
            assertTrue(
                "site=plant+one" in capturedUrl || "site=plant%20one" in capturedUrl,
                "Expected encoded query parameter in $capturedUrl",
            )
            assertEquals("Bearer token-value", capturedAuthorization)
            assertEquals("{\"enabled\":true}", capturedBody)
        } finally {
            client.close()
        }
    }

    @Test
    fun safeSameOriginRedirectIsFollowedManually() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls += 1
            if (calls == 1) {
                respond(
                    content = "redirect",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "/v2/status"),
                )
            } else {
                assertEquals("/v2/status", request.url.encodedPath)
                respond("done", status = HttpStatusCode.OK)
            }
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            val result = client.execute(
                input = HttpRequestInput(
                    url = "https://example.test/v1/status",
                    followRedirects = true,
                ),
                maxResponseBytes = 1_024,
            )
            assertEquals(2, calls)
            assertEquals("done", result.body)
            assertTrue(result.redirected)
            assertEquals("https://example.test/v2/status", result.finalUrl)
        } finally {
            client.close()
        }
    }

    @Test
    fun crossOriginRedirectWithCredentialsIsBlocked() = runTest {
        val engine = MockEngine {
            respond(
                content = "redirect",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "https://other.test/status"),
            )
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            val result = client.execute(
                input = HttpRequestInput(
                    url = "https://example.test/status",
                    followRedirects = true,
                    authType = HttpAuthType.BEARER,
                    bearerToken = "token-value",
                ),
                maxResponseBytes = 1_024,
            )
            assertContains(result.error.orEmpty(), "Cross-origin redirect")
        } finally {
            client.close()
        }
    }

    @Test
    fun embeddedUrlCredentialsAreRejectedBeforeExecution() = runTest {
        var executed = false
        val client = HttpToolClient(HttpClient(MockEngine {
            executed = true
            respond("unexpected")
        }))
        try {
            assertFailsWith<IllegalArgumentException> {
                client.execute(HttpRequestInput(url = "https://user:password@example.test/status"), 1_024)
            }
            assertTrue(!executed)
        } finally {
            client.close()
        }
    }

    @Test
    fun engineManagedHeadersAreRejectedBeforeExecution() = runTest {
        var executed = false
        val client = HttpToolClient(HttpClient(MockEngine {
            executed = true
            respond("unexpected")
        }))
        try {
            assertFailsWith<IllegalArgumentException> {
                client.execute(
                    HttpRequestInput(
                        url = "https://example.test/status",
                        headersText = "Host: attacker.test",
                    ),
                    1_024,
                )
            }
            assertTrue(!executed)
        } finally {
            client.close()
        }
    }

    @Test
    fun postOn302RedirectIsRewrittenToGetWithoutBody() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls += 1
            if (calls == 1) {
                assertEquals(HttpMethod.Post, request.method)
                respond(
                    content = "redirect",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "/complete"),
                )
            } else {
                assertEquals(HttpMethod.Get, request.method)
                respond("done")
            }
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            val result = client.execute(
                HttpRequestInput(
                    method = "POST",
                    url = "https://example.test/start",
                    followRedirects = true,
                    bodyType = HttpBodyType.JSON,
                    body = "{\"start\":true}",
                ),
                1_024,
            )
            assertEquals(2, calls)
            assertEquals("done", result.body)
        } finally {
            client.close()
        }
    }

    @Test
    fun relativeRedirectFromOriginRootResolvesAgainstSlash() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls += 1
            if (calls == 1) {
                respond(
                    content = "redirect",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "next"),
                )
            } else {
                assertEquals("/next", request.url.encodedPath)
                respond("done")
            }
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            val result = client.execute(
                HttpRequestInput(url = "https://example.test", followRedirects = true),
                1_024,
            )
            assertEquals("https://example.test/next", result.finalUrl)
        } finally {
            client.close()
        }
    }

    @Test
    fun apiKeyCannotOverrideEngineManagedHeader() = runTest {
        var executed = false
        val client = HttpToolClient(HttpClient(MockEngine {
            executed = true
            respond("unexpected")
        }))
        try {
            assertFailsWith<IllegalArgumentException> {
                client.execute(
                    HttpRequestInput(
                        url = "https://example.test/status",
                        authType = HttpAuthType.API_KEY,
                        apiKeyName = "Host",
                        apiKeyValue = "attacker.test",
                    ),
                    1_024,
                )
            }
            assertTrue(!executed)
        } finally {
            client.close()
        }
    }

    @Test
    fun manualContentTypeCannotConflictWithBodyEditor() = runTest {
        var executed = false
        val client = HttpToolClient(HttpClient(MockEngine {
            executed = true
            respond("unexpected")
        }))
        try {
            assertFailsWith<IllegalArgumentException> {
                client.execute(
                    HttpRequestInput(
                        method = "POST",
                        url = "https://example.test/status",
                        headersText = "Content-Type: text/plain",
                        bodyType = HttpBodyType.JSON,
                        contentType = "application/json",
                        body = "{}",
                    ),
                    1_024,
                )
            }
            assertTrue(!executed)
        } finally {
            client.close()
        }
    }

    @Test
    fun redirectTimeoutBudgetUsesOnlyRemainingTotalTime() {
        val budget = calculateHttpTimeoutBudget(
            totalTimeoutMillis = 1_000,
            connectTimeoutMillis = 800,
            socketTimeoutMillis = 600,
            elapsedMillis = 750,
        )
        assertEquals(250, budget.requestMillis)
        assertEquals(250, budget.connectMillis)
        assertEquals(250, budget.socketMillis)
        assertFailsWith<IllegalArgumentException> {
            calculateHttpTimeoutBudget(1_000, 800, 600, 1_000)
        }
    }

    @Test
    fun crossOrigin307RedirectWithRequestBodyIsBlocked() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls += 1
            respond(
                content = "redirect",
                status = HttpStatusCode.TemporaryRedirect,
                headers = headersOf(HttpHeaders.Location, "https://other.test/upload"),
            )
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            val result = client.execute(
                HttpRequestInput(
                    method = "POST",
                    url = "https://example.test/upload",
                    followRedirects = true,
                    bodyType = HttpBodyType.JSON,
                    body = "{\"password\":\"secret\"}",
                ),
                1_024,
            )
            assertEquals(1, calls)
            assertContains(result.error.orEmpty(), "Cross-origin redirect")
            assertTrue(!result.error.orEmpty().contains("secret"))
        } finally {
            client.close()
        }
    }

    @Test
    fun crossOrigin303RedirectMayContinueAsGetWithoutBody() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls += 1
            if (calls == 1) {
                respond(
                    content = "redirect",
                    status = HttpStatusCode.SeeOther,
                    headers = headersOf(HttpHeaders.Location, "https://other.test/complete"),
                )
            } else {
                assertEquals(HttpMethod.Get, request.method)
                respond("done")
            }
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            val result = client.execute(
                HttpRequestInput(
                    method = "POST",
                    url = "https://example.test/start",
                    followRedirects = true,
                    bodyType = HttpBodyType.JSON,
                    body = "{\"value\":true}",
                ),
                1_024,
            )
            assertEquals(2, calls)
            assertEquals("done", result.body)
            assertNull(result.error)
        } finally {
            client.close()
        }
    }

    @Test
    fun failureAfterRedirectReportsTheActualFailingTarget() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls += 1
            if (calls == 1) {
                respond(
                    content = "redirect",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "/next"),
                )
            } else {
                error("downstream failure")
            }
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            val result = client.execute(
                HttpRequestInput(
                    method = "GET",
                    url = "https://example.test/start",
                    followRedirects = true,
                ),
                1_024,
            )
            assertEquals(2, calls)
            assertEquals("https://example.test/start", result.requestUrl)
            assertEquals("https://example.test/next", result.finalUrl)
            assertTrue(result.redirected)
            assertContains(result.error.orEmpty(), "downstream failure")
        } finally {
            client.close()
        }
    }

    @Test
    fun binaryResponseRetainsExactBoundedBytesWithoutUnsafeTextRendering() = runTest {
        val payload = byteArrayOf(0x00, 0xFF.toByte(), 0x80.toByte(), 0x01)
        val engine = MockEngine {
            respond(
                content = payload,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"),
            )
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            val result = client.execute(HttpRequestInput(url = "https://example.test/firmware.bin"), 1_024)
            assertFalse(result.bodyIsText)
            assertEquals("", result.body)
            assertContentEquals(payload, result.bodyBytes)
            assertEquals(payload.size, result.bytesReceived)
        } finally {
            client.close()
        }
    }

    @Test
    fun textualResponseAlsoRetainsExactBytesForHexInspection() = runTest {
        val payload = "temperature=21.4".encodeToByteArray()
        val engine = MockEngine {
            respond(
                content = payload,
                headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=utf-8"),
            )
        }
        val client = HttpToolClient(HttpClient(engine))
        try {
            val result = client.execute(HttpRequestInput(url = "https://example.test/value"), 1_024)
            assertTrue(result.bodyIsText)
            assertEquals("temperature=21.4", result.body)
            assertContentEquals(payload, result.bodyBytes)
        } finally {
            client.close()
        }
    }

}
