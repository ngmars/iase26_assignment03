package de.seuhd.ktcodingagent.parse

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ResponseParserTest {

    @Test
    fun parsesToolWithJsonBody() {
        val parsed = ResponseParser.parse("""<tool>{"name":"list_files","args":{"path":"."}}</tool>""")
        val tool = assertIs<Parsed.Tool>(parsed)
        assertEquals("list_files", tool.name)
        assertEquals(".", tool.args["path"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun parsesToolWithSurroundingText() {
        val parsed = ResponseParser.parse(
            "Sure, here you go:\n<tool>{\"name\":\"read_file\",\"args\":{\"path\":\"a.txt\"}}</tool>\nokay"
        )
        val tool = assertIs<Parsed.Tool>(parsed)
        assertEquals("read_file", tool.name)
    }

    @Test
    fun parsesFinal() {
        val parsed = ResponseParser.parse("<final>Hello world.</final>")
        val finalAns = assertIs<Parsed.Final>(parsed)
        assertEquals("Hello world.", finalAns.text)
    }

    @Test
    fun emptyRawReturnsRetry() {
        val parsed = ResponseParser.parse("")
        val retry = assertIs<Parsed.Retry>(parsed)
        assertTrue("empty response" in retry.notice)
    }

    @Test
    fun malformedToolJsonReturnsRetry() {
        val parsed = ResponseParser.parse("""<tool>{not-json</tool>""")
        val retry = assertIs<Parsed.Retry>(parsed)
        assertTrue("malformed tool JSON" in retry.notice)
    }

    @Test
    fun missingToolNameReturnsRetry() {
        val parsed = ResponseParser.parse("""<tool>{"args":{}}</tool>""")
        val retry = assertIs<Parsed.Retry>(parsed)
        assertTrue("tool name" in retry.notice)
    }

    @Test
    fun nonObjectArgsReturnsRetry() {
        val parsed = ResponseParser.parse("""<tool>{"name":"x","args":"oops"}</tool>""")
        assertIs<Parsed.Retry>(parsed)
    }

    @Test
    fun rawTextWithoutTagsReturnsFinal() {
        val parsed = ResponseParser.parse("  done  ")
        val finalAns = assertIs<Parsed.Final>(parsed)
        assertEquals("done", finalAns.text)
    }

    @Test
    fun nonPrimitiveToolNameReturnsRetry() {
        // The model sometimes hallucinates a structured value where a string is expected.
        // The parser must not crash on `{"name": {...}}` — it must emit a Retry.
        val parsed = ResponseParser.parse("""<tool>{"name":{"x":"y"},"args":{}}</tool>""")
        val retry = assertIs<Parsed.Retry>(parsed)
        assertTrue("tool name" in retry.notice)
    }
}
