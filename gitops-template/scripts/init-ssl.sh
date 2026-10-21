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

# 2. Generate dummy certificates so Nginx can start
DUMMY_DIR="/var/lib/docker/volumes/9paws-gitops_certbot_conf/_data/live/$DOMAIN"
mkdir -p "$DUMMY_DIR"
if [ ! -f "$DUMMY_DIR/fullchain.pem" ]; then
    openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
        -keyout "$DUMMY_DIR/privkey.pem" \
        -out "$DUMMY_DIR/fullchain.pem" \
        -subj "/CN=localhost"
fi

# 3. Start Nginx
cd "$GITOPS_DIR"
docker compose --env-file /opt/ninepaws/.env -f docker-compose-prod.yml up -d nginx

# 4. Request genuine certificate via Certbot
docker compose --env-file /opt/ninepaws/.env -f docker-compose-prod.yml run --rm certbot certonly \
    --webroot --webroot-path=/var/www/certbot \
    --email "$EMAIL" --agree-tos --no-eff-email \
    -d "$DOMAIN" --force-renewal

# 5. Reload Nginx with genuine certificate
docker compose --env-file /opt/ninepaws/.env -f docker-compose-prod.yml exec nginx nginx -s reload
echo "=== SSL Initialization Successful for $DOMAIN ==="
