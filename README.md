# agent-meter

[![CI](https://github.com/hhagenbuch/agent-meter/actions/workflows/ci.yml/badge.svg)](https://github.com/hhagenbuch/agent-meter/actions)
![Java 25](https://img.shields.io/badge/Java-25-blue)
![OTel GenAI semconv](https://img.shields.io/badge/OpenTelemetry-GenAI%20semconv-blueviolet)

> Every company that shipped agents last year is now staring at the invoice and
> cannot answer the only question that matters: **which feature is spending
> this?** `agent-meter` instruments your agent with OpenTelemetry — standard
> `gen_ai.*` semantic conventions, so it lands in the Grafana you already have —
> and attributes every token and every cent to a session, a tool, a prompt
> version, and a feature. Then it enforces budgets the way a good SRE would:
> warn, degrade to a cheaper model, and only then block.

**Cost is a reliability dimension.** You'd never ship a service without latency
metrics; agents ship without cost metrics every day.

**Status: Phase 0 — design.** This repo currently contains the design only. See
[`docs/DESIGN.md`](docs/DESIGN.md) for the RFC. Code lands in phases; the roadmap
below tracks progress.

## What it does

- **Attributes** every LLM call — tokens and dollars — to a `session`, `tool`,
  `prompt_version`, and `feature`, on OpenTelemetry spans and metrics using the
  official [GenAI semantic conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/).
  It works in Grafana / Datadog / Honeycomb on day one; no vendor SDK, no lock-in.
- **Costs honestly.** A versioned price table (`prices.yaml`, `effective_from`
  dates) computes `agent.cost_usd`. Stale tables confess (`agent.cost_estimated=true`);
  unknown models are recorded as *unknown*, never `0.00`; retries count (when your
  client reports each attempt — see Integration status), because you pay for them.
- **Enforces budgets by degrading before denying.** Declarative budgets per
  feature / session / global: `warn` → `degrade` to a cheaper model (span tagged
  `agent.budget_degraded=true`) → `block` with a message a 2am on-call can act on.

  ```yaml
  meter:
    budgets:
      - scope: feature:support-chat   # or session:*, or global
        limit-usd: 5.00
        window: daily                 # hourly | daily | monthly (calendar, UTC)
        on-breach: degrade            # warn | degrade | block
        degrade-to: claude-haiku-4-5  # cheaper model for the rest of the window
  ```

  Wrap your provider client once — `instrumentation.instrument(providerClient)` —
  and you get metering + enforcement in the right order (budget outside meter, so
  a degraded model is what the span records). A degraded scope recovers on its own
  when the window rolls over.

## Modules

| Module | Contents | Deps |
|--------|----------|------|
| `meter-core` | cost engine, versioned price table, budget policy — **pure** | Jackson, OTel API |
| `meter-spring` | auto-config, provider-agnostic `LlmClient` metering + budget decorators | Spring Boot, OTel SDK |
| `meter-starter` | drop-in auto-instrumentation for `spring-ai-agent-starter` (BeanPostProcessor) | meter-spring, the starter |
| `demo/` | `docker-compose` (Collector → Prometheus + Tempo → Grafana), dashboard, real (`demo-agent`) + synthetic (`demo-app`) load | — |

## Add the dependency, get cost telemetry

For a [spring-ai-agent-starter](https://github.com/hhagenbuch/spring-ai-agent-starter)
app, that's now literal — add **`meter-starter`** and an `OpenTelemetry` bean, change no
code:

```xml
<repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>

<dependency>
  <groupId>io.github.hhagenbuch</groupId>
  <artifactId>meter-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

A `BeanPostProcessor` wraps the starter's `LlmClient` bean. Every chat call emits the
`gen_ai.*`/`agent.*` span + metrics: **model** from `agent.model`, **usage/cost** from the
starter's `LlmResponse.usage`, **session/feature** from the `X-Session-Id`/`X-Feature`
request headers (a `WebFilter` lifts them into the Reactor context), **prompt_version**
from `meter.prompt-version`. Budgets `warn` and `block` enforce here too.

For any **other** client, `meter-spring` exposes a provider-agnostic seam
(`instrumentation.instrument(yourClient)`) you adapt to directly.

### Two honest caveats

- **Retry cost is aggregate at the starter seam.** The starter retries *inside* its
  `AnthropicClient`, so per-attempt cost isn't observable from a decorator; you get the
  turn total (correct, not the retry-storm breakdown). Per-attempt capture needs a
  `WebClient` `ExchangeFilterFunction` (roadmap, DESIGN §8).
- **Budget `degrade` is not available at the starter seam.** `chat()` has no model
  argument, so the adapter can `warn`/`block` but can't swap the model; `degrade` works
  via the provider-agnostic seam, or would need an optional starter model-override
  (DESIGN §7).

## Demo

A one-command observability stack in [`demo/`](demo/): OTLP → Collector → Prometheus +
Tempo → Grafana, with a **shipped dashboard** (cost by feature, tokens by model, budget
burn-down, exemplar → trace).

> **Two modes.** With `ANTHROPIC_API_KEY` set, `docker compose --profile real up` runs
> `demo-agent` — the **real** starter, auto-instrumented by `meter-starter`, showing real
> token/cost data. Without a key, `demo-app` drives a **synthetic** fake provider (the
> meter runs for real; the token numbers are generated). Both are clearly labelled in
> [`demo/README.md`](demo/README.md).

```bash
cd demo && docker compose up -d
(cd .. && mvn -q -pl demo-app spring-boot:run)   # in another shell
./load.sh                                        # watch support-chat trip its budget → degrade
open http://localhost:3000
```

Click a cost spike and the metric **exemplar** takes you straight to the trace that caused
it. (The hero screenshot is a manual capture — see [`demo/README.md`](demo/README.md).)

## Boundary

- **[agent-blackbox](https://github.com/hhagenbuch/agent-blackbox)** answers *what
  happened* (the flight recorder); **agent-meter** answers *what did it cost*. They
  share the starter's `LlmClient` decorator seam and the OTel span; they do not
  overlap in responsibility.
- **vs. vendor observability suites** — agent-meter emits standard OTel semconv, so
  the data outlives any single vendor decision. Portability is the differentiator.

## Roadmap

- [x] Phase 0 — design doc ([`docs/DESIGN.md`](docs/DESIGN.md))
- [x] Phase 1 — `meter-core`: versioned price table + cost engine + budget policy (pure, Java 25)
- [x] Phase 2 — `meter-spring`: OTel metering decorator (spans/metrics, retry child spans),
      verified attribute-exact with `InMemorySpanExporter`; Spring auto-config
- [x] Phase 3 — budget enforcement: warn / degrade / block decorator (budget outside meter),
      degrade proven end-to-end, recovers at window reset
- [x] Phase 4 — [demo stack](demo/): compose (Collector/Prometheus/Tempo/Grafana),
      shipped dashboard, `demo-app` + load script (GIF/screenshot is a manual capture)
- [x] `meter-starter` — drop-in adapter for
      [spring-ai-agent-starter](https://github.com/hhagenbuch/spring-ai-agent-starter)
      (`BeanPostProcessor`, zero code changes); real agent wired into the demo (`demo-agent`)
- [ ] Per-attempt retry capture via a `WebClient` `ExchangeFilterFunction` (DESIGN §8),
      so retry cost is broken out even when a client retries internally
- [ ] Budget `degrade` at the starter seam via an optional per-request model override (DESIGN §7)
- [ ] Later — multi-instance budget store (Redis), gateway/proxy mode

## License

MIT — see [LICENSE](LICENSE).
