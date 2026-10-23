export type FileStatus = 'uploading' | 'ready' | 'expired' | 'deleted';

export interface FileDTO {
  id: string;
  shareCode: string;
  name: string;
  size: number;
  mimeType: string;
  status: FileStatus;
  createdAt: number;
  expiresAt: number | null;
  hasPassword: boolean;
  downloadCount: number;
  lastDownloadAt: number | null;
  folderId: string | null;
  shareUrl: string;
}

export interface FolderDTO {
  id: string;
  name: string;
  parentId: string | null;
  createdAt: number;
  fileCount: number;
  totalSize: number;
  childCount: number;
}

export interface SingleUpload {
  mode: 'single';
  file: FileDTO;
  upload: { url: string; method: 'PUT'; key: string; headers: Record<string, string> };
}

export interface MultipartUpload {
  mode: 'multipart';
  file: FileDTO;
  upload: {
    uploadId: string;
    key: string;
    partSize: number;
    partCount: number;
    urls: { partNumber: number; url: string }[];
  };
}

export type InitResponse = SingleUpload | MultipartUpload;

export interface PartsResponse {
  fileId: string;
  uploadId: string;
  partSize: number;
  partCount: number;
  uploaded: { partNumber: number; etag: string }[];
  urls: { partNumber: number; url: string }[];
}

export interface ShareInfo {
  found: boolean;
  expired: boolean;
  requiresPassword: boolean;
  name: string;
  size: number;
  mimeType: string;
  createdAt: number;
  expiresAt: number | null;
  downloadCount: number;
  status: FileStatus;
}

export interface R2Status {
  configured: boolean;
  accountId: string;
  accessKeyId: string;
  bucket: string;
  endpoint: string;
  hasSecret: boolean;
  hasUsageToken: boolean;
  storedIn: 'db' | 'env' | 'none';
}

export interface R2Usage {
  totalBytes: number;
  publishedBytes: number;
  pendingBytes: number;
  standardBytes: number;
  infrequentAccessBytes: number;
  objectCount: number;
  fetchedAt: number;
}

export interface BucketScanObject {
  key: string;
  name: string | null;
  size: number;
  lastModified: number | null;
  tracked: boolean;
  foreign: boolean;
}

export interface BucketMissingRecord {
  id: string;
  name: string;
  key: string;
  status: string;
  size: number;
  createdAt: number;
}

export interface BucketScanResult {
  scannedAt: number;
  truncated: boolean;
  objectCount: number;
  totalSize: number;
  trackedCount: number;
  orphanCount: number;
  orphanBytes: number;
  foreignCount: number;
  missingCount: number;
  objects: BucketScanObject[];
  orphans: BucketScanObject[];
  missing: BucketMissingRecord[];
}

export interface KeyMigrationCandidate {
  id: string;
  name: string;
  size: number;
  oldKey: string;
  newKey: string;
}

export interface KeyMigrationPlan {
  total: number;
  samples: KeyMigrationCandidate[];
}

export interface KeyMigrationResult {
  migrated: number;
  skipped: number;
  failed: { id: string; key: string; reason: string }[];
  remaining: number;
  total: number;
}

export interface BucketDeleteResult {
  deleted: string[];
  failed: { key: string; message: string }[];
  skipped: string[];
}

export interface R2UsageStatus {
  configured: boolean;
  hasToken: boolean;
  freeTierBytes: number;
  usage?: R2Usage;
  error?: string;
}

export interface PasswordStatus {
  storedIn: 'db' | 'env';
  changedAt: number | null;
}

export interface UsernameStatus {
  username: string;
  storedIn: 'db' | 'env';
}

export interface SiteStatus {
  baseUrl: string;
  mode: 'pinned' | 'auto';
  autoDetected: string;
}

export interface Stats {
  fileCount: number;
  readyCount: number;
  uploadingCount: number;
  expiredCount: number;
  totalSize: number;
  readySize: number;
  expiredSize: number;
  uploadSize: number;
  downloadTotal: number;
  collectionCount: number;
  folderCount: number;
}

export interface CollectionItem {
  fileId: string;
  name: string;
  size: number;
  mimeType: string;
  status: FileStatus;
  downloadCount: number;
}

export interface CollectionDTO {
  id: string;
  code: string;
  name: string;
  status: FileStatus | 'expired';
  createdAt: number;
  expiresAt: number | null;
  hasPassword: boolean;
  downloadCount: number;
  shareUrl: string;
  itemCount: number;
  totalSize: number;
  items: CollectionItem[];
}

export interface CollectionShareInfo {
  found: boolean;
  expired: boolean;
  requiresPassword: boolean;
  name: string;
  createdAt: number;
  expiresAt: number | null;
  downloadCount: number;
  status: string;
  items: CollectionItem[];
}

