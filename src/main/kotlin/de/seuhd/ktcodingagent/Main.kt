package de.seuhd.ktcodingagent

import de.seuhd.ktcodingagent.context.PromptBuilder
import de.seuhd.ktcodingagent.context.WorkspaceContextLoader
import de.seuhd.ktcodingagent.io.Console
import de.seuhd.ktcodingagent.io.Workspace
import de.seuhd.ktcodingagent.model.AvailabilityCheck
import de.seuhd.ktcodingagent.model.OllamaModelClient
import de.seuhd.ktcodingagent.session.Session
import de.seuhd.ktcodingagent.session.SessionStore
import de.seuhd.ktcodingagent.tools.ApprovalGate
import de.seuhd.ktcodingagent.tools.AutoApprove
import de.seuhd.ktcodingagent.tools.ListFilesTool
import de.seuhd.ktcodingagent.tools.ReadFileTool
import de.seuhd.ktcodingagent.tools.Tool
import de.seuhd.ktcodingagent.tools.ToolRegistry
import de.seuhd.ktcodingagent.tools.WriteFileTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import kotlin.system.exitProcess

private const val PROMPT_PREAMBLE_RESOURCE = "/prompt_preamble.txt"

/**
 * CLI entry point.
 *
 * Order of operations:
 *   1. Parse CLI flags into a [ParsedArgs] (CLI-specific shape) and resolve the workspace
 *      directory.
 *   2. Construct the [OllamaModelClient] and run [OllamaModelClient.checkAvailability]
 *      before touching the REPL, so misconfiguration surfaces as a friendly message and
 *      a non-zero exit code instead of as a mid-conversation crash.
 *   3. Wire the workspace, session, tools, and prompt builder.
 *   4. Print the banner and the welcome lines.
 *   5. Hand off to [runAgent], which builds the approval gate and the [Agent] (so the
 *      REPL spacing state stays scoped to that function), then runs either the one-shot
 *      prompt or the interactive REPL.
 */
fun main(rawArgs: Array<String>) {
    val args = parseArgs(rawArgs.toList())
    val cwd = args.workspaceRoot.toAbsolutePath().normalize()
    if (!Files.isDirectory(cwd)) {
        System.err.println("workspace directory does not exist: $cwd")
        exitProcess(1)
    }

    val ollama = OllamaModelClient(
        modelName = args.modelName,
        host = args.host,
        temperature = args.temperature,
        topP = args.topP,
        timeout = args.ollamaTimeout
    )

    when (val availability = ollama.checkAvailability()) {
        AvailabilityCheck.Ready -> Unit
        is AvailabilityCheck.OllamaUnreachable -> {
            System.err.println(availability.message)
            exitProcess(1)
        }
        is AvailabilityCheck.ModelMissing -> {
            System.err.println(availability.message)
            exitProcess(1)
        }
    }

    val workspace = Workspace(cwd)
    val tools: List<Tool> = listOf(
        ListFilesTool(workspace),
        ReadFileTool(workspace),
        WriteFileTool(workspace)
    )
    val sessionStore = SessionStore(cwd.resolve(".kt-coding-agent/sessions"))
    val session = loadOrCreateSession(args.resume, sessionStore, cwd, args.modelName)
    val workspaceContext = WorkspaceContextLoader.load(cwd, walkToRepoRoot = args.walkToRepoRoot)
    val promptBuilder = PromptBuilder(readPreamble(), tools, workspaceContext)

    printBanner()
    val sessionPath = sessionStore.pathFor(session.id)
    val sessionRel = runCatching { cwd.relativize(sessionPath).toString() }.getOrDefault(sessionPath.toString())
    println("workspace: $cwd")
    println("model: ${args.modelName}")
    println("session: $sessionRel")
    println("commands: /help /memory /session /reset /exit")

    runAgent(args, ollama, tools, session, sessionStore, promptBuilder)
}

/**
 * Builds the approval gate and the [Agent], then runs either a single one-shot prompt or
 * the interactive REPL.
 *
 * Spacing state shared between the approval gate and the tool-log / final-answer printers
 * lives here as two function-local `var`s. They never escape this function: the approval
 * gate lambda, the `onToolCall` lambda, and the REPL loop body all capture the same two
 * vars, but no caller ever sees them. Blank lines are emitted lazily, just before output
 * appears:
 *  - one blank before the first tool log of a turn,
 *  - one blank before every approval prompt and one blank after every approval response
 *    (so the prompt visually stands apart from surrounding tool logs),
 *  - one blank before the final answer.
 *
 * `skipNextLeadingBlank` lets the approval gate signal to the next tool-log /
 * final-answer printer that the cursor is already on a blank line, so the printer skips
 * its own leading blank and the response/log/answer flow naturally on from the `[y/N]`
 * line.
 */
