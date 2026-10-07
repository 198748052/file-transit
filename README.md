# 文件中转站 · file-transit

一个**个人使用**的文件中转站：登录后台上传文件、管理文件、生成分享链接，文件本体存储在 **Cloudflare R2**。

服务器只负责「认证 + 元数据 + 签发预签名 URL」，**文件字节流全程浏览器直连 R2**，不占用你的服务器带宽；配合 R2 的**零出口流量费**与免费额度，成本极低。

## 功能

- 🔐 **单管理员账号登录**（JWT），防止他人盗刷流量
- ⬆️ **大文件分片直传 R2 / 断点续传**：后端编排 `CreateMultipartUpload`，浏览器逐片 PUT，中断后可从 R2 已传分片继续
- 🔗 **分享下载链接**：短码链接 `/s/{code}`，访问时签发**短时效**预签名地址，防盗链
- 🔑 **下载提取码保护**：链接可选设置提取码
- ⏳ **有效期 / 自动过期删除**：按天设置保留期，cron 定时从 R2 删除
- 📋 文件管理面板：列表、复制链接、改名、改有效期/提取码、删除、占用统计
- 🔄 **远程一键更新**：管理员面板对比 GitHub 最新提交，确认后自动 `git reset` → 构建 → `pm2 restart`，并可后台定时检查新版本

## 技术栈

| 层 | 选型 |
|----|------|
| 后端 | Node.js 22+ / TypeScript / Express |
| 存储 | Cloudflare R2（S3 兼容，`@aws-sdk/client-s3`）|
| 元数据 | `node:sqlite`（Node 内置 SQLite，零原生依赖）|
| 鉴权 | `jsonwebtoken` + `bcryptjs` |
| 校验 | `zod` ｜ 定时任务 `node-cron` |
| 前端 | Vue 3 + Vite + TypeScript + vue-router |

## 目录结构

```
file-transit/
├─ src/                 # 后端
│  ├─ index.ts app.ts config.ts auth.ts db.ts
│  ├─ lib/ (r2.ts http.ts codes.ts)
│  ├─ routes/ (auth.ts files.ts share.ts)
│  └─ jobs/expiry.ts
├─ web/                 # 前端（Vue 3 + Vite）
│  └─ src/ (views/ components/ lib/upload.ts api.ts auth.ts router.ts)
├─ scripts/set-r2-cors.mjs   # 一键配置 R2 桶 CORS
└─ .env.example
```

## 环境要求

- Node.js **≥ 22.5**（使用内置 `node:sqlite`）
- 一个 Cloudflare 账号 + R2 桶

## 一、准备 Cloudflare R2

1. Cloudflare Dashboard → **R2 Object Storage** → 创建桶（如 `file-transit`），开启免费额度即可。
2. **Manage R2 API Tokens** → 创建 Token：
   - 权限选 **Object Read & Write**，作用范围限定到该桶；
   - 记下 `Access Key ID`、`Secret Access Key`，以及账号 ID（R2 页面可见）。

## 二、安装与配置

```bash
# 后端依赖
npm install
# 前端依赖
cd web && npm install && cd ..

# 生成配置文件
cp .env.example .env
```

编辑 `.env`，**至少填写**：

| 变量 | 说明 |
|------|------|
| `JWT_SECRET` | 长随机字符串，务必修改 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 管理员账号密码（密码启动时会被 bcrypt 加密；也可改用 `ADMIN_PASSWORD_HASH`）|
| `R2_ACCOUNT_ID` / `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` / `R2_BUCKET` | 上面的 R2 凭据 |
| `APP_BASE_URL` | 你的对外访问地址，用于拼接分享链接（如 `https://files.example.com`）|

## 三、图形化配置 R2 凭据（推荐，免重启）

登录面板后进入顶栏 **设置 → 存储设置**，填写 R2 Account ID / Access Key ID / Secret / 桶名称：

