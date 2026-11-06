<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { api, type TextDTO, type TextFormPayload } from '../api';
import { copyText } from '../utils/clipboard';
import { confirmAction, showCopyDialog, toast } from '../utils/feedback';
import { describeExpiry, formatDateTime } from '../utils/format';
import {
  TEXT_FORMATS,
  formatLabel,
  downloadTextAsFile,
  highlightCode,
  parseMarkdownBlocks,
} from '../utils/text-render';
import DriveIcon from '../components/DriveIcon.vue';
import TextModal from '../components/TextModal.vue';

const texts = ref<TextDTO[]>([]);
const loading = ref(true);
const error = ref('');
const search = ref('');
const formatFilter = ref('all');
const saving = ref(false);
const pasting = ref(false);
const editing = ref<TextDTO | null>(null);
const viewing = ref<TextDTO | null>(null);
const viewRaw = ref(false);

const form = reactive({
  title: '',
  content: '',
  format: 'plain',
  expiry: 'never',
  password: '',
  maxViews: 'unlimited',
});

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase();
  return texts.value.filter((text) => {
    if (formatFilter.value !== 'all' && (text.format || 'plain') !== formatFilter.value) {
      return false;
    }
    if (!q) return true;
    return text.title.toLowerCase().includes(q) || text.content.toLowerCase().includes(q);
  });
});

async function load() {
  loading.value = true;
  error.value = '';
  try {
    texts.value = (await api.listTexts()).texts;
  } catch {
    error.value = '加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

onMounted(load);

function resetForm() {
  form.title = '';
  form.content = '';
  form.format = 'plain';
  form.expiry = 'never';
  form.password = '';
  form.maxViews = 'unlimited';
}

async function save() {
  if (!form.content.trim()) {
    toast('请先输入要保存的文本', 'error');
    return;
  }
  saving.value = true;
  try {
    const { text } = await api.createText({
      title: form.title.trim() || undefined,
      content: form.content,
      format: form.format,
      expiresInDays: form.expiry === 'never' ? null : Number(form.expiry),
      password: form.password || undefined,
      maxViews: form.maxViews === 'unlimited' ? null : Number(form.maxViews),
    });
    texts.value.unshift(text);
    resetForm();
    toast('文本已保存');
  } catch {
    toast('保存失败，请稍后重试', 'error');
  } finally {
    saving.value = false;
  }
}

async function pasteFromClipboard() {
  pasting.value = true;
  try {
    if (!navigator.clipboard?.readText) throw new Error('unsupported');
    const text = await navigator.clipboard.readText();
    if (!text) {
      toast('剪贴板是空的', 'info');
      return;
    }
    form.content = text;
    toast('已从剪贴板粘贴');
  } catch {
    toast('无法读取剪贴板，请手动粘贴（Ctrl/⌘ + V）', 'error');
  } finally {
    pasting.value = false;
  }
}

async function copy(text: TextDTO) {
  if (await copyText(text.content)) toast(`已复制「${text.title}」`);
  else showCopyDialog(text.content, '复制文本');
}

async function copyLink(text: TextDTO) {
  if (await copyText(text.shareUrl)) toast('文本链接已复制');
  else showCopyDialog(text.shareUrl, '复制文本链接');
}

async function remove(text: TextDTO) {
  const ok = await confirmAction({
    title: '删除文本',
    message: `确定删除「${text.title}」？删除后分享链接也会失效。`,
    confirmText: '删除',
  });
  if (!ok) return;
  try {
    await api.deleteText(text.id);
    texts.value = texts.value.filter((item) => item.id !== text.id);
    toast('已删除');
  } catch {
    toast('删除失败，请稍后重试', 'error');
  }
}

async function saveEdit(payload: TextFormPayload) {
  if (!editing.value) return;
  try {
    const { text } = await api.updateText(editing.value.id, payload);
    const index = texts.value.findIndex((item) => item.id === text.id);
    if (index >= 0) texts.value[index] = text;
    editing.value = null;
    toast('已更新');
  } catch {
    toast('保存失败，请稍后重试', 'error');
  }
}

function handleKeydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
    event.preventDefault();
    save();
  }
}

function preview(content: string): string {
  const trimmed = content.trim();
  return trimmed.length > 400 ? trimmed.slice(0, 400) + '…' : trimmed;
}

function downloadText(text: TextDTO) {
  downloadTextAsFile(text.title, text.content, text.format);
}

async function copyCodeBlock(code: string) {
  if (await copyText(code)) toast('代码块已复制');
  else showCopyDialog(code, '复制代码');
}
</script>

