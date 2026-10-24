<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  api,
  type CollectionDTO,
  type CollectionFormPayload,
  type FileDTO,
  type FolderDTO,
  type FolderShareDTO,
  type FolderShareFormPayload,
  type PreviewInfo,
  type R2UsageStatus,
  type Stats,
} from '../api';
import { startUpload, resumeUpload, type UploadOptions } from '../lib/upload';
import { formatBytes, formatDuration, describeExpiry } from '../utils/format';
import { fileKind } from '../utils/filetype';
import { canPreview } from '../utils/preview';
import { copyText } from '../utils/clipboard';
import FileTable from '../components/FileTable.vue';
import EditModal from '../components/EditModal.vue';
import QrModal from '../components/QrModal.vue';
import PreviewModal from '../components/PreviewModal.vue';
import CollectionModal from '../components/CollectionModal.vue';
import FolderShareModal from '../components/FolderShareModal.vue';
import FolderModal from '../components/FolderModal.vue';
import MoveModal from '../components/MoveModal.vue';
import FolderTree, { type FolderNode } from '../components/FolderTree.vue';
import DriveIcon from '../components/DriveIcon.vue';

interface Task {
  key: number;
  name: string;
  size: number;
  loaded: number;
  status: 'hashing' | 'uploading' | 'done' | 'error';
  error: string;
  fileId?: string;
  duplicate?: boolean;
  speed: number;
  eta: number | null;
}

type MenuState =
  | { kind: 'file'; file: FileDTO; x: number; y: number }
  | { kind: 'folder'; folder: FolderDTO; x: number; y: number };

const SORTERS: Record<string, (a: FileDTO, b: FileDTO) => number> = {
  created_desc: (a, b) => b.createdAt - a.createdAt,
  created_asc: (a, b) => a.createdAt - b.createdAt,
  name_asc: (a, b) => a.name.localeCompare(b.name, 'zh'),
  name_desc: (a, b) => b.name.localeCompare(a.name, 'zh'),
  size_desc: (a, b) => b.size - a.size,
  size_asc: (a, b) => a.size - b.size,
  download_desc: (a, b) => b.downloadCount - a.downloadCount,
};

const route = useRoute();
const router = useRouter();

let seq = 0;
const tasks = reactive<Task[]>([]);
const ctrls = new Map<number, AbortController>();

const files = ref<FileDTO[]>([]);
const folders = ref<FolderDTO[]>([]);
const stats = ref<Stats | null>(null);
const collections = ref<CollectionDTO[]>([]);
const folderShares = ref<FolderShareDTO[]>([]);
const listLoading = ref(true);
const listError = ref('');
const r2Configured = ref<boolean | null>(null);
const usageStatus = ref<R2UsageStatus | null>(null);
const updateBehind = ref(0);

const accountUsage = computed(() => usageStatus.value?.usage ?? null);
const usagePct = computed(() => {
  const metrics = accountUsage.value;
  const limit = usageStatus.value?.freeTierBytes ?? 0;
  if (!metrics || !limit) return 0;
  return Math.min(100, (metrics.standardBytes / limit) * 100);
});

const view = ref<'files' | 'shares'>('files');
const sharesTab = ref<'collections' | 'folders'>('collections');
const currentFolderId = ref<string | null>(null);
const search = ref('');
const statusFilter = ref('all');
const sortKey = ref('created_desc');
const selection = ref<string[]>([]);
const viewMode = ref<'grid' | 'list'>(
  localStorage.getItem('ft_view') === 'list' ? 'list' : 'grid',
);
const treeOpen = ref(!window.matchMedia('(max-width: 960px)').matches);

const expiryChoice = ref('never');
const uploadPassword = ref('');
const dragging = ref(false);
const dropTarget = ref<string | null>(null);

const editFile = ref<FileDTO | null>(null);
const qr = ref<{ title: string; url: string } | null>(null);
const preview = ref<PreviewInfo | null>(null);
const collectionModal = ref<{ mode: 'create' | 'edit'; collection?: CollectionDTO } | null>(null);
const folderShareModal = ref<{ mode: 'create' | 'edit'; folder?: FolderDTO; share?: FolderShareDTO } | null>(null);
const folderModal = ref<{ mode: 'create' | 'rename'; folder?: FolderDTO } | null>(null);
const moveDialog = ref<{
  kind: 'file' | 'folder';
  ids: string[];
  label: string;
  currentId: string | null;
  excludeIds: string[];
} | null>(null);
const menu = ref<MenuState | null>(null);
const toast = ref('');
let toastTimer: number | undefined;

const uploadInput = ref<HTMLInputElement | null>(null);
const folderInput = ref<HTMLInputElement | null>(null);
const resumeInput = ref<HTMLInputElement | null>(null);
let resumeTargetId: string | null = null;

// ───────────────────────── folder model ─────────────────────────

function buildTree(list: FolderDTO[]): FolderNode[] {
  const map = new Map<string, FolderNode>();
  list.forEach((folder) => map.set(folder.id, { ...folder, children: [] }));
  const roots: FolderNode[] = [];
  list.forEach((folder) => {
    const node = map.get(folder.id)!;
    const parent = folder.parentId ? map.get(folder.parentId) : undefined;
    if (parent) parent.children.push(node);
    else roots.push(node);
  });
  const sortTree = (nodes: FolderNode[]) => {
    nodes.sort((a, b) => a.name.localeCompare(b.name, 'zh'));
    nodes.forEach((node) => sortTree(node.children));
  };
  sortTree(roots);
  return roots;
}

const folderTree = computed(() => buildTree(folders.value));
const folderById = computed(() => new Map(folders.value.map((folder) => [folder.id, folder])));
const currentFolder = computed(() =>
  currentFolderId.value ? folderById.value.get(currentFolderId.value) ?? null : null,
);

function childFolders(parentId: string | null): FolderDTO[] {
  return folders.value
    .filter((folder) => folder.parentId === parentId)
    .sort((a, b) => a.name.localeCompare(b.name, 'zh'));
}

const searchTerm = computed(() => search.value.trim().toLowerCase());
const searching = computed(() => searchTerm.value.length > 0);