private fun runAgent(
    args: ParsedArgs,
    ollama: OllamaModelClient,
    tools: List<Tool>,
    session: Session,
    sessionStore: SessionStore,
    promptBuilder: PromptBuilder
) {
    var toolsPrintedInTurn = 0
    var skipNextLeadingBlank = false

    val approvalGate: ApprovalGate = if (args.approval == "ask") {
        ApprovalGate { toolName, toolArgs ->
            println()
            val ok = Console.confirm("APPROVE? ${renderApproval(toolName, toolArgs)}")
            println()
            skipNextLeadingBlank = true
            ok
        }
    } else AutoApprove
    val registry = ToolRegistry(tools, approvalGate = approvalGate)

    val agent = Agent(
        modelClient = ollama,
        registry = registry,
        promptBuilder = promptBuilder,
        session = session,
        sessionStore = sessionStore,
        maxSteps = args.maxSteps,
        maxNewTokens = args.maxNewTokens,
        onToolCall = { name: String, callArgs: JsonObject, content: String, isError: Boolean ->
            if (toolsPrintedInTurn == 0 && !skipNextLeadingBlank) println()
            skipNextLeadingBlank = false
            toolsPrintedInTurn++
            if (args.verbose) {
                println("[tool:$name] $callArgs")
                println(content.lines().joinToString("\n") { "  $it" })
            } else {
                println(compactToolSummary(name, callArgs, content, isError))
            }
        }
    )

    if (args.oneShot != null) {
        val answer = agent.ask(args.oneShot)
        println()
        println("⏺ $answer")
        return
    }

    while (true) {
        print("\n> ")
        val input = Console.readLineOrNull() ?: break
        if (input.isEmpty()) continue
        when (input) {
            "/exit", "/quit", "exit", "quit", "bye" -> return
            "/help" -> {
                println("/help    Show this help.")
                println("/memory  Show the distilled session memory.")
                println("/session Show the path to the saved session JSON.")
                println("/reset   Clear history and memory.")
                println("/exit    Exit.")
            }
            "/memory" -> {
                println("task: ${session.memory.task.ifBlank { "-" }}")
                println("files: ${session.memory.files.joinToString(", ").ifBlank { "-" }}")
                println("notes:")
                session.memory.notes.forEach { println("- $it") }
            }
            "/session" -> println(sessionStore.pathFor(session.id))
            "/reset" -> {
                agent.reset()
                println("session reset")
            }
            else -> {
                toolsPrintedInTurn = 0
                skipNextLeadingBlank = false
                val answer = try {
                    agent.ask(input)
                } catch (e: RuntimeException) {
                    System.err.println(e.message ?: e.toString())
                    continue
                }
                if (!skipNextLeadingBlank) println()
                skipNextLeadingBlank = false
                println("⏺ $answer")
            }
        }
    }
}