export interface CollectionFormPayload {
  name: string;
  expiresInDays?: number | null;
  password?: string | null;
  clearPassword?: boolean;
}

export type LogResult = 'ok' | 'fail';

export interface ActivityLog {
  id: number;
  action: string;
  actor: string | null;
  targetType: string | null;
  targetId: string | null;
  targetName: string | null;
  detail: unknown;
  result: LogResult;
  ip: string | null;
  createdAt: number;
}

export interface LogsQuery {
  page?: number;
  pageSize?: number;
  action?: string;
  result?: 'ok' | 'fail';
  q?: string;
  from?: number;
  to?: number;
}

export interface LogsResponse {
  logs: ActivityLog[];
  total: number;
  page: number;
  pageSize: number;
  actions: { action: string; count: number }[];
}

export interface LogStats {
  total: number;
  failures: number;
  last24h: number;
  oldestAt: number | null;
}

export interface CommitInfo {
  sha: string;
  date: number;
  message: string;
}

export interface UpdateStatus {
  enabled: boolean;
  branch: string;
  isGit: boolean;
  current: CommitInfo | null;
  remote: CommitInfo | null;
  behind: number;
  hasUpdate: boolean;
  lastCheckedAt: number | null;
  running: boolean;
  lastRun: 'ok' | 'failed' | 'running' | 'none';
  error?: string;
}

export interface UpdateLog {
  log: string;
  lastRun: 'ok' | 'failed' | 'running' | 'none';
  running: boolean;
}

