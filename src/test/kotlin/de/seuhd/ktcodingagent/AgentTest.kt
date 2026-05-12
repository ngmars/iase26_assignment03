package de.seuhd.ktcodingagent

import de.seuhd.ktcodingagent.session.HistoryEntry
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentTest {

    @Test
    fun happyPathSingleToolThenFinal(@TempDir tempRoot: Path) {
        Files.writeString(tempRoot.resolve("hello.txt"), "alpha\nbeta\n")
        val (agent, _) = buildAgentForTest(
            tempRoot,
            listOf(
                """<tool>{"name":"read_file","args":{"path":"hello.txt","start":1,"end":2}}</tool>""",
                "<final>Read the file successfully.</final>"
            )
        )
        val answer = agent.ask("Inspect hello.txt")
        assertEquals("Read the file successfully.", answer)
        assertTrue(agent.session.history.any { it is HistoryEntry.ToolEntry && it.name == "read_file" })
        assertTrue(agent.session.memory.files.contains("hello.txt"))
    }

    @Test
    fun twoToolsThenFinal(@TempDir tempRoot: Path) {
        Files.writeString(tempRoot.resolve("a.txt"), "alpha\n")
        val (agent, _) = buildAgentForTest(
            tempRoot,
            listOf(
                """<tool>{"name":"list_files","args":{"path":"."}}</tool>""",
                """<tool>{"name":"read_file","args":{"path":"a.txt"}}</tool>""",
                "<final>Saw a.txt.</final>"
            )
        )
        val answer = agent.ask("Survey the workspace")
        assertEquals("Saw a.txt.", answer)
        val toolNames = agent.session.history.filterIsInstance<HistoryEntry.ToolEntry>().map { it.name }
        assertEquals(listOf("list_files", "read_file"), toolNames)
    }

    @Test
    fun malformedJsonRecoversViaRetry(@TempDir tempRoot: Path) {
        Files.writeString(tempRoot.resolve("hello.txt"), "alpha\n")
        val (agent, _) = buildAgentForTest(
            tempRoot,
            listOf(
                """<tool>{"name":"read_file","args":"bad"}</tool>""",
                """<tool>{"name":"read_file","args":{"path":"hello.txt","start":1,"end":1}}</tool>""",
                "<final>Recovered.</final>"
            )
        )
        val answer = agent.ask("Inspect hello.txt")
        assertEquals("Recovered.", answer)
        val notices = agent.session.history.filterIsInstance<HistoryEntry.AssistantEntry>().map { it.content }
        assertTrue(notices.any { "valid <tool> call" in it })
    }

    @Test
    fun emptyResponseRecoversViaRetry(@TempDir tempRoot: Path) {
        val (agent, _) = buildAgentForTest(
            tempRoot,
            listOf("", "<final>Recovered after empty.</final>")
        )
        val answer = agent.ask("Anything")
        assertEquals("Recovered after empty.", answer)
        val notices = agent.session.history.filterIsInstance<HistoryEntry.AssistantEntry>().map { it.content }
        assertTrue(notices.any { "empty response" in it })
    }

    @Test
    fun emptyFinalRecoversViaRetry(@TempDir tempRoot: Path) {
        val (agent, _) = buildAgentForTest(
            tempRoot,
            listOf("<final></final>", "<final>Eventually answered.</final>")
        )
        val answer = agent.ask("Anything")
        assertEquals("Eventually answered.", answer)
        val notices = agent.session.history.filterIsInstance<HistoryEntry.AssistantEntry>().map { it.content }
        assertTrue(notices.any { "empty <final>" in it })
    }

    @Test
    fun missingNameRecoversViaRetry(@TempDir tempRoot: Path) {
        val (agent, _) = buildAgentForTest(
            tempRoot,
            listOf(
                """<tool>{"args":{}}</tool>""",
                "<final>Recovered.</final>"
            )
        )
        val answer = agent.ask("Try")
        assertEquals("Recovered.", answer)
    }

    @Test
    fun nonObjectArgsRecoversViaRetry(@TempDir tempRoot: Path) {
        val (agent, _) = buildAgentForTest(
            tempRoot,
            listOf(
                """<tool>{"name":"list_files","args":"oops"}</tool>""",
                "<final>Recovered.</final>"
            )
        )
        val answer = agent.ask("Try")
        assertEquals("Recovered.", answer)
    }

    @Test
    fun maxStepsExhaustedReturnsStopMessage(@TempDir tempRoot: Path) {
        val (agent, _) = buildAgentForTest(
            tempRoot,
            listOf(
                """<tool>{"name":"list_files","args":{"path":"."}}</tool>""",
                """<tool>{"name":"list_files","args":{"path":"sub"}}</tool>""",
                """<tool>{"name":"list_files","args":{"path":"other"}}</tool>"""
            ),
            maxSteps = 2
        )
        Files.createDirectory(tempRoot.resolve("sub"))
        Files.createDirectory(tempRoot.resolve("other"))
        val answer = agent.ask("Walk")
        assertEquals("Stopped after reaching the step limit without a final answer.", answer)
    }

    @Test
    fun maxAttemptsExhaustedReturnsStopMessage(@TempDir tempRoot: Path) {
        val (agent, _) = buildAgentForTest(
            tempRoot,
            // maxSteps=1 -> maxAttempts = 3 * maxSteps = 3; three empty responses with no tool steps trip it.
            listOf("", "", "", "", ""),
            maxSteps = 1
        )
        val answer = agent.ask("Anything")
        assertEquals(
            "Stopped after too many malformed model responses without a valid tool call or final answer.",
            answer
        )
    }

    @Test
    fun firstDedupBlockTerminatesTheLoop(@TempDir tempRoot: Path) {
        // The first dedup block ends the loop. The synthesized final is the prior
        // successful identical call's content — no meta-narrative about the loop.
        Files.writeString(tempRoot.resolve("hello.txt"), "alpha\n")
        val (agent, _) = buildAgentForTest(
            tempRoot,
            listOf(
                """<tool>{"name":"read_file","args":{"path":"hello.txt"}}</tool>""",
                """<tool>{"name":"read_file","args":{"path":"hello.txt"}}</tool>"""
            )
        )
        val answer = agent.ask("Inspect")
        assertTrue("alpha" in answer, "synthesized final must carry the prior successful result")
        assertTrue("retried" !in answer, "no meta-narrative about the loop")
        assertTrue("Stopped" !in answer, "no meta-narrative about the loop")
        val toolEntries = agent.session.history.filterIsInstance<HistoryEntry.ToolEntry>()
        assertEquals(2, toolEntries.size, "the first dedup-blocked entry triggers termination")
    }

    @Test
    fun toolErrorIsRecordedSoNextCallSeesIt(@TempDir tempRoot: Path) {
        val (agent, _) = buildAgentForTest(
            tempRoot,
            listOf(
                """<tool>{"name":"read_file","args":{"path":"missing.txt"}}</tool>""",
                "<final>I saw an error.</final>"
            )
        )
        val answer = agent.ask("Try")
        assertEquals("I saw an error.", answer)
        val toolEntries = agent.session.history.filterIsInstance<HistoryEntry.ToolEntry>()
        assertTrue(toolEntries.isNotEmpty())
        assertTrue(toolEntries.first().isError)
    }

    @Test
    fun sessionMemoryCapturesTouchedFilename(@TempDir tempRoot: Path) {
        Files.writeString(tempRoot.resolve("hello.txt"), "alpha\n")
        val (agent, _) = buildAgentForTest(
            tempRoot,
            listOf(
                """<tool>{"name":"read_file","args":{"path":"hello.txt"}}</tool>""",
                "<final>ok</final>"
            )
        )
        agent.ask("Read it")
        assertTrue(agent.session.memory.files.contains("hello.txt"))
    }
}
