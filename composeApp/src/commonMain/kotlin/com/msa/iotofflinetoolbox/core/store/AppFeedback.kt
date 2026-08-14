package com.msa.iotofflinetoolbox.core.store

enum class FeedbackKind {
    ERROR,
    NOTICE,
}

/** Typed one-shot application feedback consumed by the presentation layer. */
data class AppFeedback(
    val kind: FeedbackKind,
    val message: String,
    val source: String? = null,
) {
    companion object {
        fun error(message: String, source: String? = null) = AppFeedback(FeedbackKind.ERROR, message, source)
        fun notice(message: String, source: String? = null) = AppFeedback(FeedbackKind.NOTICE, message, source)
    }
}
