import { api, type FileDTO, type InitResponse } from '../api';

export interface UploadOptions {
  expiresInDays?: number | null;
  password?: string | null;
}

export type ProgressFn = (loaded: number, total: number) => void;

const CONCURRENCY = 4;
const MAX_RETRIES = 3;

/** PUT a blob to a presigned URL, reporting upload progress and returning the ETag. */
function xhrPut(
  url: string,
  blob: Blob,
  contentType: string | undefined,
  onLoaded: (n: number) => void,
  signal?: AbortSignal,
): Promise<string> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new Error('aborted'));
      return;
    }
    const xhr = new XMLHttpRequest();
    const onAbort = () => xhr.abort();
    const detach = () => signal?.removeEventListener('abort', onAbort);
    signal?.addEventListener('abort', onAbort, { once: true });

    xhr.open('PUT', url);
    if (contentType) xhr.setRequestHeader('Content-Type', contentType);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onLoaded(e.loaded);
    };
    xhr.onload = () => {
      detach();
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.getResponseHeader('ETag') ?? '');
      } else {
        reject(new Error(`upload_failed_${xhr.status}`));
      }
    };
    xhr.onerror = () => {
      detach();
      reject(new Error('network_error'));
    };
    xhr.onabort = () => {
      detach();
      reject(new Error('aborted'));
    };
    xhr.send(blob);
  });
}

async function withRetry<T>(
  task: (onLoaded: (n: number) => void) => Promise<T>,
  onLoaded: (n: number) => void,
  beforeRetry?: () => Promise<void>,
): Promise<T> {
  let lastErr: unknown;
  for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
    try {
      return await task(onLoaded);
    } catch (err) {
      lastErr = err;
      if (err instanceof Error && err.message === 'aborted') throw err;
      if (beforeRetry) {
        try {
          await beforeRetry();
        } catch {
          /* keep the original failure if re-signing also fails */
        }
      }
      await new Promise((r) => setTimeout(r, 400 * (attempt + 1)));
    }
  }
  throw lastErr;
}

function partBytes(partNumber: number, partSize: number, total: number): number {
  const start = (partNumber - 1) * partSize;
  return Math.min(partSize, total - start);
}

/** Upload every part with a small concurrency pool, tracking aggregate progress. */
async function uploadParts(
  file: File,
  urls: { partNumber: number; url: string }[],
  partSize: number,
  skip: Set<number>,
  initialDone: number,
  onProgress: ProgressFn,
  signal: AbortSignal,
  refreshUrl?: (partNumber: number) => Promise<string>,
): Promise<{ partNumber: number; etag: string }[]> {
  const total = file.size;
  let completedBytes = initialDone;
  const active = new Map<number, number>();
  const etags: { partNumber: number; etag: string }[] = [];

  const emit = () => onProgress(Math.min(completedBytes + [...active.values()].reduce((a, b) => a + b, 0), total), total);

  const queue = urls.filter((u) => !skip.has(u.partNumber));
  let cursor = 0;
  let failure: unknown = null;

  async function worker(): Promise<void> {
    while (cursor < queue.length && !failure && !signal.aborted) {
      const item = queue[cursor++]!;
      const start = (item.partNumber - 1) * partSize;
      const blob = file.slice(start, Math.min(start + partSize, total));
      let url = item.url;
      try {
        const etag = await withRetry(
          (loaded) => xhrPut(url, blob, undefined, loaded, signal),
          (loaded) => {
            active.set(item.partNumber, loaded);
            emit();
          },
          refreshUrl
            ? async () => {
                url = await refreshUrl(item.partNumber);
              }
            : undefined,
        );
        active.delete(item.partNumber);
        etags.push({ partNumber: item.partNumber, etag });
        completedBytes += partBytes(item.partNumber, partSize, total);
        emit();
      } catch (err) {
        active.delete(item.partNumber);
        failure = err;
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, queue.length) }, worker));
  if (signal.aborted) throw new Error('aborted');
  if (failure) throw failure;
  return etags;
}

function transferSingle(
  init: Extract<InitResponse, { mode: 'single' }>,
  file: File,
  onProgress: ProgressFn,
  signal: AbortSignal,
): Promise<void> {
  const { url, headers } = init.upload;
  return xhrPut(url, file, headers['Content-Type'], (loaded) => onProgress(loaded, file.size), signal).then(
    () => undefined,
  );
}

/** Full flow for a brand-new file: init → transfer → complete. */
export async function startUpload(
  file: File,
  opts: UploadOptions,
  onProgress: ProgressFn,
  signal: AbortSignal,
  onFileId?: (id: string) => void,
): Promise<FileDTO> {
  onProgress(0, file.size);
  const init = await api.initUpload({
    name: file.name,
    size: file.size,
    mimeType: file.type || 'application/octet-stream',
    expiresInDays: opts.expiresInDays ?? null,
    password: opts.password || null,
  });
  onFileId?.(init.file.id);

  if (init.mode === 'single') {
    await transferSingle(init, file, onProgress, signal);
    const { file: done } = await api.completeUpload(init.file.id);
    onProgress(file.size, file.size);
    return done;
  }

  const refreshUrl = (partNumber: number) => api.partUrl(init.file.id, partNumber).then((r) => r.url);
  const parts = await uploadParts(file, init.upload.urls, init.upload.partSize, new Set(), 0, onProgress, signal, refreshUrl);
  const { file: done } = await api.completeUpload(init.file.id, parts);
  onProgress(file.size, file.size);
  return done;
}

/** Resume an interrupted multipart upload: query R2 for done parts, upload the rest. */
export async function resumeUpload(fileId: string, file: File, onProgress: ProgressFn, signal: AbortSignal): Promise<FileDTO> {
  onProgress(0, file.size);
  const info = await api.getParts(fileId);
  const uploadedNumbers = new Set(info.uploaded.map((p) => p.partNumber));
  const alreadyBytes = info.uploaded.reduce((sum, p) => sum + partBytes(p.partNumber, info.partSize, file.size), 0);
  const etags = [...info.uploaded];

  const refreshUrl = (partNumber: number) => api.partUrl(info.fileId, partNumber).then((r) => r.url);
  const remaining = await uploadParts(file, info.urls, info.partSize, uploadedNumbers, alreadyBytes, onProgress, signal, refreshUrl);
  etags.push(...remaining);

  const { file: done } = await api.completeUpload(info.fileId, etags);
  onProgress(file.size, file.size);
  return done;
}
