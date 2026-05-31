package de.seuhd.ktcodingagent.context

import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Snapshot of stable facts about the workspace, captured once at agent startup.
 *
 * Embedded in the stable prefix of every prompt so the model has consistent situational
 * awareness without us having to re-discover it each turn: branch and default branch, the
 * current `git status --short`, the last few commits, and excerpts of well-known project
 * documents.
 */
@Serializable
data class WorkspaceContext(
    val cwd: String,
    val repoRoot: String,
    val branch: String,
    val defaultBranch: String,
    val status: String,
    val recentCommits: List<String>,
    val projectDocs: Map<String, String>
) {
    fun render(): String {
        val commits = if (recentCommits.isEmpty()) "- none" else recentCommits.joinToString("\n") { "- $it" }
        val docs = if (projectDocs.isEmpty()) {
            "- none"
        } else {
            projectDocs.entries.joinToString("\n") { (path, body) -> "- $path\n$body" }
        }
        return buildString {
            appendLine("Workspace:")
            appendLine("- cwd: $cwd")
            appendLine("- repo_root: $repoRoot")
            appendLine("- branch: $branch")
            appendLine("- default_branch: $defaultBranch")
            appendLine("- status:")
            appendLine(status)
            appendLine("- recent_commits:")
            appendLine(commits)
            appendLine("- project_docs:")
            append(docs)
        }
    }
}

/**
 * Sub-exercise (b): implement [load].
 *
 * Build a WorkspaceContext from the directory at [cwd].
 * - Use ProcessBuilder to run "git rev-parse --show-toplevel" (fall back to cwd if it fails).
 * - "git branch --show-current" (fall back to "-").
 * - "git symbolic-ref --short refs/remotes/origin/HEAD" (fall back to "origin/main"); strip "origin/".
 * - "git status --short" (fall back to "clean"); clip to 1500 chars.
 * - "git log --oneline -5"; split on newlines and drop blanks.
 * - Read AGENTS.md, README.md, build.gradle.kts:
 *   when [walkToRepoRoot] is true (default), from both repo root and cwd (skip duplicates);
 *   when false, from cwd only. Clip each to 1200 chars.
 *
 * All git calls must degrade gracefully (no exceptions if git is missing or this is not a repo).
 *
 * See WorkspaceContextTest for the contract.
 */
object WorkspaceContextLoader {
    fun load(cwd: Path, walkToRepoRoot: Boolean = true): WorkspaceContext {
        val normalizedCwd = cwd.toAbsolutePath().normalize()
        val repoRootPath = runGit(normalizedCwd, "rev-parse", "--show-toplevel")
            ?.let { Path.of(it).toAbsolutePath().normalize() }
            ?: normalizedCwd
        val branch = runGit(normalizedCwd, "branch", "--show-current").orFallback("-")
        val defaultBranch = runGit(normalizedCwd, "symbolic-ref", "--short", "refs/remotes/origin/HEAD")
            ?.removePrefix("origin/")
            .orFallback("main")
        val status = clip(runGit(normalizedCwd, "status", "--short").orFallback("clean"), 1500)
        val recentCommits = runGit(normalizedCwd, "log", "--oneline", "-5")
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val roots = buildList {
            if (walkToRepoRoot) add(repoRootPath)
            add(normalizedCwd)
        }.distinct()

        val docs = linkedMapOf<String, String>()
        val filenames = listOf("AGENTS.md", "README.md", "build.gradle.kts")
        for (root in roots) {
            for (name in filenames) {
                val file = root.resolve(name)
                if (!Files.isRegularFile(file)) continue
                val normalized = file.toAbsolutePath().normalize()
                val key = normalized.toString()
                if (docs.containsKey(key)) continue
                val content = Files.readString(normalized)
                docs[key] = clip(content, 1200)
            }
        }

        return WorkspaceContext(
            cwd = normalizedCwd.toString(),
            repoRoot = repoRootPath.toString(),
            branch = branch,
            defaultBranch = defaultBranch,
            status = if (status.isBlank()) "clean" else status,
            recentCommits = recentCommits.take(5),
            projectDocs = docs
        )
    }

    private fun runGit(cwd: Path, vararg args: String): String? = try {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(cwd.toFile())
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(3, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() != 0) return null
        process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8).trim()
            .ifBlank { null }
    } catch (_: Exception) {
        null
    }

    private fun String?.orFallback(fallback: String): String = this?.ifBlank { null } ?: fallback

    private fun clip(text: String, limit: Int): String {
        if (text.length <= limit) return text
        return text.take(limit) + "...[truncated ${text.length - limit} chars]"
    }
}
