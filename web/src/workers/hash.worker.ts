import { Sha256 } from '../lib/sha256';

interface HashRequest {
  file: File;
}

const ctx = self as unknown as {
  onmessage: ((event: MessageEvent<HashRequest>) => void) | null;
  postMessage: (message: unknown) => void;
};

ctx.onmessage = async (event) => {
  const { file } = event.data;
  const hasher = new Sha256();
  const reader = file.stream().getReader();
  let loaded = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      hasher.update(value);
      loaded += value.length;
      ctx.postMessage({ type: 'progress', loaded, total: file.size });
    }
    ctx.postMessage({ type: 'done', hash: hasher.hex() });
  } catch (err) {
    ctx.postMessage({ type: 'error', message: err instanceof Error ? err.message : 'hash_failed' });
  }
};
