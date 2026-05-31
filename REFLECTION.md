# Reflection

**Prompt.** What classes of agent failures can `StubModelClient` not catch, and
what would you do instead?

<!-- TODO (~150 words). Name at least two concrete failure modes the stub
cannot reproduce. For each, describe how you would catch it in practice. -->

It can’t catch real-world failures outside scripted text, like:

- network/HTTP issues (timeouts, 500s, bad JSON)
- model randomness
- weird formatting the real model produces unexpectedly
- long-context/token-limit behavior
- performance/latency problems

Use `StubModelClient` for fast, deterministic unit tests, and pair it with
HTTP contract tests plus a few gated end-to-end tests against real Ollama to
catch the issues a stub won’t surface.
