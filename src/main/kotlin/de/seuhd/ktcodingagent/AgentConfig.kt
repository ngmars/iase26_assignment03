package de.seuhd.ktcodingagent

import de.seuhd.ktcodingagent.model.OllamaModelClient
import de.seuhd.ktcodingagent.tools.ApprovalGate
import de.seuhd.ktcodingagent.tools.AutoApprove
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

/**
 * Defaults for every tunable knob the agent exposes. Used by [main] as the source of
 * default values for the CLI parser; can also be constructed directly for tests.
 */
data class AgentConfig(
    val modelName: String = "qwen3.5:2b",
    val host: String = OllamaModelClient.DEFAULT_HOST,
    val workspaceRoot: Path = Paths.get("."),
    val maxSteps: Int = 16,
    val maxNewTokens: Int = 1024,
    val temperature: Double = 0.2,
    val topP: Double = 0.9,
    val ollamaTimeout: Duration = Duration.ofSeconds(300),
    val approvalGate: ApprovalGate = AutoApprove,
    val walkToRepoRoot: Boolean = true
)
