package de.seuhd.ktcodingagent.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A single line of the transcript.
 *
 * kotlinx serialization encodes each variant with a `"type"` discriminator (set via
 * [SerialName]) so the persisted JSON is self-describing.
 */
@Serializable
sealed class HistoryEntry {
    /**
     * ISO-8601 timestamp recorded when the entry was appended. Consumed only by
     * kotlinx-serialization when writing the session JSON; no Kotlin call site reads
     * it back at runtime, so the IDE's "unused" warning is a false positive.
     */
    @Suppress("unused")
    abstract val createdAt: String

    /** A message the human user typed at the REPL (or the test scripted). */
    @Serializable
    @SerialName("user")
    data class UserEntry(
        val content: String,
        @Suppress("unused") override val createdAt: String
    ) : HistoryEntry()

    /**
     * Anything the assistant produced: a final answer accepted by [Parsed.Final], or a
     * `Retry` notice the agent appended after the parser rejected the model's output.
     */
    @Serializable
    @SerialName("assistant")
    data class AssistantEntry(
        val content: String,
        @Suppress("unused") override val createdAt: String
    ) : HistoryEntry()

    /**
     * Outcome of a tool call. [isError] distinguishes successful results from validation
     * failures, security violations, and tool-side exceptions.
     */
    @Serializable
    @SerialName("tool")
    data class ToolEntry(
        val name: String,
        val args: JsonObject,
        val content: String,
        val isError: Boolean,
        @Suppress("unused") override val createdAt: String
    ) : HistoryEntry()
}
