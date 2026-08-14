package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.LogLevel

/** Shared application boundary for inventory validation, persistence failures and deterministic IDs. */
internal class ToolboxInventoryContext(
    private val state: () -> AppState,
    private val updateState: (AppState.() -> AppState) -> Unit,
    private val messages: ToolboxMessages,
    val journal: ToolboxActivityJournal,
    private val runtime: ToolboxRuntime,
) {
    fun snapshot(): AppState = state()

    fun update(transform: AppState.() -> AppState) = updateState(transform)

    fun validationFailure(source: String, message: String) {
        updateState { copy(feedback = AppFeedback.error(message, source)) }
        journal.log(LogLevel.ERROR, source, message)
    }

    fun text(english: String, persian: String): String = messages.text(english, persian)

    fun now(): Long = runtime.clock.nowMillis()

    fun newId(prefix: String): String = runtime.idGenerator.nextId(prefix, now())
}

internal object InventoryLimits {
    const val MAX_PERSISTED_ITEMS = 5_000
    const val MAX_CHILD_ITEMS = 512
    const val MAX_ID_LENGTH = 160
    const val MAX_NAME_LENGTH = 256
    const val MAX_ENDPOINT_LENGTH = 2_048
    const val MAX_HEADER_BLOCK_LENGTH = 65_536
    const val MAX_NOTES_LENGTH = 8_192
    const val MAX_TEMPLATE_BYTES = 1_048_576
}
