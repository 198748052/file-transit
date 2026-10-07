<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { api, ApiError, type R2Status } from '../api';

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

onMounted(load);

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
    </template>
  </div>
</template>
