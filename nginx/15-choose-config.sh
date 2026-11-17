#!/bin/sh
# Runs when the nginx container starts, before nginx itself (the official image runs every
# script in /docker-entrypoint.d/ in name order; "15-" runs before its own "20-envsubst",
# which turns /etc/nginx/templates/*.template into the real config with ${DOMAIN} filled in).
#
# HTTPS needs a Let's Encrypt certificate. Until it exists, serve plain HTTP, so that
# certbot can prove we own the domain (and the site already works). After certbot has run,
# "docker compose -f compose.prod.yaml restart nginx" switches to HTTPS. See docs/DEPLOY.md.
set -e

if [ -z "${DOMAIN}" ]; then
  echo "choose-config: DOMAIN is not set (add it to .env)" >&2
  exit 1
fi

mkdir -p /etc/nginx/templates
if [ -f "/etc/letsencrypt/live/${DOMAIN}/fullchain.pem" ]; then
  cp /etc/nginx/https.conf.template /etc/nginx/templates/default.conf.template
  echo "choose-config: certificate found for ${DOMAIN}, serving HTTPS"
else
  cp /etc/nginx/http.conf.template /etc/nginx/templates/default.conf.template
  echo "choose-config: no certificate for ${DOMAIN} yet, serving HTTP only"
fi
