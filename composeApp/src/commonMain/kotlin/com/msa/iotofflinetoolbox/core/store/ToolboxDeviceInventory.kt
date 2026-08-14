package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.model.SavedDevice
import com.msa.iotofflinetoolbox.core.model.SsdpDevice
import com.msa.iotofflinetoolbox.core.policy.TransportSecurity
import com.msa.iotofflinetoolbox.core.port.DevicePersistence
import com.msa.iotofflinetoolbox.core.security.SecretRedactor

/** Device inventory use-cases: normalization, bounded metadata, redaction and durable publication. */
internal class ToolboxDeviceInventory(
    private val persistence: DevicePersistence,
    private val context: ToolboxInventoryContext,
) : DeviceInventoryActions {
    override fun saveDevice(
        host: String,
        displayName: String,
        addresses: List<String>,
        services: List<String>,
    ) {
        val normalizedHost = host.trim()
        if (normalizedHost.isBlank()) return invalid(text("Device host cannot be empty", "میزبان دستگاه نمی‌تواند خالی باشد"))
        if (normalizedHost.length > InventoryLimits.MAX_ENDPOINT_LENGTH) return invalid(text("Device host is too long", "میزبان دستگاه بیش از حد طولانی است"))
        if (displayName.length > InventoryLimits.MAX_NAME_LENGTH) return invalid(text("Device name is too long", "نام دستگاه بیش از حد طولانی است"))
        if (addresses.size > InventoryLimits.MAX_CHILD_ITEMS || services.size > InventoryLimits.MAX_CHILD_ITEMS) {
            return invalid(text("Device address or service list exceeds the safety limit", "فهرست نشانی یا سرویس دستگاه از محدودیت ایمنی عبور کرده است"))
        }

        val current = context.snapshot().savedDevices
        val existing = current.firstOrNull { it.host.equals(normalizedHost, ignoreCase = true) }
        if (existing == null && current.size >= InventoryLimits.MAX_PERSISTED_ITEMS) {
            return invalid(
                text(
                    "Device inventory reached the ${InventoryLimits.MAX_PERSISTED_ITEMS} item safety limit",
                    "فهرست دستگاه‌ها به سقف ایمنی ${InventoryLimits.MAX_PERSISTED_ITEMS} مورد رسیده است",
                ),
            )
        }

        val device = SavedDevice(
            id = existing?.id ?: context.newId("device"),
            host = normalizedHost,
            displayName = displayName.ifBlank { normalizedHost }.trim().take(InventoryLimits.MAX_NAME_LENGTH),
            addresses = (existing?.addresses.orEmpty() + addresses)
                .map(String::trim)
                .filter(String::isNotBlank)
                .map { it.take(InventoryLimits.MAX_ENDPOINT_LENGTH) }
                .distinct()
                .take(InventoryLimits.MAX_CHILD_ITEMS),
            openPorts = existing?.openPorts.orEmpty()
                .filter { it in VALID_PORTS }
                .distinct()
                .sorted()
                .take(InventoryLimits.MAX_CHILD_ITEMS),
            services = (existing?.services.orEmpty() + services)
                .map(String::trim)
                .filter(String::isNotBlank)
                .map { SecretRedactor.redact(it).take(InventoryLimits.MAX_NAME_LENGTH) }
                .distinct()
                .take(InventoryLimits.MAX_CHILD_ITEMS),
            note = existing?.note.orEmpty().take(InventoryLimits.MAX_NOTES_LENGTH),
            tags = existing?.tags.orEmpty().take(InventoryLimits.MAX_CHILD_ITEMS),
            lastSeenMillis = context.now(),
        )
        val updated = (current.filterNot { it.id == existing?.id } + device).sortedBy { it.displayName.lowercase() }
        context.journal.persistenceFailure("Device inventory") { persistence.saveDevices(updated) }
            ?.let { return context.journal.reportPersistenceFailure(it) }
        context.update { copy(savedDevices = updated) }
        context.journal.log(
            LogLevel.SUCCESS,
            "Devices",
            text("Saved ${device.displayName} for offline access", "${device.displayName} برای دسترسی آفلاین ذخیره شد"),
        )
    }

    override fun saveSsdpDevice(device: SsdpDevice) {
        val host = TransportSecurity.extractHost(device.location).ifBlank { device.remoteAddress.orEmpty() }
        if (host.isBlank()) {
            return context.validationFailure(
                "SSDP",
                text("This response does not contain a usable host", "این پاسخ میزبان قابل استفاده‌ای ندارد"),
            )
        }
        saveDevice(
            host = host,
            displayName = device.server ?: device.searchTarget ?: host,
            addresses = listOfNotNull(device.remoteAddress, host),
            services = listOfNotNull(device.searchTarget, device.server),
        )
    }

    override fun updateDevice(device: SavedDevice) {
        val current = context.snapshot().savedDevices
        if (current.none { it.id == device.id }) return invalid(text("Saved device no longer exists", "دستگاه ذخیره‌شده دیگر وجود ندارد"))
        if (device.host.isBlank()) return invalid(text("Device host cannot be empty", "میزبان دستگاه نمی‌تواند خالی باشد"))
        if (device.host.length > InventoryLimits.MAX_ENDPOINT_LENGTH || device.displayName.length > InventoryLimits.MAX_NAME_LENGTH) {
            return invalid(text("Device host or name exceeds the safety limit", "میزبان یا نام دستگاه از محدودیت ایمنی عبور کرده است"))
        }
        if (device.addresses.size > InventoryLimits.MAX_CHILD_ITEMS ||
            device.services.size > InventoryLimits.MAX_CHILD_ITEMS ||
            device.tags.size > InventoryLimits.MAX_CHILD_ITEMS
        ) {
            return invalid(text("Device metadata exceeds the safety limit", "فراداده دستگاه از محدودیت ایمنی عبور کرده است"))
        }

        val normalized = device.copy(
            host = device.host.trim().take(InventoryLimits.MAX_ENDPOINT_LENGTH),
            displayName = device.displayName.ifBlank { device.host }.trim().take(InventoryLimits.MAX_NAME_LENGTH),
            addresses = device.addresses.map(String::trim).filter(String::isNotBlank).map { it.take(InventoryLimits.MAX_ENDPOINT_LENGTH) }.distinct().take(InventoryLimits.MAX_CHILD_ITEMS),
            openPorts = device.openPorts.filter { it in VALID_PORTS }.distinct().sorted().take(InventoryLimits.MAX_CHILD_ITEMS),
            services = device.services.map(String::trim).filter(String::isNotBlank).map { SecretRedactor.redact(it).take(InventoryLimits.MAX_NAME_LENGTH) }.distinct().take(InventoryLimits.MAX_CHILD_ITEMS),
            note = SecretRedactor.redact(device.note).take(InventoryLimits.MAX_NOTES_LENGTH),
            tags = device.tags.map(SecretRedactor::redact).map(String::trim).filter(String::isNotBlank).map { it.take(InventoryLimits.MAX_NAME_LENGTH) }.distinct().take(InventoryLimits.MAX_CHILD_ITEMS),
        )
        val updated = current.map { if (it.id == normalized.id) normalized else it }
        context.journal.persistenceFailure("Device inventory") { persistence.saveDevices(updated) }
            ?.let { return context.journal.reportPersistenceFailure(it) }
        context.update { copy(savedDevices = updated) }
        context.journal.log(LogLevel.INFO, "Devices", text("Updated ${normalized.displayName}", "${normalized.displayName} به‌روزرسانی شد"))
    }

    override fun deleteDevice(id: String) {
        val current = context.snapshot().savedDevices
        val target = current.firstOrNull { it.id == id }
        val updated = current.filterNot { it.id == id }
        context.journal.persistenceFailure("Device inventory") { persistence.saveDevices(updated) }
            ?.let { return context.journal.reportPersistenceFailure(it) }
        context.update { copy(savedDevices = updated) }
        context.journal.log(LogLevel.WARNING, "Devices", text("Deleted ${target?.displayName ?: id}", "${target?.displayName ?: id} حذف شد"))
    }

    private fun invalid(message: String) = context.validationFailure("Devices", message)
    private fun text(english: String, persian: String): String = context.text(english, persian)

    private companion object {
        val VALID_PORTS = 1..65_535
    }
}
