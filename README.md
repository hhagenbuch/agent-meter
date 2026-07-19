# agent-meter

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
  unknown models are recorded as *unknown*, never `0.00`; retries count, because
  you pay for them.
- **Enforces budgets by degrading before denying.** Declarative budgets per
  feature / session / global: `warn` → `degrade` to a cheaper model (span tagged
  `agent.budget_degraded=true`) → `block` with a message a 2am on-call can act on.

## Modules

| Module | Contents | Deps |
|--------|----------|------|
| `meter-core` | cost engine, versioned price table, budget policy — **pure** | Jackson, OTel API |
| `meter-spring` | auto-config, `LlmClient` metering + budget decorators, `feature` propagation | Spring Boot, OTel SDK |
| `demo/` | `docker-compose` OTel Collector → Prometheus + Tempo → Grafana, shipped dashboard, load script | — |

Built to instrument
[spring-ai-agent-starter](https://github.com/hhagenbuch/spring-ai-agent-starter);
budget enforcement rides the same `LlmClient` seam.

## Boundary

- **[agent-blackbox](https://github.com/hhagenbuch/agent-blackbox)** answers *what
  happened* (the flight recorder); **agent-meter** answers *what did it cost*. They
  share the starter's `LlmClient` decorator seam and the OTel span; they do not
  overlap in responsibility.
- **vs. vendor observability suites** — agent-meter emits standard OTel semconv, so
  the data outlives any single vendor decision. Portability is the differentiator.

## Roadmap

- [ ] Phase 0 — design doc ([`docs/DESIGN.md`](docs/DESIGN.md))
- [ ] Phase 1 — `meter-core`: price table + cost engine + budget policy (pure)
- [ ] Phase 2 — `meter-spring`: OTel spans/metrics per the attribution table
- [ ] Phase 3 — budget enforcement: warn / degrade / block decorators
- [ ] Phase 4 — demo stack (Grafana dashboard, load script), README hero screenshot
- [ ] Later — multi-instance budget store (Redis), gateway/proxy mode

## License

MIT — see [LICENSE](LICENSE).