const visibleFolders = computed(() => {
  if (searching.value) {
    return folders.value
      .filter((folder) => folder.name.toLowerCase().includes(searchTerm.value))
      .sort((a, b) => a.name.localeCompare(b.name, 'zh'));
  }
  return childFolders(currentFolderId.value);
});

const visibleFiles = computed(() => {
  let list = searching.value
    ? files.value.filter((file) => file.name.toLowerCase().includes(searchTerm.value))
    : files.value.filter((file) => file.folderId === currentFolderId.value);
  if (statusFilter.value !== 'all') list = list.filter((file) => file.status === statusFilter.value);
  return [...list].sort(SORTERS[sortKey.value] ?? SORTERS.created_desc!);
});

const breadcrumbs = computed(() => {
  const out: { id: string | null; name: string }[] = [];
  const guard = new Set<string>();
  let cursor = currentFolderId.value;
  while (cursor && !guard.has(cursor)) {
    guard.add(cursor);
    const folder = folderById.value.get(cursor);
    if (!folder) break;
    out.unshift({ id: folder.id, name: folder.name });
    cursor = folder.parentId;
  }
  out.unshift({ id: null, name: '全部文件' });
  return out;
});

function folderPath(id: string | null): string {
  const names: string[] = [];
  const guard = new Set<string>();
  let cursor = id;
  while (cursor && !guard.has(cursor)) {
    guard.add(cursor);
    const folder = folderById.value.get(cursor);
    if (!folder) break;
    names.unshift(folder.name);
    cursor = folder.parentId;
  }
  return names.length ? names.join(' / ') : '全部文件';
}

function subtreeIds(id: string): string[] {
  const byParent = new Map<string | null, string[]>();
  folders.value.forEach((folder) => {
    const list = byParent.get(folder.parentId) ?? [];
    list.push(folder.id);
    byParent.set(folder.parentId, list);
  });
  const out: string[] = [];
  const stack = [id];
  while (stack.length) {
    const current = stack.pop()!;
    out.push(current);
    stack.push(...(byParent.get(current) ?? []));
  }
  return out;
}

function subtreeFileCount(id: string): number {
  const ids = new Set(subtreeIds(id));
  return files.value.filter((file) => file.folderId && ids.has(file.folderId)).length;
}

function subtreeReadyCount(id: string): number {
  const ids = new Set(subtreeIds(id));
  return files.value.filter((file) => file.folderId && ids.has(file.folderId) && file.status === 'ready').length;
}

const selectedFiles = computed(() => files.value.filter((file) => selection.value.includes(file.id)));
const selectedReadyIds = computed(() =>
  selectedFiles.value.filter((file) => file.status === 'ready').map((file) => file.id),
);
const allChecked = computed(
  () => visibleFiles.value.length > 0 && visibleFiles.value.every((file) => selection.value.includes(file.id)),
);

const totalItems = computed(() => visibleFolders.value.length + visibleFiles.value.length);

const statusText: Record<string, string> = {
  uploading: '上传中',
  ready: '可下载',
  expired: '已过期',
};

// ───────────────────────── notifications ─────────────────────────

function flash(message: string) {
  toast.value = message;
  if (toastTimer) window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => (toast.value = ''), 2200);
}

// ───────────────────────── navigation ─────────────────────────

function applyFolder(id: string | null) {
  currentFolderId.value = id;
  selection.value = [];
  menu.value = null;
  view.value = 'files';
  search.value = '';
}

function openFolder(id: string | null) {
  if (id === currentFolderId.value) {
    menu.value = null;
    return;
  }
  if (id && !folders.value.some((folder) => folder.id === id)) return;
  applyFolder(id);
  const query: Record<string, string> = {};
  Object.entries(route.query).forEach(([key, value]) => {
    if (typeof value === 'string') query[key] = value;
  });
  if (id) query.folder = id;
  else delete query.folder;
  router.push({ query });
}

watch(
  () => route.query.folder,
  (value) => {
    const id = typeof value === 'string' ? value : null;
    if (id && !folders.value.some((folder) => folder.id === id)) return;
    if (id !== currentFolderId.value) applyFolder(id);
  },
);

watch([search, statusFilter], () => {
  selection.value = [];
});

function setView(mode: 'grid' | 'list') {
  viewMode.value = mode;
  localStorage.setItem('ft_view', mode);
}

// ───────────────────────── data ─────────────────────────

function currentOptions(folderId: string | null = currentFolderId.value): UploadOptions {
  return {
    expiresInDays: expiryChoice.value === 'never' ? null : Number(expiryChoice.value),
    password: uploadPassword.value || null,
    folderId,
  };
}

function updateTask(key: number, patch: Partial<Task>) {
  const item = tasks.find((task) => task.key === key);
  if (item) Object.assign(item, patch);
}

function removeTask(key: number) {
  const index = tasks.findIndex((task) => task.key === key);
  if (index >= 0) tasks.splice(index, 1);
  ctrls.delete(key);
}

async function cancelTask(key: number) {
  ctrls.get(key)?.abort();
  const id = tasks.find((task) => task.key === key)?.fileId;
  if (id) {
    try {
      await api.abortUpload(id);
      refresh();
    } catch {
      /* leave it for the stale-upload cleanup job */
    }
  }
}

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

