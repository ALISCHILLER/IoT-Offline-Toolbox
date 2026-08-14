package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.EndpointProfile
import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.port.ProfilePersistence
import com.msa.iotofflinetoolbox.core.security.SecretRedactor

/** Endpoint-profile use-cases with strict secret removal before persistence. */
internal class ToolboxProfileInventory(
    private val persistence: ProfilePersistence,
    private val context: ToolboxInventoryContext,
) : ProfileInventoryActions {
    override fun saveProfile(profile: EndpointProfile) {
        val current = context.snapshot().profiles
        if (profile.name.isBlank() || profile.hostOrUrl.isBlank()) {
            return invalid(text("Profile name and endpoint are required", "نام پروفایل و Endpoint الزامی هستند"))
        }
        if (profile.name.length > InventoryLimits.MAX_NAME_LENGTH || profile.hostOrUrl.length > InventoryLimits.MAX_ENDPOINT_LENGTH) {
            return invalid(text("Profile name or endpoint exceeds the safety limit", "نام یا Endpoint پروفایل از محدودیت ایمنی عبور کرده است"))
        }
        if (profile.headersText.length > InventoryLimits.MAX_HEADER_BLOCK_LENGTH ||
            profile.notes.length > InventoryLimits.MAX_NOTES_LENGTH ||
            profile.tags.size > InventoryLimits.MAX_CHILD_ITEMS
        ) {
            return invalid(text("Profile headers, notes or tags exceed the safety limit", "Headerها، یادداشت‌ها یا برچسب‌های پروفایل از محدودیت ایمنی عبور کرده‌اند"))
        }
        if (current.none { it.id == profile.id } && current.size >= InventoryLimits.MAX_PERSISTED_ITEMS) {
            return invalid(
                text(
                    "Profile inventory reached the ${InventoryLimits.MAX_PERSISTED_ITEMS} item safety limit",
                    "فهرست پروفایل‌ها به سقف ایمنی ${InventoryLimits.MAX_PERSISTED_ITEMS} مورد رسیده است",
                ),
            )
        }

        val timestamp = context.now()
        val sanitizedHeaders = SecretRedactor.removeSensitiveHeaders(profile.headersText)
        val normalized = profile.copy(
            id = profile.id.ifBlank { context.newId("profile") }.take(InventoryLimits.MAX_ID_LENGTH),
            name = profile.name.trim().take(InventoryLimits.MAX_NAME_LENGTH),
            hostOrUrl = SecretRedactor.redact(profile.hostOrUrl.trim()).take(InventoryLimits.MAX_ENDPOINT_LENGTH),
            port = profile.port?.takeIf { it in VALID_PORTS },
            username = SecretRedactor.redact(profile.username).take(InventoryLimits.MAX_NAME_LENGTH),
            defaultTopic = SecretRedactor.redact(profile.defaultTopic).take(InventoryLimits.MAX_ENDPOINT_LENGTH),
            headersText = sanitizedHeaders.value.take(InventoryLimits.MAX_HEADER_BLOCK_LENGTH),
            notes = SecretRedactor.redact(profile.notes).take(InventoryLimits.MAX_NOTES_LENGTH),
            tags = profile.tags.map(SecretRedactor::redact).map(String::trim).filter(String::isNotBlank)
                .map { it.take(InventoryLimits.MAX_NAME_LENGTH) }.distinct().take(InventoryLimits.MAX_CHILD_ITEMS),
            createdAtMillis = profile.createdAtMillis.takeIf { it > 0 } ?: timestamp,
            updatedAtMillis = timestamp,
        )
        val updated = (current.filterNot { it.id == normalized.id } + normalized).sortedBy { it.name.lowercase() }
        context.journal.persistenceFailure("Endpoint profiles") { persistence.saveProfiles(updated) }
            ?.let { return context.journal.reportPersistenceFailure(it) }
        context.update {
            copy(
                profiles = updated,
                feedback = AppFeedback.notice(
                    if (sanitizedHeaders.removedNames.isEmpty()) {
                        text("Profile saved", "پروفایل ذخیره شد")
                    } else {
                        text(
                            "Profile saved; sensitive headers were removed: ${sanitizedHeaders.removedNames.joinToString()}",
                            "پروفایل ذخیره شد؛ Headerهای حساس حذف شدند: ${sanitizedHeaders.removedNames.joinToString()}",
                        )
                    },
                    "Profiles",
                ),
            )
        }
        context.journal.log(LogLevel.SUCCESS, "Profiles", text("Saved ${normalized.name}", "پروفایل ${normalized.name} ذخیره شد"))
    }

    override fun deleteProfile(id: String) {
        val current = context.snapshot().profiles
        val target = current.firstOrNull { it.id == id }
        val updated = current.filterNot { it.id == id }
        context.journal.persistenceFailure("Endpoint profiles") { persistence.saveProfiles(updated) }
            ?.let { return context.journal.reportPersistenceFailure(it) }
        context.update { copy(profiles = updated) }
        context.journal.log(LogLevel.WARNING, "Profiles", text("Deleted ${target?.name ?: id}", "پروفایل ${target?.name ?: id} حذف شد"))
    }

    private fun invalid(message: String) = context.validationFailure("Profiles", message)
    private fun text(english: String, persian: String): String = context.text(english, persian)

    private companion object {
        val VALID_PORTS = 1..65_535
    }
}
