package com.msa.iotofflinetoolbox.core.data

import com.msa.iotofflinetoolbox.core.data.ToolboxRepository
import com.msa.iotofflinetoolbox.core.model.AppLanguage
import com.msa.iotofflinetoolbox.core.model.AppSettings
import com.msa.iotofflinetoolbox.core.model.BackupImportMode
import com.msa.iotofflinetoolbox.core.model.EndpointProfile
import com.msa.iotofflinetoolbox.core.model.EndpointProtocol
import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.model.PayloadTemplate
import com.msa.iotofflinetoolbox.core.model.SavedDevice
import com.msa.iotofflinetoolbox.core.model.ToolLog
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.set
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolboxRepositoryTest {
    @Test
    fun backupRoundTripRestoresProfiles() {
        val source = ToolboxRepository(MapSettings())
        source.saveProfiles(listOf(EndpointProfile("profile-1", "Lab broker", EndpointProtocol.MQTT_WEBSOCKET, "wss://broker.example/mqtt", createdAtMillis = 1, updatedAtMillis = 1)))
        val backup = source.exportBackup(includeLogs = true)
        val target = ToolboxRepository(MapSettings())
        val summary = target.importBackup(backup, BackupImportMode.REPLACE)
        assertEquals(1, summary.profiles)
        assertEquals("Lab broker", target.loadProfiles().single().name)
        assertTrue(target.inspectBackup(backup).compatible)
    }

    @Test
    fun mergePreservesExistingAndAddsIncoming() {
        val source = ToolboxRepository(MapSettings())
        source.saveProfiles(listOf(EndpointProfile("incoming", "Incoming", EndpointProtocol.HTTP, "https://incoming.test", createdAtMillis = 1, updatedAtMillis = 1)))
        val target = ToolboxRepository(MapSettings())
        target.saveProfiles(listOf(EndpointProfile("local", "Local", EndpointProtocol.HTTP, "https://local.test", createdAtMillis = 1, updatedAtMillis = 1)))
        target.importBackup(source.exportBackup(false), BackupImportMode.MERGE)
        assertEquals(setOf("incoming", "local"), target.loadProfiles().map { it.id }.toSet())
    }

    @Test
    fun exportAlwaysRedactsSecrets() {
        val repository = ToolboxRepository(MapSettings())
        repository.saveProfiles(listOf(EndpointProfile("secure", "Secure", EndpointProtocol.HTTP, "https://example.test?token=query-secret", headersText = "Authorization: Bearer header-secret", createdAtMillis = 1, updatedAtMillis = 1)))
        val backup = repository.exportBackup(includeLogs = true)
        assertFalse(backup.contains("query-secret"))
        assertFalse(backup.contains("header-secret"))
    }

    @Test
    fun newerBackupSchemaIsRejectedBeforeWriting() {
        val target = ToolboxRepository(MapSettings())
        val invalid = """{"schemaVersion":999,"exportedAtMillis":1,"settings":{},"devices":[],"profiles":[],"templates":[],"history":[],"logs":[]}"""
        assertFailsWith<IllegalArgumentException> { target.importBackup(invalid, BackupImportMode.REPLACE) }
        assertTrue(target.loadProfiles().isEmpty())
        assertFalse(target.inspectBackup(invalid).compatible)
    }
    @Test
    fun mergeKeepsLocalRecordWhenIdsConflict() {
        val source = ToolboxRepository(MapSettings())
        source.saveProfiles(listOf(EndpointProfile("same", "Incoming", EndpointProtocol.HTTP, "https://incoming.test", createdAtMillis = 1, updatedAtMillis = 2)))
        val target = ToolboxRepository(MapSettings())
        target.saveProfiles(listOf(EndpointProfile("same", "Local", EndpointProtocol.HTTP, "https://local.test", createdAtMillis = 1, updatedAtMillis = 3)))
        target.importBackup(source.exportBackup(false), BackupImportMode.MERGE)
        assertEquals("Local", target.loadProfiles().single().name)
        assertEquals("https://local.test", target.loadProfiles().single().hostOrUrl)
    }

    @Test
    fun corruptedPersistentJsonIsReportedAndQuarantined() {
        val settings = MapSettings()
        settings["endpoint.profiles.v3"] = "{not-valid-json"
        val repository = ToolboxRepository(settings)
        assertTrue(repository.loadProfiles().isEmpty())
        val event = repository.drainRecoveryEvents().single()
        assertEquals("endpoint.profiles.v3", event.key)
        assertTrue(event.quarantineKey?.startsWith("recovery.corrupt.endpoint.profiles.v3.") == true)
    }

    @Test
    fun recoveryEventsAreDrainedExactlyOnce() {
        val settings = MapSettings()
        settings["request.history.v3"] = "[broken"
        val repository = ToolboxRepository(settings)
        repository.loadHistory()
        assertEquals(1, repository.drainRecoveryEvents().size)
        assertTrue(repository.drainRecoveryEvents().isEmpty())
    }

    @Test
    fun settingsNormalizationEnforcesSecurityAndPersianDirection() {
        val repository = ToolboxRepository(MapSettings())
        repository.saveSettings(
            AppSettings(
                language = AppLanguage.PERSIAN,
                rtl = false,
                defaultHttpTimeoutMillis = 999_999,
                defaultHttpConnectTimeoutMillis = 0,
                defaultHttpSocketTimeoutMillis = 999_999,
                maxPortScanItems = 65_535,
                allowPublicCleartext = true,
            ),
        )
        val loaded = repository.loadSettings()
        assertTrue(loaded.rtl)
        assertEquals(120_000, loaded.defaultHttpTimeoutMillis)
        assertEquals(100, loaded.defaultHttpConnectTimeoutMillis)
        assertEquals(120_000, loaded.defaultHttpSocketTimeoutMillis)
        assertEquals(4_096, loaded.maxPortScanItems)
        assertTrue(loaded.allowPublicCleartext)
    }

    @Test
    fun corruptSnapshotIsBoundedAndSecretRedacted() {
        val settings = MapSettings()
        settings["endpoint.profiles.v3"] = "Bearer raw-secret-token"
        val repository = ToolboxRepository(settings)
        repository.loadProfiles()
        val event = repository.drainRecoveryEvents().single()
        val snapshot: String? = settings[event.quarantineKey!!]
        assertTrue(snapshot != null)
        assertFalse(snapshot!!.contains("raw-secret-token"))
        assertTrue(snapshot.contains("<REDACTED>"))
    }

    @Test
    fun validCurrentDataDoesNotReadCorruptLegacyFallback() {
        val settings = MapSettings()
        settings["saved.devices.v3"] = "[]"
        settings["saved.devices.v2"] = "[broken"
        val repository = ToolboxRepository(settings)

        assertTrue(repository.loadDevices().isEmpty())
        assertTrue(repository.drainRecoveryEvents().isEmpty())
    }

    @Test
    fun exportRedactsSecretsAcrossEveryUserControlledBackupField() {
        val repository = ToolboxRepository(MapSettings())
        repository.saveDevices(
            listOf(
                SavedDevice(
                    id = "device-1",
                    host = "device.local?token=device-secret",
                    displayName = "password=display-secret",
                    addresses = listOf("10.0.0.2?key=address-secret"),
                    services = listOf("Authorization: Bearer service-secret"),
                    note = "api_key=note-secret",
                    tags = listOf("password=tag-secret"),
                    lastSeenMillis = 1,
                ),
            ),
        )
        repository.saveTemplates(
            listOf(
                PayloadTemplate(
                    id = "template-1",
                    name = "token=name-secret",
                    content = "{\"password\":\"content-secret\"}",
                    contentType = "api_key=type-secret",
                    notes = "secret=notes-secret",
                    createdAtMillis = 1,
                    updatedAtMillis = 1,
                ),
            ),
        )
        repository.saveLogs(
            listOf(
                ToolLog(
                    id = "log-1",
                    timestampMillis = 1,
                    level = LogLevel.INFO,
                    source = "token=source-secret",
                    message = "Bearer message-secret",
                ),
            ),
        )

        val backup = repository.exportBackup(includeLogs = true)

        listOf(
            "device-secret", "display-secret", "address-secret", "service-secret", "note-secret", "tag-secret",
            "name-secret", "content-secret", "type-secret", "notes-secret", "source-secret", "message-secret",
        ).forEach { secret -> assertFalse(backup.contains(secret), "Backup leaked $secret") }
    }

    @Test
    fun localStorageWriteRejectsOversizedSerializedCollections() {
        val repository = ToolboxRepository(MapSettings())
        val oversized = "x".repeat(1_048_576)
        val templates = (0 until 9).map { index ->
            PayloadTemplate(
                id = "template-$index",
                name = "Template $index",
                content = oversized,
                createdAtMillis = 1,
                updatedAtMillis = 1,
            )
        }
        assertFailsWith<IllegalArgumentException> { repository.saveTemplates(templates) }
    }

    @Test
    fun oversizedStoredJsonIsQuarantinedWithoutParsing() {
        val settings = MapSettings()
        settings["endpoint.profiles.v3"] = "[" + " ".repeat(8 * 1024 * 1024) + "]"
        val repository = ToolboxRepository(settings)
        assertTrue(repository.loadProfiles().isEmpty())
        val event = repository.drainRecoveryEvents().single()
        assertTrue(event.message.contains("local storage safety limit"))
    }

    @Test
    fun structurallyValidButOversizedStoredFieldsAreQuarantined() {
        val settings = MapSettings()
        val oversizedName = "x".repeat(257)
        settings["endpoint.profiles.v3"] = """[{"id":"profile","name":"$oversizedName","protocol":"HTTP","hostOrUrl":"https://example.test","createdAtMillis":1,"updatedAtMillis":1}]"""
        val repository = ToolboxRepository(settings)

        assertTrue(repository.loadProfiles().isEmpty())
        val event = repository.drainRecoveryEvents().single()
        assertEquals("endpoint.profiles.v3", event.key)
        assertTrue(event.message.contains("field safety limit"))
    }

    @Test
    fun replaceImportCannotEnablePublicCleartextFromSharedBackup() {
        val source = ToolboxRepository(MapSettings())
        source.saveSettings(AppSettings(allowPublicCleartext = true))
        val target = ToolboxRepository(MapSettings())

        target.importBackup(source.exportBackup(includeLogs = false), BackupImportMode.REPLACE)

        assertFalse(target.loadSettings().allowPublicCleartext)
    }


    @Test
    fun blankPersistedProfileIsQuarantinedBeforeUse() {
        val settings = MapSettings()
        settings["endpoint.profiles.v3"] =
            """[{"id":"profile","name":"","protocol":"HTTP","hostOrUrl":"https://example.test","createdAtMillis":1,"updatedAtMillis":1}]"""
        val repository = ToolboxRepository(settings)

        assertTrue(repository.loadProfiles().isEmpty())
        val event = repository.drainRecoveryEvents().single()
        assertEquals("endpoint.profiles.v3", event.key)
        assertTrue(event.message.contains("cannot be empty"))
    }

    @Test
    fun persistedDeviceWithInvalidPortIsQuarantinedBeforeUse() {
        val settings = MapSettings()
        settings["saved.devices.v3"] =
            """[{"id":"device","host":"192.0.2.1","displayName":"Device","addresses":[],"openPorts":[70000],"services":[],"note":"","tags":[],"lastSeenMillis":1}]"""
        val repository = ToolboxRepository(settings)

        assertTrue(repository.loadDevices().isEmpty())
        val event = repository.drainRecoveryEvents().single()
        assertEquals("saved.devices.v3", event.key)
        assertTrue(event.message.contains("invalid open port"))
    }

    @Test
    fun backupAndDefaultTemplatesUseInjectedClock() {
        val fixedNow = 1_725_000_123_456L
        val repository = ToolboxRepository(MapSettings(), nowMillis = { fixedNow })

        val backup = repository.exportBackup(includeLogs = false)
        val inspection = repository.inspectBackup(backup)
        assertEquals(fixedNow, inspection.exportedAtMillis)
        assertTrue(repository.loadTemplates().all { it.createdAtMillis == fixedNow && it.updatedAtMillis == fixedNow })
    }

}
