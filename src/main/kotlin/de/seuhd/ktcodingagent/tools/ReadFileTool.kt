package de.seuhd.ktcodingagent.tools

import de.seuhd.ktcodingagent.io.Workspace
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.nio.file.Files

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
        val path = (args["path"] as? JsonPrimitive)?.contentOrNull
            ?: return ToolResult.error("missing required argument: path")
        val start = (args["start"] as? JsonPrimitive)?.intOrNull ?: 1
        val end = (args["end"] as? JsonPrimitive)?.intOrNull ?: 200
        if (start < 1 || end < start) return ToolResult.error("invalid line range")

        val file = workspace.resolveSandboxed(path)
        if (!Files.isRegularFile(file)) {
            return ToolResult.error("not a file: $path")
        }

        val lines = Files.readAllLines(file)
        val from = start - 1
        val until = minOf(end, lines.size)
        val rendered = if (from >= lines.size) {
            emptyList()
        } else {
            (from until until).map { index -> "%4d: %s".format(index + 1, lines[index]) }
        }

        val rel = workspace.root.relativize(file).toString()
        val body = rendered.joinToString("\n")
        return if (body.isEmpty()) ToolResult("# $rel") else ToolResult("# $rel\n$body")
    }
}
