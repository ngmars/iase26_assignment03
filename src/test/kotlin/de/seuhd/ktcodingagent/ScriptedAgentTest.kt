package de.seuhd.ktcodingagent

import de.seuhd.ktcodingagent.session.HistoryEntry
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
class ScriptedAgentTest {

    @Test
    fun scriptedScenarioToolErrorThenRecoveryFinal(@TempDir tempRoot: Path) {
        val (agent, _) = buildAgentForTest(
            tempRoot,
            listOf(
                """<tool>{"name":"write_file","args":{"path":"." ,"content":"oops"}}</tool>""",
                "<final>The write failed because the target is a directory.</final>"
            )
        )

        val answer = agent.ask("Write into current directory")

        assertEquals("The write failed because the target is a directory.", answer)
        val toolEntries = agent.session.history.filterIsInstance<HistoryEntry.ToolEntry>()
        assertEquals(1, toolEntries.size)
        assertTrue(toolEntries.first().isError)
        assertTrue("directory" in toolEntries.first().content)
    }

    @Test
    fun scriptedScenarioPathSafetyViolationRejectedInsideLoop(@TempDir tempRoot: Path) {
        val outside = tempRoot.resolveSibling("scripted-outside.txt")
        Files.writeString(outside, "secret\n")
        try {
            val (agent, _) = buildAgentForTest(
                tempRoot,
                listOf(
                    """<tool>{"name":"read_file","args":{"path":"../scripted-outside.txt"}}</tool>""",
                    "<final>Path traversal was blocked by sandbox.</final>"
                )
            )

            val answer = agent.ask("Read parent file")

            assertEquals("Path traversal was blocked by sandbox.", answer)
            val tool = agent.session.history.filterIsInstance<HistoryEntry.ToolEntry>().first()
            assertTrue(tool.isError)
            assertTrue("escapes workspace" in tool.content)
        } finally {
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun scriptedScenarioRetryThenToolThenFinalTracksPromptAndMemory(@TempDir tempRoot: Path) {
        Files.writeString(tempRoot.resolve("notes.txt"), "line one\nline two\n")
        val (agent, stub) = buildAgentForTest(
            tempRoot,
            listOf(
                "",
                """<tool>{"name":"read_file","args":{"path":"notes.txt","start":2,"end":2}}</tool>""",
                "<final>Done after retry.</final>"
            )
        )

        val answer = agent.ask("Read the second line")

        assertEquals("Done after retry.", answer)
        assertEquals(3, stub.prompts.size)
        assertTrue(agent.session.history.filterIsInstance<HistoryEntry.AssistantEntry>().any { "empty response" in it.content })
        assertTrue(agent.session.history.filterIsInstance<HistoryEntry.ToolEntry>().any { it.name == "read_file" && !it.isError })
        assertTrue(agent.session.memory.files.contains("notes.txt"))
    }
}
