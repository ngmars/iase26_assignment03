package de.seuhd.ktcodingagent.tools

import de.seuhd.ktcodingagent.io.Workspace
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ToolsTest {

    private fun args(vararg pairs: Pair<String, Any>): JsonObject =
        JsonObject(pairs.associate { (k, v) ->
            k to when (v) {
                is Int -> JsonPrimitive(v)
                is String -> JsonPrimitive(v)
                else -> JsonPrimitive(v.toString())
            }
        })

    @Test
    fun listFilesReturnsEntries(@TempDir root: Path) {
        Files.writeString(root.resolve("hello.txt"), "hi\n")
        Files.createDirectory(root.resolve("sub"))
        val tool = ListFilesTool(Workspace(root))
        val result = tool.execute(args())
        assertTrue("[F] hello.txt" in result.content)
        assertTrue("[D] sub" in result.content)
    }

    @Test
    fun listFilesHidesIgnoredPaths(@TempDir root: Path) {
        Files.writeString(root.resolve("hello.txt"), "hi\n")
        Files.createDirectory(root.resolve(".git"))
        Files.createDirectory(root.resolve(".kt-coding-agent"))
        Files.createDirectory(root.resolve("build"))
        Files.createDirectory(root.resolve(".gradle"))
        Files.createDirectory(root.resolve(".idea"))
        val tool = ListFilesTool(Workspace(root))
        val result = tool.execute(args())
        assertTrue(".git" !in result.content)
        assertTrue(".kt-coding-agent" !in result.content)
        assertTrue("build" !in result.content)
        assertTrue(".gradle" !in result.content)
        assertTrue(".idea" !in result.content)
        assertTrue("[F] hello.txt" in result.content)
    }

    @Test
    fun readFileReturnsLineRangeWithHeader(@TempDir root: Path) {
        Files.writeString(root.resolve("hello.txt"), "alpha\nbeta\ngamma\n")
        val tool = ReadFileTool(Workspace(root))
        val result = tool.execute(args("path" to "hello.txt", "start" to 1, "end" to 3))
        assertEquals(
            "# hello.txt\n   1: alpha\n   2: beta\n   3: gamma",
            result.content
        )
    }

    @Test
    fun readFileSingleLineRange(@TempDir root: Path) {
        Files.writeString(root.resolve("hello.txt"), "alpha\nbeta\ngamma\n")
        val tool = ReadFileTool(Workspace(root))
        val result = tool.execute(args("path" to "hello.txt", "start" to 2, "end" to 2))
        assertEquals("# hello.txt\n   2: beta", result.content)
    }

    @Test
    fun readFileRejectsParentEscape(@TempDir root: Path) {
        val tool = ReadFileTool(Workspace(root))
        val outside = root.resolveSibling("outside.txt")
        Files.writeString(outside, "secret")
        try {
            assertFailsWith<SecurityException> {
                tool.execute(args("path" to "../outside.txt"))
            }
        } finally {
            Files.deleteIfExists(outside)
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun readFileRejectsSymlinkEscape(@TempDir root: Path) {
        val outsideDir = root.resolveSibling(root.fileName.toString() + "-outside")
        Files.createDirectory(outsideDir)
        Files.writeString(outsideDir.resolve("secret.txt"), "secret")
        try {
            Files.createSymbolicLink(root.resolve("link"), outsideDir)
        } catch (e: UnsupportedOperationException) {
            return // skip if symlinks unavailable
        }
        val tool = ReadFileTool(Workspace(root))
        try {
            assertFailsWith<SecurityException> {
                tool.execute(args("path" to "link/secret.txt"))
            }
        } finally {
            Files.deleteIfExists(root.resolve("link"))
            Files.deleteIfExists(outsideDir.resolve("secret.txt"))
            Files.deleteIfExists(outsideDir)
        }
    }

    @Test
    fun writeFileWritesContentAndReportsLength(@TempDir root: Path) {
        val tool = WriteFileTool(Workspace(root))
        val result = tool.execute(args("path" to "hello.txt", "content" to "hi\n"))
        assertEquals("hi\n", Files.readString(root.resolve("hello.txt")))
        assertTrue("wrote hello.txt (3 chars)" in result.content)
    }

    @Test
    fun writeFileCreatesParentDirectories(@TempDir root: Path) {
        val tool = WriteFileTool(Workspace(root))
        tool.execute(args("path" to "nested/deep/file.txt", "content" to "x"))
        assertTrue(Files.isRegularFile(root.resolve("nested/deep/file.txt")))
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun writeFileRejectsBrokenSymlinkEscape(@TempDir root: Path) {
        // Pre-existing broken symlink inside the sandbox pointing outside it. Without the
        // NOFOLLOW walk-up in Workspace.pathIsWithinRoot, a write through the link would
        // materialise the link's target directory outside the sandbox.
        val outsideTarget = root.resolveSibling(root.fileName.toString() + "-broken-target")
        try {
            Files.createSymbolicLink(root.resolve("escape"), outsideTarget)
        } catch (_: UnsupportedOperationException) {
            return
        }
        val tool = WriteFileTool(Workspace(root))
        try {
            assertFailsWith<SecurityException> {
                tool.execute(args("path" to "escape/leaked.txt", "content" to "x"))
            }
            assertTrue(!Files.exists(outsideTarget), "the symlink target must not have been created")
        } finally {
            Files.deleteIfExists(outsideTarget.resolve("leaked.txt"))
            Files.deleteIfExists(outsideTarget)
            Files.deleteIfExists(root.resolve("escape"))
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun writeFileAcceptsSymlinkPointingInsideSandbox(@TempDir root: Path) {
        // A symlink that resolves to another directory inside the sandbox must remain
        // usable; tightening the sandbox must not break legitimate intra-sandbox links.
        val innerDir = root.resolve("inner")
        Files.createDirectory(innerDir)
        try {
            Files.createSymbolicLink(root.resolve("safe"), innerDir)
        } catch (_: UnsupportedOperationException) {
            return
        }
        val tool = WriteFileTool(Workspace(root))
        val result = tool.execute(args("path" to "safe/hello.txt", "content" to "hi"))
        assertTrue(!result.isError, "write through an inside-sandbox symlink must succeed; got: ${result.content}")
        assertEquals("hi", Files.readString(innerDir.resolve("hello.txt")))
    }
}
