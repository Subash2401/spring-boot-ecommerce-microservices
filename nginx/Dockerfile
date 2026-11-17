# Production web server: serves the frontend and forwards the API to the gateway, on one
# origin. Built from the repository root (compose.prod.yaml: context ".", this file);
# the root .dockerignore sends only frontend/ and nginx/ to the build.
FROM nginx:1.30-alpine

# ---- The frontend, switched to "same origin" ---------------------------------------------
# In development the pages call http://localhost:8080 (the gateway). Here nginx serves pages
# and API on the same address, so API_BASE becomes "" and the CSP allows only 'self'.
# The last line stops the build if any localhost:8080 is left (for example a renamed line).
COPY frontend/ /usr/share/nginx/html/
RUN sed -i "s#export const API_BASE = 'http://localhost:8080';#export const API_BASE = '';#" \
        /usr/share/nginx/html/js/config.js \
 && sed -i "s#connect-src http://localhost:8080#connect-src 'self'#" /usr/share/nginx/html/*.html \
 && ! grep -rn "localhost:8080" /usr/share/nginx/html

# ---- nginx configuration -----------------------------------------------------------------
COPY nginx/snippets/ /etc/nginx/snippets/
COPY nginx/http.conf.template nginx/https.conf.template /etc/nginx/
COPY nginx/15-choose-config.sh /docker-entrypoint.d/15-choose-config.sh
RUN chmod +x /docker-entrypoint.d/15-choose-config.sh \
 && mkdir -p /etc/nginx/templates /var/www/certbot
