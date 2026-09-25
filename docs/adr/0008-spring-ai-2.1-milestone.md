# ADR-0008 — Spring AI on the 2.1 milestone line, ahead of its release

- **Status:** accepted
- **Date:** 2026-09-25

## Context

OpenAI recommends the Responses API over Chat Completions for reasoning models,
and `gpt-6-luna` is one (ADR-0006). The api still speaks Chat Completions,
because that is all Spring AI 1.x can do, and Spring AI 2.x needs Spring Boot 4.

The Boot 4 migration makes 2.x possible, which leaves a choice of 2.x line:

| | 2.0.1 | 2.1.0-M1 |
|---|---|---|
| Release | GA | milestone |
| Responses API | **none** | `OpenAiResponsesChatModel`, selected by `spring.ai.openai.chat.api` |
| Built against | Boot 4.0 | Boot 4.2.0-M2 |

ADR-0006 said "2.x has `OpenAiResponsesChatModel`". That is true of 2.1 only;
2.0.1 has no Responses classes at all (checked in the jars, 2026-09-25).

Using a milestone contradicts Q6 in the roadmap and SPEC §12: pin milestone and
snapshot dependencies to releases, so that a build that works today still works
after another dormant stretch.

## Decision

Use **Spring AI 2.1.0-M1**, on **Spring Boot 4.1.1** (GA).

- Stay on Chat Completions for now: `spring.ai.openai.chat.api` is pinned to
  `chat-completions` (overridable with `OPENAI_CHAT_API`) rather than left to
  the default, so a later milestone cannot flip the protocol unmeasured.
- Switching to `responses` is a scanning change. It gets its own harness run
  against ground truth (ADR-0007), with nothing else changed in the same run.
- No extra Maven repository. Spring now publishes milestones to Maven Central,
  so `build.gradle` still resolves from `mavenCentral()` alone. The
  reproducibility risk Q6 describes (artifacts withdrawn from
  `repo.spring.io`) does not apply: Central does not delete releases, and
  milestones there are immutable versions.

## Why

2.0.1 would be a GA dependency that cannot do the one thing the upgrade is for.
Adopting it now means a second Spring AI migration later, with a second round of
renamed properties and schema changes, just to reach the Responses client.

The milestone's real risk is not that it disappears but that **the next
milestone changes behaviour**. 2.1.0-M1 already did, relative to 1.1.4:

- The chat options moved from `spring.ai.openai.chat.options.*` to
  `spring.ai.openai.chat.*`. The old keys still bind but are deprecated.
- The structured-output schema stopped marking fields `required`. A schema
  without `required` lets the model omit fields, so `ParsedReceipt` now states
  `required = true` on every field, and `ParsedReceiptSchemaTest` pins it.
- The schema gained `"format": "double"` on the three numeric item fields. That
  cannot be turned off without replacing Spring AI's converter; it only restates
  the Java types. The harness measures whether it matters.

Each one was found by capturing the exact request both versions send to a local
stub in place of OpenAI and diffing them. That is cheap, needs no API key, and
catches a prompt change before it costs a harness run.

**Version skew.** 2.1.0-M1 is built against Boot 4.2.0-M2, Framework 7.1.0-M2
and Micrometer 1.18.0-M2. Boot 4.1.1's dependency management resolves those to
Framework 7.0.9 and Micrometer 1.17.1. The alternative is a Boot milestone as
well, which doubles the exposure. The skew is covered by the test suite and by
the full harness run, which exercises every scanning path end to end. A
`NoSuchMethodError` on an untested path is the failure mode to watch for.

## Measured

Full 64-fixture harness, master (Boot 3.5.6, Spring AI 1.1.4) against this
change, run back to back on 2026-09-25 against the same database, OCR install
and model. Baselines in `receipt-fixtures/baselines/2026-09-25-{before,after}-boot4/`.

| | before | after | |
|---|---|---|---|
| HTTP 200 | 54 | 54 | |
| L0 passed | 23 | 23 | |
| L1 store / date / item count / reconciles | 16 / 18 of 27 / 40 / 29 | 16 / 18 of 27 / 39 / 29 | |
| L2 right price | 253 | 253 | floor ±2 |
| L2 right quantity | 74 | 73 | floor ±3 |
| L2 items matched / right discount | 267 / 254 | 269 / 255 | |
| median scan | 18.5 s | 17.6 s | |

No layer moved outside the noise floor. The before run was itself within ±2 of
the recorded M0a baseline (254 prices), so the environment is comparable.

## Exit condition

Move to **Spring AI 2.1.0 GA** as soon as it is released, together with the
Boot line it is built against (expected 4.2.x). That closes Q6 for Spring AI.

Until then:

- A new 2.1 milestone is adopted only with the request-capture diff and, if the
  prompt or the protocol changed, a harness run.
- If 2.1 GA slips past **2026-12-31**, reconsider: either 2.0.x GA with Chat
  Completions, or stay on the milestone deliberately and record why.

## Consequences

- `build.gradle` depends on a milestone. Dependabot will propose the next
  milestone and the eventual GA; neither is a routine merge (see above).
- The Responses move is one property plus one harness run. Reasoning summaries,
  which ADR-0006 wanted in order to see why a receipt parsed badly, come with it.
- Spring AI's structured-output schema is now part of what a dependency bump can
  change, so it is pinned by a test rather than assumed.
