export type PreviewKind = 'image' | 'video' | 'audio' | 'pdf' | 'text';

const TEXT_EXTENSIONS = new Set([
  'txt', 'md', 'markdown', 'log', 'ini', 'conf', 'cfg', 'env',
  'json', 'jsonl', 'xml', 'yaml', 'yml', 'toml', 'csv', 'tsv',
  'js', 'mjs', 'cjs', 'ts', 'jsx', 'tsx', 'vue', 'css', 'scss', 'less',
  'html', 'htm', 'py', 'go', 'java', 'rs', 'c', 'h', 'cpp', 'hpp',
  'sh', 'bash', 'zsh', 'sql', 'rb', 'php', 'lua', 'swift', 'kt', 'gradle',
]);

const IMAGE_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'avif', 'ico']);
const VIDEO_EXTENSIONS = new Set(['mp4', 'webm', 'mov', 'm4v', 'ogv']);
const AUDIO_EXTENSIONS = new Set(['mp3', 'wav', 'ogg', 'm4a', 'aac', 'flac', 'opus']);

function extensionOf(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : '';
}

export function previewKind(name: string, mimeType: string): PreviewKind | null {
  const mime = (mimeType || '').toLowerCase();
  const ext = extensionOf(name);

  if (mime.startsWith('image/') || IMAGE_EXTENSIONS.has(ext)) return 'image';
  if (mime.startsWith('video/') || VIDEO_EXTENSIONS.has(ext)) return 'video';
  if (mime.startsWith('audio/') || AUDIO_EXTENSIONS.has(ext)) return 'audio';
  if (mime === 'application/pdf' || ext === 'pdf') return 'pdf';
  if (mime.startsWith('text/') || TEXT_EXTENSIONS.has(ext)) return 'text';
  return null;
}

export function canPreview(name: string, mimeType: string): boolean {
  return previewKind(name, mimeType) !== null;
}
