# AGENTS.md

Context for AI coding assistants working on this repository.

## Project

`kt-coding-agent` is the starter for IASE Assignment 3 (Heidelberg University,
SS 2026) — a Kotlin/JVM port of Sebastian Raschka's
[`mini-coding-agent`](https://github.com/rasbt/mini-coding-agent). The student
implements four sub-exercises against a fixed JUnit 5 suite; turning the 56
failing tests green is the deliverable.

## Build and test

- Run the test suite: `./gradlew test`
- Full build: `./gradlew build`
- REPL against the demo fixture: `./gradlew run --console=plain --args="--cwd demo/workspace"`

The deterministic suite uses a `StubModelClient`; no Ollama install is
required for the base grade. The single live-integration test
(`OllamaModelClientIntegrationTest`) is gated behind
`-Dollama.test=true` and stays skipped otherwise.

## Layout

- `src/main/kotlin/de/seuhd/ktcodingagent/` — production sources.
- `src/test/kotlin/de/seuhd/ktcodingagent/` — JUnit 5 tests, one file per
  sub-exercise.
- `demo/workspace/` — fixture workspace for the +1 bonus live-Ollama run.
- `prompt_preamble.txt` — the system-style rules block prepended to every
  prompt; lives under `src/main/resources/`.

## Code style

- Kotlin official style (`kotlin.code.style=official`).
- Prefer expression bodies, `val` over `var`, and idiomatic Kotlin over
  Java-style boilerplate.
- No mocking framework. Tests use the in-repo `StubModelClient`, `@TempDir`,
  and small hand-written fakes.

## Don't touch

- `gradle/wrapper/` and the `gradlew` / `gradlew.bat` launchers.
- `mise.toml` (JDK pinned to Temurin 25).
- `prepare-submission.sh` (graders run it as-is to validate the zip).

## Submission

See `README.md` § Submission for the full rules. Two requirements are
load-bearing: the submitted zip must contain the `.git/` directory, and the
unzipped repo must build with `./gradlew test` on JDK 25 without further
setup. Either failure is graded 0.
