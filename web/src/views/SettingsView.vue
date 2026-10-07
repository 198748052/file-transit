<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue';
import { api, ApiError, type R2Status, type UpdateStatus, type UpdateLog } from '../api';

const form = reactive({ accountId: '', accessKeyId: '', secretAccessKey: '', bucket: '', endpoint: '' });
const status = ref<R2Status | null>(null);
const loading = ref(true);
const saving = ref(false);
const testing = ref(false);
const corsing = ref(false);

const msg = reactive<{ type: '' | 'success' | 'error' | 'info'; text: string }>({ type: '', text: '' });
function flash(type: typeof msg.type, text: string) {
  msg.type = type;
  msg.text = text;
}

async function load() {
  loading.value = true;
  try {
    const s = await api.getR2();
    status.value = s;
    form.accountId = s.accountId;
    form.accessKeyId = s.accessKeyId;
    form.bucket = s.bucket;
    form.endpoint = s.storedIn === 'db' ? s.endpoint : '';
  } finally {
    loading.value = false;
  }
}

// ── 软件更新 ──────────────────────────────────────────────
const upd = ref<UpdateStatus | null>(null);
const updLoading = ref(false);
const checking = ref(false);
const applying = ref(false);
const upMsg = reactive<{ type: '' | 'success' | 'error' | 'info'; text: string }>({ type: '', text: '' });
const logText = ref('');
let logTimer: ReturnType<typeof setInterval> | null = null;

function upFlash(type: typeof upMsg.type, text: string) {
  upMsg.type = type;
  upMsg.text = text;
}

async function loadUpdate() {
  updLoading.value = true;
  try {
    upd.value = await api.updateStatus();
    if (upd.value.running) startLogPolling();
  } catch {
    /* update section stays hidden on error */
  } finally {
    updLoading.value = false;
  }
}

async function checkNow() {
  checking.value = true;
  upMsg.type = '';
  try {
    upd.value = await api.checkUpdate();
    if (upd.value.hasUpdate) upFlash('info', `发现新版本：落后 ${upd.value.behind} 个提交。可以点“立即更新”。`);
    else upFlash('success', '已是最新版本。');
  } catch (e) {
    upFlash('error', describeUpdate(e));
  } finally {
    checking.value = false;
  }
}

async function runNow() {
  if (!confirm('将拉取最新代码、重新构建并重启服务，期间站点会短暂不可用。确定继续？')) return;
  applying.value = true;
  upMsg.type = '';
  try {
    await api.runUpdate();
    upd.value = upd.value ? { ...upd.value, running: true } : upd.value;
    upFlash('info', '更新已开始，正在后台构建… 站点稍后会自动重启。');
    startLogPolling();
  } catch (e) {
    upFlash('error', describeUpdate(e));
  } finally {
    applying.value = false;
  }
}

function startLogPolling() {
  if (logTimer) return;
  logTimer = setInterval(async () => {
    try {
      const l: UpdateLog = await api.updateLog();
      logText.value = l.log;
      if (!l.running) {
        stopLogPolling();
        try {
          upd.value = await api.updateStatus();
        } catch {
          /* server may be mid-restart */
        }
        if (l.lastRun === 'ok') upFlash('success', '更新完成，服务已重启到新版本。');
        else if (l.lastRun === 'failed') upFlash('error', '更新失败，请查看下方日志。');
      }
    } catch {
      /* backend restarting — keep polling */
    }
  }, 2000);
}

function stopLogPolling() {
  if (logTimer) {
    clearInterval(logTimer);
    logTimer = null;
  }
}

function describeUpdate(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'update_disabled') return '远程更新已禁用（UPDATE_ENABLED=false）';
    if (e.code === 'update_running') return '已有更新任务在进行中，请稍候';
    if (e.code === 'working_tree_dirty') return '工作区有未提交的改动，请先在服务器上处理后再更新';
    if (e.code === 'not_git_repo') return '当前目录不是 git 仓库，无法远程更新';
    if (e.code.startsWith('update_fetch_failed')) return '拉取远端失败：' + e.code.replace('update_fetch_failed: ', '');
    return `操作失败：${e.code}`;
  }
  return '网络错误，请稍后重试';
}

function shortSha(sha: string): string {
  return sha ? sha.slice(0, 7) : '';
}
function fmtDate(sec: number): string {
  return sec ? new Date(sec * 1000).toLocaleString() : '';
}

onMounted(() => {
  load();
  loadUpdate();
});

