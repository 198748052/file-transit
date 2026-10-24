export function hashFile(
  file: File,
  onProgress?: (loaded: number, total: number) => void,
  signal?: AbortSignal,
): Promise<string> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new Error('aborted'));
      return;
    }

    const worker = new Worker(new URL('../workers/hash.worker.ts', import.meta.url), { type: 'module' });

    const cleanup = () => {
      worker.terminate();
      signal?.removeEventListener('abort', onAbort);
    };
    const onAbort = () => {
      cleanup();
      reject(new Error('aborted'));
    };
    signal?.addEventListener('abort', onAbort, { once: true });

    worker.onmessage = (event: MessageEvent<{ type: string; loaded?: number; total?: number; hash?: string; message?: string }>) => {
      const data = event.data;
      if (data.type === 'progress' && typeof data.loaded === 'number' && typeof data.total === 'number') {
        onProgress?.(data.loaded, data.total);
      } else if (data.type === 'done' && data.hash) {
        cleanup();
        resolve(data.hash);
      } else if (data.type === 'error') {
        cleanup();
        reject(new Error(data.message ?? 'hash_failed'));
      }
    };
    worker.onerror = (event) => {
      cleanup();
      reject(new Error(event.message || 'hash_failed'));
    };

    worker.postMessage({ file });
  });
}
