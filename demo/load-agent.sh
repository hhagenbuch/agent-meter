#!/usr/bin/env bash
# Drive the REAL instrumented agent (demo-agent, :8086) with a mix of features/sessions.
# Needs ANTHROPIC_API_KEY set and the agent running (docker compose --profile real up,
# or `mvn -pl demo-agent -am spring-boot:run` on the host). Feature/session are attributed
# via the X-Feature / X-Session-Id headers the meter-starter WebFilter reads.
set -euo pipefail

APP="${APP:-http://localhost:8086}"
DURATION="${DURATION:-120}"
end=$(( $(date +%s) + DURATION ))
features=(support-chat billing search)

echo "driving the REAL agent at $APP for ${DURATION}s..."
while [ "$(date +%s)" -lt "$end" ]; do
  f=${features[$((RANDOM % ${#features[@]}))]}
  s="sess-$((RANDOM % 20))"
  curl -s -o /dev/null -X POST "$APP/api/chat" \
    -H 'content-type: application/json' -H "X-Feature: $f" -H "X-Session-Id: $s" \
    -d "{\"sessionId\":\"$s\",\"message\":\"In one sentence, what is $f?\"}"
  sleep 1
done
echo "done — open http://localhost:3000 for real cost/token data"
