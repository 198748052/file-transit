#!/usr/bin/env bash
# file-transit 一键部署（宝塔 / Ubuntu / Debian，root 运行）
# 用法:  bash <(curl -fsSL https://raw.githubusercontent.com/198748052/file-transit/main/deploy.sh)
# 可选:  FT_DIR=/www/wwwroot/file-transit FT_PORT=8642 bash deploy.sh
# 说明:  分享链接会自动使用访问站点的域名，无需在此配置域名。
set -euo pipefail

REPO="https://github.com/198748052/file-transit.git"
BRANCH="main"
DIR="${FT_DIR:-/www/wwwroot/file-transit}"
PORT="${FT_PORT:-8642}"
PM2_NAME="file-transit"

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m!! %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" = 0 ] || die "请用 root 运行（宝塔里直接用 root 终端）"
command -v git >/dev/null 2>&1 || die "服务器缺少 git，请先在宝塔软件商店安装"
command -v node >/dev/null 2>&1 || die "服务器缺少 node：宝塔 → 软件商店 → PM2管理器 → 设置 → Node版本管理 → 安装 22.x"
command -v pm2 >/dev/null 2>&1 || die "服务器缺少 pm2：宝塔 → 软件商店 → 安装 PM2管理器"
NODE_MAJOR="$(node -v | sed 's/^v//; s/\..*//')"
[ "$NODE_MAJOR" -ge 22 ] 2>/dev/null || die "需要 Node.js 22 及以上（当前 $(node -v)）。本程序用到 Node 内置的 node:sqlite"

# ── 代码就位 ────────────────────────────────────────────────
if [ -d "$DIR/.git" ]; then
  cd "$DIR"
  log "已存在代码库，拉取最新代码"
  git fetch origin "$BRANCH"
  git reset --hard "origin/$BRANCH"
else
  [ -e "$DIR" ] && { mv "$DIR" "${DIR}.bak.$(date +%F-%H%M)"; log "原目录已改名备份，数据不会丢"; }
  log "克隆代码到 $DIR"
  mkdir -p "$(dirname "$DIR")"
  git clone --branch "$BRANCH" "$REPO" "$DIR"
  cd "$DIR"
  BACKUP="$(ls -d "${DIR}.bak."* 2>/dev/null | tail -1 || true)"
  if [ -n "$BACKUP" ]; then
    [ -f "$BACKUP/.env" ] && { cp "$BACKUP/.env" .env; log "沿用备份里的 .env"; }
    [ -d "$BACKUP/data" ] && { mkdir -p data && cp -a "$BACKUP/data/." data/ && log "沿用备份里的 data/ 数据库"; }
  fi
fi
cd "$DIR"
mkdir -p data

# ── 依赖与构建 ──────────────────────────────────────────────
log "设置 npm 下载源"
npm config set registry https://registry.npmjs.org
log "安装后端依赖（首次约 1-3 分钟）"
npm install --no-audit --no-fund
log "安装并构建前端"
(cd web && npm install --no-audit --no-fund && npm run build)
log "编译后端"
npm run build

[ -f dist/index.js ] || die "构建失败：dist/index.js 不存在"
[ -f web/dist/index.html ] || die "构建失败：web/dist/index.html 不存在"

# ── 配置文件 .env ───────────────────────────────────────────
log "写入 .env"
[ -f .env ] || cp .env.example .env

if ! grep -q '^JWT_SECRET=[0-9a-f]\{32,\}$' .env; then
  SECRET="$(node -e 'console.log(require("crypto").randomBytes(32).toString("hex"))')"
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$SECRET|" .env
fi

if ! grep -qE '^ADMIN_PASSWORD=[^#]' .env || grep -q '^ADMIN_PASSWORD=change-me' .env; then
  ADMIN_PW="$(node -e 'console.log(require("crypto").randomBytes(9).toString("base64url"))')"
  sed -i "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=$ADMIN_PW|" .env
  printf '\n\033[1;33m登录密码已自动生成：%s\033[0m\n' "$ADMIN_PW"
  printf '（写在 %s/.env 里，忘了就 cat .env | grep ADMIN 查看）\n' "$DIR"
fi

set_kv() { grep -q "^$1=" .env && sed -i "s|^$1=.*|$1=$2|" .env || printf '%s=%s\n' "$1" "$2" >>.env; }
set_kv NODE_ENV production
set_kv HOST 127.0.0.1
set_kv PORT "$PORT"
set_kv UPDATE_REPO_DIR "$DIR"
set_kv UPDATE_PM2_NAME "$PM2_NAME"
chmod 600 .env

# ── 用 PM2 常驻运行 ─────────────────────────────────────────
# 先停掉本脚本自己上一次启动的进程，保证重复部署时不会误判为自己的进程占用了端口
pm2 delete "$PM2_NAME" >/dev/null 2>&1 || true

log "检查端口 $PORT 是否已被别的进程占用"
if command -v ss >/dev/null 2>&1 && ss -lntp 2>/dev/null | grep -q ":$PORT[[:space:]]"; then
  ss -lntp | grep ":$PORT[[:space:]]" || true
  pm2 list || true
  die "端口 $PORT 已被上面这个进程占用。若它是宝塔「Node 项目」里建的旧站点，请先在面板里删掉那个 Node 项目（或改 FT_PORT 换个端口）再重跑脚本，否则会出现两个进程抢同一个端口"
fi

log "启动进程"
pm2 start dist/index.js --name "$PM2_NAME" --cwd "$DIR" --update-env
pm2 save >/dev/null 2>&1 || true

sleep 2
CODE="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/api/auth/me" || echo 000)"
if [ "$CODE" = "401" ]; then
  log "后端已在 127.0.0.1:$PORT 正常运行（未登录返回 401，符合预期）"
else
  pm2 logs "$PM2_NAME" --lines 30 --nostream || true
  die "后端启动异常（HTTP $CODE）。上面是最近日志，把它发给我"
fi

cat <<EOF

──────────────────────── 脚本已完成 ────────────────────────
剩下三件事在宝塔面板里点（脚本不碰你的 Nginx 配置，避免搞坏其它站点）：

1. 网站 → 添加站点 → 域名填你的域名，PHP 版本选「纯静态」
2. 该站点 → 反向代理 → 添加反向代理
     目标 URL：http://127.0.0.1:$PORT
     发送域名：\$host
3. 该站点 → SSL → Let's Encrypt → 选域名 → 申请 → 打开「强制 HTTPS」
     （前提：域名的 A 记录已指向本机 IP，否则申请会失败）

然后浏览器打开你的域名 → 登录 → 设置 → 存储设置
填入 Cloudflare R2 的 Account ID / Access Key / Secret / 桶名
→ 点「测试连接」→ 点「应用 CORS」→ 回到面板上传一个文件试试。

分享链接会自动使用你访问站点的域名，无需额外配置。

以后更新代码：在面板「设置 → 软件更新」点一下，或者重跑本脚本同一条命令。
宝塔防火墙只需放行 80、443，不要为 $PORT 开放端口。
EOF
