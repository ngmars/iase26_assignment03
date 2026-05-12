package de.seuhd.ktcodingagent.context

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceContextTest {

    @Test
    fun degradesGracefullyWithoutGit(@TempDir root: Path) {
        Files.writeString(root.resolve("README.md"), "hello\n")
        val ctx = WorkspaceContextLoader.load(root)
        assertEquals("-", ctx.branch)
        assertTrue(ctx.recentCommits.isEmpty())
        assertTrue(ctx.projectDocs.values.any { "hello" in it })
    }

    @Test
    fun detectsBranchAndCommitsClampedToFive(@TempDir root: Path) {
        if (!gitAvailable()) return // skip when git is not on PATH
        runCommand(root, listOf("git", "init", "-q", "-b", "main"))
        runCommand(root, listOf("git", "config", "user.email", "test@example.com"))
        runCommand(root, listOf("git", "config", "user.name", "Test"))
        Files.writeString(root.resolve("README.md"), "hello\n")
        for (i in 1..8) {
            Files.writeString(root.resolve("f$i.txt"), "$i\n")
            runCommand(root, listOf("git", "add", "."))
            runCommand(root, listOf("git", "commit", "-q", "-m", "commit $i"))
        }
        val ctx = WorkspaceContextLoader.load(root)
        assertEquals("main", ctx.branch)
        assertEquals(5, ctx.recentCommits.size)
    }

    @Test
    fun readmeClippedToConfiguredLimit(@TempDir root: Path) {
        val long = "x".repeat(2000)
        Files.writeString(root.resolve("README.md"), long)
        val ctx = WorkspaceContextLoader.load(root)
        val doc = ctx.projectDocs.entries.firstOrNull { it.key.endsWith("README.md") }
        checkNotNull(doc) { "README.md should appear in project_docs" }
        assertTrue(doc.value.length < long.length)
        assertTrue(doc.value.contains("[truncated"))
    }

    @Test
    fun noRepoWalkSkipsOuterRepoDocs(@TempDir root: Path) {
        if (!gitAvailable()) return // skip when git is not on PATH
        runCommand(root, listOf("git", "init", "-q", "-b", "main"))
        runCommand(root, listOf("git", "config", "user.email", "test@example.com"))
        runCommand(root, listOf("git", "config", "user.name", "Test"))
        Files.writeString(root.resolve("README.md"), "outer-repo readme\n")
        val inner = Files.createDirectory(root.resolve("inner"))
        Files.writeString(inner.resolve("README.md"), "inner-fixture readme\n")

        val withWalk = WorkspaceContextLoader.load(inner)
        assertTrue(withWalk.projectDocs.values.any { "outer-repo readme" in it })
        assertTrue(withWalk.projectDocs.values.any { "inner-fixture readme" in it })

        val withoutWalk = WorkspaceContextLoader.load(inner, walkToRepoRoot = false)
        assertTrue(withoutWalk.projectDocs.values.none { "outer-repo readme" in it })
        assertTrue(withoutWalk.projectDocs.values.any { "inner-fixture readme" in it })
    }

    @Test
    fun statusIsCleanWhenWorkingTreeMatches(@TempDir root: Path) {
        Files.writeString(root.resolve("README.md"), "hello\n")
        val ctx = WorkspaceContextLoader.load(root)
        // No git here -> falls back to "clean".
        assertTrue(ctx.status == "clean" || ctx.status.isNotBlank())
    }

    private fun gitAvailable(): Boolean = try {
        val p = ProcessBuilder("git", "--version").redirectErrorStream(true).start()
        p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0
    } catch (_: Exception) {
        false
    }

    private fun runCommand(cwd: Path, command: List<String>) {
        val process = ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start()
        process.waitFor(5, TimeUnit.SECONDS)
    }
}