private fun printBanner() {
    println()
    println("      ⣀⣤⣶⡶⢛⠟⡿⠻⢻⢿⢶⢦⣄⡀")
    println("   ⢀⣠⡾⡫⢊⠌⡐⢡⠊⢰⠁⡎⠘⡄⢢⠙⡛⡷⢤⡀")
    println("   ⢠⢪⢋⡞⢠⠃⡜⠀⠎⠀⠉⠀⠃⠀⠃⠀⠃⠙⠘⠊⢻⠦")
    println("   ⢇⡇⡜⠀⠜⠀⠁⠀⢀⠔⠉⠉⠑⠄⠀⠀⡰⠊⠉⠑⡄⡇")
    println("   ⡸⠧⠄⠀⠀⠀⠀⠀⠘⡀⠾⠀⠀⣸⠀⠀⢧⠀⠛⠀⠌⡇")
    println("   ⠘⡇⠀⠀⠀⠀⠀⠀⠀⠀⠙⠒⠒⠚⠁⠈⠉⠲⡍⠒⠈⠀⡇")
    println("   ⠀⠈⠲⣆⠀⠀⠀⠀⠀⠀⠀⠀⣠⠖⠉⡹⠤⠶⠁⠀⠀⠀⠈⢦")
    println("   ⠀⠀⠀⠈⣦⡀⠀⠀⠀⠀⠧⣴⠁⠀⠘⠓⢲⣄⣀⣀⣀⡤⠔⠃")
    println("   ⠀⠀⠀⣜⠀⠈⠓⠦⢄⣀⣀⣸⠀⠀⠀⠀⠁⢈⢇⣼⡁")
    println("   ⠀⢠⠒⠛⠲⣄⠀⠀⠀⣠⠏⠀⠉⠲⣤⠀⢸⠋⢻⣤⡛⣄")
    println("   ⠀⢡⠀⠀⠀⠀⠉⢲⠾⠁⠀⠀⠀⠀⠈⢳⡾⣤⠟⠁⠹⣿⢆")
    println("   ⢀⠼⣆⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣼⠃⠀⠀⠀⠀⠀⠈⣧")
    println("   ⡏⠀⠘⢦⡀⠀⠀⠀⠀⠀⠀⠀⠀⣠⠞⠁⠀⠀⠀⠀⠀⠀⠀⢸⣧")
    println("  ⢰⣄⠀⠀⠀⠉⠳⠦⣤⣤⡤⠴⠖⠋⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⢯⣆")
    println("  ⢸⣉⠉⠓⠲⢦⣤⣄⣀⣀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣀⣀⣀⣠⣼⢹⡄")
    println("  ⠘⡍⠙⠒⠶⢤⣄⣈⣉⡉⠉⠙⠛⠛⠛⠛⠛⠛⢻⠉⠉⠉⢙⣏⣁⣸⠇⡇")
    println("   ⢣⠀⠀⠀⠀⠀⠀⠀⠉⠉⠉⠙⠛⠛⠛⠛⠛⠛⠛⠒⠒⠒⠋⠉⠀⠸⠚⢇")
    println("   ⠀⢧⠀⠀⠀kt-coding-agent⠀⠀⠀⢠⠇⢤⣨⠇")
    println("   ⠀⠀⢧⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣤⢻⡀⣸")
    println("   ⠀⠀⠀⢸⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢹⠛⠉⠁")
    println("   ⠀⠀⠀⢸⠀⠀⠀⠀⠀⠀⠀⠀⢠⢄⣀⣤⠤⠴⠒⠀⠀⠀⠀⢸")
    println("   ⠀⠀⠀⢸⠀⠀⠀⠀⠀⠀⠀⠀⡇⠀⠀⢸⠀⠀⠀⠀⠀⠀⠀⠘⡆")
    println("   ⠀⠀⠀⡎⠀⠀⠀⠀⠀⠀⠀⠀⢷⠀⠀⢸⠀⠀⠀⠀⠀⠀⠀⠀⡇")
    println("   ⠀⠀⢀⡷⢤⣤⣀⣀⣀⣀⣠⠤⠾⣤⣀⡘⠛⠶⠶⠶⠶⠖⠒⠋⠙⠓⠲⢤⣀")
    println("   ⠀⠀⠘⠧⣀⡀⠈⠉⠉⠁⠀⠀⠀⠀⠈⠙⠳⣤⣄⣀⣀⣀⠀⠀⠀⠀⠀⢀⣈⡇")
    println("   ⠀⠀⠀⠀⠀⠉⠛⠲⠤⠤⢤⣤⣄⣀⣀⣀⣀⡸⠇⠀⠀⠀⠉⠉⠉⠉⠉⠉⠁")
    println()
}

private fun compactToolSummary(
    name: String,
    args: JsonObject,
    content: String,
    isError: Boolean
): String {
    val argLabel = args["path"]?.jsonPrimitive?.contentOrNull ?: args.toString()
    val summary = when {
        isError && "repeated identical tool call" in content -> "blocked"
        isError && "approval denied" in content -> "denied"
        isError -> "error"
        name == "list_files" -> "${content.lines().size} entries"
        name == "read_file" -> "${content.length} chars"
        name == "write_file" -> {
            // write_file's content is "wrote <relpath> (<n> chars)" — the path is already
            // in the call display, so we only show the size info.
            content.substringAfter("(", "").removeSuffix(")")
                .ifEmpty { "${content.length} chars" }
        }
        else -> "${content.length} chars"
    }
    return "$name($argLabel): $summary"
}

/**
 * Compact `name(path, "<preview>")` rendering of a risky tool call for the approval
 * prompt. Mirrors the `name(arg): summary` shape of [compactToolSummary] minus the
 * result half. String arg values longer than 80 chars are truncated to a 50-char
 * preview plus `…[+N chars]` so the prompt fits on one terminal line.
 */
private fun renderApproval(name: String, args: JsonObject): String {
    val path = args["path"]?.jsonPrimitive?.contentOrNull
    val content = args["content"]?.jsonPrimitive?.contentOrNull
    return when {
        path != null && content != null -> {
            val flat = content.replace('\n', ' ')
            val preview = if (flat.length > 80) flat.take(50) + "…[+${flat.length - 50} chars]" else flat
            "$name($path, \"$preview\")"
        }
        path != null -> "$name($path)"
        else -> "$name($args)"
    }
}

