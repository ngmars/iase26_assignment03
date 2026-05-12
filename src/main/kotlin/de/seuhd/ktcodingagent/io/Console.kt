package de.seuhd.ktcodingagent.io

/**
 * Tiny wrapper over stdin to make EOF distinguishable from an empty line.
 *
 * Kotlin's bare `readLine()` returns `null` on EOF and `""` on an empty line; callers that
 * use `?: ""` collapse the two and end up looping forever on a closed stdin. Going through
 * [readLineOrNull] preserves the distinction so callers can `break` on null.
 */
object Console {
    /** Returns the trimmed line, or `null` on EOF. */
    fun readLineOrNull(): String? = readlnOrNull()?.trim()

    /** Prompts for `[y/N]` confirmation on its own line. Defaults to deny on anything other than `y`/`yes`. */
    fun confirm(prompt: String): Boolean {
        print("$prompt\n[y/N] ")
        val answer = readLineOrNull()?.lowercase() ?: ""
        return answer == "y" || answer == "yes"
    }
}
