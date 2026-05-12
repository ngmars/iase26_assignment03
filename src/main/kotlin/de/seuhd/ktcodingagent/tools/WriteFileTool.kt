package de.seuhd.ktcodingagent.tools

import de.seuhd.ktcodingagent.io.Workspace
import kotlinx.serialization.json.JsonObject

/**
 * Sub-exercise (a): implement [execute].
 *
 * - Read required "path" and "content" args.
 * - Resolve via workspace.resolveSandboxed(...). If the target is an existing directory, return an error.
 * - Create parent directories as needed; write the content as UTF-8.
 * - Return ToolResult("wrote <relpath> (<n> chars)").
 *
 * See ToolsTest for the contract.
 */
class WriteFileTool(private val workspace: Workspace) : Tool {
    override val name: String = "write_file"
    override val description: String = "Write a text file."
    override val schema: Map<String, String> = mapOf(
        "path" to "str",
        "content" to "str"
    )
    override val risky: Boolean = true

    override fun execute(args: JsonObject): ToolResult {
        TODO("Implement write_file (sub-exercise (a)).")
    }
}
