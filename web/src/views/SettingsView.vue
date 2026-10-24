<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  api,
  ApiError,
  type PasswordStatus,
  type ShareLimitSettings,
  type UsernameStatus,
  type R2Status,
  type R2UsageStatus,
  type SiteStatus,
  type UpdateStatus,
  type UpdateLog,
} from '../api';
import { formatBytes } from '../utils/format';
import DriveIcon from '../components/DriveIcon.vue';
import { auth } from '../auth';

const router = useRouter();
const route = useRoute();

type PanelKey = 'storage' | 'site' | 'share' | 'username' | 'password' | 'update';

const PANELS: Record<PanelKey, { title: string; desc: string }> = {
  storage: { title: '存储与用量', desc: '配置 Cloudflare R2 凭据与账户用量，保存后立即生效。' },
  site: { title: '站点地址', desc: '分享链接使用的域名；留空时自动识别当前访问地址。' },
  share: { title: '公开分享限制', desc: '限制单个 IP 的公开下载频率与提取码错误次数，降低盗链和爆破风险。' },
  username: { title: '登录用户名', desc: '用户名默认取自 .env，在面板修改后以本地数据库为准。' },
  password: { title: '登录密码', desc: '修改后所有已签发的登录状态会立即失效，需要重新登录。' },
  update: { title: '软件更新', desc: '从远程仓库拉取最新代码并重新构建、重启服务。' },
};

const active = ref<PanelKey | null>(null);
const activeMeta = computed(() => (active.value ? PANELS[active.value] : null));

