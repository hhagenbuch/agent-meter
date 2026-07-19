# agent-meter — Design (RFC)

**Status:** Draft / pre-code (Phase 0)
**Author:** Heyward Hagenbuch

OpenTelemetry-native token/cost attribution and budget enforcement for LLM
agents. This document is the contract for what gets built; code follows it in
phases.

> **Implementation status.** Shipped: `meter-core` (cost engine, budgets), `meter-spring`
> (metering + enforcement over a provider-agnostic seam), and **`meter-starter`** — the
> drop-in adapter that auto-instruments `spring-ai-agent-starter` with a `BeanPostProcessor`
> and zero code changes (§10). The demo runs the real instrumented agent (`demo-agent`)
> with a key, or a synthetic provider without one. Still roadmapped: per-attempt HTTP
> capture via a `WebClient` filter (§8), and budget `degrade` at the starter seam (§7).

## 1. Problem

Teams shipped agents fast and are now holding an invoice they can't decompose.
"We spent $40k on Claude last month" is not actionable; "the support-chat feature
spent $31k, 60% of it on retries, mostly on prompt version v7" is. The missing
capability is **attribution** — tying spend to the units a business reasons about
(feature, session, tool, prompt version) — and **control** that degrades before it
denies, the way latency budgets do.

**Cost is a reliability dimension.** You would never run a service without latency
and error-rate metrics; agents run without cost metrics every day. agent-meter
treats cost as a first-class signal on the same telemetry rails as everything else.

## 2. Non-goals (MVP)

- **No UI of our own.** We emit standard OTel; Grafana/Datadog/Honeycomb is the UI.
  We ship a Grafana dashboard as an example, not a product.
- **No multi-instance budget store.** Windows are tracked in-memory behind a store
  interface; a Redis impl is roadmap. The single-instance limitation is documented,
  not hidden.
- **No proxy/gateway mode.** Enforcement is an in-process `LlmClient` decorator, not
  a man-in-the-middle proxy. (A gateway is a different product with different trust
  and latency properties.)
- **No prompt/response content in telemetry by default.** The `gen_ai` semconv marks
  message content as opt-in; we keep it off by default (privacy, consistent with
  agent-blackbox).

## 3. Semantic-convention adherence

We use the OpenTelemetry **GenAI semantic conventions** rather than inventing a
format, so the data works in any OTel backend and interops with other GenAI
instrumentation. GenAI semconv is currently **Development** stability (still
evolving); we **pin the version we target and record it here**, revisiting each
semconv release.

- **Target semconv version:** 1.30.x (GenAI group), Development stability.
  <https://opentelemetry.io/docs/specs/semconv/gen-ai/>
- **Standard attributes we emit:** `gen_ai.system`, `gen_ai.operation.name`,
  `gen_ai.request.model`, `gen_ai.response.model`, `gen_ai.usage.input_tokens`,
  `gen_ai.usage.output_tokens`.
- **Our namespaced additions** (things the spec doesn't cover — cost and the FinOps
  attribution units) live under `agent.*` so they never collide with standard keys:
  `agent.session_id`, `agent.tool`, `agent.prompt_version`, `agent.feature`,
  `agent.cost_usd`, `agent.cost_estimated`, `agent.budget_degraded`, `agent.incomplete`.

## 4. Attribution model

Every LLM-call span carries:

| Attribute | Source |
|---|---|
| `gen_ai.request.model` / `gen_ai.response.model` | client |
| `gen_ai.usage.input_tokens` / `output_tokens` | API response `usage` |
| `agent.session_id` | conversation |
| `agent.tool` | set when the call is a step in a tool loop |
| `agent.prompt_version` | config/env (agent-operator sets this label on deployments — cross-repo synergy) |
| `agent.feature` | caller-supplied tag on the chat request (`ChatRequest.feature`) — the FinOps unit |
| `agent.cost_usd` | computed by the cost engine (§5) |

Metrics (with **exemplars** linking each datapoint back to a span, so a spike in a
dashboard is one click from the trace that caused it):

