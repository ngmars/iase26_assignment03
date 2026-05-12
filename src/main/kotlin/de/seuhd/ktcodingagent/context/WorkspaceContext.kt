package de.seuhd.ktcodingagent.context

import kotlinx.serialization.Serializable
import java.nio.file.Path

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
        TODO("Implement WorkspaceContext.load (sub-exercise (b)).")
    }
}
