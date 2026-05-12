package de.seuhd.ktcodingagent

/**
 * Sub-exercise (d): add at least three scripted scenarios in this class that exercise
 * [Agent] via [StubModelClient], beyond the twelve cases provided in [AgentTest].
 *
 * Suggested scenarios:
 *   1. A sequence where the agent receives a tool-error response and surfaces it.
 *   2. A sequence where the model attempts a path-safety violation that the
 *      sandbox rejects inside the loop.
 *   3. One scenario of your own design.
 *
 * Use [buildAgentForTest] from [AgentTestSupport] to wire up an Agent with your
 * scripted StubModelClient outputs, then assert on `agent.session.history` and the
 * returned final answer.
 *
 * This class ships empty so it does not contribute to the failing-test count. JUnit
 * picks up no tests until you add @Test methods.
 */
class ScriptedAgentTest