private fun loadOrCreateSession(
    resume: String?,
    sessionStore: SessionStore,
    cwd: Path,
    modelName: String
): Session {
    if (resume != null) {
        val id = if (resume == "latest") sessionStore.latestSessionId() else resume
        if (id != null) {
            return sessionStore.load(id)
        }
    }
    return Session(
        id = Session.newId(),
        createdAt = OffsetDateTime.now().toString(),
        workspaceRoot = cwd.toString(),
        modelName = modelName
    )
}

private fun readPreamble(): String =
    object {}.javaClass.getResource(PROMPT_PREAMBLE_RESOURCE)?.readText()
        ?: error("prompt_preamble.txt not found on classpath")

private data class ParsedArgs(
    val workspaceRoot: Path,
    val modelName: String,
    val host: String,
    val approval: String,
    val resume: String?,
    val maxSteps: Int,
    val maxNewTokens: Int,
    val temperature: Double,
    val topP: Double,
    val ollamaTimeout: java.time.Duration,
    val oneShot: String?,
    val walkToRepoRoot: Boolean,
    val verbose: Boolean
)

private fun parseArgs(args: List<String>): ParsedArgs {
    val defaults = AgentConfig()
    var workspaceRoot = defaults.workspaceRoot
    var modelName = defaults.modelName
    var host = defaults.host
    var approval = "ask"
    var resume: String? = null
    var maxSteps = defaults.maxSteps
    var maxNewTokens = defaults.maxNewTokens
    var temperature = defaults.temperature
    var topP = defaults.topP
    var ollamaTimeout = defaults.ollamaTimeout
    var walkToRepoRoot = defaults.walkToRepoRoot
    var verbose = false
    val positional = mutableListOf<String>()

    var i = 0
    while (i < args.size) {
        when (val token = args[i]) {
            "--cwd" -> workspaceRoot = Path.of(args[++i])
            "--model" -> modelName = args[++i]
            "--host" -> host = args[++i]
            "--approval" -> approval = args[++i]
            "--resume" -> resume = args[++i]
            "--max-steps" -> maxSteps = args[++i].toInt()
            "--max-new-tokens" -> maxNewTokens = args[++i].toInt()
            "--temperature" -> temperature = args[++i].toDouble()
            "--top-p" -> topP = args[++i].toDouble()
            "--ollama-timeout" -> ollamaTimeout = java.time.Duration.ofSeconds(args[++i].toLong())
            "--no-repo-walk" -> walkToRepoRoot = false
            "--verbose", "-v" -> verbose = true
            "--help", "-h" -> {
                printHelp()
                exitProcess(0)
            }
            else -> {
                if (token.startsWith("--")) {
                    System.err.println("unknown flag: $token")
                    exitProcess(2)
                }
                positional += token
            }
        }
        i++
    }
    val oneShot = if (positional.isEmpty()) null else positional.joinToString(" ")
    return ParsedArgs(
        workspaceRoot = workspaceRoot,
        modelName = modelName,
        host = host,
        approval = approval,
        resume = resume,
        maxSteps = maxSteps,
        maxNewTokens = maxNewTokens,
        temperature = temperature,
        topP = topP,
        ollamaTimeout = ollamaTimeout,
        oneShot = oneShot,
        walkToRepoRoot = walkToRepoRoot,
        verbose = verbose
    )
}

private fun printHelp() {
    println(
        """
        Usage: kt-coding-agent [prompt] [flags]

        Flags:
          --cwd <dir>          Workspace directory (default: .)
          --model <name>       Ollama model (default: qwen3.5:2b)
          --host <url>         Ollama host (default: http://127.0.0.1:11434)
          --approval auto|ask  Approval mode for risky tools (default: ask)
          --resume <id>        Resume a session by id, or 'latest'
          --max-steps <n>      Max tool/model iterations per request (default: 16)
          --max-new-tokens <n> Max output tokens per step (default: 1024)
          --temperature <f>    Sampling temperature (default: 0.2)
          --top-p <f>          Sampling top-p (default: 0.9)
          --ollama-timeout <s> Request timeout in seconds for Ollama HTTP calls (default: 300)
          --no-repo-walk       Only read project docs from --cwd (don't walk up to the git
                               repo root). Useful when --cwd is a self-contained fixture
                               nested inside an unrelated outer repo.
          --verbose, -v        Print each tool call's full output in the REPL. Default is
                               a one-line summary per tool call.
        """.trimIndent()
    )
}
