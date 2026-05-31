package de.seuhd.ktcodingagent.tools

import de.seuhd.ktcodingagent.io.Workspace
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.nio.file.Files

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
        val path = (args["path"] as? JsonPrimitive)?.contentOrNull
            ?: return ToolResult.error("missing required argument: path")
        val content = (args["content"] as? JsonPrimitive)?.contentOrNull
            ?: return ToolResult.error("missing required argument: content")

        val target = workspace.resolveSandboxed(path)
        if (Files.isDirectory(target)) {
            return ToolResult.error("cannot write to directory: $path")
        }

        target.parent?.let { Files.createDirectories(it) }
        Files.writeString(target, content)
        val rel = workspace.root.relativize(target).toString()
        return ToolResult("wrote $rel (${content.length} chars)")
    }
}
