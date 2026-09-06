#!/usr/bin/env bash
# Push to GitHub using a local (gitignored) token file.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOKEN_FILE="${STUDY_SPARK_GH_TOKEN_FILE:-$ROOT/.secrets/github_token}"
REMOTE_URL="${STUDY_SPARK_GIT_REMOTE:-https://github.com/dicko2563repos/study-spark.git}"

if [[ ! -f "$TOKEN_FILE" ]]; then
  echo "Missing token file: $TOKEN_FILE"
  echo "Create a classic PAT with 'repo' scope:"
  echo "  mkdir -p .secrets && chmod 700 .secrets"
  echo "  printf '%s\\n' 'YOUR_PAT' > .secrets/github_token && chmod 600 .secrets/github_token"
  exit 1
fi

TOKEN="$(tr -d '[:space:]' < "$TOKEN_FILE")"
# Strip credentials if already present; inject oauth2 token
HOST_PATH="${REMOTE_URL#https://}"
HOST_PATH="${HOST_PATH#http://}"
AUTH_URL="https://oauth2:${TOKEN}@${HOST_PATH}"

cd "$ROOT"
BRANCH="${1:-main}"
shift || true
git push "$AUTH_URL" "$BRANCH" "$@"