- **保存**：凭据写入本地 SQLite 的 `settings` 表，**优先级高于 `.env`**，即时生效无需重启；Secret 保存后不回显。
- **测试连接**：先保存，再对该桶发起 `HeadBucket`，快速验证凭据与桶是否可用。
- **应用 CORS**：先保存，再自动给桶配置跨域规则（允许当前站点 + `localhost` 直传，暴露 `ETag`）。

> 也可以只用 `.env`（下面的脚本方式），二选一即可。数据库中的配置会覆盖环境变量；想改回用 `.env`，清空对应设置即可。

命令行方式（等价，无需 UI 时）：

```bash
npm run r2:cors
```

## 四、本地开发

开两个终端：

```bash
# 终端 1：后端（默认 8642）
npm run dev

# 终端 2：前端（5173，/api 自动代理到 8642）
cd web && npm run dev
```

访问 http://localhost:5173 。

## 五、生产构建与运行

```bash
# 1) 构建前端 → web/dist
cd web && npm run build && cd ..

# 2) 编译后端 → dist/
npm run build

# 3) 启动（后端会自动托管 web/dist 作为单页应用）
NODE_ENV=production npm start
```

后端在 `web/dist` 存在时会托管前端静态资源，并对非 `/api` 路径回退到 `index.html`，因此**单进程即可同时提供 API 与网站**。

## 六、部署到你的服务器

1. 上传代码、`npm install`（后端）+ 构建前端与后端（见上）。
2. 用 **HTTPS 反向代理**（Nginx/Caddy）指向 `127.0.0.1:8642`，并设置正确的 `APP_BASE_URL`。
3. 建议用 **systemd** 常驻，例：

```ini
[Service]
WorkingDirectory=/opt/file-transit
ExecStart=/usr/bin/node dist/index.js
EnvironmentFile=/opt/file-transit/.env
Restart=always
```

> 反代只需转发正常 HTTP 请求即可；**文件上传/下载走浏览器 ↔ R2，不经过 Nginx**，服务器压力很小。

## 七、宝塔面板（Baota / aaPanel）部署

1. **装环境**：软件商店 → 安装 **Nginx** 与 **PM2 管理器**；在 PM2 管理器里安装 **Node.js 22+**（宝塔「Node 项目」就是基于 PM2 托管）。
2. **取代码**：`git clone` 本仓库到服务器（如 `/www/wwwroot/file-transit`），或上传 zip 后解压。
3. **装依赖并构建**（进入项目目录的终端）：
   ```bash
   npm install
   cd web && npm install && npm run build && cd ..   # 构建前端 → web/dist
   npm run build                                      # 编译后端 → dist/
   ```
4. **配置 `.env`**：复制 `.env.example` 为 `.env`，填 `JWT_SECRET`、`ADMIN_USERNAME/ADMIN_PASSWORD`、`APP_BASE_URL=https://你的域名`。R2 可先留空，登录后在「设置」页填即可。
5. **新建 Node 项目**：网站 → Node项目 → 添加，项目目录 `/www/wwwroot/file-transit`、启动文件 `dist/index.js`（或 `npm start`）、端口 `8642`、选择已装的 Node 22。PM2 会常驻并随系统重启。
6. **域名 + HTTPS**：给该站点绑定域名，「反向代理」到 `127.0.0.1:8642`（若 Node 项目已内置反代则跳过）；申请 Let's Encrypt 证书并开启强制 HTTPS。
7. **放行端口**：安全 → 防火墙开放 `80`、`443`。
8. **首次配置**：浏览器打开域名 → 登录 → **设置** 填 R2 凭据 → 点 **测试连接**、**应用 CORS** → 回 **面板** 上传测试。

> 数据文件（SQLite）在 `data/` 目录，随项目备份即可；`.env` 与 `settings` 表含密钥，注意权限、切勿公开。上传/下载不经过 Nginx，无需调整 `client_max_body_size`。

## 八、远程自动更新

