package de.seuhd.ktcodingagent

import de.seuhd.ktcodingagent.context.PromptBuilder
import de.seuhd.ktcodingagent.context.WorkspaceContext
import de.seuhd.ktcodingagent.io.Workspace
import de.seuhd.ktcodingagent.session.Session
import de.seuhd.ktcodingagent.session.SessionStore
import de.seuhd.ktcodingagent.tools.ListFilesTool
import de.seuhd.ktcodingagent.tools.ReadFileTool
import de.seuhd.ktcodingagent.tools.Tool
import de.seuhd.ktcodingagent.tools.ToolRegistry
import de.seuhd.ktcodingagent.tools.WriteFileTool
import java.nio.file.Path
import java.time.OffsetDateTime

fun emptyWorkspaceContext(root: Path): WorkspaceContext = WorkspaceContext(
    cwd = root.toString(),
    repoRoot = root.toString(),
    branch = "-",
    defaultBranch = "main",
    status = "clean",
    recentCommits = emptyList(),
    projectDocs = emptyMap()
)

fun buildAgentForTest(
    tempRoot: Path,
    scripted: List<String>,
    maxSteps: Int = 16
): Pair<Agent, StubModelClient> {
    val workspace = Workspace(tempRoot)
    val tools: List<Tool> = listOf(
        ListFilesTool(workspace),
        ReadFileTool(workspace),
        WriteFileTool(workspace)
    )
    val registry = ToolRegistry(tools)
    val sessionStore = SessionStore(tempRoot.resolve(".kt-coding-agent/sessions"))
    val session = Session(
        id = Session.newId(),
        createdAt = OffsetDateTime.now().toString(),
        workspaceRoot = tempRoot.toString(),
        modelName = "stub"
    )
    val promptBuilder = PromptBuilder("system-preamble", tools, emptyWorkspaceContext(tempRoot))
    val stub = StubModelClient(scripted)
    val agent = Agent(
        modelClient = stub,
        registry = registry,
        promptBuilder = promptBuilder,
        session = session,
        sessionStore = sessionStore,
        maxSteps = maxSteps,
        maxNewTokens = 128
    )
    return agent to stub
}
