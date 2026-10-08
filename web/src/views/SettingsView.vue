<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { api, ApiError, type PasswordStatus, type R2Status, type SiteStatus, type UpdateStatus, type UpdateLog } from '../api';
import { auth } from '../auth';

const router = useRouter();

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

// ── 登录密码 ──────────────────────────────────────────────
const pwd = reactive({ current: '', next: '', confirm: '' });
const pwdStatus = ref<PasswordStatus | null>(null);
const pwdSaving = ref(false);
const pwdMsg = reactive<{ type: '' | 'success' | 'error' | 'info'; text: string }>({ type: '', text: '' });

function pFlash(type: typeof pwdMsg.type, text: string) {
  pwdMsg.type = type;
  pwdMsg.text = text;
}

async function loadPassword() {
  try {
    pwdStatus.value = await api.passwordStatus();
  } catch {
    /* 密码卡片保留默认提示 */
  }
}

async function savePassword() {
  pwdMsg.type = '';
  if (!pwd.current) return pFlash('error', '请填写当前密码');
  if (pwd.next.length < 8) return pFlash('error', '新密码至少 8 位');
  if (pwd.next !== pwd.confirm) return pFlash('error', '两次输入的新密码不一致');
  pwdSaving.value = true;
  try {
    await api.changePassword(pwd.current, pwd.next);
    Object.assign(pwd, { current: '', next: '', confirm: '' });
    auth.logout();
    await router.push({ path: '/login', query: { pwd: 'changed' } });
  } catch (e) {
    pFlash('error', describePwd(e));
  } finally {
    pwdSaving.value = false;
  }
}

async function clearPassword() {
  if (!confirm('删除面板里设置过的密码，恢复使用服务器 .env 中的 ADMIN_PASSWORD。确定继续？')) return;
  pwdMsg.type = '';
  try {
    await api.clearPanelPassword();
    auth.logout();
    await router.push({ path: '/login', query: { pwd: 'cleared' } });
  } catch (e) {
    pFlash('error', describePwd(e));
  }
}

function describePwd(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'wrong_password') return '当前密码不正确';
    if (e.code === 'same_password') return '新密码和当前密码相同';
    if (e.code === 'validation_failed') return '新密码至少 8 位';
    if (e.code === 'unauthorized' || e.code === 'token_revoked') return '登录已过期，请重新登录';
    return `修改失败：${e.code}`;
  }
  return '网络错误，请稍后重试';
}

// ── 站点地址（分享链接域名）────────────────────────────────
const site = ref<SiteStatus | null>(null);
const siteForm = reactive({ baseUrl: '' });
const siteSaving = ref(false);
const siteMsg = reactive<{ type: '' | 'success' | 'error' | 'info'; text: string }>({ type: '', text: '' });

function sFlash(type: typeof siteMsg.type, text: string) {
  siteMsg.type = type;
  siteMsg.text = text;
}

async function loadSite() {
  try {
    const s = await api.getSite();
    site.value = s;
    siteForm.baseUrl = s.baseUrl;
  } catch {
    /* 站点地址卡片保留默认值 */
  }
}

async function saveSite() {
  siteSaving.value = true;
  siteMsg.type = '';
  try {
    const s = await api.saveSite(siteForm.baseUrl.trim());
    site.value = s;
    siteForm.baseUrl = s.baseUrl;
    sFlash('success', s.mode === 'pinned' ? '已固定站点地址。' : '已恢复为自动识别。');
  } catch (e) {
    sFlash('error', describeSite(e));
  } finally {
    siteSaving.value = false;
  }
}

function clearSite() {
  siteForm.baseUrl = '';
  saveSite();
}

