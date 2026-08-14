package com.msa.iotofflinetoolbox.core.store

import kotlin.random.Random
import kotlin.time.Clock

/** Clock boundary used by application orchestration and deterministic tests. */
fun interface ToolboxClock {
    fun nowMillis(): Long
}

/** Identifier boundary used to avoid hard-coding randomness inside business orchestration. */
fun interface ToolboxIdGenerator {
    fun nextId(prefix: String, timestampMillis: Long): String
}

/** Runtime dependencies that are nondeterministic in production and replaceable in tests. */
data class ToolboxRuntime(
    val clock: ToolboxClock = ToolboxClock { Clock.System.now().toEpochMilliseconds() },
    val idGenerator: ToolboxIdGenerator = ToolboxIdGenerator { prefix, timestampMillis ->
        "$prefix-$timestampMillis-${Random.nextInt(100_000, 999_999)}"
    },
)
