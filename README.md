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
| `meter-spring` | auto-config, `LlmClient` metering + budget decorators, `feature` propagation | Spring Boot, OTel SDK |
| `demo/` | `docker-compose` OTel Collector → Prometheus + Tempo → Grafana, shipped dashboard, load script | — |

## Integration status (read this)

`meter-spring` instruments a **provider-agnostic `LlmClient` seam it defines** —
`LlmResponse call(LlmRequest)`, where the response carries the token usage and the
per-attempt breakdown. You wrap your client with
`instrumentation.instrument(yourClient)` and get metering + budget enforcement.

What that means honestly:

- **There is no drop-in `spring-ai-agent-starter` adapter yet.** agent-meter does not
  depend on the starter; you supply an implementation of its seam. A `meter-starter`
  adapter module (with `BeanPostProcessor` auto-wiring) is the roadmap item that makes
  "add the dependency, get cost telemetry" literally true — and it is now **unblocked**:
  the starter [exposes token usage](https://github.com/hhagenbuch/spring-ai-agent-starter/pull/6)
  on its responses as of that change (it was previously parsed and discarded, so there
  was nothing to meter).
- **Retry cost is aggregate unless your adapter reports per-attempt data.** The seam
  accepts a list of `Attempt`s and sums their cost, so if your client surfaces each HTTP
  attempt (the starter's retries happen *inside* its `AnthropicClient`), you get
  per-attempt spans and true retry cost. If it only returns a final response, you get one
  attempt's worth — correct, but not the retry-storm breakdown. Capturing attempts from a
  client that retries internally needs a `WebClient` `ExchangeFilterFunction` at the HTTP
  layer (roadmap, DESIGN §8).

## Demo

A one-command observability stack in [`demo/`](demo/): OTLP → Collector → Prometheus +
Tempo → Grafana, with a **shipped dashboard** (cost by feature, tokens by model, budget
burn-down, exemplar → trace).

> **The demo is synthetic.** The `demo-app` drives agent-meter with a **fake provider**
> (`FakeProvider` — plausible token counts, no real LLM). The *meter runs for real* — real
> cost engine, real spans/metrics, real budget enforcement — but the token numbers are
> generated, not from a live model. It exercises the library end-to-end; it is not a real
> agent under load. (Wiring a real agent in is the `meter-starter` roadmap item above.)

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
- [ ] `meter-starter` — a drop-in adapter for
      [spring-ai-agent-starter](https://github.com/hhagenbuch/spring-ai-agent-starter)
      (`BeanPostProcessor` wiring), now unblocked by the starter exposing usage
- [ ] Per-attempt retry capture via a `WebClient` `ExchangeFilterFunction` (DESIGN §8),
      so retry cost is broken out even when a client retries internally
- [ ] Wire the real (adapter-instrumented) agent into the demo, replacing the synthetic provider
- [ ] Later — multi-instance budget store (Redis), gateway/proxy mode

## License

MIT — see [LICENSE](LICENSE).