function describeSite(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'validation_failed') return '请输入合法网址（需包含 http:// 或 https://）';
    return `保存失败：${e.code}`;
  }
  return '网络错误，请稍后重试';
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
  if (!confirm('将拉取最新代码、重新构建并重启服务，期间站点会短暂不可用。服务器上未提交的代码改动会被覆盖（.env 与数据不受影响）。确定继续？')) return;
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
  loadPassword();
  loadSite();
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
        <p class="section-title">站点地址</p>
        <p class="muted" style="font-size: 13px; margin-bottom: 12px">
          分享链接使用的域名。留空时自动使用你当前访问站点的域名（反向代理后即为你的域名）。
          固定为你的域名后，即使直接用 IP 打开面板，分享链接也不会带上服务器 IP。
        </p>

        <div v-if="siteMsg.type" class="alert" :class="siteMsg.type" style="margin-bottom: 12px">{{ siteMsg.text }}</div>

        <div class="field">
          <label>公开访问地址（域名）</label>
          <input v-model="siteForm.baseUrl" type="text" placeholder="例如 https://files.example.com，留空为自动识别" />
        </div>

        <div class="row" style="margin-top: 10px; align-items: center">
          <button class="btn btn-primary" :disabled="siteSaving" @click="saveSite">
            {{ siteSaving ? '保存中…' : '保存' }}
          </button>
          <button v-if="site?.mode === 'pinned'" class="btn btn-ghost" @click="clearSite">恢复自动识别</button>
          <span v-if="site" class="muted" style="font-size: 13px">
            当前生效：{{ site.mode === 'pinned' ? '固定地址' : `自动识别（${site.autoDetected}）` }}
          </span>
        </div>
        <p class="hint">保存后立即生效，分享链接的域名会随之更新。</p>
      </div>

      <div class="card" style="margin-top: 18px">
        <p class="section-title">说明</p>
        <ul class="muted" style="margin: 0; padding-left: 18px; line-height: 1.8; font-size: 14px">
          <li>凭据保存在本地 SQLite（<code>settings</code> 表），优先级高于 <code>.env</code>。</li>
          <li>Secret 一旦保存不会回显；如需更换，填入新值并保存即可。</li>
          <li>分享链接域名默认自动识别访问域名，也可在上方「站点地址」里固定，避免暴露服务器 IP。</li>
        </ul>
      </div>

      <div class="card" style="margin-top: 18px">
        <p class="section-title">登录密码</p>
        <div class="toolbar" style="margin-bottom: 12px">
          <span class="muted" style="font-size: 13px">
            当前密码来源：{{ pwdStatus?.storedIn === 'db' ? '面板设置（保存在本地数据库）' : '.env 的 ADMIN_PASSWORD' }}
            <template v-if="pwdStatus?.changedAt"> · 修改于 {{ new Date(pwdStatus.changedAt).toLocaleString() }}</template>
          </span>
        </div>

        <div v-if="pwdMsg.type" class="alert" :class="pwdMsg.type" style="margin-bottom: 12px">{{ pwdMsg.text }}</div>

        <div class="field">
          <label>当前密码</label>
          <input v-model="pwd.current" type="password" autocomplete="current-password" placeholder="登录时用的那个密码" />
        </div>
        <div class="row">
          <div class="field">
            <label>新密码</label>
            <input v-model="pwd.next" type="password" autocomplete="new-password" placeholder="至少 8 位" />
          </div>
          <div class="field">
            <label>确认新密码</label>
            <input v-model="pwd.confirm" type="password" autocomplete="new-password" placeholder="再输入一次" />
          </div>
        </div>

        <div class="row" style="margin-top: 10px">
          <button class="btn btn-primary" :disabled="pwdSaving" @click="savePassword">
            {{ pwdSaving ? '保存中…' : '修改密码' }}
          </button>
          <button v-if="pwdStatus?.storedIn === 'db'" class="btn btn-ghost" @click="clearPassword">
            改用 .env 里的密码
          </button>
        </div>
        <p class="hint">
          密码以 bcrypt 哈希保存在本地 <code>settings</code> 表，优先级高于 <code>.env</code>，明文不会落盘。
          修改成功后当前登录状态立即失效（所有已签发的令牌同时作废），需要用新密码重新登录。
        </p>
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

          <div v-if="upMsg.type" class="alert" :class="upMsg.type" style="white-space: pre-line; margin-bottom: 12px">{{ upMsg.text }}</div>

          <div class="row">
            <button class="btn" :disabled="checking || upd.running" @click="checkNow">
              {{ checking ? '检查中…' : '检查更新' }}
            </button>
            <button
              class="btn btn-primary"
              :disabled="!upd.hasUpdate || upd.running || applying"
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
