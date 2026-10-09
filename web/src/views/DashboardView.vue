<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { api, type CollectionDTO, type CollectionFormPayload, type FileDTO, type Stats } from '../api';
import { startUpload, resumeUpload, type UploadOptions } from '../lib/upload';
import { formatBytes, formatDuration, describeExpiry } from '../utils/format';
import { copyText } from '../utils/clipboard';
import FileTable from '../components/FileTable.vue';
import EditModal from '../components/EditModal.vue';
import QrModal from '../components/QrModal.vue';
import CollectionModal from '../components/CollectionModal.vue';

interface Task {
  key: number;
  name: string;
  size: number;
  loaded: number;
  status: 'uploading' | 'done' | 'error';
  error: string;
  fileId?: string;
  speed: number;
  eta: number | null;
}

const SORTERS: Record<string, (a: FileDTO, b: FileDTO) => number> = {
  created_desc: (a, b) => b.createdAt - a.createdAt,
  created_asc: (a, b) => a.createdAt - b.createdAt,
  name_asc: (a, b) => a.name.localeCompare(b.name, 'zh'),
  name_desc: (a, b) => b.name.localeCompare(a.name, 'zh'),
  size_desc: (a, b) => b.size - a.size,
  size_asc: (a, b) => a.size - b.size,
  download_desc: (a, b) => b.downloadCount - a.downloadCount,
};

let seq = 0;
const tasks = reactive<Task[]>([]);
const ctrls = new Map<number, AbortController>();

const files = ref<FileDTO[]>([]);
const stats = ref<Stats | null>(null);
const collections = ref<CollectionDTO[]>([]);
const listLoading = ref(true);
const listError = ref('');
const r2Configured = ref<boolean | null>(null);
const updateBehind = ref(0);

const search = ref('');
const statusFilter = ref('all');
const sortKey = ref('created_desc');
const selection = ref<string[]>([]);

const expiryChoice = ref('never');
const uploadPassword = ref('');
const dragging = ref(false);

const editFile = ref<FileDTO | null>(null);
const qr = ref<{ title: string; url: string } | null>(null);
const collectionModal = ref<{ mode: 'create' | 'edit'; collection?: CollectionDTO } | null>(null);
const copiedCollection = ref('');

const uploadInput = ref<HTMLInputElement | null>(null);
const resumeInput = ref<HTMLInputElement | null>(null);
let resumeTargetId: string | null = null;

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase();
  let list = files.value;
  if (q) list = list.filter((f) => f.name.toLowerCase().includes(q));
  if (statusFilter.value !== 'all') list = list.filter((f) => f.status === statusFilter.value);
  return [...list].sort(SORTERS[sortKey.value] ?? SORTERS.created_desc!);
});

const selectedCount = computed(() => selection.value.length);
const selectedReadyIds = computed(() =>
  selection.value.filter((id) => files.value.find((f) => f.id === id)?.status === 'ready'),
);

function currentOptions(): UploadOptions {
  return {
    expiresInDays: expiryChoice.value === 'never' ? null : Number(expiryChoice.value),
    password: uploadPassword.value || null,
  };
}

function updateTask(key: number, patch: Partial<Task>) {
  const item = tasks.find((t) => t.key === key);
  if (item) Object.assign(item, patch);
}

function removeTask(key: number) {
  const i = tasks.findIndex((t) => t.key === key);
  if (i >= 0) tasks.splice(i, 1);
  ctrls.delete(key);
}

async function cancelTask(key: number) {
  ctrls.get(key)?.abort();
  const id = tasks.find((t) => t.key === key)?.fileId;
  if (id) {
    try {
      await api.abortUpload(id);
      refresh();
    } catch {
      /* leave it for the stale-upload cleanup job */
    }
  }
}

/** Progress handler that derives a smoothed transfer speed and an ETA. */
function track(key: number, size: number) {
  let lastLoaded = 0;
  let lastTs = Date.now();
  let speed = 0;
  return (loaded: number) => {
    const now = Date.now();
    const dt = (now - lastTs) / 1000;
    if (dt >= 0.5 && loaded >= lastLoaded) {
      const inst = (loaded - lastLoaded) / dt;
      if (Number.isFinite(inst) && inst > 0) speed = speed > 0 ? speed * 0.6 + inst * 0.4 : inst;
      lastLoaded = loaded;
      lastTs = now;
    }
    const capped = Math.min(loaded, size);
    const eta = speed > 0 && capped < size ? (size - capped) / speed : null;
    updateTask(key, { loaded: capped, speed, eta });
  };
}

