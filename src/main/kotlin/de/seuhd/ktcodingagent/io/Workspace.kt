package de.seuhd.ktcodingagent.io

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths

private const val MAX_SYMLINK_DEPTH = 16

/**
 * Path sandbox for the agent's tools.
 *
 * Wraps a workspace root directory and exposes [resolveSandboxed], which turns a relative or
 * absolute path supplied by the model into a canonical [Path] and rejects anything that
 * would resolve outside the sandbox.
 *
 * Design goals:
 *  - **No escape via `..`**: relative paths are normalized before any disk access.
 *  - **No escape via symlinks**: every symlink encountered on the path is resolved manually
 *    (see [pathIsWithinRoot]) and its target re-checked against the root.
 *  - **No escape via broken symlinks**: a symlink whose target does not yet exist must not
 *    be silently traversed during a write. The walk uses [LinkOption.NOFOLLOW_LINKS] so it
 *    stops at the symlink itself and resolves it manually.
 *  - **Compatibility with symlinked filesystem roots** (e.g. macOS `/tmp -> /private/tmp`):
 *    the workspace root is canonicalized once at construction; comparisons happen between
 *    canonical paths so a workspace under `/tmp` is recognized after symlink resolution.
 */
class Workspace(val root: Path) {
    private val rootReal: Path = root.toRealPath()

    /**
     * Resolves [relative] against the workspace root and returns the resulting absolute,
     * normalized [Path]. Throws [SecurityException] if the path resolves outside the
     * sandbox at any depth, including through pre-existing or broken symlinks.
     */
    fun resolveSandboxed(relative: String): Path {
        val raw = Paths.get(relative)
        val absolute = if (raw.isAbsolute) raw else root.resolve(raw)
        val resolved = absolute.normalize()
        if (!pathIsWithinRoot(resolved, depth = 0)) {
            throw SecurityException("path escapes workspace: $relative")
        }
        return resolved
    }

    /**
     * Determines whether [resolved] points inside the sandbox.
     *
     * The challenge is to make the right call even when [resolved] does not yet exist
     * (typical for a write) and even when its parent chain crosses symlinks. The algorithm:
     *
     *  1. Walk up the parent chain using [LinkOption.NOFOLLOW_LINKS] until reaching a
     *     component that exists on disk. NOFOLLOW means a symlink is treated as "exists"
     *     even if its target is missing — so we stop AT the symlink instead of stepping
     *     past it as the simpler `toFile().exists()` would.
     *  2. If the stopping point is a symlink, read its target manually with
     *     [Files.readSymbolicLink] (which works on broken symlinks), resolve it against
     *     the symlink's parent if relative, and recurse on the result. This handles
     *     chains of symlinks; [MAX_SYMLINK_DEPTH] bounds the recursion to break cycles.
     *  3. Otherwise the stopping point is a real file or directory. Canonicalize via
     *     [Path.toRealPath] and test descendancy with [Path.startsWith] against [rootReal].
     *     `startsWith` compares path components (not string prefixes), so it correctly
     *     distinguishes `/sandbox/foo` (inside) from `/sandbox-evil/foo` (outside).
     */
    private fun pathIsWithinRoot(resolved: Path, depth: Int): Boolean {
        if (depth > MAX_SYMLINK_DEPTH) return false

        var probe: Path = resolved
        while (probe.parent != null && !Files.exists(probe, LinkOption.NOFOLLOW_LINKS)) {
            probe = probe.parent
        }

        if (Files.isSymbolicLink(probe)) {
            val rawTarget = Files.readSymbolicLink(probe)
            val absoluteTarget = if (rawTarget.isAbsolute) {
                rawTarget
            } else {
                // A relative symlink at the filesystem root has no parent to resolve against;
                // treat that as out-of-sandbox rather than NPE-ing on probe.parent.
                val parent = probe.parent ?: return false
                parent.resolve(rawTarget)
            }
            return pathIsWithinRoot(absoluteTarget.normalize(), depth + 1)
        }

        return probe.toRealPath().startsWith(rootReal)
    }
}
