package de.seuhd.ktcodingagent.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val FILES_LIMIT = 8
private const val NOTES_LIMIT = 5
private const val NOTE_RESULT_CLIP = 220
private val PATH_TOOLS = setOf("read_file", "write_file", "patch_file")

/**
 * Compact, "distilled" view of session state, kept alongside the full transcript so the
 * prompt can stay small.
 *
 * Three buckets:
 *  - [task]   — the first user message of the run, clipped to 300 characters. Set once
 *               via [setInitialTask]; subsequent user messages do not change it.
 *  - [files]  — LRU-ordered paths the agent has touched (cap [FILES_LIMIT] = 8). Updated
 *               by [recordToolCall] for the three path-bearing tools.
 *  - [notes]  — short one-line summaries of recent tool results and the final answer (cap
 *               [NOTES_LIMIT] = 5). Each note is `"name: <single-line result clipped to 220>"`.
 */
@Serializable
data class SessionMemory(
    var task: String = "",
    val files: MutableList<String> = mutableListOf(),
    val notes: MutableList<String> = mutableListOf()
) {
    /** Records a tool dispatch into [files] (if it has a `path` arg) and [notes]. */
    fun recordToolCall(name: String, args: JsonObject, result: String) {
        if (name in PATH_TOOLS) {
            val pathPrimitive = args["path"] as? JsonPrimitive
            val path = if (pathPrimitive?.isString == true) pathPrimitive.content else null
            if (!path.isNullOrBlank()) {
                remember(files, path, FILES_LIMIT)
            }
        }
        val note = "$name: ${clipSingleLine(result, NOTE_RESULT_CLIP)}"
        remember(notes, note, NOTES_LIMIT)
    }

    /** Records the agent's final answer as a one-line note in [notes]. */
    fun recordFinal(text: String) {
        remember(notes, clipSingleLine(text, NOTE_RESULT_CLIP), NOTES_LIMIT)
    }

    /** Captures the first user message of the session as [task]. No-op if [task] is non-empty. */
    fun setInitialTask(userMessage: String) {
        if (task.isEmpty()) {
            task = userMessage.trim().take(300)
        }
    }

    /** Resets the distilled memory in place. Invoked by the REPL's `/reset` slash command. */
    fun clear() {
        task = ""
        files.clear()
        notes.clear()
    }

    companion object {
        /**
         * Move [item] to the end of [bucket] (LRU touch), then trim to the last [limit]
         * entries. If [item] is already present, the existing position is removed first so
         * we don't end up with duplicates.
         */
        fun <T> remember(bucket: MutableList<T>, item: T, limit: Int) {
            bucket.remove(item)
            bucket.add(item)
            while (bucket.size > limit) bucket.removeAt(0)
        }

        private fun clipSingleLine(text: String, limit: Int): String {
            val flat = text.replace('\n', ' ')
            return if (flat.length <= limit) flat
            else flat.take(limit) + "...[truncated ${flat.length - limit} chars]"
        }
    }
}
