package de.seuhd.ktcodingagent.tools

import de.seuhd.ktcodingagent.session.HistoryEntry
import kotlinx.serialization.json.JsonObject

const val MAX_TOOL_OUTPUT = 4_000

/**
 * Sub-exercise (a): implement [dispatch].
 *
 * Pipeline order:
 *   1. Look up the tool by name. Unknown name -> error result.
 *   2. Validate args via Validation.validate(name, args). On failure, return an error
 *      that includes the example from Validation.toolCallExample(name).
 *   3. Reject the call as "repeated identical tool call" if a prior successful ToolEntry
 *      has the same name and identical args, *unless* a successful `write_file` has run
 *      between that prior call and the current one. A `write_file` invalidates the cache
 *      because the workspace state has changed. Identical `write_file` calls (same path,
 *      same content) stay blocked — there is nothing between them.
 *   4. If the tool is risky and the approval gate denies the call, return an error.
 *   5. Execute the tool. Catch SecurityException (path escape) and other exceptions as errors.
 *   6. Clip the resulting content to MAX_TOOL_OUTPUT chars before returning.
 *
 * See ToolRegistryTest for the contract.
 */
class ToolRegistry(
    val tools: List<Tool>,
    private val approvalGate: ApprovalGate = AutoApprove
) {
    private val byName: Map<String, Tool> = tools.associateBy { it.name }

    fun dispatch(name: String, args: JsonObject, history: List<HistoryEntry>): ToolResult {
        TODO("Implement dispatch (sub-exercise (a)).")
    }
}
