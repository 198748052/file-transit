#!/bin/sh
set -e

# A host bind mount (./data:/app/data) is usually created as root. Fix ownership
# once, then run the app as the unprivileged "node" user.
DATA_DIR="$(dirname "${DB_PATH:-/app/data/file-transit.db}")"

if [ "$(id -u)" = "0" ]; then
  mkdir -p "$DATA_DIR"
  chown -R node:node "$DATA_DIR"
  exec su-exec node "$@"
fi

exec "$@"
