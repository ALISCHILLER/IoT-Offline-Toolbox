package com.msa.iotofflinetoolbox.core.security

import com.msa.iotofflinetoolbox.core.security.SecretRedactor
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecretRedactorTest {
    @Test
    fun redactsBearerAuthorizationWithoutLeakingToken() {
        val redacted = SecretRedactor.redact("Authorization: Bearer very-secret-token")
        assertTrue(redacted.contains("Authorization: ***"))
        assertFalse(redacted.contains("very-secret-token"))
    }

    @Test
    fun redactsQuotedJsonAndQueryParameters() {
        val redacted = SecretRedactor.redact("{\"password\":\"p@ss\",\"apiKey\":\"abc\"} https://host/path?token=query-secret")
        assertFalse(redacted.contains("p@ss"))
        assertFalse(redacted.contains("abc"))
        assertFalse(redacted.contains("query-secret"))
    }

    @Test
    fun redactsUrlUserInfo() {
        val redacted = SecretRedactor.redact("mqtt://operator:broker-password@example.local")
        assertFalse(redacted.contains("broker-password"))
        assertTrue(redacted.contains("operator:***@"))
    }
    @Test
    fun removesSensitiveHeadersFromReusableProfiles() {
        val sanitized = SecretRedactor.removeSensitiveHeaders(
            "Accept: application/json\nAuthorization: Bearer secret\nX-Api-Key: key\nX-Device: lab",
        )
        assertEquals("Accept: application/json\nX-Device: lab", sanitized.value)
        assertEquals(setOf("authorization", "x-api-key"), sanitized.removedNames.map(String::lowercase).toSet())
        assertFalse(sanitized.value.contains("secret"))
        assertFalse(sanitized.value.contains("key"))
    }

    @Test
    fun redactsSensitiveResponseHeadersAndValues() {
        val headers = SecretRedactor.redactHeaders(
            mapOf(
                "Set-Cookie" to "session=secret-cookie",
                "X-Api-Key" to "secret-key",
                "Location" to "https://user:password@example.test/path?token=query-secret",
            ),
        )
        assertEquals("***", headers["Set-Cookie"])
        assertEquals("***", headers["X-Api-Key"])
        assertFalse(headers.getValue("Location").contains("password"))
        assertFalse(headers.getValue("Location").contains("query-secret"))
    }

    @Test
    fun recognizesAndRemovesCustomSensitiveHeaderNames() {
        val sanitized = SecretRedactor.removeSensitiveHeaders(
            "X-Client-Secret: alpha\nX-Custom-Token: beta\nX-Trace-Id: safe",
        )
        assertEquals("X-Trace-Id: safe", sanitized.value)
        assertEquals(setOf("x-client-secret", "x-custom-token"), sanitized.removedNames.map(String::lowercase).toSet())
    }

    @Test
    fun genericHistoryRedactionMasksCustomSensitiveHeaderLines() {
        val redacted = SecretRedactor.redact("X-Client-Secret: alpha\nX-Custom-Token: beta")
        assertFalse(redacted.contains("alpha"))
        assertFalse(redacted.contains("beta"))
        assertTrue(redacted.contains("X-Client-Secret: ***"))
        assertTrue(redacted.contains("X-Custom-Token: ***"))
    }

    @Test
    fun redactsPrefixedSensitiveFieldNamesAcrossStructuredText() {
        val redacted = SecretRedactor.redact(
            "{\"x-client-secret\":\"json-value\"} " +
                "https://host/path?x-custom-token=query-value " +
                "x-service-password=form-value --x-api-key=cli-value",
        )
        assertFalse(redacted.contains("json-value"))
        assertFalse(redacted.contains("query-value"))
        assertFalse(redacted.contains("form-value"))
        assertFalse(redacted.contains("cli-value"))
        assertTrue(SecretRedactor.isSensitiveFieldName("x-client-secret"))
        assertTrue(SecretRedactor.isSensitiveFieldName("custom_refresh_token_value"))
    }

}
