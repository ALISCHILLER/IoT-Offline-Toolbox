package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.ToolScreen
import com.msa.iotofflinetoolbox.core.port.SettingsPersistence

/** Navigation use-case boundary; persists the last route without leaking storage into presentation. */
internal class ToolboxNavigationManager(
    private val persistence: SettingsPersistence,
    private val state: () -> AppState,
    private val updateState: (AppState.() -> AppState) -> Unit,
    private val journal: ToolboxActivityJournal,
) {
    fun navigate(screen: ToolScreen) {
        if (state().currentScreen == screen) return
        val settings = state().settings.copy(lastScreen = screen)
        val persistenceError = journal.persistenceFailure("Navigation settings") {
            persistence.saveSettings(settings)
        }
        updateState {
            copy(
                currentScreen = screen,
                settings = settings,
                feedback = persistenceError?.let { AppFeedback.error(it, "Navigation settings") } ?: feedback,
            )
        }
        persistenceError?.let(journal::reportPersistenceFailure)
    }
}
