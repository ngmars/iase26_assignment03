package de.seuhd.ktcodingagent.session

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * JSON-on-disk persistence for [Session].
 *
 * One file per session, named `{id}.json`, all under [root] (typically
 * `{workspaceRoot}/.kt-coding-agent/sessions/`). The [Agent] saves after every history
 * entry so an interrupted run still leaves a complete partial transcript on disk.
 */
class SessionStore(private val root: Path) {

    init {
        Files.createDirectories(root)
    }

    /** Writes [session] to disk, overwriting any previous file with the same id. */
    fun save(session: Session): Path {
        val target = pathFor(session.id)
        Files.writeString(target, JSON.encodeToString(session))
        return target
    }

    /** Reads a session by id. Throws [IllegalArgumentException] if the file does not exist. */
    fun load(sessionId: String): Session {
        val target = pathFor(sessionId)
        if (!Files.isRegularFile(target)) {
            throw IllegalArgumentException("session not found: $sessionId")
        }
        return JSON.decodeFromString(Files.readString(target))
    }

    /**
     * Returns the id of the most-recently-modified session under [root], or `null` if there
     * are none. Used by `--resume latest`.
     */
    fun latestSessionId(): String? = Files.list(root).use { stream ->
        stream
            .filter { it.fileName.toString().endsWith(".json") }
            .sorted(compareBy { Files.getLastModifiedTime(it) })
            .toList()
            .lastOrNull()
            ?.fileName
            ?.toString()
            ?.removeSuffix(".json")
    }

    /** Absolute path that [save] would write for the given session id. */
    fun pathFor(sessionId: String): Path = root.resolve("$sessionId.json")

    companion object {
        val JSON: Json = Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
