package de.seuhd.ktcodingagent.io

import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkspaceTest {

    @Test
    fun resolvesInsidePathToCanonicalAbsolute(@TempDir root: Path) {
        val workspace = Workspace(root)
        val target = workspace.resolveSandboxed("foo/bar.txt")
        assertEquals(root.resolve("foo/bar.txt").toAbsolutePath().normalize(), target)
    }

    @Test
    fun rejectsParentTraversal(@TempDir root: Path) {
        val workspace = Workspace(root)
        assertFailsWith<SecurityException> { workspace.resolveSandboxed("../escape.txt") }
    }

    @Test
    fun rejectsAbsolutePathOutsideRoot(@TempDir root: Path) {
        val workspace = Workspace(root)
        val outside = root.resolveSibling("outside.txt").toAbsolutePath().toString()
        assertFailsWith<SecurityException> { workspace.resolveSandboxed(outside) }
    }

    @Test
    fun acceptsAbsolutePathInsideRoot(@TempDir root: Path) {
        Files.writeString(root.resolve("foo.txt"), "x")
        val workspace = Workspace(root)
        val absoluteInside = root.resolve("foo.txt").toAbsolutePath().toString()
        val target = workspace.resolveSandboxed(absoluteInside)
        assertEquals(root.resolve("foo.txt").toAbsolutePath().normalize(), target)
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun rejectsSymlinkPointingToExistingOutsideTarget(@TempDir root: Path) {
        val outsideDir = root.resolveSibling(root.fileName.toString() + "-out")
        Files.createDirectory(outsideDir)
        Files.writeString(outsideDir.resolve("secret.txt"), "secret")
        try {
            Files.createSymbolicLink(root.resolve("link"), outsideDir)
        } catch (_: UnsupportedOperationException) {
            return
        }
        val workspace = Workspace(root)
        try {
            assertFailsWith<SecurityException> { workspace.resolveSandboxed("link/secret.txt") }
        } finally {
            Files.deleteIfExists(root.resolve("link"))
            Files.deleteIfExists(outsideDir.resolve("secret.txt"))
            Files.deleteIfExists(outsideDir)
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun rejectsBrokenSymlinkPointingOutside(@TempDir root: Path) {
        val outsideTarget = root.resolveSibling(root.fileName.toString() + "-broken")
        try {
            Files.createSymbolicLink(root.resolve("link"), outsideTarget)
        } catch (_: UnsupportedOperationException) {
            return
        }
        val workspace = Workspace(root)
        try {
            assertFailsWith<SecurityException> { workspace.resolveSandboxed("link/new.txt") }
            assertTrue(!Files.exists(outsideTarget), "the broken symlink target must not have been touched")
        } finally {
            Files.deleteIfExists(root.resolve("link"))
            Files.deleteIfExists(outsideTarget)
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun acceptsSymlinkPointingInsideRoot(@TempDir root: Path) {
        val inner = root.resolve("inner")
        Files.createDirectory(inner)
        try {
            Files.createSymbolicLink(root.resolve("safe"), inner)
        } catch (_: UnsupportedOperationException) {
            return
        }
        val workspace = Workspace(root)
        // Should not throw; the resolved path is valid even though the target file doesn't exist yet.
        workspace.resolveSandboxed("safe/note.txt")
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun rejectsSymlinkChainEndingOutside(@TempDir root: Path) {
        // sandbox/a -> sandbox/b, sandbox/b -> /outside
        val outsideTarget = root.resolveSibling(root.fileName.toString() + "-chain-end")
        try {
            Files.createSymbolicLink(root.resolve("b"), outsideTarget)
            Files.createSymbolicLink(root.resolve("a"), root.resolve("b"))
        } catch (_: UnsupportedOperationException) {
            return
        }
        val workspace = Workspace(root)
        try {
            assertFailsWith<SecurityException> { workspace.resolveSandboxed("a/new.txt") }
        } finally {
            Files.deleteIfExists(root.resolve("a"))
            Files.deleteIfExists(root.resolve("b"))
            Files.deleteIfExists(outsideTarget)
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun rejectsSymlinkChainExceedingDepthLimit(@TempDir root: Path) {
        // Self-referential symlink: a -> a. Must terminate (depth cap), not loop forever.
        try {
            Files.createSymbolicLink(root.resolve("loop"), root.resolve("loop"))
        } catch (_: UnsupportedOperationException) {
            return
        }
        val workspace = Workspace(root)
        try {
            assertFailsWith<SecurityException> { workspace.resolveSandboxed("loop/x") }
        } finally {
            Files.deleteIfExists(root.resolve("loop"))
        }
    }

    @Test
    fun rejectsSiblingPathThatStartsWithRootName(@TempDir parent: Path) {
        // Regression: ensure descendancy uses path components, not string prefix matching.
        // /tmp/junit-x/sandbox should NOT contain /tmp/junit-x/sandbox-evil/foo.
        val root = parent.resolve("sandbox")
        Files.createDirectory(root)
        val sibling = parent.resolve("sandbox-evil")
        Files.createDirectory(sibling)
        Files.writeString(sibling.resolve("foo.txt"), "x")
        val workspace = Workspace(root)
        assertFailsWith<SecurityException> {
            workspace.resolveSandboxed(sibling.resolve("foo.txt").toAbsolutePath().toString())
        }
    }

    @Test
    fun acceptsRootViaCanonicalSymlinkAlias(@TempDir parent: Path) {
        // Regression for macOS /tmp -> /private/tmp: if the workspace root is reached via
        // a symlink, paths inside it must still be accepted because Workspace canonicalizes
        // the root at construction.
        val realRoot = parent.resolve("real-root")
        Files.createDirectory(realRoot)
        try {
            Files.createSymbolicLink(parent.resolve("alias"), realRoot)
        } catch (_: UnsupportedOperationException) {
            return
        }
        try {
            val workspace = Workspace(parent.resolve("alias"))
            workspace.resolveSandboxed("file.txt") // should not throw
        } finally {
            Files.deleteIfExists(parent.resolve("alias"))
            Files.deleteIfExists(realRoot)
        }
    }
}
