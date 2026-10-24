<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { api, ApiError, type PreviewInfo, type ShareInfo } from '../api';
import { formatBytes, formatDateTime, describeExpiry } from '../utils/format';
import DriveIcon from '../components/DriveIcon.vue';
import PreviewModal from '../components/PreviewModal.vue';
import { canPreview } from '../utils/preview';

const route = useRoute();
const code = String(route.params.code ?? '');

const loading = ref(true);
const info = ref<ShareInfo | null>(null);
const notFound = ref(false);
const password = ref('');
const error = ref('');
const downloading = ref(false);
const previewing = ref(false);
const preview = ref<PreviewInfo | null>(null);

async function load() {
  loading.value = true;
  error.value = '';
  notFound.value = false;
  try {
    info.value = await api.shareInfo(code);
  } catch (e) {
    if (e instanceof ApiError && e.code === 'not_found') notFound.value = true;
    else error.value = '加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

onMounted(load);

const ready = computed(() => !!info.value && !info.value.expired && info.value.status === 'ready');
const canDownload = computed(() => ready.value && (!info.value!.requiresPassword || password.value.length > 0));

async function openPreview() {
  if (!info.value || !canDownload.value) return;
  previewing.value = true;
  error.value = '';
  try {
    preview.value = await api.sharePreview(code, password.value || undefined);
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.code === 'wrong_password') error.value = '提取码错误';
      else if (e.code === 'password_required') error.value = '请输入提取码';
      else if (e.code === 'too_many_attempts' || e.code === 'too_many_requests') {
        error.value = '尝试过于频繁，请稍后再试';
      } else if (e.code === 'expired') {
        error.value = '文件已过期';
        load();
      } else error.value = '预览失败，请稍后重试';
    } else error.value = '预览失败，请稍后重试';
  } finally {
    previewing.value = false;
  }
}

async function download() {
  if (!info.value || !canDownload.value) return;
  downloading.value = true;
  error.value = '';
  try {
    const { url } = await api.shareDownload(code, password.value || undefined);
    const a = document.createElement('a');
    a.href = url;
    a.rel = 'noopener';
    document.body.appendChild(a);
    a.click();
    a.remove();
    info.value.downloadCount += 1;
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.code === 'wrong_password') error.value = '提取码错误';
      else if (e.code === 'password_required') error.value = '请输入提取码';
      else if (e.code === 'expired') {
        error.value = '文件已过期';
        load();
      } else error.value = '下载失败，请稍后重试';
    } else error.value = '下载失败，请稍后重试';
  } finally {
    downloading.value = false;
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
        <div class="hero-icon danger"><DriveIcon name="close" :size="30" /></div>
        <div class="file-name">链接不存在</div>
        <p class="muted">该分享链接无效、已被删除，或从未存在。</p>
      </div>
    </div>

    <div v-else-if="info" class="card auth-card">
      <div class="share-hero">
        <div class="hero-icon" :class="info.expired ? 'expired' : 'file'">
          <DriveIcon :name="info.expired ? 'clock' : 'file'" :size="32" />
        </div>
        <div class="file-name">{{ info.name }}</div>
        <p class="muted">{{ formatBytes(info.size) }}</p>
      </div>

      <div v-if="error" class="alert error">{{ error }}</div>

      <div v-if="info.expired" class="alert info">该文件已超过有效期，无法下载。</div>

      <template v-else>
        <div class="field" v-if="info.requiresPassword">
          <label>提取码</label>
          <input v-model="password" type="text" placeholder="请输入提取码" @keyup.enter="download" />
        </div>
        <div v-if="canPreview(info.name, info.mimeType)" class="row mb-sm">
          <button class="btn btn-block" :disabled="!canDownload || previewing" @click="openPreview">
            {{ previewing ? '准备中…' : '在线预览' }}
          </button>
        </div>
        <button class="btn btn-primary btn-block" :disabled="!canDownload || downloading" @click="download">
          {{ downloading ? '准备中…' : '下载文件' }}
        </button>
      </template>

      <div class="meta-grid">
        <div class="meta-item"><div class="k">上传时间</div><div class="v">{{ formatDateTime(info.createdAt) }}</div></div>
        <div class="meta-item"><div class="k">有效期</div><div class="v">{{ describeExpiry(info.expiresAt) }}</div></div>
        <div class="meta-item"><div class="k">下载次数</div><div class="v">{{ info.downloadCount }}</div></div>
      </div>
    </div>

    <PreviewModal
      v-if="preview"
      :name="preview.name"
      :url="preview.url"
      :size="preview.size"
      :mimeType="preview.mimeType"
      @close="preview = null"
    />
  </div>
</template>
