package com.msa.iotofflinetoolbox.core.store

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OperationCoordinatorTest {
    @Test
    fun successorWaitsForCancelledOperationCleanup() = runTest {
        val events = mutableListOf<String>()
        val coordinator = OperationCoordinator(this)

        coordinator.launch(
            onStart = { events += "first-start" },
            onFailure = { error("unexpected failure: $it") },
            onFinish = { events += "first-finish" },
        ) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    delay(50)
                    events += "first-cleanup"
                }
            }
        }
        advanceUntilIdle()

        coordinator.launch(
            onStart = { events += "second-start" },
            onFailure = { error("unexpected failure: $it") },
            onFinish = { events += "second-finish" },
        ) {
            events += "second-body"
        }
        advanceUntilIdle()

        assertTrue(events.indexOf("first-cleanup") < events.indexOf("second-start"))
        assertFalse("first-finish" in events)
        assertEquals(listOf("second-start", "second-body", "second-finish"), events.takeLast(3))
    }

    @Test
    fun manualCancellationIsReportedOnlyOnce() = runTest {
        val coordinator = OperationCoordinator(this)
        coordinator.launch({}, { error("unexpected failure") }, {}, block = { awaitCancellation() })
        advanceUntilIdle()

        assertTrue(coordinator.cancelCurrent())
        assertFalse(coordinator.cancelCurrent())
        advanceUntilIdle()
    }
}
