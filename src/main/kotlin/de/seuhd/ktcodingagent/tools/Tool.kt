package de.seuhd.ktcodingagent.tools

import kotlinx.serialization.json.JsonObject

/**
 * Contract for a single tool the agent can invoke.
 *
 * The agent never executes a [Tool] directly; calls go through [ToolRegistry.dispatch],
 * which applies validation, repeat detection, approval gating, and output clipping.
 *
 * Implementations should be small and side-effect-narrow. Errors inside [execute] should
 * return a `ToolResult` with `isError = true` (preferred) or throw — the registry catches
 * exceptions and turns them into error results so the agent loop never crashes on a tool.
 */
interface Tool {
    /** Stable identifier the model uses to invoke this tool, e.g. `"read_file"`. */
    val name: String

    /** One-line human-readable description embedded in the prompt prefix. */
    val description: String

    /**
     * Arg name to type/default sketch, e.g. `mapOf("path" to "str", "start" to "int=1")`.
     * Rendered into the tool listing inside the prompt prefix.
     */
    val schema: Map<String, String>

    /** `true` if invocation should be gated by an [ApprovalGate]; e.g. `write_file`. */
    val risky: Boolean

    /** Executes the tool. Path-bearing args should be resolved via [de.seuhd.ktcodingagent.io.Workspace.resolveSandboxed]. */
    fun execute(args: JsonObject): ToolResult
}
