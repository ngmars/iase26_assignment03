package de.seuhd.ktcodingagent.parse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Sub-exercise (c): the response parser.
 *
 * Implement [parse] to return one of:
 *   - Parsed.Tool(name, args)   when the raw text contains <tool>{json}</tool>
 *   - Parsed.Final(text)        when the raw text contains <final>...</final>,
 *                               or contains neither tag but is non-empty
 *   - Parsed.Retry(notice)      on empty input, empty <final>, malformed JSON
 *                               inside <tool>, missing "name", or non-object "args"
 *
 * Retry notices follow the form:
 *   "Runtime notice: <problem>. Reply with a valid <tool> call or a non-empty <final> answer."
 *
 * See ResponseParserTest for the contract.
 */
object ResponseParser {
    private val toolRegex = Regex("<tool>(.*?)</tool>", setOf(RegexOption.DOT_MATCHES_ALL))
    private val finalRegex = Regex("<final>(.*?)</final>", setOf(RegexOption.DOT_MATCHES_ALL))

    fun parse(raw: String): Parsed {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return retry("empty response")

        val toolBody = toolRegex.find(raw)?.groupValues?.get(1)?.trim()
        if (toolBody != null) {
            val payload = try {
                Json.parseToJsonElement(toolBody).jsonObject
            } catch (_: Exception) {
                return retry("malformed tool JSON")
            }
            val name = (payload["name"] as? JsonPrimitive)?.contentOrNull
            if (name.isNullOrBlank()) return retry("missing or non-string tool name")

            val args = payload["args"] as? JsonObject
                ?: return retry("tool args must be an object")
            return Parsed.Tool(name, args)
        }

        val finalBody = finalRegex.find(raw)?.groupValues?.get(1)?.trim()
        if (finalBody != null) {
            if (finalBody.isEmpty()) return retry("empty <final>")
            return Parsed.Final(finalBody)
        }

        return Parsed.Final(trimmed)
    }

    private fun retry(problem: String): Parsed.Retry =
        Parsed.Retry(
            "Runtime notice: $problem. Reply with a valid <tool> call or a non-empty <final> answer."
        )
}