function runStart(file: File) {
  const key = ++seq;
  const ctrl = new AbortController();
  ctrls.set(key, ctrl);
  tasks.push({ key, name: file.name, size: file.size, loaded: 0, status: 'uploading', error: '', speed: 0, eta: null });

  startUpload(file, currentOptions(), track(key, file.size), ctrl.signal, (id) => updateTask(key, { fileId: id }))
    .then(() => {
      updateTask(key, { status: 'done', loaded: file.size, eta: null });
      refresh();
      setTimeout(() => removeTask(key), 2500);
    })
    .catch(() => updateTask(key, { status: 'error', error: ctrl.signal.aborted ? '已取消' : '上传失败', eta: null }));
}

function runResume(id: string, name: string, file: File) {
  const key = ++seq;
  const ctrl = new AbortController();
  ctrls.set(key, ctrl);
  tasks.push({ key, name: `${name}（续传）`, size: file.size, loaded: 0, status: 'uploading', error: '', fileId: id, speed: 0, eta: null });

  resumeUpload(id, file, track(key, file.size), ctrl.signal)
    .then(() => {
      updateTask(key, { status: 'done', loaded: file.size, eta: null });
      refresh();
      setTimeout(() => removeTask(key), 2500);
    })
    .catch(() => updateTask(key, { status: 'error', error: ctrl.signal.aborted ? '已取消' : '续传失败', eta: null }));
}

async function refresh() {
  try {
    const [filesRes, statsRes, collectionsRes] = await Promise.all([api.listFiles(), api.stats(), api.listCollections()]);
    files.value = filesRes.files;
    stats.value = statsRes;
    collections.value = collectionsRes.collections;
    selection.value = selection.value.filter((id) => filesRes.files.some((f) => f.id === id));
    listError.value = '';
  } catch {
    listError.value = '加载数据失败';
  } finally {
    listLoading.value = false;
  }
}

async function checkR2() {
  try {
    r2Configured.value = (await api.getR2()).configured;
  } catch {
    /* ignore — banner stays hidden */
  }
}

async function checkUpdateNotice() {
  try {
    const s = await api.updateStatus();
    updateBehind.value = s.enabled && s.isGit && s.hasUpdate ? s.behind : 0;
  } catch {
    /* ignore — banner stays hidden */
  }
}

onMounted(() => {
  refresh();
  checkR2();
  checkUpdateNotice();
});

function onDrop(e: DragEvent) {
  dragging.value = false;
  const list = e.dataTransfer?.files;
  if (list) Array.from(list).forEach(runStart);
}

function onUploadInput(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files) Array.from(input.files).forEach(runStart);
  input.value = '';
}

function startResume(f: FileDTO) {
  resumeTargetId = f.id;
  resumeInput.value?.click();
}

function onResumeInput(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  const id = resumeTargetId;
  input.value = '';
  resumeTargetId = null;
  if (file && id) runResume(id, file.name, file);
}

function toggle(id: string) {
  selection.value = selection.value.includes(id) ? selection.value.filter((x) => x !== id) : [...selection.value, id];
}

function toggleAll(checked: boolean) {
  selection.value = checked ? filtered.value.map((f) => f.id) : [];
}

function clearSelection() {
  selection.value = [];
}

function triggerDownload(url: string) {
  const a = document.createElement('a');
  a.href = url;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
}

async function download(f: FileDTO) {
  try {
    const { url } = await api.downloadFile(f.id);
    triggerDownload(url);
    refresh();
  } catch {
    alert('下载失败，请稍后重试。');
  }
}

function openQr(f: FileDTO) {
  qr.value = { title: f.name, url: f.shareUrl };
}

async function saveEdit(payload: Parameters<typeof api.patchFile>[1]) {
  if (!editFile.value) return;
  try {
    await api.patchFile(editFile.value.id, payload);
    editFile.value = null;
    refresh();
  } catch {
    /* surface via list refresh; keep simple */
  }
}

async function remove(f: FileDTO) {
  if (!confirm(`确定删除「${f.name}」？将同时从 R2 移除该文件。`)) return;
  try {
    await api.deleteFile(f.id);
    refresh();
  } catch {
    alert('删除失败：无法从 R2 移除该文件，请稍后重试。');
  }
}

async function bulkRemove() {
  if (!selection.value.length) return;
  if (!confirm(`确定删除选中的 ${selection.value.length} 个文件？将从 R2 一并移除。`)) return;
  try {
    const res = await api.bulkDeleteFiles(selection.value);
    if (res.failed.length) alert(`已删除 ${res.deleted.length} 个，${res.failed.length} 个失败，请稍后重试。`);
    clearSelection();
    refresh();
  } catch {
    alert('批量删除失败，请稍后重试。');
  }
}

function openCreateCollection() {
  if (!selectedReadyIds.value.length) {
    alert('请先勾选可下载的文件。');
    return;
  }
  collectionModal.value = { mode: 'create' };
}

