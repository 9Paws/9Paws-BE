#!/bin/bash
set -euo pipefail

REPO_DIR="/opt/ninepaws/9Paws-Gitops"
ENV_FILE="/opt/ninepaws/.env"

if [ ! -d "$REPO_DIR" ]; then
    echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] Error: $REPO_DIR does not exist."
    exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
    echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] Error: Environment file $ENV_FILE does not exist. Please create it from .env.example."
    exit 1
fi

cd "$REPO_DIR"

git fetch origin main --quiet
LOCAL_HASH=$(git rev-parse HEAD)
REMOTE_HASH=$(git rev-parse origin/main)

if [ "$LOCAL_HASH" != "$REMOTE_HASH" ]; then
    echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] Drift detected: updating from $LOCAL_HASH to $REMOTE_HASH"
    git pull origin main --quiet
    if grep -q '\${DOMAIN_NAME}' "$REPO_DIR/nginx/conf.d/app.conf"; then
        echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] Warning: app.conf contains uninitialized \${DOMAIN_NAME}. Please run scripts/init-ssl.sh first."
    fi
    docker compose --env-file "$ENV_FILE" -f docker-compose-prod.yml up -d --remove-orphans
    docker image prune -f --filter "until=24h"
    echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] Reconciliation completed successfully."
fi
