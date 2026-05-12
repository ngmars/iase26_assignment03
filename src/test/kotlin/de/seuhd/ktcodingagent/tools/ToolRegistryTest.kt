package de.seuhd.ktcodingagent.tools

import de.seuhd.ktcodingagent.io.Workspace
import de.seuhd.ktcodingagent.session.HistoryEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolRegistryTest {

    private fun argsOf(vararg pairs: Pair<String, String>): JsonObject =
        JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })

    private fun emptyArgs(): JsonObject = JsonObject(emptyMap())

    private fun toolEntry(name: String, args: JsonObject): HistoryEntry.ToolEntry =
        HistoryEntry.ToolEntry(name, args, "ok", isError = false, createdAt = OffsetDateTime.now().toString())

    @Test
    fun dispatchRoutesByName(@TempDir root: Path) {
        Files.writeString(root.resolve("hello.txt"), "hi\n")
        val registry = ToolRegistry(
            listOf(ListFilesTool(Workspace(root)), ReadFileTool(Workspace(root)), WriteFileTool(Workspace(root)))
        )
        val result = registry.dispatch("list_files", emptyArgs(), emptyList())
        assertFalse(result.isError)
        assertTrue("hello.txt" in result.content)
    }

    @Test
    fun unknownToolReturnsError(@TempDir root: Path) {
        val registry = ToolRegistry(listOf(ListFilesTool(Workspace(root))))
        val result = registry.dispatch("nope", emptyArgs(), emptyList())
        assertTrue(result.isError)
        assertTrue("unknown tool 'nope'" in result.content)
    }

    @Test
    fun validationRunsBeforeApproval(@TempDir root: Path) {
        var approvalCalled = false
        val approval = ApprovalGate { _, _ -> approvalCalled = true; true }
        val registry = ToolRegistry(
            listOf(WriteFileTool(Workspace(root))),
            approvalGate = approval
        )
        val result = registry.dispatch("write_file", emptyArgs(), emptyList())
        assertTrue(result.isError)
        assertTrue("invalid arguments for write_file" in result.content)
        assertFalse(approvalCalled)
    }

    @Test
    fun repeatedIdenticalCallBlockedOnSinglePriorMatch(@TempDir root: Path) {
        // A single prior identical call within the same write generation is enough to
        // trigger the guard; the rule does not require two consecutive matches.
        val registry = ToolRegistry(listOf(ListFilesTool(Workspace(root))))
        val history = listOf(toolEntry("list_files", emptyArgs()))
        val result = registry.dispatch("list_files", emptyArgs(), history)
        assertTrue(result.isError)
        assertTrue("repeated identical tool call" in result.content)
    }

    @Test
    fun repeatedCallBlockedAcrossInterveningOtherTools(@TempDir root: Path) {
        // An intervening read_file does not reset the dedup window: a second list_files
        // still repeats the first within the same write generation.
        val registry = ToolRegistry(listOf(ListFilesTool(Workspace(root))))
        Files.writeString(root.resolve("README.md"), "hi\n")
        val history = listOf(
            toolEntry("list_files", emptyArgs()),
            toolEntry("read_file", argsOf("path" to "README.md"))
        )
        val result = registry.dispatch("list_files", emptyArgs(), history)
        assertTrue(result.isError)
        assertTrue("repeated identical tool call" in result.content)
    }

    @Test
    fun writeFileWithIdenticalArgsIsAlsoBlocked(@TempDir root: Path) {
        // write_file itself must be subject to dedup when the args are identical (same
        // path, same content) — otherwise the model can spam the same write indefinitely.
        // Different content (different args) would not trigger this guard.
        val registry = ToolRegistry(listOf(WriteFileTool(Workspace(root))))
        val args = argsOf("path" to "x.txt", "content" to "hi")
        val history = listOf(toolEntry("write_file", args))
        val result = registry.dispatch("write_file", args, history)
        assertTrue(result.isError)
        assertTrue("repeated identical tool call" in result.content)
    }

    @Test
    fun writeFileResetsRepeatDedup(@TempDir root: Path) {
        // A successful write_file mutates the workspace and so opens a new write
        // generation; a prior identical read-only call is legitimate to re-issue.
        val registry = ToolRegistry(listOf(ListFilesTool(Workspace(root))))
        val history = listOf(
            toolEntry("list_files", emptyArgs()),
            toolEntry("write_file", argsOf("path" to "x.txt", "content" to "hi"))
        )
        val result = registry.dispatch("list_files", emptyArgs(), history)
        assertFalse(result.isError, "write_file should invalidate prior dedup state")
    }

    @Test
    fun deniedApprovalIsStickyAcrossRepeats(@TempDir root: Path) {
        // A prior identical write_file that was denied by the approval gate counts as
        // a "prior identical" for dedup purposes: the model can't bypass the user's
        // decision by re-issuing the same call.
        val registry = ToolRegistry(
            listOf(WriteFileTool(Workspace(root))),
            approvalGate = ApprovalGate { _, _ -> false }
        )
        val args = argsOf("path" to "x.txt", "content" to "hi")
        val history = listOf(
            HistoryEntry.ToolEntry(
                name = "write_file",
                args = args,
                content = "approval denied for write_file",
                isError = true,
                createdAt = OffsetDateTime.now().toString()
            )
        )
        val result = registry.dispatch("write_file", args, history)
        assertTrue(result.isError)
        assertTrue("repeated identical tool call" in result.content)
    }

    @Test
    fun longToolOutputIsClipped(@TempDir root: Path) {
        Files.writeString(root.resolve("big.txt"), "x".repeat(MAX_TOOL_OUTPUT + 500))
        val registry = ToolRegistry(listOf(ReadFileTool(Workspace(root))))
        val result = registry.dispatch("read_file", argsOf("path" to "big.txt"), emptyList())
        assertEquals(true, "...[truncated" in result.content)
    }

    @Test
    fun nonStringPathArgReturnsValidationError(@TempDir root: Path) {
        val registry = ToolRegistry(listOf(ReadFileTool(Workspace(root))))
        // A model that emits {"path": {"nested": "v"}} instead of a string.
        val badArgs = JsonObject(
            mapOf("path" to JsonObject(mapOf("nested" to JsonPrimitive("v"))))
        )
        val result = registry.dispatch("read_file", badArgs, emptyList())
        assertTrue(result.isError)
        assertTrue("invalid arguments for read_file" in result.content)
        assertTrue("non-string" in result.content)
    }
}
