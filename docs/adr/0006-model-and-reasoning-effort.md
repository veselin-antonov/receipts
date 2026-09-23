# ADR-0006 — gpt-6-luna at medium reasoning effort

- **Status:** accepted
- **Date:** 2026-09-23

## Context

Parsing was on `gpt-5-mini`. Scans took a median of 38 seconds, which is too
slow for the capture loop, and accuracy had plateaued at 232 correct prices
across the 64-fixture set.

## Decision

`gpt-6-luna`, `reasoning-effort: medium`, `temperature: 1`. Both the model and
the effort are environment-overridable (`OPENAI_MODEL`,
`OPENAI_REASONING_EFFORT`); temperature is not.

## Why

Measured on the fixture set, against a ±2 run-to-run noise floor established by
running the same configuration twice:

| | gpt-5-mini | gpt-6-luna |
|---|---|---|
| correct prices | 232 | **252** |
| median scan | 38s | **16s** |
| cost per scan | ~$0.0050 | ~$0.00055 |

Better, roughly twice as fast, and about 8× cheaper — unusual enough to be
worth stating plainly, because it means there was no trade to make.

**Temperature is pinned at 1 because the model rejects anything else**:
"does not support 0 with this model. Only the default (1) value is supported."
Lowering temperature for determinism is the obvious instinct and it does not
work on reasoning models. The equivalent lever is `reasoning_effort`.

`medium` is the provider default and beat the alternatives here. `none` was
tested and was worse; `gpt-5.6-luna` was tested and did not justify its price
(4× the cost for no measured gain at this task).

## Consequences

- **Spring AI stays on 1.1.4.** 2.x has `OpenAiResponsesChatModel` but requires
  Spring Boot 4; 2.0.1 on Boot 3 fails at runtime with
  `NoClassDefFoundError: org.springframework.boot.EnvironmentPostProcessor`.
  Boot 4 was trialled and reverted: it drops `spring-boot-starter-aop` in favour
  of `aspectjweaver` and moves `@WebMvcTest` out of
  `spring-boot-test-autoconfigure`. Worth doing, but on its own.
- **The LLM is 93% of scan time**, OCR 6%, cropping 0.6%. Any future latency
  work belongs at the model, not the pipeline. This also caps what preprocessing
  can win: it can buy accuracy, not speed.
- Milestone and snapshot repositories were removed from `build.gradle` —
  everything resolves from Maven Central.
