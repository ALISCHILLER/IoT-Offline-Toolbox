#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/msa-iot-application-architecture-harness"
rm -rf "$WORK"
mkdir -p \
  "$WORK/com/msa/iotofflinetoolbox/core/model" \
  "$WORK/com/msa/iotofflinetoolbox/core/port" \
  "$WORK/com/msa/iotofflinetoolbox/core/network" \
  "$WORK/com/msa/iotofflinetoolbox/core/policy" \
  "$WORK/com/msa/iotofflinetoolbox/core/security" \
  "$WORK/com/msa/iotofflinetoolbox/core/store"

cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/security/SecretRedactor.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/security/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/ToolboxMessages.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/store/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/AppFeedback.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/store/"
cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/ToolboxActivityJournal.kt" \
  "$WORK/com/msa/iotofflinetoolbox/core/store/"
for component in ToolboxInventoryContext.kt ToolboxDeviceInventory.kt ToolboxProfileInventory.kt ToolboxTemplateInventory.kt; do
  cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/$component" \
    "$WORK/com/msa/iotofflinetoolbox/core/store/"
done

cat > "$WORK/com/msa/iotofflinetoolbox/core/model/Models.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.model

enum class AppLanguage { ENGLISH, PERSIAN }
enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }
enum class HistoryProtocol { HTTP }

data class AppSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val retainLogs: Int = 3,
    val retainHistory: Int = 3,
)

data class ToolLog(
    val id: String,
    val timestampMillis: Long,
    val level: LogLevel,
    val source: String,
    val message: String,
)

data class HistoryEntry(
    val id: String,
    val timestampMillis: Long,
    val protocol: HistoryProtocol,
    val target: String,
    val summary: String,
    val successful: Boolean,
    val requestPreview: String,
    val responsePreview: String,
    val durationMillis: Long?,
)

data class SsdpDevice(
    val location: String,
    val remoteAddress: String? = null,
    val server: String? = null,
    val searchTarget: String? = null,
)

data class SavedDevice(
    val id: String = "",
    val host: String,
    val displayName: String,
    val addresses: List<String> = emptyList(),
    val openPorts: List<Int> = emptyList(),
    val services: List<String> = emptyList(),
    val note: String = "",
    val tags: List<String> = emptyList(),
    val lastSeenMillis: Long = 0,
)

data class EndpointProfile(
    val id: String = "",
    val name: String,
    val protocol: String = "HTTP",
    val hostOrUrl: String,
    val port: Int? = null,
    val username: String = "",
    val defaultTopic: String = "",
    val headersText: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
)

data class PayloadTemplate(
    val id: String = "",
    val name: String,
    val content: String,
    val contentType: String = "text/plain",
    val notes: String = "",
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
)

data class BackupInspection(
    val schemaVersion: Int = 0,
    val exportedAtMillis: Long = 0,
    val applicationVersion: String? = null,
    val devices: Int = 0,
    val profiles: Int = 0,
    val templates: Int = 0,
    val history: Int = 0,
    val logs: Int = 0,
    val compatible: Boolean,
    val message: String,
)
KT

cat > "$WORK/com/msa/iotofflinetoolbox/core/store/AppState.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.*

data class AppState(
    val settings: AppSettings = AppSettings(),
    val logs: List<ToolLog> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val savedDevices: List<SavedDevice> = emptyList(),
    val profiles: List<EndpointProfile> = emptyList(),
    val templates: List<PayloadTemplate> = emptyList(),
    val feedback: AppFeedback? = null,
)
KT

cat > "$WORK/com/msa/iotofflinetoolbox/core/port/ToolboxPersistence.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.port

import com.msa.iotofflinetoolbox.core.model.*

interface ActivityPersistence {
    fun saveLogs(value: List<ToolLog>)
    fun saveHistory(value: List<HistoryEntry>)
}
interface DevicePersistence { fun saveDevices(value: List<SavedDevice>) }
interface ProfilePersistence { fun saveProfiles(value: List<EndpointProfile>) }
interface TemplatePersistence { fun saveTemplates(value: List<PayloadTemplate>) }
interface ToolboxPersistence : ActivityPersistence, DevicePersistence, ProfilePersistence, TemplatePersistence
KT

cat > "$WORK/com/msa/iotofflinetoolbox/core/policy/HttpRequestValidationIssue.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.policy

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
KT

cat > "$WORK/com/msa/iotofflinetoolbox/core/policy/TransportSecurity.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.policy

object TransportSecurity {
    fun extractHost(url: String): String = url.substringAfter("://", url).substringBefore('/').substringBefore(':')
}
KT

