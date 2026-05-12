package de.seuhd.ktcodingagent

import de.seuhd.ktcodingagent.context.PromptBuilder
import de.seuhd.ktcodingagent.model.ModelClient
import de.seuhd.ktcodingagent.session.Session
import de.seuhd.ktcodingagent.session.SessionStore
import de.seuhd.ktcodingagent.tools.ToolRegistry
import kotlinx.serialization.json.JsonObject

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
        TODO("Implement the agent loop (sub-exercise (d)).")
    }

    fun reset() {
        session.history.clear()
        session.memory.clear()
        sessionStore.save(session)
    }
}