function runStart(file: File, folderId: string | null = currentFolderId.value) {
  const key = ++seq;
  const ctrl = new AbortController();
  ctrls.set(key, ctrl);
  tasks.push({ key, name: file.name, size: file.size, loaded: 0, status: 'hashing', error: '', speed: 0, eta: null });

  const progress = track(key, file.size);
  const onUploadProgress = (loaded: number) => {
    if (tasks.find((task) => task.key === key)?.status === 'hashing') {
      updateTask(key, { status: 'uploading', speed: 0, eta: null });
    }
    progress(loaded);
  };
  const onHashProgress = (loaded: number) => {
    updateTask(key, { loaded, status: 'hashing', speed: 0, eta: null });
  };

  startUpload(
    file,
    currentOptions(folderId),
    onUploadProgress,
    ctrl.signal,
    (id) => updateTask(key, { fileId: id }),
    onHashProgress,
  )
    .then((result) => {
      updateTask(key, { status: 'done', loaded: file.size, eta: null, duplicate: result.duplicate });
      if (result.duplicate) flash(`「${file.name}」已秒传，复用已有文件`);
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
    const [filesRes, foldersRes, statsRes, collectionsRes, folderSharesRes] = await Promise.all([
      api.listFiles(),
      api.listFolders(),
      api.stats(),
      api.listCollections(),
      api.listFolderShares(),
    ]);
    files.value = filesRes.files;
    folders.value = foldersRes.folders;
    stats.value = statsRes;
    collections.value = collectionsRes.collections;
    folderShares.value = folderSharesRes.shares;
    selection.value = selection.value.filter((id) => filesRes.files.some((file) => file.id === id));
    if (currentFolderId.value && !foldersRes.folders.some((folder) => folder.id === currentFolderId.value)) {
      currentFolderId.value = null;
    }
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
    /* banner stays hidden */
  }
}

async function checkUsage() {
  try {
    usageStatus.value = await api.r2Usage();
  } catch {
    /* card falls back to app usage */
  }
}

async function checkUpdateNotice() {
  try {
    const status = await api.updateStatus();
    updateBehind.value = status.enabled && status.isGit && status.hasUpdate ? status.behind : 0;
  } catch {
    /* banner stays hidden */
  }
}

onMounted(async () => {
  await refresh();
  const initial = route.query.folder;
  if (typeof initial === 'string' && folders.value.some((folder) => folder.id === initial)) {
    currentFolderId.value = initial;
  }
  checkR2();
  checkUsage();
  checkUpdateNotice();
  window.addEventListener('click', onWindowClick);
  window.addEventListener('scroll', onWindowClick, true);
  window.addEventListener('keydown', onWindowKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener('click', onWindowClick);
  window.removeEventListener('scroll', onWindowClick, true);
  window.removeEventListener('keydown', onWindowKeydown);
});

// ───────────────────────── upload entry points ─────────────────────────

function onBrowserDrop(e: DragEvent) {
  dragging.value = false;
  dropTarget.value = null;
  const movedId = e.dataTransfer?.getData('application/x-ft-file');
  if (movedId) {
    moveFileTo(currentFolderId.value, movedId);
    return;
  }
  const list = e.dataTransfer?.files;
  if (list?.length) Array.from(list).forEach((file) => runStart(file));
}

function onUploadInput(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files) Array.from(input.files).forEach((file) => runStart(file));
  input.value = '';
}

function onFolderInput(e: Event) {
  const input = e.target as HTMLInputElement;
  const list = input.files;
  input.value = '';
  if (list?.length) uploadFolder(Array.from(list));
}

async function uploadFolder(entries: File[]) {
  const cache = new Map<string, string | null>();
  cache.set('', currentFolderId.value);

  async function ensureFolder(dir: string): Promise<string | null> {
    if (cache.has(dir)) return cache.get(dir)!;
    const parts = dir.split('/').filter(Boolean);
    let parent = currentFolderId.value;
    let prefix = '';
    for (const part of parts) {
      prefix = prefix ? `${prefix}/${part}` : part;
      if (cache.has(prefix)) {
        parent = cache.get(prefix)!;
        continue;
      }
      const existing = folders.value.find((folder) => folder.parentId === parent && folder.name === part);
      if (existing) {
        parent = existing.id;
        cache.set(prefix, parent);
        continue;
      }
      const { folder } = await api.createFolder(part, parent);
      folders.value.push(folder);
      parent = folder.id;
      cache.set(prefix, parent);
    }
    cache.set(dir, parent);
    return parent;
  }

  for (const entry of entries) {
    const relative = (entry as File & { webkitRelativePath?: string }).webkitRelativePath || entry.name;
    const parts = relative.split('/');
    parts.pop();
    const dir = parts.join('/');
    try {
      const folderId = await ensureFolder(dir);
      runStart(entry, folderId);
    } catch {
      alert(`无法为「${relative}」创建对应的文件夹。`);
    }
  }
  refresh();
}

function startResume(f: FileDTO) {
  menu.value = null;
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

// ───────────────────────── folders ─────────────────────────

function openCreateFolder() {
  folderModal.value = { mode: 'create' };
}

function openRenameFolder(folder: FolderDTO) {
  menu.value = null;
  folderModal.value = { mode: 'rename', folder };
}

async function saveFolder(name: string) {
  const modal = folderModal.value;
  if (!modal) return;
  try {
    if (modal.mode === 'create') {
      await api.createFolder(name, currentFolderId.value);
      flash(`已创建文件夹「${name}」`);
    } else if (modal.folder) {
      await api.renameFolder(modal.folder.id, name);
      flash('文件夹已重命名');
    }
    folderModal.value = null;
    refresh();
  } catch {
    alert('保存失败：同一目录下可能已存在同名文件夹。');
  }
}

function openMoveFolder(folder: FolderDTO) {
  menu.value = null;
  moveDialog.value = {
    kind: 'folder',
    ids: [folder.id],
    label: `移动文件夹「${folder.name}」`,
    currentId: folder.parentId,
    excludeIds: subtreeIds(folder.id),
  };
}

async function removeFolder(folder: FolderDTO) {
  menu.value = null;
  const subtreeSize = subtreeIds(folder.id).length;
  const fileCount = subtreeFileCount(folder.id);
  if (
    !confirm(
      `确定删除文件夹「${folder.name}」？\n其中 ${subtreeSize} 个文件夹、${fileCount} 个文件将被永久删除，无法恢复。`,
    )
  ) {
    return;
  }
  try {
    await api.deleteFolder(folder.id);
    if (currentFolderId.value && subtreeIds(folder.id).includes(currentFolderId.value)) {
      openFolder(folder.parentId);
    }
    flash('文件夹已删除');
    refresh();
  } catch {
    alert('删除失败：部分文件未能从存储中移除，请稍后重试。');
  }
}

function onFolderDragOver(e: DragEvent, folder: FolderDTO) {
  e.preventDefault();
  e.stopPropagation();
  dropTarget.value = folder.id;
}

function onFolderDragLeave(folder: FolderDTO) {
  if (dropTarget.value === folder.id) dropTarget.value = null;
}

function onFolderDrop(e: DragEvent, folder: FolderDTO) {
  e.preventDefault();
  e.stopPropagation();
  dropTarget.value = null;
  const movedId = e.dataTransfer?.getData('application/x-ft-file');
  if (movedId) {
    moveFileTo(folder.id, movedId);
    return;
  }
  const list = e.dataTransfer?.files;
  if (list?.length) Array.from(list).forEach((file) => runStart(file, folder.id));
}

// ───────────────────────── files ─────────────────────────

function startDragFile(e: DragEvent, file: FileDTO) {
  e.dataTransfer?.setData('application/x-ft-file', file.id);
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move';
}

async function moveFileTo(folderId: string | null, fileId: string) {
  const file = files.value.find((item) => item.id === fileId);
  if (!file || file.folderId === folderId) return;
  try {
    await api.moveFiles([fileId], folderId);
    flash('已移动');
    refresh();
  } catch {
    alert('移动失败，请稍后重试。');
  }
}

function toggle(id: string) {
  selection.value = selection.value.includes(id)
    ? selection.value.filter((value) => value !== id)
    : [...selection.value, id];
}

function toggleAll(checked: boolean) {
  selection.value = checked ? visibleFiles.value.map((file) => file.id) : [];
}

function clearSelection() {
  selection.value = [];
}

function triggerDownload(url: string) {
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
}

async function download(file: FileDTO) {
  menu.value = null;
  try {
    const { url } = await api.downloadFile(file.id);
    triggerDownload(url);
    refresh();
  } catch {
    alert('下载失败，请稍后重试。');
  }
}

async function copyFileLink(file: FileDTO) {
  menu.value = null;
  if (await copyText(file.shareUrl)) flash('分享链接已复制');
  else window.prompt('复制下面的链接', file.shareUrl);
}

function openQr(file: FileDTO) {
  menu.value = null;
  qr.value = { title: file.name, url: file.shareUrl };
}

async function openPreview(file: FileDTO) {
  menu.value = null;
  if (!canPreview(file.name, file.mimeType)) {
    alert('该文件类型暂不支持在线预览，请下载后查看。');
    return;
  }
  try {
    preview.value = await api.previewFile(file.id);
  } catch {
    alert('无法加载预览，请稍后重试。');
  }
}

function openEdit(file: FileDTO) {
  menu.value = null;
  editFile.value = file;
}

async function saveEdit(payload: Parameters<typeof api.patchFile>[1]) {
  if (!editFile.value) return;
  try {
    await api.patchFile(editFile.value.id, payload);
    editFile.value = null;
    refresh();
  } catch {
    alert('保存失败，请稍后重试。');
  }
}

async function remove(file: FileDTO) {
  menu.value = null;
  if (!confirm(`确定删除「${file.name}」？将同时从 R2 移除该文件。`)) return;
  try {
    await api.deleteFile(file.id);
    flash('文件已删除');
    refresh();
  } catch {
    alert('删除失败：无法从 R2 移除该文件，请稍后重试。');
  }
}

async function bulkRemove() {
  if (!selection.value.length) return;
  if (!confirm(`确定删除选中的 ${selection.value.length} 个文件？将从 R2 一并移除。`)) return;
  try {
    const result = await api.bulkDeleteFiles(selection.value);
    if (result.failed.length) {
      alert(`已删除 ${result.deleted.length} 个，${result.failed.length} 个失败，请稍后重试。`);
    }
    clearSelection();
    refresh();
  } catch {
    alert('批量删除失败，请稍后重试。');
  }
}

function openMoveFiles(list: FileDTO[]) {
  menu.value = null;
  if (!list.length) return;
  moveDialog.value = {
    kind: 'file',
    ids: list.map((file) => file.id),
    label: list.length === 1 ? `移动「${list[0]!.name}」` : `移动 ${list.length} 个文件`,
    currentId: currentFolderId.value,
    excludeIds: [],
  };
}

async function doMove(target: string | null) {
  const dialog = moveDialog.value;
  if (!dialog) return;
  try {
    if (dialog.kind === 'file') await api.moveFiles(dialog.ids, target);
    else await api.moveFolder(dialog.ids[0]!, target);
    moveDialog.value = null;
    clearSelection();
    flash('已移动');
    refresh();
  } catch {
    alert('移动失败：不能移动到自身或其子文件夹。');
  }
}

function openMenu(event: MouseEvent, kind: 'file' | 'folder', item: FileDTO | FolderDTO) {
  event.preventDefault();
  event.stopPropagation();
  const x = Math.max(8, Math.min(event.clientX, window.innerWidth - 190));
  const y = Math.max(8, Math.min(event.clientY, window.innerHeight - 380));
  menu.value =
    kind === 'file'
      ? { kind: 'file', file: item as FileDTO, x, y }
      : { kind: 'folder', folder: item as FolderDTO, x, y };
}

function onWindowClick() {
  if (menu.value) menu.value = null;
}

function onWindowKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    menu.value = null;
    editFile.value = null;
    qr.value = null;
    preview.value = null;
    folderModal.value = null;
    moveDialog.value = null;
    collectionModal.value = null;
    folderShareModal.value = null;
  }
}