- `agent.tokens` — counter, `direction` label (`input`/`output`)
- `agent.cost_usd` — counter
- `agent.turn.duration` — histogram

All dimensioned by `gen_ai.response.model`, `agent.feature`, `agent.prompt_version`.
(Cardinality note: `agent.session_id` is span/exemplar-only, never a metric label.)

## 5. Cost engine (`meter-core`) — pure and versioned

- **Price table as data, not code.** `prices.yaml` ships in the jar; overridable via
  file or env. Each model has one or more rate periods; each period carries an
  `effective_from` date and per-MTok `input` / `output` (and optional `cached_input`)
  rates. The engine selects the period whose `effective_from` is the latest one at or
  before the call time. Values come from public pricing pages with the retrieval date
  cited in-file.
- **Cost formula:** `cost = input_tokens/1e6 · input_rate + output_tokens/1e6 ·
  output_rate`, plus cached-input tokens at the cached rate when the response reports
  them (Anthropic splits `cache_creation_input_tokens` / `cache_read_input_tokens`).
- **Staleness is loud.** The table carries a `generated_on` date. If it is older than
  **90 days** at startup, log a prominent warning and set `agent.cost_estimated=true`
  on every span. Prices drift; silently-wrong cost data is worse than none.
- **Unknown model is `unknown`, never `0.00`.** A model with no rate records no
  `agent.cost_usd` attribute, increments an `agent.cost.unknown_model` counter, and
  warns once. Zero is a lie that hides exactly the spend you most need to see.
- Pure functions; the **test suite is the spec**: rate boundaries either side of an
  `effective_from`, cached-token pricing, unknown model, missing `usage`.

## 6. Budget enforcement (`meter-core` policy + `meter-spring` decorator)

Budgets are declarative:

```yaml
meter:
  budgets:
    - scope: feature:support-chat      # or session:*, or global
      limit_usd: 5.00
      window: daily                    # hourly | daily | monthly (calendar, UTC)
      on_breach: degrade               # warn | degrade | block
      degrade_to: claude-haiku-4-5     # cheaper model for the rest of the window
```

