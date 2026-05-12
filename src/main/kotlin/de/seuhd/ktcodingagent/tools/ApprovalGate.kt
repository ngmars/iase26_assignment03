package de.seuhd.ktcodingagent.tools

import kotlinx.serialization.json.JsonObject

/**
 * Decides whether a tool flagged as `risky` should be allowed to run.
 *
 * Consulted by [ToolRegistry.dispatch] only after argument validation and repeat
 * detection have already passed, so the user is never asked to approve a call that
 * would have failed anyway.
 *
 * The interactive REPL builds its own gate inline in `Main.kt` (it needs access to
 * the REPL's spacing state and renders a compact prompt). Tests and headless runs
 * use [AutoApprove].
 */
fun interface ApprovalGate {
    fun approve(toolName: String, args: JsonObject): Boolean
}

/** Approves every call. Default in tests and in `--approval auto`. */
object AutoApprove : ApprovalGate {
    override fun approve(toolName: String, args: JsonObject): Boolean = true
}
