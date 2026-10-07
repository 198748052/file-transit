<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { api, type FileDTO } from '../api';
import { startUpload, resumeUpload, type UploadOptions } from '../lib/upload';
import { formatBytes } from '../utils/format';
import FileTable from '../components/FileTable.vue';
import EditModal from '../components/EditModal.vue';

interface Task {
  key: number;
  name: string;
  size: number;
  loaded: number;
  status: 'uploading' | 'done' | 'error';
  error: string;
}

let seq = 0;
const tasks = reactive<Task[]>([]);
const ctrls = new Map<number, AbortController>();

const files = ref<FileDTO[]>([]);
const listLoading = ref(true);
const listError = ref('');
const r2Configured = ref<boolean | null>(null);
const updateBehind = ref(0);

const expiryChoice = ref('never');
const uploadPassword = ref('');
const dragging = ref(false);

const editFile = ref<FileDTO | null>(null);
const uploadInput = ref<HTMLInputElement | null>(null);
const resumeInput = ref<HTMLInputElement | null>(null);
let resumeTargetId: string | null = null;

const totalSize = computed(() => files.value.reduce((s, f) => s + f.size, 0));
const readyCount = computed(() => files.value.filter((f) => f.status === 'ready').length);

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

function cancelTask(key: number) {
  ctrls.get(key)?.abort();
}

function track(key: number, size: number) {
  return (loaded: number) => updateTask(key, { loaded: Math.min(loaded, size) });
}

function runStart(file: File) {
  const key = ++seq;
  const ctrl = new AbortController();
  ctrls.set(key, ctrl);
  tasks.push({ key, name: file.name, size: file.size, loaded: 0, status: 'uploading', error: '' });

  startUpload(file, currentOptions(), track(key, file.size), ctrl.signal)
    .then(() => {
      updateTask(key, { status: 'done', loaded: file.size });
      refresh();
      setTimeout(() => removeTask(key), 2500);
    })
    .catch(() => updateTask(key, { status: 'error', error: ctrl.signal.aborted ? '已取消' : '上传失败' }));
}

function runResume(id: string, name: string, file: File) {
  const key = ++seq;
  const ctrl = new AbortController();
  ctrls.set(key, ctrl);
  tasks.push({ key, name: `${name}（续传）`, size: file.size, loaded: 0, status: 'uploading', error: '' });

  resumeUpload(id, file, track(key, file.size), ctrl.signal)
    .then(() => {
      updateTask(key, { status: 'done', loaded: file.size });
      refresh();
      setTimeout(() => removeTask(key), 2500);
    })
    .catch(() => updateTask(key, { status: 'error', error: ctrl.signal.aborted ? '已取消' : '续传失败' }));
}

async function refresh() {
  try {
    const res = await api.listFiles();
    files.value = res.files;
    listError.value = '';
  } catch {
    listError.value = '加载文件列表失败';
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
  await api.deleteFile(f.id);
  refresh();
}

function pct(t: Task): number {
  return t.size ? Math.round((t.loaded / t.size) * 100) : 0;
}
</script>

<template>
  <div class="dashboard">
    <div v-if="r2Configured === false" class="alert info" style="margin-bottom: 18px">
      ⚠️ R2 存储尚未配置，无法上传。请先在
      <RouterLink to="/settings">存储设置</RouterLink>
      中填写凭据并应用 CORS。
    </div>

    <div v-if="updateBehind > 0" class="alert info" style="margin-bottom: 18px">
      🔄 发现新版本（落后 {{ updateBehind }} 个提交）。可前往
      <RouterLink to="/settings">设置 → 软件更新</RouterLink>
      一键更新。
    </div>

    <div class="stats row" style="margin-bottom: 20px">
      <div class="card" style="flex: 1">
        <div class="muted" style="font-size: 13px">可下载文件</div>
        <div style="font-size: 24px; font-weight: 700">{{ readyCount }}</div>
      </div>
      <div class="card" style="flex: 1">
        <div class="muted" style="font-size: 13px">已占用空间</div>
        <div style="font-size: 24px; font-weight: 700">{{ formatBytes(totalSize) }}</div>
      </div>
    </div>

    <div class="card" style="margin-bottom: 20px">
      <p class="section-title">上传新文件</p>
      <div
        class="dropzone"
        :class="{ drag: dragging }"
        @click="uploadInput?.click()"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="onDrop"
      >
        <div class="big">⬆️</div>
        <div>点击选择或拖拽文件到此处（支持多选 / 大文件分片直传）</div>
      </div>

      <div class="row" style="margin-top: 16px">
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

    <div v-if="tasks.length" class="card" style="margin-bottom: 20px">
      <p class="section-title">传输中的任务</p>
      <div v-for="t in tasks" :key="t.key" class="task">
        <div class="task-name">{{ t.name }}</div>
        <div class="task-progress">
          <div class="progress"><div class="progress-bar" :style="{ width: pct(t) + '%' }"></div></div>
        </div>
        <div style="width: 60px; text-align: right; font-size: 13px">
          <span v-if="t.status === 'uploading'">{{ pct(t) }}%</span>
          <span v-else-if="t.status === 'done'" style="color: var(--success)">完成</span>
          <span v-else style="color: var(--danger)">{{ t.error }}</span>
        </div>
        <button v-if="t.status === 'uploading'" class="btn btn-sm btn-ghost" @click="cancelTask(t.key)">取消</button>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <p class="section-title" style="margin: 0">文件管理</p>
        <button class="btn btn-sm btn-ghost" @click="refresh">刷新</button>
      </div>

      <div v-if="listError" class="alert error">{{ listError }}</div>
      <div v-else-if="listLoading" class="empty">加载中…</div>
      <div v-else-if="!files.length" class="empty">还没有文件，拖一个进来吧。</div>
      <FileTable v-else :files="files" @resume="startResume" @edit="(f) => (editFile = f)" @remove="remove" />
    </div>

    <EditModal v-if="editFile" :file="editFile" @save="saveEdit" @close="editFile = null" />
  </div>
</template>
