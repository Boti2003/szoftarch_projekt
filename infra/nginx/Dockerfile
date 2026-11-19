# ---- Frontend build stage ----
FROM node:24 AS frontend-build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- Nginx stage ----
FROM nginx:1.27
RUN apt-get update && apt-get install -y --no-install-recommends openssl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=frontend-build /app/dist /usr/share/nginx/html
COPY infra/nginx/nginx.conf /etc/nginx/conf.d/default.conf
COPY infra/nginx/entrypoint.sh /docker-entrypoint.d/99-gen-cert.sh
RUN chmod +x /docker-entrypoint.d/99-gen-cert.sh

EXPOSE 80 443
