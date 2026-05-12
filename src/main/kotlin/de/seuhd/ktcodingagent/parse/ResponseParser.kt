package de.seuhd.ktcodingagent.parse

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
    fun parse(raw: String): Parsed {
        TODO("Implement the parser (sub-exercise (d)).")
    }
}
