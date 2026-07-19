#!/usr/bin/env bash
# Drive the demo-app through a mix of features and models so the dashboard comes
# alive. support-chat is weighted with the priciest model so its small budget trips
# — watch it degrade (and eventually block) on the dashboard.
set -euo pipefail

APP="${APP:-http://localhost:8085}"
DURATION="${DURATION:-120}"
end=$(( $(date +%s) + DURATION ))

features=(support-chat billing search onboarding)
models=(claude-opus-4-8 claude-sonnet-5 claude-haiku-4-5)

echo "driving $APP for ${DURATION}s (Ctrl-C to stop)..."
while [ "$(date +%s)" -lt "$end" ]; do
  # burn the support-chat budget with the expensive model
  curl -s -o /dev/null -X POST "$APP/simulate?feature=support-chat&model=claude-opus-4-8"
  # plus a random mix across the other features/models
  f=${features[$((RANDOM % ${#features[@]}))]}
  m=${models[$((RANDOM % ${#models[@]}))]}
  curl -s -o /dev/null -X POST "$APP/simulate?feature=$f&model=$m"
  sleep 0.3
done
echo "done — open http://localhost:3000"