onUnmounted(stopLogPolling);

function validate(): boolean {
  if (!form.accountId || !form.accessKeyId || !form.bucket) {
    flash('error', 'Account ID、Access Key ID、桶名称 为必填项');
    return false;
  }
  if (!status.value?.hasSecret && !form.secretAccessKey) {
    flash('error', '首次配置需要填写 Secret Access Key');
    return false;
  }
  return true;
}

async function save() {
  if (!validate()) return;
  saving.value = true;
  msg.type = '';
  try {
    status.value = await api.saveR2({
      accountId: form.accountId,
      accessKeyId: form.accessKeyId,
      bucket: form.bucket,
      endpoint: form.endpoint,
      secretAccessKey: form.secretAccessKey || undefined,
    });
    form.secretAccessKey = '';
    flash('success', '已保存，凭据即时生效（无需重启）。');
  } catch (e) {
    flash('error', describe(e));
  } finally {
    saving.value = false;
  }
}

async function test() {
  if (!validate()) return;
  testing.value = true;
  msg.type = '';
  try {
    status.value = await api.saveR2({
      accountId: form.accountId,
      accessKeyId: form.accessKeyId,
      bucket: form.bucket,
      endpoint: form.endpoint,
      secretAccessKey: form.secretAccessKey || undefined,
    });
    const r = await api.testR2();
    flash('success', `连接成功：桶「${r.bucket}」可访问。`);
    form.secretAccessKey = '';
  } catch (e) {
    flash('error', describe(e));
  } finally {
    testing.value = false;
  }
}

async function applyCors() {
  if (!validate()) return;
  corsing.value = true;
  msg.type = '';
  try {
    await api.saveR2({
      accountId: form.accountId,
      accessKeyId: form.accessKeyId,
      bucket: form.bucket,
      endpoint: form.endpoint,
      secretAccessKey: form.secretAccessKey || undefined,
    });
    const r = await api.applyR2Cors();
    flash('success', `CORS 已应用到当前源：\n${r.origins.join('  ')}`);
    form.secretAccessKey = '';
  } catch (e) {
    flash('error', describe(e));
  } finally {
    corsing.value = false;
  }
}

function describe(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'secret_required') return '首次配置需要填写 Secret Access Key';
    if (e.code.startsWith('r2_check_failed')) return e.code.replace('r2_check_failed: ', '测试失败：');
    if (e.code.startsWith('r2_cors_failed')) return e.code.replace('r2_cors_failed: ', 'CORS 失败：');
    return `请求失败：${e.code}`;
  }
  return '网络错误，请稍后重试';
}

const storedLabel = () => {
  const s = status.value?.storedIn;
  return s === 'db' ? '本地数据库' : s === 'env' ? '环境变量 .env' : '未配置';
};
</script>

