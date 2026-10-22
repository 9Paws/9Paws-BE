#!/bin/bash
set -euo pipefail

if [ -z "${1:-}" ] || [ -z "${2:-}" ]; then
    echo "Usage: $0 <domain-name> <email-address>"
    exit 1
fi

DOMAIN="$1"
EMAIL="$2"
GITOPS_DIR="/opt/ninepaws/9Paws-Gitops"

echo "=== Initializing SSL for $DOMAIN ==="
# 1. Substitute DOMAIN_NAME in app.conf
sed -i "s|\${DOMAIN_NAME}|$DOMAIN|g" "$GITOPS_DIR/nginx/conf.d/app.conf"

# 2. Stop any container using port 80 temporarily
cd "$GITOPS_DIR"
docker compose -f docker-compose-prod.yml stop nginx 2>/dev/null || true

# 3. Request genuine certificate via standalone Certbot container
echo "Requesting genuine SSL certificate from Let's Encrypt via standalone Certbot..."
docker run --rm -p 80:80 \
    -v 9paws-gitops_certbot_conf:/etc/letsencrypt \
    -v 9paws-gitops_certbot_www:/var/www/certbot \
    certbot/certbot:v2.11.0 certonly --standalone \
    --email "$EMAIL" --agree-tos --no-eff-email \
    -d "$DOMAIN" --non-interactive --force-renewal

echo "=== SSL Initialization Successful for $DOMAIN ==="