function panelFromRoute(): PanelKey | null {
  const requested = route.query.panel;
  if (typeof requested === 'string' && requested in PANELS) return requested as PanelKey;
  const hash = route.hash.replace(/^#/, '');
  if (hash === 'account') return 'username';
  if (hash in PANELS) return hash as PanelKey;
  return null;
}

watch(
  () => [route.query.panel, route.hash],
  () => {
    active.value = panelFromRoute();
  },
);

function openPanel(key: PanelKey) {
  active.value = key;
  router.push({ path: '/settings', query: { panel: key } });
}

function closePanel() {
  active.value = null;
  router.push({ path: '/settings' });
}

const form = reactive({ accountId: '', accessKeyId: '', secretAccessKey: '', bucket: '', endpoint: '' });
const status = ref<R2Status | null>(null);
const loading = ref(true);
const saving = ref(false);
const testing = ref(false);

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
  } catch {
    flash('error', '读取存储配置失败，请刷新重试。');
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

// ── 登录用户名 ────────────────────────────────────────────
const user = reactive({ password: '', next: '' });
const userStatus = ref<UsernameStatus | null>(null);
const userSaving = ref(false);
const userMsg = reactive<{ type: '' | 'success' | 'error' | 'info'; text: string }>({ type: '', text: '' });

function uFlash(type: typeof userMsg.type, text: string) {
  userMsg.type = type;
  userMsg.text = text;
}

async function loadUsername() {
  try {
    userStatus.value = await api.usernameStatus();
  } catch {
    /* 用户名卡片保留默认提示 */
  }
}

async function saveUsername() {
  userMsg.type = '';
  if (!user.password) return uFlash('error', '请输入当前密码');
  const next = user.next.trim();
  if (!next) return uFlash('error', '请填写新用户名');
  if (next.length > 64) return uFlash('error', '用户名最多 64 个字符');
  if (next === userStatus.value?.username) return uFlash('error', '新用户名和当前用户名相同');
  userSaving.value = true;
  try {
    await api.changeUsername(user.password, next);
    Object.assign(user, { password: '', next: '' });
    auth.logout();
    await router.push({ path: '/login', query: { user: 'changed' } });
  } catch (e) {
    uFlash('error', describeUser(e));
  } finally {
    userSaving.value = false;
  }
}

async function clearUsername() {
  if (!confirm('删除面板里设置过的用户名，恢复使用服务器 .env 中的 ADMIN_USERNAME。确定继续？')) return;
  userMsg.type = '';
  try {
    await api.clearPanelUsername();
    auth.logout();
    await router.push({ path: '/login', query: { user: 'cleared' } });
  } catch (e) {
    uFlash('error', describeUser(e));
  }
}

function describeUser(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'wrong_password') return '当前密码不正确';
    if (e.code === 'same_username') return '新用户名和当前用户名相同';
    if (e.code === 'validation_failed') return '用户名需为 1-64 个字符';
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

// ── 公开分享限制 ──────────────────────────────────────────
const shareLimit = ref<ShareLimitSettings | null>(null);
const shareForm = reactive({ enabled: true, maxFailures: 10, windowMinutes: 15, maxDownloads: 300 });
const shareSaving = ref(false);
const shareMsg = reactive<{ type: '' | 'success' | 'error' | 'info'; text: string }>({ type: '', text: '' });

function shFlash(type: typeof shareMsg.type, text: string) {
  shareMsg.type = type;
  shareMsg.text = text;
}

async function loadShareLimit() {
  try {
    const settings = await api.getShareLimit();
    shareLimit.value = settings;
    Object.assign(shareForm, settings);
  } catch {
    /* 分享限制卡片保留默认值 */
  }
}

async function saveShareLimit() {
  const maxFailures = Number(shareForm.maxFailures);
  const windowMinutes = Number(shareForm.windowMinutes);
  const maxDownloads = Number(shareForm.maxDownloads);
  if (!Number.isInteger(maxFailures) || maxFailures < 1 || maxFailures > 100) {
    return shFlash('error', '提取码错误上限需为 1-100 的整数');
  }
  if (!Number.isInteger(windowMinutes) || windowMinutes < 1 || windowMinutes > 1440) {
    return shFlash('error', '统计窗口需为 1-1440 分钟');
  }
  if (!Number.isInteger(maxDownloads) || maxDownloads < 0 || maxDownloads > 100000) {
    return shFlash('error', '下载/预览上限需为 0-100000 的整数');
  }
  shareSaving.value = true;
  shareMsg.type = '';
  try {
    const settings = await api.saveShareLimit({
      enabled: shareForm.enabled,
      maxFailures,
      windowMinutes,
      maxDownloads,
    });
    shareLimit.value = settings;
    Object.assign(shareForm, settings);
    shFlash('success', settings.enabled ? '限制已保存并即时生效。' : '限制已关闭，公开分享不再限流。');
  } catch (e) {
    shFlash('error', e instanceof ApiError ? `保存失败：${e.code}` : '网络错误，请稍后重试');
  } finally {
    shareSaving.value = false;
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

const storageEntrySub = computed(() => {
  if (!status.value) return '读取配置…';
  if (!status.value.configured) return '尚未配置，上传功能不可用';
  const used =
    usage.value?.configured && usage.value.usage
      ? ` · 已用 ${formatBytes(usage.value.usage.totalBytes)}`
      : '';
  return `桶 ${status.value.bucket}${used}`;
});

const siteSummary = computed(() => {
  if (!site.value) return '读取配置…';
  return site.value.mode === 'pinned' ? `固定为 ${site.value.baseUrl}` : `自动识别（${site.value.autoDetected}）`;
});

const usernameSummary = computed(() => userStatus.value?.username ?? '读取中…');

const passwordSummary = computed(() => {
  if (!pwdStatus.value) return '读取中…';
  const source = pwdStatus.value.storedIn === 'db' ? '面板设置' : '.env';
  if (!pwdStatus.value.changedAt) return source;
  return `${source} · 修改于 ${new Date(pwdStatus.value.changedAt).toLocaleString()}`;
});

const shareLimitSummary = computed(() => {
  if (!shareLimit.value) return '读取配置…';
  if (!shareLimit.value.enabled) return '已关闭';
  const requestCap = shareLimit.value.maxDownloads > 0 ? ` · 每 ${shareLimit.value.windowMinutes} 分钟最多 ${shareLimit.value.maxDownloads} 次下载` : '';
  return `提取码错误 ${shareLimit.value.maxFailures} 次 / ${shareLimit.value.windowMinutes} 分钟${requestCap}`;
});

const updateSummary = computed(() => {
  if (updLoading.value) return '读取版本信息…';
  if (!upd.value) return '未启用';
  if (!upd.value.enabled) return '未启用';
  if (!upd.value.isGit) return '非 git 部署，不可用';
  return upd.value.hasUpdate ? `落后 ${upd.value.behind} 个提交` : '已是最新';
});

const updateAttention = computed(() => !!upd.value?.enabled && !!upd.value.hasUpdate);

onMounted(() => {
  active.value = panelFromRoute();
  load();
  loadUsage();
  loadPassword();
  loadUsername();
  loadSite();
  loadShareLimit();
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

function describe(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'secret_required') return '首次配置需要填写 Secret Access Key';
    if (e.code.startsWith('r2_check_failed')) return e.code.replace('r2_check_failed: ', '测试失败：');
    return `请求失败：${e.code}`;
  }
  return '网络错误，请稍后重试';
}

// ── 账户总用量（可选）──────────────────────────────────────
const usage = ref<R2UsageStatus | null>(null);
const usageToken = ref('');
const usageSaving = ref(false);
const usageLoading = ref(false);
const usageMsg = reactive<{ type: '' | 'success' | 'error' | 'info'; text: string }>({ type: '', text: '' });

function usageFlash(type: typeof usageMsg.type, text: string) {
  usageMsg.type = type;
  usageMsg.text = text;
}

const usagePct = computed(() => {
  const metrics = usage.value?.usage;
  const limit = usage.value?.freeTierBytes ?? 0;
  if (!metrics || !limit) return 0;
  return Math.min(100, (metrics.standardBytes / limit) * 100);
});

function describeUsageCode(code: string): string {
  if (code === 'r2_not_configured') return '尚未配置 R2 Account ID，请先完成上方存储配置。';
  if (code === 'usage_token_required') return '请先保存 Cloudflare API Token。';
  if (code === 'usage_token_invalid') return 'Token 无效或已过期，请重新创建。';
  if (code === 'usage_token_forbidden') return 'Token 权限不足，请勾选 Workers R2 Storage Read（只读）。';
  if (code === 'usage_fetch_timeout') return '读取超时，请稍后重试。';
  if (code.startsWith('usage_fetch_failed')) return '读取失败：' + code.replace('usage_fetch_failed: ', '');
  return '读取失败：' + code;
}

function describeUsage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'invalid_token') return 'Token 格式不正确，请检查是否粘贴完整。';
    return describeUsageCode(e.code);
  }
  return '网络错误，请稍后重试';
}