// ───────────────────────── collections ─────────────────────────

function openCreateCollection() {
  if (!selectedReadyIds.value.length) {
    alert('请先勾选可下载的文件。');
    return;
  }
  collectionModal.value = { mode: 'create' };
}

function openEditCollection(collection: CollectionDTO) {
  collectionModal.value = { mode: 'edit', collection };
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
      flash('分享集合已创建');
    } else if (modal.collection) {
      await api.updateCollection(modal.collection.id, payload);
    }
    collectionModal.value = null;
    refresh();
  } catch {
    alert('保存失败，请稍后重试。');
  }
}

async function removeCollection(collection: CollectionDTO) {
  if (!confirm(`确定删除集合「${collection.name}」？集合内的文件不会被删除。`)) return;
  try {
    await api.deleteCollection(collection.id);
    refresh();
  } catch {
    alert('删除失败，请稍后重试。');
  }
}

async function copyCollection(collection: CollectionDTO) {
  if (await copyText(collection.shareUrl)) flash('集合链接已复制');
  else window.prompt('复制集合链接', collection.shareUrl);
}

function openCollectionQr(collection: CollectionDTO) {
  qr.value = { title: collection.name, url: collection.shareUrl };
}

// ───────────────────────── folder shares ─────────────────────────

function openCreateFolderShare(folder: FolderDTO) {
  menu.value = null;
  folderShareModal.value = { mode: 'create', folder };
}

