# Demo stack

`docker compose up` gives you the observability backends (Collector → Prometheus + Tempo
→ Grafana). Two ways to feed them — a **real agent** or a **synthetic** generator:

```
[ real: demo-agent = starter + meter-starter ]  ──OTLP──┐
[ synthetic: demo-app = fake provider        ]  ──OTLP──┤
                                                        ▼
                        otel-collector ──> Prometheus + Tempo ──> Grafana
```

## Mode A — real instrumented agent (needs a key)

The `demo-agent` module is the actual `spring-ai-agent-starter`, instrumented purely by
adding the `meter-starter` dependency. With a key, the dashboard shows **real** token/cost
data.

```bash
export ANTHROPIC_API_KEY=sk-ant-...
cd demo
docker compose --profile real up -d --build     # backends + the real agent (:8086)
./load-agent.sh                                  # drives POST /api/chat with X-Feature/X-Session-Id
open http://localhost:3000
```

## Mode B — synthetic (no key)

The `demo-app` drives agent-meter with a **fake provider** — the meter runs for real, but
the token numbers are generated. Good for seeing the dashboard without a key or a bill.

```bash
cd demo
docker compose up -d                         # backends only
mvn -q -pl demo-app spring-boot:run          # from the repo root, in another shell (:8085)
./load.sh                                     # ~2 min of synthetic traffic
open http://localhost:3000
```

The dashboard shows **cost by feature**, **tokens by model**, **budget enforcements**
(warn/degrade/block — support-chat trips its small budget under load), turn-duration
p95, and unknown-model calls. Metric **exemplars** link a cost spike straight to the
Tempo trace that caused it (click a point → *Query with Tempo*).

## Note on metric names

Metric names follow the OTel → Prometheus normalization the collector applies (dots →
underscores, counters get `_total`, and some builds append unit suffixes). If a panel is
empty, open Grafana's metric browser (or `http://localhost:9090` → the metric explorer)
and confirm the exact name — e.g. `agent_cost_usd_total`, `agent_tokens_total`,
`agent_budget_enforced_total` — and adjust the panel query. The `demo-app`'s telemetry is
real agent-meter output, so whatever names your collector emits are what the panels need.

## The hero screenshot

The README's dashboard screenshot is a **manual capture** — run the steps above and grab
`http://localhost:3000`. It isn't checked in (binary asset); this file is the recipe.