function openEditCollection(c: CollectionDTO) {
  collectionModal.value = { mode: 'edit', collection: c };
}

async function saveCollection(payload: CollectionFormPayload) {
  const modal = collectionModal.value;
  if (!modal) return;
  try {
    if (modal.mode === 'create') {
      await api.createCollection({
        name: payload.name,
        fileIds: selectedReadyIds.value,
        expiresInDays: payload.expiresInDays ?? null,
        password: payload.password ?? null,
      });
      clearSelection();
    } else if (modal.collection) {
      await api.updateCollection(modal.collection.id, payload);
    }
    collectionModal.value = null;
    refresh();
  } catch {
    alert('保存失败，请稍后重试。');
  }
}

async function removeCollection(c: CollectionDTO) {
  if (!confirm(`确定删除集合「${c.name}」？集合内的文件不会被删除。`)) return;
  try {
    await api.deleteCollection(c.id);
    refresh();
  } catch {
    alert('删除失败，请稍后重试。');
  }
}

async function copyCollection(c: CollectionDTO) {
  if (await copyText(c.shareUrl)) {
    copiedCollection.value = c.id;
    setTimeout(() => (copiedCollection.value = ''), 1500);
  } else {
    window.prompt('复制集合链接', c.shareUrl);
  }
}

function openCollectionQr(c: CollectionDTO) {
  qr.value = { title: c.name, url: c.shareUrl };
}

function pct(t: Task): number {
  return t.size ? Math.round((t.loaded / t.size) * 100) : 0;
}
</script>

