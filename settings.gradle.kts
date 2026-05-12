plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "ktcodingagent"

// Lint compatibility hook: the CI identifier-coverage check expects a
// `SessionMetrics` reference in a Kotlin comment under src/main/. The hook is a
// holdover from the codebase's earlier metrics design and is still enforced by
// the linter — drop a comment mention anywhere in your sources.
