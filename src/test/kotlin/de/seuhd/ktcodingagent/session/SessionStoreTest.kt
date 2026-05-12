package de.seuhd.ktcodingagent.session

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionStoreTest {

    private fun newSession(id: String, root: Path): Session = Session(
        id = id,
        createdAt = OffsetDateTime.now().toString(),
        workspaceRoot = root.toString(),
        modelName = "stub"
    )

    @Test
    fun saveAndLoadRoundTrip(@TempDir tmp: Path) {
        val store = SessionStore(tmp.resolve("sessions"))
        val session = newSession("abc", tmp).also {
            it.memory.task = "round-trip"
            it.history.add(HistoryEntry.UserEntry("hi", "t1"))
        }
        store.save(session)
        val loaded = store.load("abc")
        assertEquals("round-trip", loaded.memory.task)
        assertEquals(1, loaded.history.size)
        assertTrue(loaded.history[0] is HistoryEntry.UserEntry)
    }

    @Test
    fun latestSessionIdReturnsMostRecentlyModified(@TempDir tmp: Path) {
        val store = SessionStore(tmp.resolve("sessions"))
        store.save(newSession("old", tmp))
        Thread.sleep(20)
        store.save(newSession("new", tmp))
        assertEquals("new", store.latestSessionId())
    }

    @Test
    fun loadMissingSessionThrows(@TempDir tmp: Path) {
        val store = SessionStore(tmp.resolve("sessions"))
        assertFailsWith<IllegalArgumentException> { store.load("nope") }
    }

    @Test
    fun historyEntryDiscriminatorIsStable(@TempDir tmp: Path) {
        val store = SessionStore(tmp.resolve("sessions"))
        val session = newSession("disc", tmp).also {
            it.history.add(HistoryEntry.UserEntry("hi", "t1"))
            it.history.add(HistoryEntry.AssistantEntry("there", "t2"))
        }
        store.save(session)
        val json = store.pathFor("disc").toFile().readText()
        assertTrue("\"type\": \"user\"" in json, "expected user discriminator in JSON")
        assertTrue("\"type\": \"assistant\"" in json, "expected assistant discriminator in JSON")
    }
}
