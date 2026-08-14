package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.model.PayloadTemplate
import com.msa.iotofflinetoolbox.core.port.TemplatePersistence
import com.msa.iotofflinetoolbox.core.security.SecretRedactor

/** Payload-template use-cases with bounded content and pre-persistence secret redaction. */
internal class ToolboxTemplateInventory(
    private val persistence: TemplatePersistence,
    private val context: ToolboxInventoryContext,
) : TemplateInventoryActions {
    override fun saveTemplate(template: PayloadTemplate) {
        val current = context.snapshot().templates
        if (template.name.isBlank() || template.content.isBlank()) {
            return invalid(text("Template name and content are required", "نام و محتوای قالب الزامی هستند"))
        }
        if (template.name.length > InventoryLimits.MAX_NAME_LENGTH ||
            template.contentType.length > InventoryLimits.MAX_NAME_LENGTH ||
            template.notes.length > InventoryLimits.MAX_NOTES_LENGTH
        ) {
            return invalid(text("Template metadata exceeds the safety limit", "فراداده قالب از محدودیت ایمنی عبور کرده است"))
        }
        if (template.content.encodeToByteArray().size > InventoryLimits.MAX_TEMPLATE_BYTES) {
            return invalid(text("Template content exceeds the 1 MiB safety limit", "محتوای قالب از سقف ایمنی ۱ مگابایت عبور کرده است"))
        }
        if (current.none { it.id == template.id } && current.size >= InventoryLimits.MAX_PERSISTED_ITEMS) {
            return invalid(
                text(
                    "Template inventory reached the ${InventoryLimits.MAX_PERSISTED_ITEMS} item safety limit",
                    "فهرست قالب‌ها به سقف ایمنی ${InventoryLimits.MAX_PERSISTED_ITEMS} مورد رسیده است",
                ),
            )
        }

        val timestamp = context.now()
        val redactedContent = SecretRedactor.redact(template.content)
        val normalized = template.copy(
            id = template.id.ifBlank { context.newId("template") }.take(InventoryLimits.MAX_ID_LENGTH),
            name = template.name.trim().take(InventoryLimits.MAX_NAME_LENGTH),
            content = redactedContent,
            contentType = template.contentType.trim().take(InventoryLimits.MAX_NAME_LENGTH),
            notes = SecretRedactor.redact(template.notes).take(InventoryLimits.MAX_NOTES_LENGTH),
            createdAtMillis = template.createdAtMillis.takeIf { it > 0 } ?: timestamp,
            updatedAtMillis = timestamp,
        )
        val updated = (current.filterNot { it.id == normalized.id } + normalized).sortedBy { it.name.lowercase() }
        context.journal.persistenceFailure("Payload templates") { persistence.saveTemplates(updated) }
            ?.let { return context.journal.reportPersistenceFailure(it) }
        context.update {
            copy(
                templates = updated,
                feedback = AppFeedback.notice(
                    if (redactedContent == template.content) {
                        text("Payload template saved", "قالب Payload ذخیره شد")
                    } else {
                        text(
                            "Payload template saved; detected secret values were redacted",
                            "قالب Payload ذخیره شد؛ مقادیر محرمانه شناسایی‌شده پاک‌سازی شدند",
                        )
                    },
                    "Templates",
                ),
            )
        }
        context.journal.log(LogLevel.SUCCESS, "Templates", text("Saved ${normalized.name}", "قالب ${normalized.name} ذخیره شد"))
    }

    override fun deleteTemplate(id: String) {
        val current = context.snapshot().templates
        val target = current.firstOrNull { it.id == id }
        val updated = current.filterNot { it.id == id }
        context.journal.persistenceFailure("Payload templates") { persistence.saveTemplates(updated) }
            ?.let { return context.journal.reportPersistenceFailure(it) }
        context.update { copy(templates = updated) }
        context.journal.log(LogLevel.WARNING, "Templates", text("Deleted ${target?.name ?: id}", "قالب ${target?.name ?: id} حذف شد"))
    }

    private fun invalid(message: String) = context.validationFailure("Templates", message)
    private fun text(english: String, persian: String): String = context.text(english, persian)
}
