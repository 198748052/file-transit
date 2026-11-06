<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { api, ApiError, type TextShareInfo } from '../api';
import { copyText } from '../utils/clipboard';
import { showCopyDialog, toast } from '../utils/feedback';
import { describeExpiry, formatDateTime } from '../utils/format';
import {
  formatLabel,
  downloadTextAsFile,
  highlightCode,
  parseMarkdownBlocks,
} from '../utils/text-render';
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
const viewRaw = ref(false);

const exhausted = computed(
  () =>
    !!info.value &&
    !content.value &&
    (!!info.value.exhausted || (info.value.maxViews !== null && info.value.viewCount >= info.value.maxViews)),
);
const ready = computed(() => !!info.value && !info.value.expired && !exhausted.value);
const canReveal = computed(() => ready.value && (!info.value!.requiresPassword || password.value.length > 0));

async function load() {
  loading.value = true;
  error.value = '';
  notFound.value = false;
  try {
    info.value = await api.textShareInfo(code);
    if (
      info.value.found &&
      !info.value.expired &&
      !info.value.exhausted &&
      !info.value.requiresPassword &&
      !info.value.maxViews
    ) {
      await reveal();
    }
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
    if (typeof result.viewCount === 'number') info.value.viewCount = result.viewCount;
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.code === 'wrong_password') error.value = '提取码错误';
      else if (e.code === 'password_required') error.value = '请输入提取码';
      else if (e.code === 'too_many_attempts' || e.code === 'too_many_requests') {
        error.value = '尝试过于频繁，请稍后再试';
      } else if (e.code === 'expired') {
        error.value = '文本已过期';
        load();
      } else if (e.code === 'download_limit_reached') {
        error.value = '该文本已达到查看次数上限';
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

async function copyCodeBlock(blockCode: string) {
  if (await copyText(blockCode)) toast('代码块已复制');
  else showCopyDialog(blockCode, '复制代码');
}

function downloadFile() {
  if (!content.value || !info.value) return;
  downloadTextAsFile(info.value.title, content.value, info.value.format);
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
          <span class="format-badge" style="margin-right: 6px">{{ formatLabel(info.format) }}</span>
          {{ info.charCount }} 字 · {{ formatDateTime(info.updatedAt) }} · {{ describeExpiry(info.expiresAt) }}
        </p>
      </div>

      <div v-if="error" class="alert error">{{ error }}</div>
      <div v-if="info.expired" class="alert info">该文本已超过有效期，无法查看。</div>
      <div v-else-if="exhausted" class="alert info">
        {{ info.maxViews === 1 ? '该文本为阅后即焚，已被查看并销毁。' : `该文本已达到查看次数上限（${info.viewCount} / ${info.maxViews}）。` }}
      </div>

      <template v-else>
        <div v-if="info.maxViews && !content" class="alert info">
          {{ info.maxViews === 1 ? '此文本开启了阅后即焚，点击查看后链接立即失效。' : `此文本限制查看 ${info.maxViews} 次（剩余 ${Math.max(0, info.maxViews - info.viewCount)} 次）。` }}
        </div>
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
            {{ revealing ? '读取中…' : info.maxViews === 1 ? '查看文本（阅后即焚）' : '查看文本' }}
          </button>
        </div>

        <template v-else>
          <div v-if="info.maxViews === 1" class="alert info">
            此文本为阅后即焚，关闭或刷新页面后将无法再次打开，请及时复制。
          </div>

          <div
            v-if="info.format === 'markdown'"
            style="display: flex; justify-content: flex-end; margin-bottom: 8px"
          >
            <button class="btn btn-sm" @click="viewRaw = !viewRaw">
              {{ viewRaw ? '切换为渲染视图' : '切换为源码视图' }}
            </button>
          </div>

          <div v-if="info.format === 'markdown' && !viewRaw" class="markdown-body text-share-content">
            <template v-for="(block, idx) in parseMarkdownBlocks(content)" :key="idx">
              <div v-if="block.type === 'html'" v-html="block.html"></div>
              <div v-else class="md-code-wrap">
                <div class="md-code-head">
                  <span>{{ block.lang || 'code' }}</span>
                  <button class="btn btn-sm btn-ghost" @click="copyCodeBlock(block.code || '')">
                    复制代码
                  </button>
                </div>
                <pre class="md-code-pre"><code v-html="highlightCode(block.code || '', block.lang)"></code></pre>
              </div>
            </template>
          </div>
          <pre
            v-else
            class="text-share-content"
            v-html="highlightCode(content, info.format)"
          ></pre>

          <div class="row mt-md">
            <button class="btn btn-primary" style="flex: 2" @click="copyAll">
              <DriveIcon name="file" :size="16" />
              复制全文
            </button>
            <button class="btn" style="flex: 1" @click="downloadFile">
              <DriveIcon name="download" :size="16" />
              下载文件
            </button>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>
