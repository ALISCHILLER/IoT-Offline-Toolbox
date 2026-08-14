#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/msa-iot-store-facade-compile"
KOTLIN_LIB="/root/.sdkman/candidates/kotlin/current/lib"
rm -rf "$WORK"
mkdir -p \
  "$WORK/com/msa/iotofflinetoolbox/core/model" \
  "$WORK/com/msa/iotofflinetoolbox/core/port" \
  "$WORK/com/msa/iotofflinetoolbox/core/data" \
  "$WORK/com/msa/iotofflinetoolbox/core/network" \
  "$WORK/com/msa/iotofflinetoolbox/core/policy" \
  "$WORK/com/msa/iotofflinetoolbox/core/payload" \
  "$WORK/com/msa/iotofflinetoolbox/core/security" \
  "$WORK/com/msa/iotofflinetoolbox/core/store" \
  "$WORK/kotlinx/serialization" \
  "$WORK/kotlinx/coroutines/flow"

cp "$ROOT"/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/model/*.kt "$WORK/com/msa/iotofflinetoolbox/core/model/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ToolboxComposition.kt" "$WORK/com/msa/iotofflinetoolbox/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/AppState.kt" "$WORK/com/msa/iotofflinetoolbox/core/store/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/AppFeedback.kt" "$WORK/com/msa/iotofflinetoolbox/core/store/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/ToolboxStore.kt" "$WORK/com/msa/iotofflinetoolbox/core/store/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/ToolboxActions.kt" "$WORK/com/msa/iotofflinetoolbox/core/store/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/ToolboxNavigationManager.kt" "$WORK/com/msa/iotofflinetoolbox/core/store/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/OperationCoordinator.kt" "$WORK/com/msa/iotofflinetoolbox/core/store/"
for component in \
  ToolboxOperationRunner.kt \
  ToolboxDiagnosticsOperations.kt \
  ToolboxProtocolOperations.kt \
  ToolboxPayloadOperations.kt; do
  cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/$component" "$WORK/com/msa/iotofflinetoolbox/core/store/"
done
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/ToolboxSettingsManager.kt" "$WORK/com/msa/iotofflinetoolbox/core/store/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/ToolboxStateLoader.kt" "$WORK/com/msa/iotofflinetoolbox/core/store/"
for component in \
  ToolboxInventoryContext.kt \
  ToolboxDeviceInventory.kt \
  ToolboxProfileInventory.kt \
  ToolboxTemplateInventory.kt; do
  cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/$component" "$WORK/com/msa/iotofflinetoolbox/core/store/"
done

cat > "$WORK/kotlinx/serialization/Serializable.kt" <<'KT'
package kotlinx.serialization

@Target(AnnotationTarget.CLASS)
annotation class Serializable
KT

cat > "$WORK/kotlinx/coroutines/flow/Update.kt" <<'KT'
package kotlinx.coroutines.flow

fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
KT

# Compile the real application ports rather than a duplicated synthetic copy.
# This makes the harness sensitive to contract drift between ToolboxStore/services and core/port.
cp "$ROOT"/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/port/*.kt \
  "$WORK/com/msa/iotofflinetoolbox/core/port/"


cat > "$WORK/com/msa/iotofflinetoolbox/core/data/ToolboxRepository.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.data

import com.msa.iotofflinetoolbox.core.model.*
import com.msa.iotofflinetoolbox.core.port.*

class ToolboxRepository(
    private val nowMillis: () -> Long = { 0L },
) : ToolboxPersistence {
    override fun drainRecoveryEvents(): List<StorageRecoveryEvent> = emptyList()
    override fun loadSettings(): AppSettings = error("compile stub")
    override fun saveSettings(value: AppSettings) = Unit
    override fun loadDevices(): List<SavedDevice> = emptyList()
    override fun saveDevices(value: List<SavedDevice>) = Unit
    override fun loadProfiles(): List<EndpointProfile> = emptyList()
    override fun saveProfiles(value: List<EndpointProfile>) = Unit
    override fun loadTemplates(): List<PayloadTemplate> = emptyList()
    override fun saveTemplates(value: List<PayloadTemplate>) = Unit
    override fun loadHistory(): List<HistoryEntry> = emptyList()
    override fun saveHistory(value: List<HistoryEntry>) = Unit
    override fun loadLogs(): List<ToolLog> = emptyList()
    override fun saveLogs(value: List<ToolLog>) = Unit
    override fun exportBackup(includeLogs: Boolean): String = "{}"
    override fun inspectBackup(raw: String): BackupInspection = error("compile stub")
    override fun importBackup(raw: String, mode: BackupImportMode): ImportSummary = error("compile stub")
}
KT

cat > "$WORK/com/msa/iotofflinetoolbox/core/network/AdapterStubs.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.network

import com.msa.iotofflinetoolbox.core.model.*
import com.msa.iotofflinetoolbox.core.port.*

fun createPlatformNetworkProbe(): NetworkProbe = error("compile stub")

class HttpToolClient : HttpOperationClient {
    override suspend fun execute(input: HttpRequestInput, maxResponseBytes: Int, allowPublicCleartext: Boolean): HttpResponseResult = error("compile stub")
    override fun close() = Unit
}

class WebSocketToolClient : WebSocketOperationClient {
    override suspend fun execute(input: WebSocketRequestInput, allowPublicCleartext: Boolean): WebSocketResult = error("compile stub")
    override fun close() = Unit
}

class MqttWebSocketClient : MqttOperationClient {
    override suspend fun execute(input: MqttWebSocketInput, allowPublicCleartext: Boolean): MqttResult = error("compile stub")
    override fun close() = Unit
}

class CoapClient(private val networkProbe: NetworkProbe) : CoapOperationClient {
    override suspend fun execute(input: CoapRequestInput, maxResponseBytes: Int): CoapResponseResult = error("compile stub")
}
KT

cat > "$WORK/com/msa/iotofflinetoolbox/core/security/SecretRedactor.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.security

data class SanitizedHeaders(val value: String, val removedNames: List<String>)
object SecretRedactor {
    fun redact(value: String): String = value
    fun removeSensitiveHeaders(value: String): SanitizedHeaders = SanitizedHeaders(value, emptyList())
}
KT

cat > "$WORK/com/msa/iotofflinetoolbox/core/store/ApplicationStubs.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.port.ActivityPersistence
import com.msa.iotofflinetoolbox.core.model.*
import com.msa.iotofflinetoolbox.core.policy.HttpRequestValidationIssue

fun interface ToolboxClock { fun nowMillis(): Long }
fun interface ToolboxIdGenerator { fun nextId(prefix: String, timestampMillis: Long): String }
data class ToolboxRuntime(
    val clock: ToolboxClock = ToolboxClock { 1L },
    val idGenerator: ToolboxIdGenerator = ToolboxIdGenerator { prefix, time -> "$prefix-$time" },
)

class ToolboxMessages(private val language: () -> AppLanguage) {
    fun text(english: String, persian: String): String = if (language() == AppLanguage.PERSIAN) persian else english
    fun source(source: String): String = source
    fun httpValidationIssue(issue: HttpRequestValidationIssue): String = issue.name
    fun localizeBackupInspection(raw: String, inspection: BackupInspection): BackupInspection = inspection
}

class ToolboxActivityJournal(
    persistence: ActivityPersistence,
    state: () -> AppState,
    updateState: (AppState.() -> AppState) -> Unit,
    messages: ToolboxMessages,
    runtime: ToolboxRuntime,
) {
    fun log(level: LogLevel, source: String, message: String) = Unit
    fun addHistory(protocol: HistoryProtocol, target: String, summary: String, successful: Boolean, request: String = "", response: String = "", durationMillis: Long? = null) = Unit
    fun trimLogs() = Unit
    fun trimHistory() = Unit
    fun clearLogs(): Boolean = true
    fun clearHistory(): Boolean = true
    fun persistenceFailure(source: String, operation: () -> Unit): String? = null
    fun reportPersistenceFailure(message: String) = Unit
}

KT

cat > "$WORK/com/msa/iotofflinetoolbox/core/policy/PolicyStubs.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.policy

import com.msa.iotofflinetoolbox.core.model.*

object CidrCalculator { fun calculate(cidr: String): SubnetResult = error("compile stub") }
object PortSpecParser { fun parse(value: String, maximumPorts: Int): List<Int> = emptyList() }
object DiscoveryEndpoints {
    const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
    const val MDNS_MULTICAST_ADDRESS = "224.0.0.251"
}
object HttpRequestPreview { fun curl(input: HttpRequestInput, redactSecrets: Boolean): String = "" }

enum class HttpRequestValidationIssue {
    INVALID_URL, PUBLIC_CLEARTEXT_BLOCKED, UNSUPPORTED_METHOD, INVALID_TIMEOUT,
    REDIRECT_UNSUPPORTED, INVALID_HEADERS, RESTRICTED_HEADERS, BROWSER_FORBIDDEN_HEADERS,
    COOKIE_UNSUPPORTED, DUPLICATE_COOKIE, DUPLICATE_CONTENT_TYPE, INVALID_QUERY,
    INVALID_COOKIES, INVALID_AUTH, INVALID_BODY, REQUEST_BODY_TOO_LARGE,
}

data class HttpRequestValidationPolicy(
    val allowPublicCleartext: Boolean = false,
    val manualRedirects: Boolean = true,
    val manualCookieHeader: Boolean = true,
    val browserManagedRequestHeaders: Boolean = false,
)

object HttpRequestValidator {
    fun firstIssue(input: HttpRequestInput, policy: HttpRequestValidationPolicy): HttpRequestValidationIssue? = null
}

object TransportSecurity {
    fun extractHost(url: String): String = ""
    fun isPublicCleartext(url: String): Boolean = false
}
KT

cat > "$WORK/com/msa/iotofflinetoolbox/core/payload/PayloadStubs.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.payload

import com.msa.iotofflinetoolbox.core.model.*

object PayloadCodec { fun decode(value: String, encoding: PayloadEncoding): ByteArray = byteArrayOf() }
object PayloadTools { fun transform(input: String, operation: PayloadOperation, variables: Map<String, String>): PayloadToolResult = error("compile stub") }
KT

kotlinc "$WORK" \
  -cp "$KOTLIN_LIB/kotlinx-coroutines-core-jvm.jar" \
  -d "$WORK/store-facade.jar"

echo STORE_FACADE_COMPILE_HARNESS_PASSED