<template>
  <div class="texts-page">
    <header class="shares-head">
      <div>
        <h1>文本中转</h1>
        <p class="muted text-sm">
          把一段文字、Markdown 笔记或代码片段暂存在这里，支持语法高亮、一键复制与文件导出。
        </p>
      </div>
      <span class="muted text-sm">{{ texts.length }} 条文本</span>
    </header>

    <section class="card text-composer">
      <div class="field">
        <label>内容</label>
        <textarea
          v-model="form.content"
          class="text-content-input"
          rows="6"
          maxlength="100000"
          placeholder="粘贴或输入要保存的文本、Markdown 或代码…（Ctrl/⌘ + Enter 保存）"
          @keydown="handleKeydown"
        ></textarea>
      </div>
      <div class="row">
        <div class="field" style="flex: 2">
          <label>标题</label>
          <input v-model="form.title" type="text" maxlength="120" placeholder="可选，留空则取第一行文字" />
        </div>
        <div class="field" style="flex: 1">
          <label>格式 / 语言</label>
          <select v-model="form.format">
            <option v-for="fmt in TEXT_FORMATS" :key="fmt.value" :value="fmt.value">
              {{ fmt.label }}
            </option>
          </select>
        </div>
      </div>
      <div class="composer-options">
        <div class="field">
          <label>有效期</label>
          <select v-model="form.expiry">
            <option value="never">永久</option>
            <option value="1">1 天</option>
            <option value="7">7 天</option>
            <option value="30">30 天</option>
            <option value="90">90 天</option>
          </select>
        </div>
        <div class="field">
          <label>提取码</label>
          <input v-model="form.password" type="text" placeholder="留空则不需要" />
        </div>
        <div class="field">
          <label>查看次数</label>
          <select v-model="form.maxViews">
            <option value="unlimited">不限</option>
            <option value="1">1 次（阅后即焚）</option>
            <option value="3">3 次</option>
            <option value="5">5 次</option>
            <option value="10">10 次</option>
            <option value="50">50 次</option>
          </select>
        </div>
        <div class="composer-actions">
          <button class="btn" :disabled="pasting" @click="pasteFromClipboard">
            <DriveIcon name="file" :size="16" />
            {{ pasting ? '读取中…' : '从剪贴板粘贴' }}
          </button>
          <button class="btn btn-primary" :disabled="saving || !form.content.trim()" @click="save">
            <DriveIcon name="plus" :size="16" />
            {{ saving ? '保存中…' : '保存文本' }}
          </button>
        </div>
      </div>
    </section>

    <div class="toolbar mt-lg">
      <div class="search-field">
        <DriveIcon name="search" :size="16" />
        <input v-model="search" type="search" placeholder="搜索标题或内容" />
      </div>
      <select v-model="formatFilter" style="width: auto; min-width: 130px">
        <option value="all">全部格式</option>
        <option v-for="fmt in TEXT_FORMATS" :key="fmt.value" :value="fmt.value">
          {{ fmt.label }}
        </option>
      </select>
    </div>

    <div v-if="error" class="alert error">{{ error }}</div>

    <div v-if="loading" class="card text-loading">
      <div class="loading-spinner" role="status"><span class="sr-only">加载中…</span></div>
    </div>

    <div v-else-if="!filtered.length" class="empty-drive">
      <DriveIcon name="file" :size="48" />
      <p>{{ texts.length ? '没有匹配的文本' : '还没有保存任何文本' }}</p>
      <p class="muted text-sm">
        {{ texts.length ? '换个关键词试试。' : '在上方粘贴一段文字，保存后即可随时复制。' }}
      </p>
    </div>

    <div v-else class="text-grid">
      <article v-for="text in filtered" :key="text.id" class="text-card">
        <header class="text-card-head">
          <h3 class="text-card-title" :title="text.title">{{ text.title }}</h3>
          <div style="display: flex; align-items: center; gap: 6px; flex-shrink: 0">
            <span class="format-badge">{{ formatLabel(text.format) }}</span>
            <span class="text-card-chars">{{ text.content.length }} 字</span>
          </div>
        </header>
        <pre
          class="text-card-preview"
          style="cursor: pointer"
          title="点击打开渲染预览"
          @click="viewing = text; viewRaw = false"
          v-html="highlightCode(preview(text.content), text.format)"
        ></pre>
        <div class="text-card-meta muted text-sm">
          <span>{{ formatDateTime(text.updatedAt) }}</span>
          <span>{{ describeExpiry(text.expiresAt) }}</span>
          <span v-if="text.hasPassword">需提取码</span>
          <span v-if="text.maxViews">
            {{ text.maxViews === 1 ? '阅后即焚' : `限看 ${text.maxViews} 次` }}（{{ text.viewCount }}/{{ text.maxViews }}）
          </span>
          <span v-else-if="text.viewCount">查看 {{ text.viewCount }}</span>
        </div>
        <div class="text-card-actions">
          <button class="btn btn-sm btn-primary" @click="copy(text)">复制</button>
          <button class="btn btn-sm" @click="copyLink(text)">复制链接</button>
          <button class="btn btn-sm" @click="viewing = text; viewRaw = false">预览</button>
          <button class="btn btn-sm" @click="downloadText(text)">下载</button>
          <button class="btn btn-sm btn-ghost" @click="editing = text">编辑</button>
          <button class="btn btn-sm btn-danger" @click="remove(text)">删除</button>
        </div>
      </article>
    </div>

    <TextModal v-if="editing" :text="editing" @save="saveEdit" @close="editing = null" />

    <div v-if="viewing" class="modal-mask" @click.self="viewing = null">
      <div class="card modal text-modal" role="dialog" aria-modal="true" :aria-label="viewing.title">
        <div class="row" style="justify-content: space-between; align-items: center; margin-bottom: 12px">
          <div style="display: flex; align-items: center; gap: 8px; min-width: 0">
            <h2 style="margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
              {{ viewing.title }}
            </h2>
            <span class="format-badge">{{ formatLabel(viewing.format) }}</span>
          </div>
          <div style="display: flex; gap: 8px; flex-shrink: 0">
            <button
              v-if="viewing.format === 'markdown'"
              class="btn btn-sm"
              @click="viewRaw = !viewRaw"
            >
              {{ viewRaw ? '渲染视图' : '源码视图' }}
            </button>
            <button class="btn btn-sm" @click="downloadText(viewing)">下载文件</button>
            <button class="btn btn-sm btn-primary" @click="copy(viewing)">复制全文</button>
          </div>
        </div>

        <div v-if="viewing.format === 'markdown' && !viewRaw" class="markdown-body text-share-content">
          <template v-for="(block, idx) in parseMarkdownBlocks(viewing.content)" :key="idx">
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
          v-html="highlightCode(viewing.content, viewing.format)"
        ></pre>

        <div class="row mt-lg" style="justify-content: flex-end">
          <button class="btn btn-ghost" @click="viewing = null">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>
