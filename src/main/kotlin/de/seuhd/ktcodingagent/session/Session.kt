package de.seuhd.ktcodingagent.session

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Durable state of a single agent run, serialized to JSON via [SessionStore].
 *
 * Two layers of state:
 *  - [history]: the full transcript (user / assistant / tool entries) appended to as the
 *    loop progresses; used to rebuild the prompt every turn.
 *  - [memory]: a compact distilled view (task, recently-touched files, short notes) used to
 *    keep the prompt size manageable while remaining informative.
 *
 * [modelName] is recorded so the persisted JSON identifies which model produced it.
 */
@Serializable
data class Session(
    val id: String,
    val createdAt: String,
    val workspaceRoot: String,
    val modelName: String,
    val history: MutableList<HistoryEntry> = mutableListOf(),
    val memory: SessionMemory = SessionMemory()
) {
    /** Appends [entry] to the in-memory history. Persistence is the caller's responsibility. */
    fun record(entry: HistoryEntry) {
        history.add(entry)
    }

    companion object {
        private val ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        /** New session id in the form `yyyyMMdd-HHmmss-{uuid6}`. */
        fun newId(): String = LocalDateTime.now().format(ID_FORMAT) + "-" + UUID.randomUUID().toString().take(6)
    }
}
