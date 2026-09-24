# syntax=docker/dockerfile:1.7
# ---------- build ----------
FROM node:24.19.0-alpine3.24 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npx ng build

# ---------- runtime: nginx sin privilegios (puerto 8080) ----------
FROM nginxinc/nginx-unprivileged:1.30.1-alpine3.23
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1:8080/healthz > /dev/null || exit 1
