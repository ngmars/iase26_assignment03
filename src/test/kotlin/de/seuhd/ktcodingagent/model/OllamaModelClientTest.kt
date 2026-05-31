package de.seuhd.ktcodingagent.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OllamaModelClientUnitTest {

    @Test
    fun completePostsExpectedPayload() {
        val capture = RequestCapture()
        val stub = StubHttpClient(
            capture = capture,
            cannedBody = """{"response":"<final>ok</final>"}""",
            cannedStatus = 200
        )
        val client = OllamaModelClient(
            modelName = "qwen3.5:2b",
            host = "http://127.0.0.1:11434",
            httpClient = stub
        )

        val result = client.complete("hello", 42)

        assertEquals("<final>ok</final>", result)
        assertEquals(URI.create("http://127.0.0.1:11434/api/generate"), capture.uri)
        val body = Json.parseToJsonElement(capture.body).jsonObject
        assertEquals("qwen3.5:2b", body["model"]?.jsonPrimitive?.content)
        assertEquals("hello", body["prompt"]?.jsonPrimitive?.content)
        assertEquals("false", body["stream"]?.jsonPrimitive?.toString().orEmpty().trim('"'))
        val options = body["options"] as JsonObject
        assertEquals("42", options["num_predict"]?.jsonPrimitive?.toString().orEmpty().trim('"'))
    }

    @Test
    fun checkAvailabilityReportsModelMissing() {
        val capture = RequestCapture()
        val stub = StubHttpClient(
            capture = capture,
            cannedBody = """{"models":[{"name":"llama3:8b"}]}""",
            cannedStatus = 200
        )
        val client = OllamaModelClient(
            modelName = "qwen3.5:2b",
            host = "http://127.0.0.1:11434",
            httpClient = stub
        )

        val result = client.checkAvailability()

        assertTrue(result is AvailabilityCheck.ModelMissing)
        assertEquals(URI.create("http://127.0.0.1:11434/api/tags"), capture.uri)
    }

    @Test
    fun checkAvailabilityReportsReadyWhenModelPresent() {
        val stub = StubHttpClient(
            capture = RequestCapture(),
            cannedBody = """{"models":[{"name":"qwen3.5:2b"}]}""",
            cannedStatus = 200
        )
        val client = OllamaModelClient(
            modelName = "qwen3.5:2b",
            host = "http://127.0.0.1:11434",
            httpClient = stub
        )

        assertEquals(AvailabilityCheck.Ready, client.checkAvailability())
    }

    @Test
    fun completeThrowsOnHttpError() {
        val stub = StubHttpClient(
            capture = RequestCapture(),
            cannedBody = "internal server error",
            cannedStatus = 500
        )
        val client = OllamaModelClient(
            modelName = "qwen3.5:2b",
            host = "http://127.0.0.1:11434",
            httpClient = stub
        )

        val failure = assertFailsWith<RuntimeException> { client.complete("hello", 16) }

        assertTrue("500" in failure.message.orEmpty())
        assertTrue("internal server error" in failure.message.orEmpty())
    }

    @Test
    fun completeThrowsOllamaUnreachableOnIoException() {
        val client = OllamaModelClient(
            modelName = "qwen3.5:2b",
            host = "http://127.0.0.1:11434",
            httpClient = FailingHttpClient()
        )

        val failure = assertFailsWith<OllamaUnreachableException> { client.complete("hello", 16) }

        assertTrue("Could not reach Ollama" in failure.message.orEmpty())
    }
}

class OllamaModelClientIntegrationTest {

    @Test
    @EnabledIfSystemProperty(named = "ollama.test", matches = "true")
    fun smokeTestAgainstLiveOllama() {
        val client = OllamaModelClient(modelName = "qwen3.5:2b")
        val availability = client.checkAvailability()
        assertEquals(AvailabilityCheck.Ready, availability)
        val response = client.complete("Reply with exactly: ok", 16)
        assertTrue(response.isNotBlank())
    }
}
