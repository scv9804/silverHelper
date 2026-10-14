#!/bin/sh
# Certbot deploy hook: run only after a successful renewal/issuance.
set -eu
cd "$(dirname "$0")"
docker compose -f docker-compose.prod.yml -f docker-compose.https.yml exec -T web nginx -t
docker compose -f docker-compose.prod.yml -f docker-compose.https.yml exec -T web nginx -s reload
