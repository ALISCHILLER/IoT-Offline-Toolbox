package com.msa.iotofflinetoolbox.core.store

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Serializes user-triggered toolbox operations without blocking the UI thread.
 *
 * A cancelled job remains the operation tail until it has actually completed. A successor therefore
 * joins the previous job before publishing any state, preventing late socket/DNS results from an
 * obsolete operation from overwriting a newer screen state.
 */
internal class OperationCoordinator(
    private val scope: CoroutineScope,
) {
    private var activeJob: Job? = null
    private var operationTail: Job? = null
    private var sequence: Long = 0

    fun cancelCurrent(): Boolean {
        val job = activeJob ?: return false
        sequence += 1
        job.cancel()
        activeJob = null
        // operationTail intentionally keeps the cancelled job until a successor can join it.
        return true
    }

    fun launch(
        onStart: () -> Unit,
        onFailure: (Throwable) -> Unit,
        onFinish: () -> Unit,
        block: suspend () -> Unit,
    ) {
        val operationId = ++sequence
        val previousJob = operationTail
        previousJob?.cancel()

        val job = scope.launch(start = CoroutineStart.LAZY) operation@{
            previousJob?.join()
            if (sequence != operationId) return@operation

            try {
                onStart()
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (sequence == operationId) onFailure(failure)
            } finally {
                if (sequence == operationId) {
                    activeJob = null
                    onFinish()
                }
            }
        }
        activeJob = job
        operationTail = job
        job.start()
    }

    fun close() {
        sequence += 1
        activeJob?.cancel()
        operationTail?.cancel()
        activeJob = null
    }
}
