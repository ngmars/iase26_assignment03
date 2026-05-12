package de.seuhd.ktcodingagent.context

import de.seuhd.ktcodingagent.session.HistoryEntry
import de.seuhd.ktcodingagent.session.Session
import de.seuhd.ktcodingagent.tools.ListFilesTool
import de.seuhd.ktcodingagent.tools.ReadFileTool
import de.seuhd.ktcodingagent.tools.Tool
import de.seuhd.ktcodingagent.io.Workspace
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptBuilderTest {

    private fun newSession(root: Path): Session = Session(
        id = "test",
        createdAt = OffsetDateTime.now().toString(),
        workspaceRoot = root.toString(),
        modelName = "stub"
    )

    private fun newBuilder(root: Path): PromptBuilder {
        val workspace = Workspace(root)
        val tools: List<Tool> = listOf(ListFilesTool(workspace), ReadFileTool(workspace))
        val ctx = WorkspaceContext(
            cwd = root.toString(),
            repoRoot = root.toString(),
            branch = "main",
            defaultBranch = "main",
            status = "clean",
            recentCommits = emptyList(),
            projectDocs = emptyMap()
        )
        return PromptBuilder("You are an agent.", tools, ctx)
    }

    @Test
    fun stablePrefixIsByteIdenticalAcrossBuilds(@TempDir root: Path) {
        Files.writeString(root.resolve("a.txt"), "x")
        val builder = newBuilder(root)
        val sessionA = newSession(root).also {
            it.memory.task = "first"
            it.history.add(HistoryEntry.UserEntry("hi", "t1"))
        }
        val sessionB = newSession(root).also {
            it.memory.task = "second"
            it.memory.files += "a.txt"
            it.memory.notes += "note"
            it.history.add(HistoryEntry.AssistantEntry("there", "t2"))
        }
        val promptA = builder.build(sessionA, "do this")
        val promptB = builder.build(sessionB, "do that")
        val prefixA = promptA.substringBefore("\n\nMemory:")
        val prefixB = promptB.substringBefore("\n\nMemory:")
        assertEquals(prefixA, prefixB)
        assertEquals(prefixA, builder.prefix())
    }

    @Test
    fun memorySectionAppearsAfterPrefix(@TempDir root: Path) {
        val builder = newBuilder(root)
        val session = newSession(root).also { it.memory.task = "hello" }
        val prompt = builder.build(session, "go")
        assertTrue(prompt.indexOf("Memory:") > prompt.indexOf("Workspace:"))
        assertTrue("task: hello" in prompt)
    }

    @Test
    fun transcriptSectionAppearsAfterMemory(@TempDir root: Path) {
        val builder = newBuilder(root)
        val session = newSession(root).also {
            it.history.add(HistoryEntry.UserEntry("inspect", "t1"))
        }
        val prompt = builder.build(session, "again")
        assertTrue(prompt.indexOf("Transcript:") > prompt.indexOf("Memory:"))
        assertTrue("[user] inspect" in prompt)
    }

    @Test
    fun userRequestAppearsLast(@TempDir root: Path) {
        val builder = newBuilder(root)
        val session = newSession(root)
        val prompt = builder.build(session, "the request")
        assertTrue(prompt.trimEnd().endsWith("the request"))
        assertTrue("Current user request:" in prompt)
    }

    @Test
    fun prefixListsAllTools(@TempDir root: Path) {
        val builder = newBuilder(root)
        val prefix = builder.prefix()
        assertTrue("Tools:" in prefix)
        assertTrue("- list_files(" in prefix)
        assertTrue("- read_file(" in prefix)
        assertTrue("[safe]" in prefix)
    }
}
