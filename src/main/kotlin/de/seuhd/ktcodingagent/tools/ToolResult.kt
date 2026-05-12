package de.seuhd.ktcodingagent.tools

/**
 * Outcome of a tool invocation, recorded verbatim in the session transcript.
 *
 * @property content   string the model will see on the next turn. For successful calls this is
 *                     the tool's output; for errors it is the diagnostic message.
 * @property isError   `true` if the call failed (validation, repeat detection, approval denied,
 *                     or an exception inside `execute`). The agent loop continues either way;
 *                     the flag lets the transcript distinguish successful from failed calls.
 */
data class ToolResult(val content: String, val isError: Boolean = false) {
    companion object {
        /** Shorthand for `ToolResult(message, isError = true)`. */
        fun error(message: String): ToolResult = ToolResult(message, isError = true)
    }
}
