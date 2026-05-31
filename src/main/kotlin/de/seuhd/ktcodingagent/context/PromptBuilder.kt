package de.seuhd.ktcodingagent.context

import de.seuhd.ktcodingagent.session.Session
import de.seuhd.ktcodingagent.session.HistoryEntry
import de.seuhd.ktcodingagent.tools.Tool
import de.seuhd.ktcodingagent.tools.Validation

/**
 * Sub-exercise (b): implement [build].
 *
 * The full prompt is:
 *   prefix + "\n\n" + memoryText + "\n\nTranscript:\n" + historyText + "\n\nCurrent user request:\n" + userMessage
 *
 * The stable prefix (built once at construction; byte-identical across calls):
 *   <promptPreamble>
 *
 *   Tools:
 *   - <name>(<schema fields joined by ", ">) [safe|approval required] <description>
 *   ... (one line per tool)
 *
 *   Valid response examples:
 *   <tool>{"name":"list_files","args":{"path":"."}}</tool>
 *   <tool>{"name":"read_file","args":{"path":"README.md","start":1,"end":80}}</tool>
 *   <tool>{"name":"write_file","args":{"path":"hello.txt","content":"hi\n"}}</tool>
 *   <final>Done.</final>
 *
 *   <workspace.render()>
 *
 * The memory text:
 *   Memory:
 *   - task: <task or "->">
 *   - files: <comma-separated paths or "->">
 *   - notes:
 *   - <note>
 *   ...
 *
 * The transcript text:
 *   - "- empty" when history is empty
 *   - otherwise, one or two lines per entry:
 *     ToolEntry      -> "[tool:<name>] <args as compact JSON>" then clipped content
 *     UserEntry      -> "[user] <clipped content>"
 *     AssistantEntry -> "[assistant] <clipped content>"
 *   - recent window: entries with index >= max(0, history.size - 6) use limit 900;
 *     older entries use 180 (tool) or 220 (user/assistant)
 *   - final transcript clipped to MAX_HISTORY = 12000 chars
 *
 * See PromptBuilderTest for the contract.
 */
class PromptBuilder(
    private val promptPreamble: String,
    private val tools: List<Tool>,
    private val workspace: WorkspaceContext
) {
    private val stablePrefix: String = buildPrefix()

    fun build(session: Session, userMessage: String): String {
        val memoryText = buildMemoryText(session)
        val historyText = buildHistoryText(session)
        return stablePrefix +
            "\n\n" +
            memoryText +
            "\n\nTranscript:\n" +
            historyText +
            "\n\nCurrent user request:\n" +
            userMessage
    }

    fun prefix(): String {
        return stablePrefix
    }

    private fun buildPrefix(): String {
        val toolLines = tools.joinToString("\n") { tool ->
            val schema = tool.schema.entries.joinToString(", ") { (name, ty) -> "$name=$ty" }
            val risk = if (tool.risky) "approval required" else "safe"
            "- ${tool.name}($schema) [$risk] ${tool.description}"
        }
        return buildString {
            append(promptPreamble)
            append("\n\nTools:\n")
            append(toolLines)
            append("\n\nValid response examples:\n")
            appendLine(Validation.toolCallExample("list_files"))
            appendLine(Validation.toolCallExample("read_file"))
            appendLine(Validation.toolCallExample("write_file"))
            append("<final>Done.</final>\n\n")
            append(workspace.render())
        }
    }

    private fun buildMemoryText(session: Session): String {
        val task = session.memory.task.ifBlank { "-" }
        val files = if (session.memory.files.isEmpty()) "-" else session.memory.files.joinToString(", ")
        val notes = if (session.memory.notes.isEmpty()) {
            "-"
        } else {
            session.memory.notes.joinToString("\n") { "- $it" }
        }
        return buildString {
            appendLine("Memory:")
            appendLine("- task: $task")
            appendLine("- files: $files")
            appendLine("- notes:")
            append(notes)
        }
    }

    private fun buildHistoryText(session: Session): String {
        if (session.history.isEmpty()) return "- empty"
        val cutoff = (session.history.size - 6).coerceAtLeast(0)
        val rendered = session.history.mapIndexed { index, entry ->
            val isRecent = index >= cutoff
            when (entry) {
                is HistoryEntry.ToolEntry -> {
                    val limit = if (isRecent) 900 else 180
                    val content = clip(entry.content, limit)
                    "[tool:${entry.name}] ${entry.args}\n$content"
                }
                is HistoryEntry.UserEntry -> {
                    val limit = if (isRecent) 900 else 220
                    "[user] ${clip(entry.content, limit)}"
                }
                is HistoryEntry.AssistantEntry -> {
                    val limit = if (isRecent) 900 else 220
                    "[assistant] ${clip(entry.content, limit)}"
                }
            }
        }.joinToString("\n")
        return clip(rendered, MAX_HISTORY)
    }

    private fun clip(text: String, limit: Int): String {
        if (text.length <= limit) return text
        return text.take(limit) + "...[truncated ${text.length - limit} chars]"
    }

    companion object {
        private const val MAX_HISTORY = 12_000
    }
}