cat > "$WORK/com/msa/iotofflinetoolbox/core/store/InventoryActionStubs.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.EndpointProfile
import com.msa.iotofflinetoolbox.core.model.PayloadTemplate
import com.msa.iotofflinetoolbox.core.model.SavedDevice
import com.msa.iotofflinetoolbox.core.model.SsdpDevice

interface DeviceInventoryActions {
    fun saveDevice(host: String, displayName: String = host, addresses: List<String> = listOf(host), services: List<String> = emptyList())
    fun saveSsdpDevice(device: SsdpDevice)
    fun updateDevice(device: SavedDevice)
    fun deleteDevice(id: String)
}
interface ProfileInventoryActions {
    fun saveProfile(profile: EndpointProfile)
    fun deleteProfile(id: String)
}
interface TemplateInventoryActions {
    fun saveTemplate(template: PayloadTemplate)
    fun deleteTemplate(id: String)
}
KT

cat > "$WORK/com/msa/iotofflinetoolbox/core/store/ToolboxRuntime.kt" <<'KT'
package com.msa.iotofflinetoolbox.core.store

fun interface ToolboxClock { fun nowMillis(): Long }
fun interface ToolboxIdGenerator { fun nextId(prefix: String, timestampMillis: Long): String }
data class ToolboxRuntime(val clock: ToolboxClock, val idGenerator: ToolboxIdGenerator)
KT

cat > "$WORK/Main.kt" <<'KT'
import com.msa.iotofflinetoolbox.core.port.ToolboxPersistence
import com.msa.iotofflinetoolbox.core.model.*
import com.msa.iotofflinetoolbox.core.store.*

private class MemoryPersistence : ToolboxPersistence {
    var logs = emptyList<ToolLog>()
    var history = emptyList<HistoryEntry>()
    var devices = emptyList<SavedDevice>()
    var profiles = emptyList<EndpointProfile>()
    var templates = emptyList<PayloadTemplate>()
    override fun saveLogs(value: List<ToolLog>) { logs = value }
    override fun saveHistory(value: List<HistoryEntry>) { history = value }
    override fun saveDevices(value: List<SavedDevice>) { devices = value }
    override fun saveProfiles(value: List<EndpointProfile>) { profiles = value }
    override fun saveTemplates(value: List<PayloadTemplate>) { templates = value }
}

fun main() {
    var current = AppState(settings = AppSettings(retainLogs = 2, retainHistory = 2))
    val persistence = MemoryPersistence()
    var idSequence = 0
    val runtime = ToolboxRuntime(
        clock = ToolboxClock { 1234L },
        idGenerator = ToolboxIdGenerator { prefix, time -> "$prefix-$time-${++idSequence}" },
    )
    val messages = ToolboxMessages { current.settings.language }
    val update: (AppState.() -> AppState) -> Unit = { transform -> current = current.transform() }
    val journal = ToolboxActivityJournal(persistence, { current }, update, messages, runtime)
    val inventoryContext = ToolboxInventoryContext({ current }, update, messages, journal, runtime)
    val deviceInventory = ToolboxDeviceInventory(persistence, inventoryContext)
    val profileInventory = ToolboxProfileInventory(persistence, inventoryContext)
    val templateInventory = ToolboxTemplateInventory(persistence, inventoryContext)

    deviceInventory.saveDevice(" 192.168.1.10 ", " Gateway ", listOf("192.168.1.10", "192.168.1.10"), listOf("token=abc"))
    check(current.savedDevices.single().id == "device-1234-1")
    check(current.savedDevices.single().host == "192.168.1.10")
    check(current.savedDevices.single().addresses == listOf("192.168.1.10"))
    check("abc" !in current.savedDevices.single().services.single())

    profileInventory.saveProfile(
        EndpointProfile(
            name = " Local API ",
            hostOrUrl = "http://192.168.1.10",
            headersText = "Authorization: Bearer secret-value\nX-Mode: test",
        ),
    )
    val profile = current.profiles.single()
    check(profile.id == "profile-1234-3")
    check("Authorization" !in profile.headersText)
    check("X-Mode: test" in profile.headersText)

    templateInventory.saveTemplate(PayloadTemplate(name = "Command", content = "password=super-secret"))
    check("super-secret" !in current.templates.single().content)

    journal.log(LogLevel.INFO, "System", "one")
    journal.log(LogLevel.INFO, "System", "two")
    journal.log(LogLevel.INFO, "System", "three")
    check(current.logs.size == 2)
    check(persistence.logs.size == 2)

    journal.addHistory(HistoryProtocol.HTTP, "https://example.test?token=secret", "ok", true)
    check("secret" !in current.history.single().target)

    println("APPLICATION_ARCHITECTURE_RUNTIME_HARNESS_PASSED")
}
KT

kotlinc "$WORK" -include-runtime -d "$WORK/harness.jar"
java -jar "$WORK/harness.jar"
