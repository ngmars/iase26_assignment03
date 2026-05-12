package de.seuhd.ktcodingagent.tools

import de.seuhd.ktcodingagent.io.Workspace
import kotlinx.serialization.json.JsonObject

/**
 * Sub-exercise (a): implement [execute].
 *
 * - Read the optional "path" arg (default ".") and resolve it via workspace.resolveSandboxed(...).
 * - If not a directory, return an error ToolResult.
 * - List entries (directories first, then files; alphabetic). Hide IGNORED_PATH_NAMES.
 * - Format each entry as "[D] relpath" or "[F] relpath". Return "(empty)" if none.
 *
 * See ToolsTest for the contract.
 */
private val IGNORED_PATH_NAMES = setOf(".git", ".kt-coding-agent", "build", ".gradle", ".idea")

class ListFilesTool(private val workspace: Workspace) : Tool {
    override val name: String = "list_files"
    override val description: String = "List files in the workspace."
    override val schema: Map<String, String> = mapOf("path" to "str='.'")
    override val risky: Boolean = false

    override fun execute(args: JsonObject): ToolResult {
        TODO("Implement list_files (sub-exercise (a)).")
    }
}