function openEditFolderShare(share: FolderShareDTO) {
  folderShareModal.value = { mode: 'edit', share };
}

async function saveFolderShare(payload: FolderShareFormPayload) {
  const modal = folderShareModal.value;
  if (!modal) return;
  try {
    if (modal.mode === 'create' && modal.folder) {
      await api.createFolderShare({
        folderId: modal.folder.id,
        name: payload.name,
        expiresInDays: payload.expiresInDays ?? null,
        password: payload.password ?? null,
      });
      sharesTab.value = 'folders';
      flash('文件夹分享已创建');
    } else if (modal.share) {
      await api.updateFolderShare(modal.share.id, payload);
      flash('文件夹分享已更新');
    }
    folderShareModal.value = null;
    refresh();
  } catch {
    alert('保存失败，请稍后重试。');
  }
}

async function removeFolderShare(share: FolderShareDTO) {
  if (!confirm(`确定删除文件夹分享「${share.name}」？文件夹和其中的文件不会被删除。`)) return;
  try {
    await api.deleteFolderShare(share.id);
    refresh();
  } catch {
    alert('删除失败，请稍后重试。');
  }
}

async function copyFolderShare(share: FolderShareDTO) {
  if (await copyText(share.shareUrl)) flash('文件夹分享链接已复制');
  else window.prompt('复制文件夹分享链接', share.shareUrl);
}

function openFolderShareQr(share: FolderShareDTO) {
  qr.value = { title: share.name, url: share.shareUrl };
}

function pct(task: Task): number {
  return task.size ? Math.round((task.loaded / task.size) * 100) : 0;
}
</script>

