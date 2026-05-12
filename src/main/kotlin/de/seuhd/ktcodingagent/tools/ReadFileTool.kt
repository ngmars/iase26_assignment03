package de.seuhd.ktcodingagent.tools

import de.seuhd.ktcodingagent.io.Workspace
import kotlinx.serialization.json.JsonObject

/**
 * Sub-exercise (a): implement [execute].
 *
 * - Read required "path" arg and optional "start" (default 1) and "end" (default 200).
 * - Resolve via workspace.resolveSandboxed(...). If not a regular file, return an error.
 * - Validate the range; return an error on invalid range.
 * - Read the file, slice lines [start..end], and format each line as
 *   "%4d: %s" prefixed by a "# <relpath>" header.
 *
 * See ToolsTest for the contract.
 */
class ReadFileTool(private val workspace: Workspace) : Tool {
    override val name: String = "read_file"
    override val description: String = "Read a UTF-8 file by line range."
    override val schema: Map<String, String> = mapOf(
        "path" to "str",
        "start" to "int=1",
        "end" to "int=200"
    )
    override val risky: Boolean = false

    override fun execute(args: JsonObject): ToolResult {
        TODO("Implement read_file (sub-exercise (a)).")
    }
}
