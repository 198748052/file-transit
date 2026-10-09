<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { api, ApiError, type CollectionShareInfo } from '../api';
import { formatBytes, formatDateTime, describeExpiry } from '../utils/format';
import { copyText } from '../utils/clipboard';

const route = useRoute();
const code = String(route.params.code ?? '');

const loading = ref(true);
const info = ref<CollectionShareInfo | null>(null);
const notFound = ref(false);
const password = ref('');
const error = ref('');
const downloading = ref(false);
const activeId = ref('');
const copied = ref(false);

async function load() {
  loading.value = true;
  error.value = '';
  notFound.value = false;
  try {
    info.value = await api.collectionShare(code);
  } catch (e) {
    if (e instanceof ApiError && e.code === 'not_found') notFound.value = true;
    else error.value = '加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

onMounted(load);

const ready = computed(() => !!info.value && !info.value.expired && info.value.items.length > 0);
const canDownload = computed(() => ready.value && (!info.value!.requiresPassword || password.value.length > 0));

function handleError(e: unknown, fallback: string) {
  if (e instanceof ApiError) {
    if (e.code === 'wrong_password') error.value = '提取码错误';
    else if (e.code === 'password_required') error.value = '请输入提取码';
    else if (e.code === 'expired') {
      error.value = '集合已过期';
      load();
    } else error.value = fallback;
  } else error.value = fallback;
}

function trigger(url: string) {
  const a = document.createElement('a');
  a.href = url;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
}

async function downloadItem(fileId: string) {
  if (!canDownload.value) return;
  activeId.value = fileId;
  error.value = '';
  try {
    const { url } = await api.collectionItemDownload(code, fileId, password.value || undefined);
    trigger(url);
    const item = info.value?.items.find((i) => i.fileId === fileId);
    if (item) item.downloadCount += 1;
  } catch (e) {
    handleError(e, '下载失败，请稍后重试');
  } finally {
    activeId.value = '';
  }
}

async function downloadAll() {
  if (!canDownload.value) return;
  downloading.value = true;
  error.value = '';
  try {
    const { downloads } = await api.collectionDownloadAll(code, password.value || undefined);
    for (let i = 0; i < downloads.length; i++) {
      trigger(downloads[i]!.url);
      if (i < downloads.length - 1) await new Promise((r) => setTimeout(r, 500));
    }
    if (info.value) info.value.downloadCount += 1;
  } catch (e) {
    handleError(e, '下载失败，请稍后重试');
  } finally {
    downloading.value = false;
  }
}

async function copyLink() {
  if (await copyText(window.location.href)) {
    copied.value = true;
    setTimeout(() => (copied.value = false), 1500);
  }
}
</script>

<template>
  <div class="center-wrap">
    <div v-if="loading" class="card auth-card text-center">
      <p class="muted">加载中…</p>
    </div>

    <div v-else-if="notFound" class="card auth-card text-center">
      <div class="share-hero">
        <div class="file-icon">🚫</div>
        <div class="file-name">链接不存在</div>
        <p class="muted">该集合分享链接无效、已被删除，或从未存在。</p>
      </div>
    </div>

    <div v-else-if="info" class="card auth-card collection-card">
      <div class="share-hero">
        <div class="file-icon">{{ info.expired ? '⌛' : '📚' }}</div>
        <div class="file-name">{{ info.name }}</div>
        <p class="muted">{{ info.items.length }} 个文件 · {{ formatBytes(info.items.reduce((s, i) => s + i.size, 0)) }}</p>
      </div>

      <div v-if="error" class="alert error">{{ error }}</div>

      <div v-if="info.expired" class="alert info">该集合已超过有效期，无法下载。</div>

      <template v-else>
        <div v-if="info.requiresPassword" class="field">
          <label>提取码</label>
          <input v-model="password" type="text" placeholder="请输入提取码" @keyup.enter="downloadAll" />
        </div>

        <button class="btn btn-primary btn-block" :disabled="!canDownload || downloading" @click="downloadAll">
          {{ downloading ? '准备中…' : `全部下载（${info.items.length}）` }}
        </button>

        <div class="collection-items mt-lg">
          <div v-for="item in info.items" :key="item.fileId" class="collection-file">
            <div class="collection-file-main">
              <div class="fname" :title="item.name">{{ item.name }}</div>
              <div class="muted text-sm">{{ formatBytes(item.size) }}</div>
            </div>
            <button
              class="btn btn-sm"
              :disabled="!canDownload || activeId === item.fileId"
              @click="downloadItem(item.fileId)"
            >
              {{ activeId === item.fileId ? '准备中…' : '下载' }}
            </button>
          </div>
        </div>
      </template>

      <div class="meta-grid">
        <div class="meta-item"><div class="k">创建时间</div><div class="v">{{ formatDateTime(info.createdAt) }}</div></div>
        <div class="meta-item"><div class="k">有效期</div><div class="v">{{ describeExpiry(info.expiresAt) }}</div></div>
        <div class="meta-item"><div class="k">下载次数</div><div class="v">{{ info.downloadCount }}</div></div>
      </div>

      <button class="btn btn-ghost btn-block mt-md" @click="copyLink">{{ copied ? '已复制链接' : '复制本页链接' }}</button>
    </div>
  </div>
</template>