登录管理员面板 → **设置 → 软件更新**，即可对比 GitHub 上的最新提交并一键更新。

工作方式（全部在**管理员鉴权**下执行，命令为固定序列、无用户输入拼接）：

1. `git fetch origin` 比较本地 `HEAD` 与 `origin/<UPDATE_BRANCH>`，显示落后多少提交；
2. 点「立即更新」后，后台以**独立进程**依次执行：`git reset --hard origin/<branch>` → `npm install` → 前端/后端构建 → `pm2 restart <UPDATE_PM2_NAME>`，站点会短暂重启；
3. 面板每 2 秒轮询日志，实时更新进度与结果。

前提与注意：

- **必须用 `git clone` 部署**（zip 部署无 git 仓库，该功能会提示不可用）。
- `.env`、`data/`（含 SQLite 数据库）都被 gitignore，`reset --hard` **不会**动它们，凭据和数据安全保留。
- 为防误删，更新前会检查工作区是否有**未提交的跟踪文件改动**；若有则拒绝执行（`working_tree_dirty`），请先在服务器处理。
- `UPDATE_PM2_NAME` 要与 PM2 里的进程名一致（宝塔 Node 项目名即进程名，默认 `file-transit`）。
- 后台 `UPDATE_CHECK_CRON`（默认每 6 小时）只做**只读**检查，发现新版本时在面板顶部提示，**不会自动应用**，更新始终需你手动确认。
- 相关环境变量见 `.env.example`：`UPDATE_ENABLED`、`UPDATE_BRANCH`、`UPDATE_PM2_NAME`、`UPDATE_REPO_DIR`、`UPDATE_CHECK_CRON`。设 `UPDATE_ENABLED=false` 可彻底关闭。

## 安全与成本提示

- `PRESIGNED_GET_TTL_SECONDS`（默认 300s）保持较短，分享地址取到后需尽快下载，避免被长期盗链。
- 管理接口全部要求 JWT；`/api/share/*` 为公开，靠有效期 + 提取码约束。
- R2 免费额度：10GB 存储、Class A/B 操作各 100万/1000万，**出口流量免费**。
- 未完成/过期的上传由内置 cron 自动清理（默认每小时）。
- 远程更新会执行 `git reset --hard` 并重启进程，仅在管理员鉴权下触发；工作区有未提交改动时自动拒绝，避免丢失。

## API 概览

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/api/auth/login` | 否 | 登录，返回 JWT |
| GET | `/api/auth/me` | 是 | 当前用户 |
| POST | `/api/files/init` | 是 | 初始化上传（单片/多片，返回预签名 URL）|
| GET | `/api/files/:id/parts` | 是 | 查询已传分片（断点续传）|
| POST | `/api/files/:id/complete` | 是 | 完成上传 |
| GET | `/api/files` | 是 | 文件列表 |
| PATCH | `/api/files/:id` | 是 | 改名 / 有效期 / 提取码 |
| DELETE | `/api/files/:id` | 是 | 删除（同时删 R2 对象）|
| GET | `/api/share/:code` | 否 | 分享页元信息 |
| POST | `/api/share/:code/download` | 否 | 校验提取码并签发下载地址 |
| GET | `/api/settings/r2` | 是 | 读取当前 R2 配置（不含 secret）|
| PUT | `/api/settings/r2` | 是 | 保存 R2 凭据（DB 覆盖 .env，即时生效）|
| POST | `/api/settings/r2/test` | 是 | HeadBucket 连通测试 |
| POST | `/api/settings/r2/cors` | 是 | 给桶应用 CORS 规则 |
| GET | `/api/update/status` | 是 | 版本/更新状态（本地快照 + 缓存）|
| POST | `/api/update/check` | 是 | 只读 `git fetch` 比较远端 |
| POST | `/api/update/run` | 是 | 触发更新（reset + 构建 + pm2 重启）|
| GET | `/api/update/log` | 是 | 更新日志尾部与运行状态 |