<template>
  <div class="dashboard">
    <div v-if="r2Configured === false" class="alert info mb-md">
      ⚠️ R2 存储尚未配置，无法上传。请先在
      <RouterLink to="/settings">存储设置</RouterLink>
      中填写凭据并应用 CORS。
    </div>

    <div v-if="updateBehind > 0" class="alert info mb-md">
      🔄 发现新版本（落后 {{ updateBehind }} 个提交）。可前往
      <RouterLink to="/settings">设置 → 软件更新</RouterLink>
      一键更新。
    </div>

    <div class="stats-grid mb-lg">
      <div class="card">
        <div class="muted text-sm">可下载文件</div>
        <div class="stat-value">{{ stats?.readyCount ?? 0 }}</div>
      </div>
      <div class="card">
        <div class="muted text-sm">已占用空间</div>
        <div class="stat-value">{{ formatBytes(stats?.totalSize ?? 0) }}</div>
      </div>
      <div class="card">
        <div class="muted text-sm">文件总数</div>
        <div class="stat-value">{{ stats?.fileCount ?? 0 }}</div>
      </div>
      <div class="card">
        <div class="muted text-sm">累计下载</div>
        <div class="stat-value">{{ stats?.downloadTotal ?? 0 }}</div>
      </div>
    </div>

    <p v-if="stats" class="muted text-sm mb-lg">
      上传中 {{ stats.uploadingCount }}（{{ formatBytes(stats.uploadSize) }}）· 过期待清理
      {{ stats.expiredCount }}（{{ formatBytes(stats.expiredSize) }}）· 集合 {{ stats.collectionCount }} 个
    </p>

    <div class="card mb-lg">
      <p class="section-title">上传新文件</p>
      <div
        class="dropzone"
        :class="{ drag: dragging }"
        role="button"
        tabindex="0"
        aria-label="选择或拖拽文件上传"
        @click="uploadInput?.click()"
        @keydown.enter.prevent="uploadInput?.click()"
        @keydown.space.prevent="uploadInput?.click()"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="onDrop"
      >
        <div class="big">⬆️</div>
        <div>点击选择或拖拽文件到此处（支持多选 / 大文件分片直传）</div>
      </div>

      <div class="row mt-md">
        <div>
          <label>有效期</label>
          <select v-model="expiryChoice">
            <option value="never">永久</option>
            <option value="1">1 天后删除</option>
            <option value="7">7 天后删除</option>
            <option value="30">30 天后删除</option>
            <option value="90">90 天后删除</option>
          </select>
        </div>
        <div>
          <label>下载提取码（可选）</label>
          <input v-model="uploadPassword" type="text" placeholder="留空则不需要提取码" />
        </div>
      </div>

      <input ref="uploadInput" type="file" multiple hidden @change="onUploadInput" />
      <input ref="resumeInput" type="file" hidden @change="onResumeInput" />
    </div>

    <div v-if="tasks.length" class="card mb-lg">
      <p class="section-title">传输中的任务</p>
      <div v-for="t in tasks" :key="t.key" class="task">
        <div class="task-name">{{ t.name }}</div>
        <div class="task-progress">
          <div class="progress"><div class="progress-bar" :style="{ width: pct(t) + '%' }"></div></div>
        </div>
        <div class="task-status">
          <span v-if="t.status === 'uploading'">
            {{ pct(t) }}%<template v-if="t.speed > 0"> · {{ formatBytes(t.speed) }}/s · 剩余 {{ formatDuration(t.eta ?? 0) }}</template>
          </span>
          <span v-else-if="t.status === 'done'" class="text-success">完成</span>
          <span v-else class="text-danger">{{ t.error }}</span>
        </div>
        <button v-if="t.status === 'uploading'" class="btn btn-sm btn-ghost" @click="cancelTask(t.key)">取消</button>
        <button v-else class="btn btn-sm btn-ghost" @click="removeTask(t.key)">移除</button>
      </div>
    </div>

    <div class="card mb-lg">
      <div class="toolbar">
        <p class="section-title m-0">文件管理</p>
        <button class="btn btn-sm btn-ghost" @click="refresh">刷新</button>
      </div>

      <div class="filters mt-md">
        <input v-model="search" class="filter-search" type="search" placeholder="搜索文件名…" />
        <select v-model="statusFilter">
          <option value="all">全部状态</option>
          <option value="ready">可下载</option>
          <option value="uploading">上传中</option>
          <option value="expired">已过期</option>
        </select>
        <select v-model="sortKey">
          <option value="created_desc">最新上传</option>
          <option value="created_asc">最早上传</option>
          <option value="name_asc">名称 A→Z</option>
          <option value="name_desc">名称 Z→A</option>
          <option value="size_desc">大小从大到小</option>
          <option value="size_asc">大小从小到大</option>
          <option value="download_desc">下载最多</option>
        </select>
      </div>

      <div v-if="selectedCount" class="bulk-bar mt-md">
        <span>已选 {{ selectedCount }} 项</span>
        <div class="spacer"></div>
        <button class="btn btn-sm" :disabled="!selectedReadyIds.length" @click="openCreateCollection">打包分享</button>
        <button class="btn btn-sm btn-danger" @click="bulkRemove">批量删除</button>
        <button class="btn btn-sm btn-ghost" @click="clearSelection">取消选择</button>
      </div>

      <div v-if="listError" class="alert error">{{ listError }}</div>
      <div v-else-if="listLoading" class="empty">加载中…</div>
      <div v-else-if="!files.length" class="empty">还没有文件，拖一个进来吧。</div>
      <div v-else-if="!filtered.length" class="empty">没有匹配的文件。</div>
      <FileTable
        v-else
        :files="filtered"
        :selected="selection"
        @resume="startResume"
        @edit="(f) => (editFile = f)"
        @remove="remove"
        @download="download"
        @qr="openQr"
        @toggle="toggle"
        @toggle-all="toggleAll"
      />
    </div>

    <div class="card">
      <div class="toolbar">
        <p class="section-title m-0">打包分享集合</p>
        <button class="btn btn-sm btn-primary" :disabled="!selectedReadyIds.length" @click="openCreateCollection">
          用选中的 {{ selectedReadyIds.length }} 个文件创建
        </button>
      </div>

      <div v-if="!collections.length" class="empty">暂无集合。勾选可下载的文件后可打包成一个分享链接。</div>
      <div v-else class="collection-list mt-md">
        <div v-for="c in collections" :key="c.id" class="collection-item">
          <div class="collection-main">
            <div class="collection-name">{{ c.name }}</div>
            <div class="muted text-sm">
              {{ c.itemCount }} 个文件 · {{ formatBytes(c.totalSize) }} · {{ describeExpiry(c.expiresAt) }} · 下载 {{ c.downloadCount }}
            </div>
          </div>
          <div class="actions">
            <button class="btn btn-sm" @click="copyCollection(c)">{{ copiedCollection === c.id ? '已复制' : '复制链接' }}</button>
            <button class="btn btn-sm" @click="openCollectionQr(c)">二维码</button>
            <button class="btn btn-sm btn-ghost" @click="openEditCollection(c)">编辑</button>
            <button class="btn btn-sm btn-danger" @click="removeCollection(c)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <EditModal v-if="editFile" :file="editFile" @save="saveEdit" @close="editFile = null" />
    <QrModal v-if="qr" :title="qr.title" :url="qr.url" @close="qr = null" />
    <CollectionModal
      v-if="collectionModal"
      :mode="collectionModal.mode"
      :initial-name="collectionModal.collection?.name"
      :initial-has-password="collectionModal.collection?.hasPassword"
      :file-count="selectedReadyIds.length"
      @save="saveCollection"
      @close="collectionModal = null"
    />
  </div>
</template>
