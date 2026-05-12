package de.seuhd.ktcodingagent.model

/**
 * Abstraction over the model: take a fully assembled prompt, return the raw textual response.
 *
 * One method only. [OllamaModelClient] is the live implementation; a scripted test double
 * lives in the test sources. Keeping this interface narrow lets the agent loop run
 * deterministically without an LLM. Prefer wrapping over widening.
 */
interface ModelClient {
    /**
     * Sends [prompt] to the model and returns the raw response text.
     *
     * @param prompt        the full concatenated prompt (prefix + memory + transcript + request).
     * @param maxNewTokens  the maximum number of output tokens the model may emit.
     */
    fun complete(prompt: String, maxNewTokens: Int): String
}
