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
  shareUrl: string;
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
  storedIn: 'db' | 'env' | 'none';
}

export interface PasswordStatus {
  storedIn: 'db' | 'env';
  changedAt: number | null;
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
  dirty: boolean;
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
  passwordStatus: () => request<PasswordStatus>('/auth/password'),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<{ ok: true }>('/auth/password', {
      method: 'POST',
      body: { currentPassword, newPassword },
    }),
  clearPanelPassword: () => request<{ ok: true }>('/auth/password', { method: 'DELETE' }),

  listFiles: () => request<{ files: FileDTO[] }>('/files'),
  initUpload: (payload: {
    name: string;
    size: number;
    mimeType: string;
    expiresInDays?: number | null;
    password?: string | null;
  }) => request<InitResponse>('/files/init', { method: 'POST', body: payload }),
  getParts: (id: string) => request<PartsResponse>(`/files/${id}/parts`),
  completeUpload: (id: string, parts?: { partNumber: number; etag: string }[]) =>
    request<{ file: FileDTO }>(`/files/${id}/complete`, { method: 'POST', body: { parts } }),
  patchFile: (
    id: string,
    payload: { name?: string; expiresInDays?: number | null; password?: string | null; clearPassword?: boolean },
  ) => request<{ file: FileDTO }>(`/files/${id}`, { method: 'PATCH', body: payload }),
  deleteFile: (id: string) => request<{ ok: boolean }>(`/files/${id}`, { method: 'DELETE' }),

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

  updateStatus: () => request<UpdateStatus>('/update/status'),
  checkUpdate: () => request<UpdateStatus>('/update/check', { method: 'POST' }),
  runUpdate: () => request<{ started: true }>('/update/run', { method: 'POST' }),
  updateLog: () => request<UpdateLog>('/update/log'),
};