async function loadUsage(refresh = false) {
  usageLoading.value = true;
  try {
    usage.value = await api.r2Usage(refresh);
  } catch (e) {
    usageFlash('error', describeUsage(e));
  } finally {
    usageLoading.value = false;
  }
}

async function saveUsageToken() {
  const token = usageToken.value.trim();
  if (!token) {
    usageFlash('error', '请填入 Cloudflare API Token');
    return;
  }
  usageSaving.value = true;
  usageMsg.type = '';
  try {
    const result = await api.saveR2UsageToken(token);
    usageToken.value = '';
    usageFlash(
      'success',
      result.configured ? 'Token 已保存，正在读取账户用量…' : 'Token 已保存，但还需在上方配置 R2 Account ID。',
    );
    await loadUsage(true);
  } catch (e) {
    usageFlash('error', describeUsage(e));
  } finally {
    usageSaving.value = false;
  }
}

async function clearUsageToken() {
  if (!confirm('删除已保存的 Cloudflare API Token？删除后将无法显示全部桶的用量。')) return;
  usageSaving.value = true;
  usageMsg.type = '';
  try {
    await api.saveR2UsageToken('');
    usage.value = await api.r2Usage();
    usageFlash('success', '已删除 Token。');
  } catch (e) {
    usageFlash('error', describeUsage(e));
  } finally {
    usageSaving.value = false;
  }
}