const TOKEN_KEY = 'ft_token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}
export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(
  path: string,
  opts: { method?: string; body?: unknown; auth?: boolean } = {},
): Promise<T> {
  const { method = 'GET', body, auth = true } = opts;
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (auth && token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch('/api' + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (res.status === 401 && auth) clearToken();

  let data: unknown = null;
  try {
    data = await res.json();
  } catch {
    /* non-JSON body */
  }

  if (!res.ok) {
    const d = (data ?? {}) as { error?: string };
    throw new ApiError(res.status, d.error ?? 'request_failed', d.error ?? `HTTP ${res.status}`);
  }
  return data as T;
}

export const api = {
  login: (username: string, password: string) =>
    request<{ token: string; username: string }>('/auth/login', {
      method: 'POST',
      body: { username, password },
      auth: false,
    }),
  me: () => request<{ username: string }>('/auth/me'),
  usernameStatus: () => request<UsernameStatus>('/auth/username'),
  changeUsername: (currentPassword: string, newUsername: string) =>
    request<{ ok: true; username: string }>('/auth/username', {
      method: 'POST',
      body: { currentPassword, newUsername },
    }),
  clearPanelUsername: () => request<{ ok: true }>('/auth/username', { method: 'DELETE' }),
  passwordStatus: () => request<PasswordStatus>('/auth/password'),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<{ ok: true }>('/auth/password', {
      method: 'POST',
      body: { currentPassword, newPassword },
    }),
  clearPanelPassword: () => request<{ ok: true }>('/auth/password', { method: 'DELETE' }),

  listFiles: (folderId?: string | null) =>
    request<{ files: FileDTO[] }>(
      folderId ? `/files?folderId=${encodeURIComponent(folderId)}` : '/files',
    ),
  moveFiles: (ids: string[], folderId: string | null) =>
    request<{ moved: number; folderId: string | null }>('/files/move', {
      method: 'POST',
      body: { ids, folderId },
    }),
  initUpload: (payload: {
    name: string;
    size: number;
    mimeType: string;
    expiresInDays?: number | null;
    password?: string | null;
    folderId?: string | null;
  }) => request<InitResponse>('/files/init', { method: 'POST', body: payload }),
  getParts: (id: string) => request<PartsResponse>(`/files/${id}/parts`),
  partUrl: (id: string, partNumber: number) =>
    request<{ partNumber: number; url: string }>(`/files/${id}/parts/${partNumber}`),
  completeUpload: (id: string, parts?: { partNumber: number; etag: string }[]) =>
    request<{ file: FileDTO }>(`/files/${id}/complete`, { method: 'POST', body: { parts } }),
  abortUpload: (id: string) => request<{ ok: boolean }>(`/files/${id}/abort`, { method: 'POST' }),
  patchFile: (
    id: string,
    payload: { name?: string; expiresInDays?: number | null; password?: string | null; clearPassword?: boolean },
  ) => request<{ file: FileDTO }>(`/files/${id}`, { method: 'PATCH', body: payload }),
  deleteFile: (id: string) => request<{ ok: boolean }>(`/files/${id}`, { method: 'DELETE' }),
  bulkDeleteFiles: (ids: string[]) =>
    request<{ deleted: string[]; failed: string[] }>('/files/bulk-delete', { method: 'POST', body: { ids } }),
  downloadFile: (id: string) => request<{ url: string }>(`/files/${id}/download`),
  stats: () => request<Stats>('/files/stats'),

  listFolders: () => request<{ folders: FolderDTO[] }>('/folders'),
  createFolder: (name: string, parentId: string | null) =>
    request<{ folder: FolderDTO }>('/folders', { method: 'POST', body: { name, parentId } }),
  renameFolder: (id: string, name: string) =>
    request<{ folder: FolderDTO }>(`/folders/${id}`, { method: 'PATCH', body: { name } }),
  moveFolder: (id: string, parentId: string | null) =>
    request<{ folder: FolderDTO }>(`/folders/${id}`, { method: 'PATCH', body: { parentId } }),
  deleteFolder: (id: string) =>
    request<{ ok: boolean; deletedFolders: number; deletedFiles: number }>(`/folders/${id}`, {
      method: 'DELETE',
    }),

  listCollections: () => request<{ collections: CollectionDTO[] }>('/collections'),
  createCollection: (payload: { name: string; fileIds: string[]; expiresInDays?: number | null; password?: string | null }) =>
    request<{ collection: CollectionDTO }>('/collections', { method: 'POST', body: payload }),
  updateCollection: (
    id: string,
    payload: { name?: string; expiresInDays?: number | null; password?: string | null; clearPassword?: boolean },
  ) => request<{ collection: CollectionDTO }>(`/collections/${id}`, { method: 'PATCH', body: payload }),
  deleteCollection: (id: string) => request<{ ok: boolean }>(`/collections/${id}`, { method: 'DELETE' }),

  collectionShare: (code: string) => request<CollectionShareInfo>(`/collections/share/${code}`, { auth: false }),
  collectionItemDownload: (code: string, fileId: string, password?: string) =>
    request<{ url: string }>(`/collections/share/${code}/item/${fileId}/download`, {
      method: 'POST',
      body: { password },
      auth: false,
    }),
  collectionDownloadAll: (code: string, password?: string) =>
    request<{ downloads: { name: string; url: string }[] }>(`/collections/share/${code}/download-all`, {
      method: 'POST',
      body: { password },
      auth: false,
    }),

  shareInfo: (code: string) => request<ShareInfo>(`/share/${code}`, { auth: false }),
  shareDownload: (code: string, password?: string) =>
    request<{ url: string }>(`/share/${code}/download`, {
      method: 'POST',
      body: { password },
      auth: false,
    }),

  getR2: () => request<R2Status>('/settings/r2'),
  saveR2: (payload: {
    accountId: string;
    accessKeyId: string;
    bucket: string;
    secretAccessKey?: string;
    endpoint?: string;
  }) => request<R2Status>('/settings/r2', { method: 'PUT', body: payload }),
  testR2: () => request<{ ok: boolean; bucket: string; endpoint: string }>('/settings/r2/test', { method: 'POST' }),
  applyR2Cors: () => request<{ ok: boolean; origins: string[] }>('/settings/r2/cors', { method: 'POST' }),

  r2Usage: (refresh = false) =>
    request<R2UsageStatus>(`/settings/r2/usage${refresh ? '?refresh=1' : ''}`),
  saveR2UsageToken: (token: string) =>
    request<{ hasToken: boolean; configured: boolean }>('/settings/r2/usage-token', {
      method: 'PUT',
      body: { token },
    }),

  scanBucket: () => request<BucketScanResult>('/bucket/scan'),
  deleteOrphanObjects: (keys: string[]) =>
    request<BucketDeleteResult>('/bucket/delete-orphans', { method: 'POST', body: { keys } }),

  keyMigrationStatus: () => request<KeyMigrationPlan>('/bucket/key-migration'),
  migrateKeys: (limit = 100) =>
    request<KeyMigrationResult>('/bucket/migrate-keys', { method: 'POST', body: { limit } }),

  getSite: () => request<SiteStatus>('/settings/site'),
  saveSite: (baseUrl: string) =>
    request<SiteStatus>('/settings/site', { method: 'PUT', body: { baseUrl } }),

  listLogs: (query: LogsQuery = {}) => {
    const params = new URLSearchParams();
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
    });
    const qs = params.toString();
    return request<LogsResponse>(`/logs${qs ? `?${qs}` : ''}`);
  },
  logStats: () => request<LogStats>('/logs/stats'),
  clearLogs: () => request<{ ok: boolean; cleared: number }>('/logs', { method: 'DELETE' }),

  updateStatus: () => request<UpdateStatus>('/update/status'),
  checkUpdate: () => request<UpdateStatus>('/update/check', { method: 'POST' }),
  runUpdate: () => request<{ started: true }>('/update/run', { method: 'POST' }),
  updateLog: () => request<UpdateLog>('/update/log'),
};
