package de.seuhd.ktcodingagent.parse

import kotlinx.serialization.json.JsonObject

/**
 * Outcome of parsing a single model response.
 *
 * The parser ([ResponseParser]) classifies every model output into one of three cases.
 * The agent loop then knows exactly what to do next:
 *  - [Tool]  — dispatch the call, record the result, continue.
 *  - [Final] — record the answer and return.
 *  - [Retry] — record the notice as an assistant message; the next prompt will include it,
 *              giving the model a chance to correct itself.
 */
sealed class Parsed {
    /** A well-formed tool call. */
    data class Tool(val name: String, val args: JsonObject) : Parsed()

    /** A non-empty terminal answer extracted from `<final>...</final>` (or a tag-less reply). */
    data class Final(val text: String) : Parsed()

    /** The model emitted unparseable output. [notice] is appended to the transcript verbatim. */
    data class Retry(val notice: String) : Parsed()
}
