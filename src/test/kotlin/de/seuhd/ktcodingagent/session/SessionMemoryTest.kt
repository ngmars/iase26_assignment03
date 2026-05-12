package de.seuhd.ktcodingagent.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionMemoryTest {

    private fun args(path: String): JsonObject = JsonObject(mapOf("path" to JsonPrimitive(path)))

    @Test
    fun recordToolCallClipsLongResultsToSingleLine() {
        val memory = SessionMemory()
        val longMultilineResult = "first line\n" + "x".repeat(500) + "\nthird"
        memory.recordToolCall("read_file", args("hello.txt"), longMultilineResult)
        assertEquals(1, memory.notes.size)
        val note = memory.notes[0]
        assertTrue(note.startsWith("read_file: "))
        assertFalse('\n' in note, "notes must be single-line")
        assertTrue(note.contains("...[truncated"), "should emit truncation marker")
        // 11 chars for the "read_file: " prefix + 220 truncation budget +
        // a short single-line suffix (e.g. "...[truncated 297 chars]"). Keep slack for the
        // truncation count digits, but assert the suffix is bounded.
        assertTrue(note.length <= "read_file: ".length + 220 + 40)
    }
}
