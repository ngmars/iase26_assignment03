package de.seuhd.ktcodingagent

import de.seuhd.ktcodingagent.context.PromptBuilder
import de.seuhd.ktcodingagent.model.ModelClient
import de.seuhd.ktcodingagent.parse.Parsed
import de.seuhd.ktcodingagent.parse.ResponseParser
import de.seuhd.ktcodingagent.session.HistoryEntry
import de.seuhd.ktcodingagent.session.Session
import de.seuhd.ktcodingagent.session.SessionStore
import de.seuhd.ktcodingagent.tools.ToolRegistry
import kotlinx.serialization.json.JsonObject
import java.time.OffsetDateTime

/**
 * Sub-exercise (c): the agent loop.
 *
 * Implement [ask] per the loop in the assignment sheet:
 *   - record the user message and persist the session
 *   - on each iteration, build the prompt, call modelClient.complete,
 *     parse the response, and act on the three cases (Tool, Final, Retry)
 *   - bound by maxSteps (tool calls) and maxAttempts = 3 * maxSteps
 *   - on tool calls, call session.memory.recordToolCall(...)
 *   - distinguish the two stop conditions in the final message
 *
 * See AgentTest for the contract.
 */
class Agent(
    private val modelClient: ModelClient,
    private val registry: ToolRegistry,
    private val promptBuilder: PromptBuilder,
    val session: Session,
    private val sessionStore: SessionStore,
    private val maxSteps: Int = 16,
    private val maxNewTokens: Int = 1024,
    private val onToolCall: (name: String, args: JsonObject, content: String, isError: Boolean) -> Unit = { _, _, _, _ -> }
) {
    fun ask(userMessage: String): String {
        session.memory.setInitialTask(userMessage)
        session.record(HistoryEntry.UserEntry(userMessage, now()))
        sessionStore.save(session)

        val maxAttempts = maxSteps * 3
        var toolSteps = 0
        var attempts = 0

        while (true) {
            if (toolSteps >= maxSteps) {
                return stopWith("Stopped after reaching the step limit without a final answer.")
            }
            if (attempts >= maxAttempts) {
                return stopWith("Stopped after too many malformed model responses without a valid tool call or final answer.")
            }

            val prompt = promptBuilder.build(session, userMessage)
            val raw = modelClient.complete(prompt, maxNewTokens)
            attempts++

            when (val parsed = ResponseParser.parse(raw)) {
                is Parsed.Retry -> {
                    session.record(HistoryEntry.AssistantEntry(parsed.notice, now()))
                    sessionStore.save(session)
                }

                is Parsed.Final -> {
                    session.memory.recordFinal(parsed.text)
                    session.record(HistoryEntry.AssistantEntry(parsed.text, now()))
                    sessionStore.save(session)
                    return parsed.text
                }

                is Parsed.Tool -> {
                    val result = registry.dispatch(parsed.name, parsed.args, session.history)
                    onToolCall(parsed.name, parsed.args, result.content, result.isError)

                    session.record(
                        HistoryEntry.ToolEntry(
                            name = parsed.name,
                            args = parsed.args,
                            content = result.content,
                            isError = result.isError,
                            createdAt = now()
                        )
                    )
                    session.memory.recordToolCall(parsed.name, parsed.args, result.content)
                    sessionStore.save(session)
                    toolSteps++

                    if (result.isError && "repeated identical tool call" in result.content) {
                        val synthesized = priorSuccessfulToolContent(parsed.name, parsed.args) ?: result.content
                        return stopWith(synthesized)
                    }
                }
            }
        }
    }

    fun reset() {
        session.history.clear()
        session.memory.clear()
        sessionStore.save(session)
    }

    private fun priorSuccessfulToolContent(name: String, args: JsonObject): String? =
        session.history
            .asReversed()
            .filterIsInstance<HistoryEntry.ToolEntry>()
            .firstOrNull { it.name == name && it.args == args && !it.isError }
            ?.content

    private fun stopWith(message: String): String {
        session.memory.recordFinal(message)
        session.record(HistoryEntry.AssistantEntry(message, now()))
        sessionStore.save(session)
        return message
    }

    private fun now(): String = OffsetDateTime.now().toString()
}