- **Enforcement is on accumulated window spend**, checked *before* a call (a call's
  own cost isn't known until after it runs). So the call that crosses the limit is
  allowed — a small, bounded overshoot — and *subsequent* calls in the window take the
  breach action. This overshoot is documented, not pretended away.
- **Actions:** `warn` emits an event + metric and proceeds; `degrade` swaps the
  request model to `degrade_to` and tags the span `agent.budget_degraded=true` (the
  user-visible-provenance idea from castaway, applied to cost); `block` fails fast with
  a message written for the annoyed developer reading it at 2am (which budget, the
  window, current spend vs limit, when it resets).
- **Windows** are calendar-aligned (UTC) and tracked in-memory behind a
  `BudgetStore` interface. At window rollover, spend resets and a degraded scope
  recovers to its normal model automatically. Redis impl is roadmap; single-instance
  is the documented MVP limitation.
- **State machine** per (scope, window): `OK → (spend ≥ limit) → BREACHED`; the action
  taken in BREACHED is the budget's `on_breach`; rollover returns to `OK`.

## 7. Decorator ordering

Two decorators wrap the starter's `LlmClient`: agent-meter and (separately)
agent-blackbox. **agent-meter sits outermost.** Rationale:

1. Budget `degrade` must mutate the request model *before* anything else runs, so the
   flight recorder (blackbox, inner) records the model that actually executed.
2. Cost is the outer envelope: the meter's turn span is the parent, and per-attempt
   spans (retries, §8) nest beneath it.

Order: `ModelRouter/AnthropicClient` ← blackbox ← **meter** ← caller. In `meter-starter`
this is enforced by giving the metering `BeanPostProcessor` `Ordered.LOWEST_PRECEDENCE`, so
it runs last and therefore wraps outermost — outside any recorder that also post-processes
the client. (Cross-repo ordering with agent-blackbox is documented here rather than tested,
since blackbox isn't a dependency.)

### 7.1 The `meter-starter` adapter (drop-in)

`meter-starter` auto-instruments a `spring-ai-agent-starter` app with no code changes: a
`BeanPostProcessor` wraps the starter's `LlmClient` bean with a reactive metering decorator.
Because `chat(messages, tools)` carries no model/session/feature, the adapter sources them
as: **model** from `agent.model` config, **usage/cost** from the starter's `LlmResponse.usage`,
**session/feature** from `X-Session-Id`/`X-Feature` headers a `WebFilter` lifts into the
Reactor context (read via `Mono.deferContextual`), **prompt_version** from `meter.prompt-version`.

### 7.2 Budget `degrade` decision at the starter seam

`degrade` swaps the request model — but the starter's `chat()` has **no model argument**, so
a decorator outside it cannot change the model. Decision: at the starter seam `meter-starter`
enforces **`warn` and `block` only**; `degrade` is a no-op-to-`warn` there and is documented
as such. Full `degrade` is available through the provider-agnostic `meter-spring` seam (which
owns the request), or would need an **optional** `spring-ai-agent-starter` change: a
per-request model override (e.g. a Reactor-context key `AnthropicClient` consults). That is a
separate starter PR, deliberately not bundled here; until then, degrade-at-the-starter is
facade-only and the docs say so.

## 8. Streaming and edge cases (designed in, not discovered late)

- **Streaming.** Usage arrives only in the terminal event, so the turn span stays open
  until then. A stream that dies mid-flight records tokens-so-far and sets
  `agent.incomplete=true` — partial cost is real cost.
- **Retries.** Each HTTP attempt is a child span and **cost counts every attempt** —
  you pay for retries, and that visibility is a feature (the README will show a
  retry-storm as a selling point). Per-attempt visibility needs the HTTP layer, so
  meter-spring installs a `WebClient` `ExchangeFilterFunction` that emits a child span
  per attempt and sums usage; where a client doesn't expose attempts, cost reflects the
  final response and the span is flagged.
- **Privacy.** Prompt/response content is never placed in attributes by default
  (`gen_ai` marks content opt-in). Opting in is a conscious per-deployment choice.

## 9. Modules

| Module | Contents | Deps |
|---|---|---|
| `meter-core` | cost engine, price table, budget policy — pure | Jackson, OTel API only |
| `meter-spring` | auto-config, `LlmClient` metering + budget decorators, `feature` propagation | Spring Boot, OTel SDK |
| `demo/` | compose stack (Collector → Prometheus + Tempo → Grafana), dashboard JSON, load script | — |

## 10. Build phases

0. **Design** — this document, committed alone.
1. **`meter-core`** — `prices.yaml` schema + cost engine + budget policy as pure code;
   table-driven tests (effective-date boundaries, cached pricing, unknown model, window
   rollover, degrade-then-recover at reset).
2. **`meter-spring`** — the metering decorator emitting spans/metrics per §4; usage
   extraction from the starter's Anthropic responses and the streaming terminal event;
   retry child spans. Verified attribute-exact via OTel `InMemorySpanExporter` in CI
   (no collector needed).
3. **Budget enforcement** — warn/degrade/block decorators; degrade proven end-to-end
   with a fake client (request model actually swapped, span tagged, recovers at reset);
   `block` message quality asserted by a test.
4. **Demo + README** — compose stack, Grafana dashboard JSON, load script, hero
   screenshot; README with the "cost is a reliability dimension" framing and the
   boundary notes.

## 11. Boundary

- **vs. [agent-blackbox](https://github.com/hhagenbuch/agent-blackbox):** blackbox
  answers *what happened* (flight recorder); agent-meter answers *what did it cost*.
  Same `LlmClient` decorator seam and the same OTel span; disjoint responsibilities.
- **vs. vendor observability suites:** we emit standard OTel GenAI semconv, so the data
  is portable and outlives any single vendor decision — the differentiator is no
  lock-in, exemplars included.
