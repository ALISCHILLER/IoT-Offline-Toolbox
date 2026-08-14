package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.port.StorageRecoveryEvent
import com.msa.iotofflinetoolbox.core.port.ToolboxPersistence
import com.msa.iotofflinetoolbox.core.model.AppLanguage
import com.msa.iotofflinetoolbox.core.model.AppSettings
import com.msa.iotofflinetoolbox.core.store.AppState
import com.msa.iotofflinetoolbox.core.model.BackupImportMode
import com.msa.iotofflinetoolbox.core.model.BackupInspection
import com.msa.iotofflinetoolbox.core.model.EndpointProfile
import com.msa.iotofflinetoolbox.core.model.EndpointProtocol
import com.msa.iotofflinetoolbox.core.model.HistoryEntry
import com.msa.iotofflinetoolbox.core.model.HistoryProtocol
import com.msa.iotofflinetoolbox.core.model.ImportSummary
import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.model.PayloadTemplate
import com.msa.iotofflinetoolbox.core.model.PlatformCapabilities
import com.msa.iotofflinetoolbox.core.model.SavedDevice
import com.msa.iotofflinetoolbox.core.model.ToolLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolboxApplicationServicesTest {
    @Test
    fun inventoryUsesInjectedRuntimeAndNormalizesDurableData() {
        val fixture = Fixture()

        fixture.deviceInventory.saveDevice(
            host = " 192.168.1.10 ",
            displayName = " Gateway ",
            addresses = listOf("192.168.1.10", "192.168.1.10"),
            services = listOf("token=secret-value"),
        )

        val device = fixture.state.savedDevices.single()
        assertEquals("device-1234-1", device.id)
        assertEquals("192.168.1.10", device.host)
        assertEquals(listOf("192.168.1.10"), device.addresses)
        assertFalse(device.services.single().contains("secret-value"))
        assertEquals(fixture.persistence.devices, fixture.state.savedDevices)
    }

    @Test
    fun profileAndTemplateSecretsAreRemovedBeforePersistence() {
        val fixture = Fixture()

        fixture.profileInventory.saveProfile(
            EndpointProfile(
                id = "",
                name = "Local API",
                protocol = EndpointProtocol.HTTP,
                hostOrUrl = "http://192.168.1.10",
                headersText = "Authorization: Bearer secret-value\nX-Mode: test",
                createdAtMillis = 0,
                updatedAtMillis = 0,
            ),
        )
        fixture.templateInventory.saveTemplate(
            PayloadTemplate(
                id = "",
                name = "Command",
                content = "password=super-secret",
                createdAtMillis = 0,
                updatedAtMillis = 0,
            ),
        )

        val profile = fixture.state.profiles.single()
        assertFalse(profile.headersText.contains("Authorization", ignoreCase = true))
        assertTrue(profile.headersText.contains("X-Mode: test"))
        assertFalse(fixture.state.templates.single().content.contains("super-secret"))
        assertEquals(fixture.persistence.profiles, fixture.state.profiles)
        assertEquals(fixture.persistence.templates, fixture.state.templates)
    }

    @Test
    fun journalEnforcesRetentionAndRedactsHistory() {
        val fixture = Fixture(retainLogs = 2, retainHistory = 1)

        fixture.journal.log(LogLevel.INFO, "System", "one")
        fixture.journal.log(LogLevel.INFO, "System", "two")
        fixture.journal.log(LogLevel.INFO, "System", "three")
        fixture.journal.addHistory(
            protocol = HistoryProtocol.HTTP,
            target = "https://example.test?token=secret-value",
            summary = "completed",
            successful = true,
        )

        assertEquals(2, fixture.state.logs.size)
        assertEquals(2, fixture.persistence.logs.size)
        assertEquals(1, fixture.state.history.size)
        assertFalse(fixture.state.history.single().target.contains("secret-value"))
    }


    @Test
    fun stateLoaderPublishesBoundedRedactedRecoveryEvidence() {
        val persistence = MemoryPersistence().apply {
            recoveryOnDeviceLoad = StorageRecoveryEvent(
                key = "devices",
                quarantineKey = "quarantine.devices.latest",
                message = "token=secret-value could not be decoded",
            )
        }
        var sequence = 0
        val runtime = ToolboxRuntime(
            clock = ToolboxClock { 2_000L },
            idGenerator = ToolboxIdGenerator { prefix, timestamp -> "$prefix-$timestamp-${++sequence}" },
        )

        val state = ToolboxStateLoader(persistence, runtime).load(testCapabilities)

        assertEquals(1, state.logs.size)
        assertTrue(state.feedback?.message?.contains("Recovered 1") == true)
        assertFalse(state.logs.single().message.contains("secret-value"))
        assertEquals(persistence.logs, state.logs)
    }

    @Test
    fun settingsManagerReloadsImportedStateThroughPersistencePort() {
        var state = AppState(settings = AppSettings(), capabilities = testCapabilities)
        val persistence = MemoryPersistence().apply {
            importedDevices = listOf(SavedDevice(id = "imported", host = "10.0.0.5", displayName = "Imported"))
        }
        val runtime = ToolboxRuntime(
            clock = ToolboxClock { 3_000L },
            idGenerator = ToolboxIdGenerator { prefix, timestamp -> "$prefix-$timestamp" },
        )
        val messages = ToolboxMessages { state.settings.language }
        val update: (AppState.() -> AppState) -> Unit = { transform -> state = state.transform() }
        val journal = ToolboxActivityJournal(persistence, { state }, update, messages, runtime)
        val manager = ToolboxSettingsManager(persistence, update, messages, journal)

        manager.importBackup("{}", BackupImportMode.MERGE)

        assertEquals("imported", state.savedDevices.single().id)
        assertTrue(state.feedback?.message?.contains("complete", ignoreCase = true) == true)
    }

    @Test
    fun persianMessagePolicyIsIndependentFromStore() {
        var language = AppLanguage.PERSIAN
        val messages = ToolboxMessages { language }

        assertEquals("تنظیمات", messages.source("Settings"))
        assertEquals("بله", messages.text("yes", "بله"))

        language = AppLanguage.ENGLISH
        assertEquals("Settings", messages.source("Settings"))
        assertEquals("yes", messages.text("yes", "بله"))
    }

    private class Fixture(
        retainLogs: Int = 10,
        retainHistory: Int = 10,
    ) {
        var state = AppState(
            settings = AppSettings(retainLogs = retainLogs, retainHistory = retainHistory),
            capabilities = testCapabilities,
        )
        val persistence = MemoryPersistence()
        private var sequence = 0
        private val runtime = ToolboxRuntime(
            clock = ToolboxClock { 1234L },
            idGenerator = ToolboxIdGenerator { prefix, timestamp -> "$prefix-$timestamp-${++sequence}" },
        )
        private val messages = ToolboxMessages { state.settings.language }
        private val updateState: (AppState.() -> AppState) -> Unit = { transform -> state = state.transform() }
        val journal = ToolboxActivityJournal(persistence, { state }, updateState, messages, runtime)
        private val inventoryContext = ToolboxInventoryContext({ state }, updateState, messages, journal, runtime)
        val deviceInventory = ToolboxDeviceInventory(persistence, inventoryContext)
        val profileInventory = ToolboxProfileInventory(persistence, inventoryContext)
        val templateInventory = ToolboxTemplateInventory(persistence, inventoryContext)
    }

    private class MemoryPersistence : ToolboxPersistence {
        var settings = AppSettings()
        var devices = emptyList<SavedDevice>()
        var logs = emptyList<ToolLog>()
        var profiles = emptyList<EndpointProfile>()
        var templates = emptyList<PayloadTemplate>()
        var history = emptyList<HistoryEntry>()
        var recoveryEvents = emptyList<StorageRecoveryEvent>()
        var recoveryOnDeviceLoad: StorageRecoveryEvent? = null
        var importedDevices = emptyList<SavedDevice>()

        override fun drainRecoveryEvents(): List<StorageRecoveryEvent> = recoveryEvents.also { recoveryEvents = emptyList() }
        override fun loadSettings(): AppSettings = settings
        override fun saveSettings(value: AppSettings) { settings = value }
        override fun loadDevices(): List<SavedDevice> {
            recoveryOnDeviceLoad?.let { recoveryEvents = recoveryEvents + it }
            recoveryOnDeviceLoad = null
            return devices
        }
        override fun saveDevices(value: List<SavedDevice>) { devices = value }
        override fun loadLogs(): List<ToolLog> = logs
        override fun saveLogs(value: List<ToolLog>) { logs = value }
        override fun loadProfiles(): List<EndpointProfile> = profiles
        override fun saveProfiles(value: List<EndpointProfile>) { profiles = value }
        override fun loadTemplates(): List<PayloadTemplate> = templates
        override fun saveTemplates(value: List<PayloadTemplate>) { templates = value }
        override fun loadHistory(): List<HistoryEntry> = history
        override fun saveHistory(value: List<HistoryEntry>) { history = value }
        override fun exportBackup(includeLogs: Boolean): String = "{}"
        override fun inspectBackup(raw: String): BackupInspection = BackupInspection(1, 1, null, 0, 0, 0, 0, 0, true, "ok")
        override fun importBackup(raw: String, mode: BackupImportMode): ImportSummary {
            devices = importedDevices
            return ImportSummary(devices.size, profiles.size, templates.size, history.size, logs.size, mode)
        }
    }

    private companion object {
        val testCapabilities = PlatformCapabilities(
            platformName = "test",
            ping = false,
            tcpPortScan = false,
            hostDiscovery = false,
            ssdpDiscovery = false,
            mdnsDiscovery = false,
            dnsLookup = false,
            tcpClient = false,
            udpClient = false,
            notes = "",
        )
    }
}
