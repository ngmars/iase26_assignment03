package de.seuhd.ktcodingagent.context

import de.seuhd.ktcodingagent.session.Session
import de.seuhd.ktcodingagent.tools.Tool

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
    fun build(session: Session, userMessage: String): String {
        TODO("Implement PromptBuilder.build (sub-exercise (b)).")
    }

    fun prefix(): String {
        TODO("Implement PromptBuilder.prefix (sub-exercise (b)).")
    }
}
