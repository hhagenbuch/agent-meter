# Demo stack

`docker compose up` gives you the observability backends; the `demo-app` runs on the
host, exports OTLP to the collector, and the load script drives it. Live dashboard in
about a minute.

```
demo-app ──OTLP──> otel-collector ──> Prometheus (metrics)  ┐
                                  └──> Tempo      (traces)   ├──> Grafana
                                                             ┘
```

## Run it

```bash
cd demo
docker compose up -d                         # collector + prometheus + tempo + grafana

# in another shell, from the repo root:
mvn -q -pl demo-app spring-boot:run          # exports OTLP to localhost:4317, serves :8085

# back in demo/:
./load.sh                                    # ~2 min of mixed traffic

open http://localhost:3000                   # anonymous admin; "agent-meter" dashboard
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