const storedLabel = () => {
  const s = status.value?.storedIn;
  return s === 'db' ? '本地数据库' : s === 'env' ? '环境变量 .env' : '未配置';
};
</script>

<template>
  <div class="settings">
    <template v-if="!active">
      <div class="page-head">
        <h1>设置</h1>
        <p class="muted">选择一个入口进行配置，修改后即时生效。</p>
      </div>

      <div class="settings-groups">
        <section class="settings-group">
          <p class="settings-group-title">存储与站点</p>
          <div class="settings-group-card">
            <button type="button" class="settings-entry" @click="openPanel('storage')">
              <span class="settings-entry-icon"><DriveIcon name="storage" :size="20" /></span>
              <span class="settings-entry-body">
                <span class="settings-entry-title">存储与用量</span>
                <span class="settings-entry-sub">{{ storageEntrySub }}</span>
              </span>
              <DriveIcon name="chevron" :size="16" class="settings-entry-chevron" />
            </button>

            <button type="button" class="settings-entry" @click="openPanel('site')">
              <span class="settings-entry-icon"><DriveIcon name="link" :size="20" /></span>
              <span class="settings-entry-body">
                <span class="settings-entry-title">站点地址</span>
                <span class="settings-entry-sub">{{ siteSummary }}</span>
              </span>
              <DriveIcon name="chevron" :size="16" class="settings-entry-chevron" />
            </button>
          </div>
        </section>

        <section class="settings-group">
          <p class="settings-group-title">账号与安全</p>
          <div class="settings-group-card">
            <button type="button" class="settings-entry" @click="openPanel('username')">
              <span class="settings-entry-icon"><DriveIcon name="edit" :size="20" /></span>
              <span class="settings-entry-body">
                <span class="settings-entry-title">登录用户名</span>
                <span class="settings-entry-sub">当前用户名：{{ usernameSummary }}</span>
              </span>
              <DriveIcon name="chevron" :size="16" class="settings-entry-chevron" />
            </button>

            <button type="button" class="settings-entry" @click="openPanel('password')">
              <span class="settings-entry-icon"><DriveIcon name="lock" :size="20" /></span>
              <span class="settings-entry-body">
                <span class="settings-entry-title">登录密码</span>
                <span class="settings-entry-sub">{{ passwordSummary }}</span>
              </span>
              <DriveIcon name="chevron" :size="16" class="settings-entry-chevron" />
            </button>

            <button type="button" class="settings-entry" @click="openPanel('share')">
              <span class="settings-entry-icon"><DriveIcon name="share" :size="20" /></span>
              <span class="settings-entry-body">
                <span class="settings-entry-title">公开分享限制</span>
                <span class="settings-entry-sub">{{ shareLimitSummary }}</span>
              </span>
              <DriveIcon name="chevron" :size="16" class="settings-entry-chevron" />
            </button>
          </div>
        </section>

        <section class="settings-group">
          <p class="settings-group-title">系统</p>
          <div class="settings-group-card">
            <button type="button" class="settings-entry" @click="openPanel('update')">
              <span class="settings-entry-icon"><DriveIcon name="refresh" :size="20" /></span>
              <span class="settings-entry-body">
                <span class="settings-entry-title">软件更新</span>
                <span class="settings-entry-sub">{{ updateSummary }}</span>
              </span>
              <span v-if="updateAttention" class="settings-entry-dot" aria-label="有可用更新"></span>
              <DriveIcon name="chevron" :size="16" class="settings-entry-chevron" />
            </button>
          </div>
        </section>
      </div>
    </template>

    <template v-else>
      <button type="button" class="settings-back" @click="closePanel">
        <DriveIcon name="chevron" :size="15" class="settings-back-icon" />
        返回设置
      </button>

      <div class="page-head">
        <h1>{{ activeMeta?.title }}</h1>
        <p class="muted">{{ activeMeta?.desc }}</p>
      </div>

      <div v-if="loading" class="card"><p class="muted">加载中…</p></div>

      <template v-else>
      <!-- ── 存储 R2 ─────────────────────────────────────── -->
      <section v-if="active === 'storage'" id="storage" class="settings-block">
        <div class="card">
          <div class="toolbar">
            <div>
              <span class="badge" :class="status?.configured ? 'ready' : 'expired'">
                {{ status?.configured ? '已配置' : '未配置' }}
              </span>
              <span class="muted ms-sm text-sm">来源：{{ storedLabel() }}</span>
            </div>
            <button class="btn btn-sm btn-ghost" @click="load">重新读取</button>
          </div>

          <div v-if="msg.type" class="alert pre-line" :class="msg.type">{{ msg.text }}</div>

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

          <div class="row mt-sm">
            <button class="btn btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
            <button class="btn" :disabled="testing" @click="test">{{ testing ? '测试中…' : '测试连接' }}</button>
          </div>
          <p class="hint">
            “测试连接”会先保存再执行。浏览器直传 R2 需要桶已配置 CORS；如更换访问域名或首次部署，可用
            <code>npm run r2:cors</code> 或在 Cloudflare 控制台的桶设置里配置。
            凭据保存在本地数据库（<code>settings</code> 表），优先级高于 <code>.env</code>，Secret 保存后不会回显。
          </p>
        </div>

        <div class="card mt-md">
          <div class="toolbar">
            <div>
              <p class="section-title">账户总用量（全部桶）</p>
              <p class="muted text-sm">
                通过 Cloudflare API 读取账号下所有 R2 桶的存储量，用来对照 10 GB 免费额度。
              </p>
            </div>
            <span class="badge" :class="usage?.configured ? 'ready' : 'expired'">
              {{ usage?.configured ? '已启用' : '未启用' }}
            </span>
          </div>

          <div v-if="usageMsg.type" class="alert" :class="usageMsg.type">{{ usageMsg.text }}</div>

          <div v-if="usage?.usage" class="usage-summary">
            <div class="usage-value">{{ formatBytes(usage.usage.totalBytes) }}</div>
            <div class="usage-bar mt-sm">
              <div class="usage-bar-fill" :class="{ warn: usagePct >= 80 }" :style="{ width: usagePct + '%' }"></div>
            </div>
            <div class="usage-meta">
              <span>标准存储 {{ formatBytes(usage.usage.standardBytes) }}</span>
              <span v-if="usage.usage.infrequentAccessBytes">低频存储 {{ formatBytes(usage.usage.infrequentAccessBytes) }}</span>
              <span>{{ usage.usage.objectCount }} 个对象</span>
              <span>占 10 GB 免费额度 {{ usagePct.toFixed(1) }}%</span>
            </div>
            <div class="usage-meta">
              <span>更新于 {{ new Date(usage.usage.fetchedAt).toLocaleTimeString() }}</span>
              <span v-if="usage.usage.pendingBytes">含未完成分片 {{ formatBytes(usage.usage.pendingBytes) }}</span>
            </div>
          </div>

          <div v-else-if="usage?.error" class="alert error">{{ describeUsageCode(usage.error) }}</div>
          <div v-else class="muted text-sm">尚未启用。保存只读 Token 后即可统计账号下全部桶的用量。</div>

          <div class="field mt-md">
            <label for="usage-token">Cloudflare API Token</label>
            <div class="usage-token-row">
              <input
                id="usage-token"
                v-model="usageToken"
                type="password"
                autocomplete="off"
                placeholder="粘贴只读 API Token"
              />
              <button class="btn btn-primary" :disabled="usageSaving" @click="saveUsageToken">
                {{ usageSaving ? '保存中…' : usage?.hasToken ? '更新 Token' : '保存 Token' }}
              </button>
            </div>
          </div>

          <div class="row align-center">
            <button class="btn" :disabled="!usage?.configured || usageLoading" @click="loadUsage(true)">
              {{ usageLoading ? '读取中…' : '刷新用量' }}
            </button>
            <button v-if="usage?.hasToken" class="btn btn-ghost" :disabled="usageSaving" @click="clearUsageToken">
              删除 Token
            </button>
          </div>

          <p class="hint">
            在
            <a href="https://dash.cloudflare.com/profile/api-tokens" target="_blank" rel="noopener">Cloudflare API Token</a>
            页面创建只读 Token，权限勾选 <code>Workers R2 Storage Read</code>（如提示无权限，再补上 <code>Account Analytics Read</code>）。
            统计范围为整个账号，包含其他桶；Token 仅保存在本地数据库，不会回显。
          </p>
        </div>
      </section>

      <!-- ── 站点地址 ────────────────────────────────────── -->
      <section v-else-if="active === 'site'" id="site" class="settings-block">
        <div class="card">
          <div v-if="siteMsg.type" class="alert" :class="siteMsg.type">{{ siteMsg.text }}</div>

          <div class="field">
            <label>公开访问地址（域名）</label>
            <input v-model="siteForm.baseUrl" type="text" placeholder="例如 https://files.example.com，留空为自动识别" />
          </div>

          <div class="row mt-sm align-center">
            <button class="btn btn-primary" :disabled="siteSaving" @click="saveSite">
              {{ siteSaving ? '保存中…' : '保存' }}
            </button>
            <button v-if="site?.mode === 'pinned'" class="btn btn-ghost" @click="clearSite">恢复自动识别</button>
            <span v-if="site" class="muted text-sm">
              当前生效：{{ site.mode === 'pinned' ? '固定地址' : `自动识别（${site.autoDetected}）` }}
            </span>
          </div>
          <p class="hint">保存后立即生效，分享链接的域名会随之更新。</p>
        </div>
      </section>

      <!-- ── 公开分享限制 ────────────────────────────────── -->
      <section v-else-if="active === 'share'" id="share" class="settings-block">
        <div class="card">
          <div v-if="shareMsg.type" class="alert" :class="shareMsg.type">{{ shareMsg.text }}</div>

          <label class="checkbox-row mb-md">
            <input v-model="shareForm.enabled" type="checkbox" />
            启用公开分享限流
          </label>

          <div class="row">
            <div class="field">
              <label>提取码错误上限（次）</label>
              <input v-model.number="shareForm.maxFailures" type="number" min="1" max="100" :disabled="!shareForm.enabled" />
            </div>
            <div class="field">
              <label>统计窗口（分钟）</label>
              <input v-model.number="shareForm.windowMinutes" type="number" min="1" max="1440" :disabled="!shareForm.enabled" />
            </div>
          </div>
          <div class="field">
            <label>单 IP 下载/预览上限（次 / 窗口）</label>
            <input v-model.number="shareForm.maxDownloads" type="number" min="0" max="100000" :disabled="!shareForm.enabled" />
            <p class="hint">填 0 表示不限制请求次数，仅保留提取码错误限制。</p>
          </div>

          <div class="row mt-sm">
            <button class="btn btn-primary" :disabled="shareSaving" @click="saveShareLimit">
              {{ shareSaving ? '保存中…' : '保存' }}
            </button>
            <span v-if="shareLimit" class="muted text-sm">
              当前：{{ shareLimit.enabled ? `错误 ${shareLimit.maxFailures} 次 / ${shareLimit.windowMinutes} 分钟` : '已关闭' }}
            </span>
          </div>
          <p class="hint">
            限制按来源 IP 统计，作用于文件、集合与文件夹分享的下载和在线预览。触发限制时返回 429，并在操作记录中记为「分享限流」。
          </p>
        </div>
      </section>

      <!-- ── 账号与安全 ──────────────────────────────────── -->
      <section v-if="active === 'username'" id="username" class="settings-block">
        <div class="card">
          <div class="toolbar mb-sm">
            <span class="muted text-sm">
              当前用户名：<code>{{ userStatus?.username ?? '—' }}</code>
              · {{ userStatus?.storedIn === 'db' ? '面板设置（保存在本地数据库）' : '.env 的 ADMIN_USERNAME' }}
            </span>
          </div>

          <div v-if="userMsg.type" class="alert" :class="userMsg.type">{{ userMsg.text }}</div>

          <div class="row">
            <div class="field">
              <label>新用户名</label>
              <input v-model="user.next" type="text" autocomplete="username" placeholder="例如 admin" />
            </div>
            <div class="field">
              <label>当前密码</label>
              <input v-model="user.password" type="password" autocomplete="current-password" placeholder="验证身份用" />
            </div>
          </div>

          <div class="row mt-sm">
            <button class="btn btn-primary" :disabled="userSaving" @click="saveUsername">
              {{ userSaving ? '保存中…' : '修改用户名' }}
            </button>
            <button v-if="userStatus?.storedIn === 'db'" class="btn btn-ghost" @click="clearUsername">
              改用 .env 里的用户名
            </button>
          </div>
          <p class="hint">
            用户名保存在本地 <code>settings</code> 表，优先级高于 <code>.env</code> 的 <code>ADMIN_USERNAME</code>。
            修改成功后当前登录状态立即失效，需要用新的用户名和密码重新登录。
          </p>
        </div>
      </section>

      <section v-if="active === 'password'" id="password" class="settings-block">
        <div class="card">
          <div class="toolbar mb-sm">
            <span class="muted text-sm">
              当前密码来源：{{ pwdStatus?.storedIn === 'db' ? '面板设置（保存在本地数据库）' : '.env 的 ADMIN_PASSWORD' }}
              <template v-if="pwdStatus?.changedAt"> · 修改于 {{ new Date(pwdStatus.changedAt).toLocaleString() }}</template>
            </span>
          </div>

          <div v-if="pwdMsg.type" class="alert" :class="pwdMsg.type">{{ pwdMsg.text }}</div>

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

          <div class="row mt-sm">
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
      </section>

      <!-- ── 软件更新 ────────────────────────────────────── -->
      <section v-if="active === 'update'" id="update" class="settings-block">
        <div class="card">
          <div v-if="updLoading" class="muted">读取版本信息…</div>
          <div v-else-if="!upd || !upd.enabled" class="muted">
            远程更新未启用。在服务器的 <code>.env</code> 中设置 <code>UPDATE_ENABLED=true</code> 后重启即可开启。
          </div>
          <template v-else-if="upd.isGit">
            <div class="row align-center mb-sm">
              <span class="badge" :class="upd.hasUpdate ? 'expired' : 'ready'">
                {{ upd.hasUpdate ? `可更新（落后 ${upd.behind} 个提交）` : '已是最新' }}
              </span>
              <span class="muted text-sm">
                分支 <code>{{ upd.branch }}</code>
                <template v-if="upd.lastCheckedAt"> · 检查于 {{ new Date(upd.lastCheckedAt).toLocaleString() }}</template>
              </span>
            </div>

            <ul class="muted list-tight">
              <li>当前版本：<code>{{ shortSha(upd.current?.sha ?? '') }}</code>{{ upd.current ? ` · ${fmtDate(upd.current.date)} · ${upd.current.message}` : '' }}</li>
              <li v-if="upd.remote">
                远端最新：<code>{{ shortSha(upd.remote.sha) }}</code> · {{ fmtDate(upd.remote.date) }} · {{ upd.remote.message }}
              </li>
            </ul>

            <div v-if="upMsg.type" class="alert pre-line" :class="upMsg.type">{{ upMsg.text }}</div>

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

            <pre v-if="logText" class="log-box">{{ logText }}</pre>

            <p class="hint">
              更新流程：<code>git reset --hard origin/{{ upd.branch }}</code> → 安装依赖并构建前端/后端 → <code>pm2 restart</code>。
              <code>.env</code> 与 <code>data/</code>（含数据库）不受影响。
            </p>
          </template>
          <div v-else class="muted">当前运行目录不是 git 仓库（可能以压缩包部署），无法使用远程更新。</div>
        </div>
      </section>
      </template>
    </template>
  </div>
</template>
