#!/bin/bash
set -euo pipefail

REPO_DIR="/opt/ninepaws/9Paws-Gitops"
ENV_FILE="/opt/ninepaws/.env"

if [ ! -d "$REPO_DIR" ]; then
    echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] Error: $REPO_DIR does not exist."
    exit 1
fi

cd "$REPO_DIR"

git fetch origin main --quiet
LOCAL_HASH=$(git rev-parse HEAD)
REMOTE_HASH=$(git rev-parse origin/main)

if [ "$LOCAL_HASH" != "$REMOTE_HASH" ]; then
    echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] Drift detected: updating from $LOCAL_HASH to $REMOTE_HASH"
    git pull origin main --quiet
    docker compose --env-file "$ENV_FILE" -f docker-compose-prod.yml up -d --remove-orphans
    docker image prune -f
    echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] Reconciliation completed successfully."
fi
