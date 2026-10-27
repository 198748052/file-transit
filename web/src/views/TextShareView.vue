<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { api, ApiError, type TextShareInfo } from '../api';
import { copyText } from '../utils/clipboard';
import { showCopyDialog, toast } from '../utils/feedback';
import { describeExpiry, formatDateTime } from '../utils/format';
import DriveIcon from '../components/DriveIcon.vue';

const route = useRoute();
const code = String(route.params.code ?? '');

const loading = ref(true);
const info = ref<TextShareInfo | null>(null);
const notFound = ref(false);
const error = ref('');
const password = ref('');
const content = ref('');
const revealing = ref(false);

const ready = computed(() => !!info.value && !info.value.expired);
const canReveal = computed(() => ready.value && (!info.value!.requiresPassword || password.value.length > 0));

async function load() {
  loading.value = true;
  error.value = '';
  notFound.value = false;
  try {
    info.value = await api.textShareInfo(code);
    if (info.value.found && !info.value.expired && !info.value.requiresPassword) await reveal();
  } catch (e) {
    if (e instanceof ApiError && e.code === 'not_found') notFound.value = true;
    else error.value = '加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

onMounted(load);

async function reveal() {
  if (!info.value || !canReveal.value) return;
  revealing.value = true;
  error.value = '';
  try {
    const result = await api.textShareReveal(code, password.value || undefined);
    content.value = result.content;
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.code === 'wrong_password') error.value = '提取码错误';
      else if (e.code === 'password_required') error.value = '请输入提取码';
      else if (e.code === 'too_many_attempts' || e.code === 'too_many_requests') {
        error.value = '尝试过于频繁，请稍后再试';
      } else if (e.code === 'expired') {
        error.value = '文本已过期';
        load();
      } else error.value = '加载失败，请稍后重试';
    } else error.value = '加载失败，请稍后重试';
  } finally {
    revealing.value = false;
  }
}

async function copyAll() {
  if (!content.value) return;
  if (await copyText(content.value)) toast('全文已复制');
  else showCopyDialog(content.value, '复制文本');
}
</script>

<template>
  <div class="center-wrap">
    <div v-if="loading" class="card auth-card text-center">
      <div class="loading-spinner" role="status"><span class="sr-only">加载中…</span></div>
    </div>

    <div v-else-if="notFound" class="card auth-card text-center">
      <div class="share-hero">
        <div class="hero-icon danger"><DriveIcon name="close" :size="30" /></div>
        <div class="file-name">链接不存在</div>
        <p class="muted">该文本链接无效、已被删除，或从未存在。</p>
      </div>
    </div>

    <div v-else-if="info" class="card auth-card text-share-card">
      <div class="share-hero">
        <div class="hero-icon" :class="{ expired: info.expired }">
          <DriveIcon :name="info.expired ? 'clock' : 'file'" :size="32" />
        </div>
        <div class="file-name">{{ info.title }}</div>
        <p class="muted">
          {{ info.charCount }} 字 · {{ formatDateTime(info.updatedAt) }} · {{ describeExpiry(info.expiresAt) }}
        </p>
      </div>

      <div v-if="error" class="alert error">{{ error }}</div>
      <div v-if="info.expired" class="alert info">该文本已超过有效期，无法查看。</div>

      <template v-else>
        <div v-if="info.requiresPassword && !content" class="field">
          <label>提取码</label>
          <input
            v-model="password"
            type="text"
            placeholder="请输入提取码"
            @keyup.enter="reveal"
          />
        </div>

        <div v-if="!content" class="row">
          <button class="btn btn-primary btn-block" :disabled="revealing || !canReveal" @click="reveal">
            {{ revealing ? '读取中…' : '查看文本' }}
          </button>
        </div>

        <template v-else>
          <pre class="text-share-content">{{ content }}</pre>
          <button class="btn btn-primary btn-block mt-md" @click="copyAll">
            <DriveIcon name="file" :size="16" />
            复制全文
          </button>
        </template>
      </template>
    </div>
  </div>
</template>