<template>
  <div class="settings">
    <h1>存储设置</h1>
    <p class="muted" style="margin-bottom: 20px">在此配置 Cloudflare R2 凭据，保存后立即生效，无需重启服务或改 .env。</p>

    <div v-if="loading" class="card"><p class="muted">加载中…</p></div>

    <template v-else>
      <div class="card" style="margin-bottom: 18px">
        <div class="toolbar" style="margin-bottom: 0">
          <div>
            <span class="badge" :class="status?.configured ? 'ready' : 'expired'">
              {{ status?.configured ? '已配置' : '未配置' }}
            </span>
            <span class="muted" style="margin-left: 10px; font-size: 13px">来源：{{ storedLabel() }}</span>
          </div>
          <button class="btn btn-sm btn-ghost" @click="load">重新读取</button>
        </div>
      </div>

      <div v-if="msg.type" class="alert" :class="msg.type" style="white-space: pre-line">{{ msg.text }}</div>

      <div class="card">
        <p class="section-title">R2 凭据</p>
        <div class="field">
          <label>Account ID</label>
          <input v-model="form.accountId" type="text" placeholder="Cloudflare 账号 ID" />
        </div>
        <div class="row">
          <div class="field">
            <label>Access Key ID</label>
            <input v-model="form.accessKeyId" type="text" placeholder="R2 API Token 的 Access Key ID" />
          </div>
          <div class="field">
            <label>Secret Access Key</label>
            <input
              v-model="form.secretAccessKey"
              type="password"
              :placeholder="status?.hasSecret ? '已保存，留空保持不变' : '必填'"
            />
          </div>
        </div>
        <div class="row">
          <div class="field">
            <label>桶名称 (Bucket)</label>
            <input v-model="form.bucket" type="text" placeholder="例如 file-transit" />
          </div>
          <div class="field">
            <label>Endpoint（可选）</label>
            <input v-model="form.endpoint" type="text" placeholder="留空则由 Account ID 自动推导" />
          </div>
        </div>

        <div class="row" style="margin-top: 10px">
          <button class="btn btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
          <button class="btn" :disabled="testing" @click="test">{{ testing ? '测试中…' : '测试连接' }}</button>
          <button class="btn" :disabled="corsing" @click="applyCors">
            {{ corsing ? '应用中…' : '应用 CORS' }}
          </button>
        </div>
        <p class="hint">
          “测试连接”和“应用 CORS”会先保存再执行。首次上传前务必点一次“应用 CORS”，否则浏览器无法直传 R2。
        </p>
      </div>

      <div class="card" style="margin-top: 18px">
        <p class="section-title">说明</p>
        <ul class="muted" style="margin: 0; padding-left: 18px; line-height: 1.8; font-size: 14px">
          <li>凭据保存在本地 SQLite（<code>settings</code> 表），优先级高于 <code>.env</code>。</li>
          <li>Secret 一旦保存不会回显；如需更换，填入新值并保存即可。</li>
          <li>分享链接域名由 <code>.env</code> 的 <code>APP_BASE_URL</code> 决定，生产环境请设置。</li>
        </ul>
      </div>
      <div class="card" style="margin-top: 18px">
        <p class="section-title">软件更新</p>

        <div v-if="updLoading" class="muted">读取版本信息…</div>
        <div v-else-if="!upd || !upd.enabled" class="muted">
          远程更新未启用。在服务器的 <code>.env</code> 中设置 <code>UPDATE_ENABLED=true</code> 后重启即可开启。
        </div>
        <template v-else-if="upd.isGit">
          <div class="row" style="align-items: center; margin-bottom: 10px">
            <span class="badge" :class="upd.hasUpdate ? 'expired' : 'ready'">
              {{ upd.hasUpdate ? `可更新（落后 ${upd.behind} 个提交）` : '已是最新' }}
            </span>
            <span class="muted" style="font-size: 13px">
              分支 <code>{{ upd.branch }}</code>
              <template v-if="upd.lastCheckedAt"> · 检查于 {{ new Date(upd.lastCheckedAt).toLocaleString() }}</template>
            </span>
          </div>

          <ul class="muted" style="margin: 0 0 12px; padding-left: 18px; line-height: 1.8; font-size: 13px">
            <li>当前版本：<code>{{ shortSha(upd.current?.sha ?? '') }}</code>{{ upd.current ? ` · ${fmtDate(upd.current.date)} · ${upd.current.message}` : '' }}</li>
            <li v-if="upd.remote">
              远端最新：<code>{{ shortSha(upd.remote.sha) }}</code> · {{ fmtDate(upd.remote.date) }} · {{ upd.remote.message }}
            </li>
          </ul>

          <div v-if="upd.dirty" class="alert error" style="margin-bottom: 12px">
            检测到工作区有未提交的改动，为避免丢失，更新被禁止。请在服务器上提交或还原后重试。
          </div>

          <div v-if="upMsg.type" class="alert" :class="upMsg.type" style="white-space: pre-line; margin-bottom: 12px">{{ upMsg.text }}</div>

          <div class="row">
            <button class="btn" :disabled="checking || upd.running" @click="checkNow">
              {{ checking ? '检查中…' : '检查更新' }}
            </button>
            <button
              class="btn btn-primary"
              :disabled="!upd.hasUpdate || upd.dirty || upd.running || applying"
              @click="runNow"
            >
              {{ upd.running ? '更新进行中…' : '立即更新' }}
            </button>
          </div>

          <pre
            v-if="logText"
            style="margin-top: 12px; background: var(--surface-2); border: 1px solid var(--border); border-radius: 8px; padding: 10px; font-size: 12px; max-height: 260px; overflow: auto; white-space: pre-wrap"
            >{{ logText }}</pre
          >

          <p class="hint">
            更新流程：<code>git reset --hard origin/{{ upd.branch }}</code> → 安装依赖并构建前端/后端 → <code>pm2 restart</code>。
            <code>.env</code> 与 <code>data/</code>（含数据库）不受影响。
          </p>
        </template>
        <div v-else class="muted">当前运行目录不是 git 仓库（可能以压缩包部署），无法使用远程更新。</div>
      </div>
    </template>
  </div>
</template>
