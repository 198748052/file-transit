export interface FileKind {
  label: string;
  tone: string;
}

interface Rule {
  extensions: string[];
  label: string;
  tone: string;
}

const RULES: Rule[] = [
  { extensions: ['pdf'], label: 'PDF', tone: 'pdf' },
  { extensions: ['doc', 'docx', 'rtf', 'odt'], label: 'DOC', tone: 'doc' },
  { extensions: ['xls', 'xlsx', 'csv', 'ods'], label: 'XLS', tone: 'sheet' },
  { extensions: ['ppt', 'pptx', 'odp', 'key'], label: 'PPT', tone: 'slide' },
  {
    extensions: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico', 'heic', 'avif', 'tif', 'tiff'],
    label: 'IMG',
    tone: 'image',
  },
  { extensions: ['mp4', 'mov', 'mkv', 'avi', 'webm', 'flv', 'wmv', 'm4v'], label: 'VID', tone: 'video' },
  { extensions: ['mp3', 'wav', 'flac', 'aac', 'm4a', 'ogg', 'opus', 'wma'], label: 'AUD', tone: 'audio' },
  { extensions: ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'zst', 'iso'], label: 'ZIP', tone: 'archive' },
  {
    extensions: ['js', 'ts', 'jsx', 'tsx', 'json', 'html', 'css', 'scss', 'py', 'go', 'java', 'rs', 'sh', 'yml', 'yaml', 'xml', 'sql', 'toml'],
    label: 'CODE',
    tone: 'code',
  },
  { extensions: ['txt', 'md', 'log', 'ini', 'conf', 'env'], label: 'TXT', tone: 'text' },
];

export function fileKind(name: string): FileKind {
  const dot = name.lastIndexOf('.');
  const ext = dot >= 0 ? name.slice(dot + 1).toLowerCase() : '';
  const rule = RULES.find((item) => item.extensions.includes(ext));
  if (rule) return { label: rule.label, tone: rule.tone };
  if (ext && ext.length <= 4) return { label: ext.toUpperCase(), tone: 'other' };
  return { label: 'FILE', tone: 'other' };
}