<template>
  <div class="drive-shell">
    <aside class="drive-side">
      <div class="drive-actions">
        <button class="btn btn-primary btn-block" @click="uploadInput?.click()">
          <DriveIcon name="upload" :size="17" />
          上传文件
        </button>
        <div class="drive-actions-row">
          <button class="btn btn-sm" @click="folderInput?.click()">上传文件夹</button>
          <button class="btn btn-sm" @click="openCreateFolder">新建文件夹</button>
        </div>
        <details class="upload-options">
          <summary>上传选项</summary>
          <div class="upload-option-field">
            <label>有效期</label>
            <select v-model="expiryChoice">
              <option value="never">永久</option>
              <option value="1">1 天后删除</option>
              <option value="7">7 天后删除</option>
              <option value="30">30 天后删除</option>
              <option value="90">90 天后删除</option>
            </select>
          </div>
          <div class="upload-option-field">
            <label>下载提取码</label>
            <input v-model="uploadPassword" type="text" placeholder="留空则不需要" />
          </div>
        </details>
      </div>

      <nav class="drive-nav">
        <button :class="{ active: view === 'files' && !currentFolderId }" @click="openFolder(null)">
          <DriveIcon name="home" :size="18" />
          全部文件
        </button>
        <button :class="{ active: view === 'shares' }" @click="view = 'shares'">
          <DriveIcon name="share" :size="18" />
          分享
          <span v-if="collections.length + folderShares.length" class="nav-badge">
            {{ collections.length + folderShares.length }}
          </span>
        </button>
        <RouterLink to="/logs">
          <DriveIcon name="clock" :size="18" />
          操作记录
        </RouterLink>
      </nav>

      <div class="tree-block" :class="{ collapsed: !treeOpen }">
        <button
          class="tree-title tree-title-toggle"
          type="button"
          :aria-expanded="treeOpen"
          @click="treeOpen = !treeOpen"
        >
          <span>文件夹</span>
          <DriveIcon name="chevron" :size="14" class="tree-chevron" :class="{ open: treeOpen }" />
        </button>
        <div v-show="treeOpen" class="tree-body">
          <FolderTree
            v-if="folderTree.length"
            :nodes="folderTree"
            :active-id="currentFolderId"
            @open="openFolder"
            @drop-files="(id, list) => Array.from(list).forEach((file) => runStart(file, id))"
            @drop-file="(id, fileId) => moveFileTo(id, fileId)"
          />
          <p v-else class="muted text-sm">还没有文件夹</p>
        </div>
      </div>

      <div class="storage-card">
        <div class="storage-head">
          <DriveIcon name="storage" :size="17" />
          {{ accountUsage ? '账户用量（全部桶）' : '存储用量' }}
        </div>
        <div class="storage-value">{{ formatBytes(accountUsage?.totalBytes ?? stats?.totalSize ?? 0) }}</div>
        <div v-if="accountUsage" class="usage-bar storage-usage">
          <div class="usage-bar-fill" :class="{ warn: usagePct >= 80 }" :style="{ width: usagePct + '%' }"></div>
        </div>
        <div v-if="accountUsage" class="storage-meta">
          标准 {{ formatBytes(accountUsage.standardBytes) }} · {{ accountUsage.objectCount }} 个对象
        </div>
        <div class="storage-meta">
          {{ stats?.fileCount ?? 0 }} 个文件 · {{ stats?.folderCount ?? 0 }} 个文件夹
        </div>
        <div class="storage-meta">累计下载 {{ stats?.downloadTotal ?? 0 }} 次</div>
        <div v-if="accountUsage" class="storage-meta">占 10 GB 免费额度 {{ usagePct.toFixed(1) }}%</div>
        <RouterLink v-else class="storage-link" to="/settings?panel=storage">
          {{ usageStatus?.configured ? '账户用量读取失败 · 去设置检查' : '显示全部桶用量' }}
        </RouterLink>
      </div>
    </aside>

    <section class="drive-main">
      <div v-if="r2Configured === false" class="alert info mb-md">
        R2 存储尚未配置，无法上传。请先在
        <RouterLink to="/settings?panel=storage">存储设置</RouterLink>
        中填写凭据。如上传失败，请确认桶已配置 CORS。
      </div>

      <div v-if="updateBehind > 0" class="alert info mb-md">
        发现新版本（落后 {{ updateBehind }} 个提交）。可前往
        <RouterLink to="/settings?panel=update">设置 → 软件更新</RouterLink>
        一键更新。
      </div>

      <template v-if="view === 'shares'">
        <header class="shares-head">
          <div>
            <h1>分享</h1>
            <p class="muted text-sm">把文件集合或整个文件夹生成分享链接，访问者可逐个下载或一键全部下载。</p>
          </div>
          <button
            v-if="sharesTab === 'collections'"
            class="btn btn-primary"
            :disabled="!selectedReadyIds.length"
            @click="openCreateCollection"
          >
            用选中的 {{ selectedReadyIds.length }} 个文件创建
          </button>
        </header>

        <div class="bucket-tabs mb-md">
          <button :class="{ active: sharesTab === 'collections' }" @click="sharesTab = 'collections'">
            文件集合（{{ collections.length }}）
          </button>
          <button :class="{ active: sharesTab === 'folders' }" @click="sharesTab = 'folders'">
            文件夹（{{ folderShares.length }}）
          </button>
        </div>

        <template v-if="sharesTab === 'collections'">
          <div v-if="!collections.length" class="empty-drive">
            <DriveIcon name="pack" :size="48" />
            <p>还没有分享集合</p>
            <p class="muted text-sm">回到「全部文件」勾选可下载的文件，再点「打包分享」。</p>
            <button class="btn" @click="view = 'files'">去选择文件</button>
          </div>

          <div v-else class="collection-list mt-md">
            <div v-for="collection in collections" :key="collection.id" class="collection-item">
              <div class="collection-main">
                <div class="collection-name">{{ collection.name }}</div>
                <div class="muted text-sm">
                  {{ collection.itemCount }} 个文件 · {{ formatBytes(collection.totalSize) }} ·
                  {{ describeExpiry(collection.expiresAt) }} · 下载 {{ collection.downloadCount }}
                </div>
              </div>
              <div class="actions">
                <button class="btn btn-sm" @click="copyCollection(collection)">复制链接</button>
                <button class="btn btn-sm" @click="openCollectionQr(collection)">二维码</button>
                <button class="btn btn-sm btn-ghost" @click="openEditCollection(collection)">编辑</button>
                <button class="btn btn-sm btn-danger" @click="removeCollection(collection)">删除</button>
              </div>
            </div>
          </div>
        </template>

        <template v-else>
          <div v-if="!folderShares.length" class="empty-drive">
            <DriveIcon name="folder" :size="48" />
            <p>还没有文件夹分享</p>
            <p class="muted text-sm">在文件夹上右键选择「分享文件夹」，或进入文件夹后点击「分享此文件夹」。</p>
            <button class="btn" @click="view = 'files'">去选择文件夹</button>
          </div>

          <div v-else class="collection-list mt-md">
            <div v-for="share in folderShares" :key="share.id" class="collection-item">
              <div class="collection-main">
                <div class="collection-name">{{ share.name }}</div>
                <div class="muted text-sm">
                  {{ share.folderPath || '根目录' }} · {{ share.itemCount }} 个文件 ·
                  {{ formatBytes(share.totalSize) }} · {{ describeExpiry(share.expiresAt) }} ·
                  下载 {{ share.downloadCount }}
                </div>
              </div>
              <div class="actions">
                <button class="btn btn-sm" @click="copyFolderShare(share)">复制链接</button>
                <button class="btn btn-sm" @click="openFolderShareQr(share)">二维码</button>
                <button class="btn btn-sm btn-ghost" @click="openEditFolderShare(share)">编辑</button>
                <button class="btn btn-sm btn-danger" @click="removeFolderShare(share)">删除</button>
              </div>
            </div>
          </div>
        </template>
      </template>

      <template v-else>
        <header class="drive-head">
          <div class="crumb" aria-label="当前位置">
            <template v-for="(crumb, index) in breadcrumbs" :key="crumb.id ?? 'root'">
              <span v-if="index > 0" class="crumb-sep">/</span>
              <button
                class="crumb-item"
                :class="{ current: index === breadcrumbs.length - 1 }"
                type="button"
                @click="openFolder(crumb.id)"
              >
                {{ crumb.name }}
              </button>
            </template>
          </div>

          <div class="drive-tools">
            <div class="search-field">
              <DriveIcon name="search" :size="17" />
              <input v-model="search" type="search" placeholder="搜索全部文件与文件夹" />
              <button v-if="search" class="search-clear" type="button" aria-label="清除搜索" @click="search = ''">
                <DriveIcon name="close" :size="15" />
              </button>
            </div>
            <select v-model="statusFilter" aria-label="状态筛选">
              <option value="all">全部状态</option>
              <option value="ready">可下载</option>
              <option value="uploading">上传中</option>
              <option value="expired">已过期</option>
            </select>
            <select v-model="sortKey" aria-label="排序方式">
              <option value="created_desc">最新上传</option>
              <option value="created_asc">最早上传</option>
              <option value="name_asc">名称 A→Z</option>
              <option value="name_desc">名称 Z→A</option>
              <option value="size_desc">大小从大到小</option>
              <option value="size_asc">大小从小到大</option>
              <option value="download_desc">下载最多</option>
            </select>
            <div class="view-switch">
              <button
                type="button"
                :class="{ active: viewMode === 'grid' }"
                aria-label="网格视图"
                @click="setView('grid')"
              >
                <DriveIcon name="grid" :size="17" />
              </button>
              <button
                type="button"
                :class="{ active: viewMode === 'list' }"
                aria-label="列表视图"
                @click="setView('list')"
              >
                <DriveIcon name="list" :size="17" />
              </button>
            </div>
          </div>
        </header>

        <div v-if="tasks.length" class="transfer-panel">
          <div class="transfer-head">
            <DriveIcon name="cloud" :size="17" />
            <span>传输任务</span>
            <span class="muted text-sm">{{ tasks.length }} 个</span>
          </div>
          <div v-for="task in tasks" :key="task.key" class="transfer-row">
            <div class="transfer-info">
              <span class="transfer-name">{{ task.name }}</span>
              <span class="transfer-state">
                <template v-if="task.status === 'hashing'">校验中 {{ pct(task) }}%</template>
                <template v-else-if="task.status === 'uploading'">
                  {{ pct(task) }}%<template v-if="task.speed > 0">
                    · {{ formatBytes(task.speed) }}/s · 剩余 {{ formatDuration(task.eta ?? 0) }}</template>
                </template>
                <span v-else-if="task.status === 'done'" class="text-success">
                  {{ task.duplicate ? '已秒传' : '完成' }}
                </span>
                <span v-else class="text-danger">{{ task.error }}</span>
              </span>
            </div>
            <div class="progress transfer-progress">
              <div class="progress-bar" :style="{ width: pct(task) + '%' }"></div>
            </div>
            <button
              v-if="task.status === 'hashing' || task.status === 'uploading'"
              class="btn btn-sm btn-ghost"
              @click="cancelTask(task.key)"
            >
              取消
            </button>
            <button v-else class="btn btn-sm btn-ghost" @click="removeTask(task.key)">移除</button>
          </div>
        </div>

        <div v-if="selection.length" class="bulk-bar">
          <span>已选 {{ selection.length }} 个文件</span>
          <div class="spacer"></div>
          <button class="btn btn-sm" @click="openMoveFiles(selectedFiles)">移动到</button>
          <button class="btn btn-sm" :disabled="!selectedReadyIds.length" @click="openCreateCollection">打包分享</button>
          <button class="btn btn-sm btn-danger" @click="bulkRemove">删除</button>
          <button class="btn btn-sm btn-ghost" @click="clearSelection">取消选择</button>
        </div>

        <div
          class="drive-drop"
          :class="{ drag: dragging }"
          @dragover.prevent="dragging = true"
          @dragleave.prevent="dragging = false"
          @drop.prevent="onBrowserDrop"
        >
          <div class="section-line">
            <div>
              <h2>{{ searching ? '搜索结果' : currentFolder?.name ?? '全部文件' }}</h2>
              <span class="muted text-sm">
                {{ visibleFolders.length }} 个文件夹 · {{ visibleFiles.length }} 个文件
                <template v-if="searching"> · 关键词「{{ search.trim() }}」</template>
              </span>
            </div>
            <div class="section-tools">
              <label v-if="visibleFiles.length" class="select-all">
                <input type="checkbox" :checked="allChecked" @change="toggleAll(($event.target as HTMLInputElement).checked)" />
                全选
              </label>
              <button
                v-if="currentFolder && !searching"
                class="btn btn-sm btn-ghost"
                @click="openCreateFolderShare(currentFolder)"
              >
                <DriveIcon name="share" :size="15" />
                分享此文件夹
              </button>
              <button class="btn btn-sm btn-ghost" @click="refresh">
                <DriveIcon name="refresh" :size="15" />
                刷新
              </button>
            </div>
          </div>

          <div v-if="listError" class="alert error">{{ listError }}</div>
          <div v-else-if="listLoading" class="empty">加载中…</div>
          <div v-else-if="!totalItems" class="empty-drive">
            <DriveIcon :name="searching ? 'search' : 'folder'" :size="48" />
            <p>{{ searching ? '没有匹配的内容' : '这个文件夹还是空的' }}</p>
            <p v-if="!searching" class="muted text-sm">拖拽文件到此处，或点击「上传文件」。</p>
            <button v-if="!searching" class="btn" @click="uploadInput?.click()">上传文件</button>
          </div>

          <div v-else-if="viewMode === 'grid'" class="file-grid">
            <div
              v-for="folder in visibleFolders"
              :key="folder.id"
              class="item-card folder-card"
              :class="{ 'drop-target': dropTarget === folder.id }"
              role="button"
              tabindex="0"
              :aria-label="`打开文件夹 ${folder.name}`"
              @click="openFolder(folder.id)"
              @keydown.enter.prevent="openFolder(folder.id)"
              @contextmenu="openMenu($event, 'folder', folder)"
              @dragover="onFolderDragOver($event, folder)"
              @dragleave="onFolderDragLeave(folder)"
              @drop="onFolderDrop($event, folder)"
            >
              <button class="item-more" type="button" aria-label="更多操作" @click.stop="openMenu($event, 'folder', folder)">
                <DriveIcon name="more" :size="16" />
              </button>
              <div class="item-icon-folder">
                <DriveIcon name="folder" :size="46" />
              </div>
              <div class="item-name" :title="folder.name">{{ folder.name }}</div>
              <div class="item-meta">
                {{ folder.fileCount + folder.childCount }} 项 · {{ formatBytes(folder.totalSize) }}
                <template v-if="searching"> · {{ folderPath(folder.parentId) }}</template>
              </div>
            </div>

            <div
              v-for="file in visibleFiles"
              :key="file.id"
              class="item-card file-card"
              :class="{ selected: selection.includes(file.id), 'uploading-card': file.status === 'uploading' }"
              role="button"
              tabindex="0"
              :draggable="file.status !== 'uploading'"
              :aria-label="`选择 ${file.name}`"
              @click="toggle(file.id)"
              @keydown.enter.prevent="toggle(file.id)"
              @dblclick="file.status === 'ready' && (canPreview(file.name, file.mimeType) ? openPreview(file) : download(file))"
              @dragstart="startDragFile($event, file)"
              @contextmenu="openMenu($event, 'file', file)"
            >
              <label class="item-check" @click.stop>
                <input
                  type="checkbox"
                  :checked="selection.includes(file.id)"
                  :aria-label="`选择 ${file.name}`"
                  @change="toggle(file.id)"
                />
              </label>
              <button class="item-more" type="button" aria-label="更多操作" @click.stop="openMenu($event, 'file', file)">
                <DriveIcon name="more" :size="16" />
              </button>
              <div class="item-icon" :class="`tone-${fileKind(file.name).tone}`">{{ fileKind(file.name).label }}</div>
              <div class="item-name" :title="file.name">{{ file.name }}</div>
              <div class="item-meta">
                {{ formatBytes(file.size) }}
                <template v-if="searching"> · {{ folderPath(file.folderId) }}</template>
              </div>
              <div class="item-foot">
                <span class="badge" :class="file.status">{{ statusText[file.status] ?? file.status }}</span>
                <span v-if="file.hasPassword" class="item-tag">提取码</span>
                <button
                  v-if="file.status === 'uploading'"
                  class="linklike"
                  type="button"
                  @click.stop="startResume(file)"
                >
                  继续上传
                </button>
              </div>
            </div>
          </div>

          <div v-else>
            <div v-if="visibleFolders.length" class="folder-row-list">
              <div
                v-for="folder in visibleFolders"
                :key="folder.id"
                class="folder-row"
                :class="{ 'drop-target': dropTarget === folder.id }"
                role="button"
                tabindex="0"
                @click="openFolder(folder.id)"
                @keydown.enter.prevent="openFolder(folder.id)"
                @contextmenu="openMenu($event, 'folder', folder)"
                @dragover="onFolderDragOver($event, folder)"
                @dragleave="onFolderDragLeave(folder)"
                @drop="onFolderDrop($event, folder)"
              >
                <span class="folder-row-icon"><DriveIcon name="folder" :size="22" /></span>
                <span class="folder-row-name" :title="folder.name">{{ folder.name }}</span>
                <span class="folder-row-meta muted text-sm">{{ folder.fileCount + folder.childCount }} 项</span>
                <span class="folder-row-meta muted text-sm">{{ formatBytes(folder.totalSize) }}</span>
                <button class="item-more" type="button" aria-label="更多操作" @click.stop="openMenu($event, 'folder', folder)">
                  <DriveIcon name="more" :size="16" />
                </button>
              </div>
            </div>
            <FileTable
              v-if="visibleFiles.length"
              :files="visibleFiles"
              :selected="selection"
              @resume="startResume"
              @edit="openEdit"
              @remove="remove"
              @preview="openPreview"
              @download="download"
              @qr="openQr"
              @toggle="toggle"
              @toggle-all="toggleAll"
            />
            <p v-else class="list-empty muted text-sm">此文件夹内还没有文件。</p>
          </div>
        </div>
      </template>
    </section>

    <input ref="uploadInput" type="file" multiple hidden @change="onUploadInput" />
    <input ref="folderInput" type="file" webkitdirectory multiple hidden @change="onFolderInput" />
    <input ref="resumeInput" type="file" hidden @change="onResumeInput" />

    <div v-if="menu" class="drive-menu" :style="{ left: menu.x + 'px', top: menu.y + 'px' }" @click.stop>
      <template v-if="menu.kind === 'file'">
        <button
          :disabled="menu.file.status !== 'ready' || !canPreview(menu.file.name, menu.file.mimeType)"
          @click="openPreview(menu.file)"
        >
          <DriveIcon name="eye" :size="16" />预览
        </button>
        <button :disabled="menu.file.status !== 'ready'" @click="download(menu.file)">
          <DriveIcon name="download" :size="16" />下载
        </button>
        <button :disabled="menu.file.status !== 'ready'" @click="copyFileLink(menu.file)">
          <DriveIcon name="link" :size="16" />复制分享链接
        </button>
        <button :disabled="menu.file.status !== 'ready'" @click="openQr(menu.file)">
          <DriveIcon name="qr" :size="16" />二维码
        </button>
        <button :disabled="menu.file.status === 'expired'" @click="openEdit(menu.file)">
          <DriveIcon name="edit" :size="16" />编辑
        </button>
        <button @click="openMoveFiles([menu.file])"><DriveIcon name="move" :size="16" />移动到</button>
        <button v-if="menu.file.status === 'uploading'" @click="startResume(menu.file)">
          <DriveIcon name="upload" :size="16" />继续上传
        </button>
        <button class="danger" @click="remove(menu.file)"><DriveIcon name="trash" :size="16" />删除</button>
      </template>
      <template v-else>
        <button @click="openFolder(menu.folder.id)"><DriveIcon name="folder" :size="16" />打开</button>
        <button @click="openCreateFolderShare(menu.folder)"><DriveIcon name="share" :size="16" />分享文件夹</button>
        <button @click="openRenameFolder(menu.folder)"><DriveIcon name="edit" :size="16" />重命名</button>
        <button @click="openMoveFolder(menu.folder)"><DriveIcon name="move" :size="16" />移动到</button>
        <button class="danger" @click="removeFolder(menu.folder)"><DriveIcon name="trash" :size="16" />删除</button>
      </template>
    </div>

    <div v-if="toast" class="drive-toast" role="status" aria-live="polite">{{ toast }}</div>

    <EditModal v-if="editFile" :file="editFile" @save="saveEdit" @close="editFile = null" />
    <QrModal v-if="qr" :title="qr.title" :url="qr.url" @close="qr = null" />
    <PreviewModal
      v-if="preview"
      :name="preview.name"
      :url="preview.url"
      :size="preview.size"
      :mimeType="preview.mimeType"
      @close="preview = null"
    />
    <CollectionModal
      v-if="collectionModal"
      :mode="collectionModal.mode"
      :initial-name="collectionModal.collection?.name"
      :initial-has-password="collectionModal.collection?.hasPassword"
      :file-count="selectedReadyIds.length"
      @save="saveCollection"
      @close="collectionModal = null"
    />
    <FolderShareModal
      v-if="folderShareModal"
      :mode="folderShareModal.mode"
      :initial-name="folderShareModal.share?.name ?? folderShareModal.folder?.name"
      :initial-has-password="folderShareModal.share?.hasPassword"
      :folder-name="folderShareModal.share?.folderPath ?? folderShareModal.folder?.name"
      :file-count="folderShareModal.folder ? subtreeReadyCount(folderShareModal.folder.id) : folderShareModal.share?.itemCount"
      @save="saveFolderShare"
      @close="folderShareModal = null"
    />
    <FolderModal
      v-if="folderModal"
      :mode="folderModal.mode"
      :initial-name="folderModal.folder?.name"
      :parent-name="currentFolder?.name"
      :count="folderModal.folder ? folderModal.folder.fileCount + folderModal.folder.childCount : 0"
      @save="saveFolder"
      @close="folderModal = null"
    />
    <MoveModal
      v-if="moveDialog"
      :title="moveDialog.label"
      subtitle="选择目标文件夹"
      :folders="folderTree"
      :exclude-ids="moveDialog.excludeIds"
      :current-id="moveDialog.currentId"
      @move="doMove"
      @close="moveDialog = null"
    />
  </div>
</template>
