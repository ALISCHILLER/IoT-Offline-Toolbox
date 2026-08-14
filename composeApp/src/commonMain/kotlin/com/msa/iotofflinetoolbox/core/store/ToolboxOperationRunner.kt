package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.HistoryProtocol
import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.policy.HttpRequestValidationIssue
import com.msa.iotofflinetoolbox.core.policy.TransportSecurity
import com.msa.iotofflinetoolbox.core.security.SecretRedactor

/**
 * Shared lifecycle boundary for user-triggered operations.
 *
 * This is the only application service that knows how an operation starts, fails, finishes or is
 * cancelled. Feature services stay focused on protocol/diagnostic behavior and publish state only
 * through this boundary.
 */
internal class ToolboxOperationRunner(
    private val state: () -> AppState,
    private val updateState: (AppState.() -> AppState) -> Unit,
    private val messages: ToolboxMessages,
    private val journal: ToolboxActivityJournal,
    private val coordinator: OperationCoordinator,
) {
    fun snapshot(): AppState = state()

    fun update(transform: AppState.() -> AppState) = updateState(transform)

    fun launch(title: String, block: suspend () -> Unit) {
        coordinator.launch(
            onStart = {
                updateState {
                    copy(
                        isBusy = true,
                        operationTitle = title,
                        feedback = null,
                    )
                }
            },
            onFailure = { failure -> fail(title, failure) },
            onFinish = { updateState { copy(isBusy = false, operationTitle = null) } },
            block = block,
        )
    }

    fun requireCapability(enabled: Boolean, source: String): Boolean {
        if (enabled) return true
        val snapshot = state()
        val displaySource = messages.source(source)
        updateState {
            copy(
                feedback = AppFeedback.notice(
                    text(
                        "$displaySource is unavailable on ${snapshot.capabilities.platformName}. ${snapshot.capabilities.notes}",
                        "$displaySource در ${snapshot.capabilities.platformName} در دسترس نیست. ${snapshot.capabilities.notes}",
                    ),
                    source,
                ),
            )
        }
        log(
            LogLevel.WARNING,
            source,
            text(
                "Capability unavailable on ${snapshot.capabilities.platformName}",
                "این قابلیت در ${snapshot.capabilities.platformName} در دسترس نیست",
            ),
        )
        return false
    }

    fun allowEndpoint(url: String, source: String): Boolean {
        val snapshot = state()
        val overrideEnabled = snapshot.settings.allowPublicCleartext && snapshot.capabilities.publicCleartextOverride
        if (!TransportSecurity.isPublicCleartext(url) || overrideEnabled) return true

        val message = text(
            "Public cleartext transport is blocked by default. Use HTTPS/WSS or a local/private endpoint.",
            "انتقال عمومی بدون رمزنگاری به‌صورت پیش‌فرض مسدود است؛ از HTTPS/WSS یا مقصد محلی/خصوصی استفاده کنید.",
        )
        updateState { copy(feedback = AppFeedback.error(message, source)) }
        log(LogLevel.ERROR, "$source security", message)
        return false
    }

    fun fail(source: String, throwable: Throwable) {
        val message = throwable.message ?: throwable::class.simpleName ?: text("Unknown error", "خطای ناشناخته")
        updateState { copy(feedback = AppFeedback.error(SecretRedactor.redact(message), source)) }
        log(LogLevel.ERROR, source, message)
    }

    fun validationFailure(source: String, message: String) {
        updateState { copy(feedback = AppFeedback.error(message, source)) }
        log(LogLevel.ERROR, source, message)
    }

    fun log(level: LogLevel, source: String, message: String) = journal.log(level, source, message)

    fun addHistory(
        protocol: HistoryProtocol,
        target: String,
        summary: String,
        successful: Boolean,
        request: String = "",
        response: String = "",
        durationMillis: Long? = null,
    ) = journal.addHistory(protocol, target, summary, successful, request, response, durationMillis)

    fun httpValidationIssue(issue: HttpRequestValidationIssue): String = messages.httpValidationIssue(issue)

    fun text(english: String, persian: String): String = messages.text(english, persian)

    fun cancel() {
        if (!coordinator.cancelCurrent()) return
        updateState { copy(isBusy = false, operationTitle = null) }
        log(LogLevel.WARNING, "System", text("Operation cancelled", "عملیات لغو شد"))
    }
}
