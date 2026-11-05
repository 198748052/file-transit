# syntax=docker/dockerfile:1

# ─────────────── Stage 1: build the Vue frontend → web/dist ───────────────
FROM node:22-alpine AS web-build
WORKDIR /app/web
COPY web/package.json web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY web/ ./
RUN npm run build

# ─────────────── Stage 2: compile the TypeScript backend → dist ───────────
FROM node:22-alpine AS server-build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build

# ─────────────── Stage 3: production-only dependencies ────────────────────
FROM node:22-alpine AS prod-deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund && npm cache clean --force

# ─────────────── Stage 4: runtime image ───────────────────────────────────
FROM node:22-alpine AS runtime

# su-exec: drop root after fixing volume permissions; tzdata: honour $TZ for cron/logs
RUN apk add --no-cache su-exec tzdata

WORKDIR /app
ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=8642 \
    DB_PATH=/app/data/file-transit.db \
    DEPLOY_MODE=docker

COPY --from=prod-deps /app/node_modules ./node_modules
COPY --from=server-build /app/dist ./dist
COPY --from=web-build /app/web/dist ./web/dist
COPY package.json ./
COPY scripts/ ./scripts/
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh && mkdir -p /app/data && chown -R node:node /app/data

VOLUME ["/app/data"]
EXPOSE 8642

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/api/health" >/dev/null 2>&1 || exit 1

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["node", "dist/index.js"]
