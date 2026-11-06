export interface TextFormatOption {
  value: string;
  label: string;
  ext: string;
}

export const TEXT_FORMATS: TextFormatOption[] = [
  { value: 'plain', label: '纯文本', ext: 'txt' },
  { value: 'markdown', label: 'Markdown', ext: 'md' },
  { value: 'json', label: 'JSON', ext: 'json' },
  { value: 'javascript', label: 'JavaScript', ext: 'js' },
  { value: 'typescript', label: 'TypeScript', ext: 'ts' },
  { value: 'python', label: 'Python', ext: 'py' },
  { value: 'bash', label: 'Shell / Bash', ext: 'sh' },
  { value: 'sql', label: 'SQL', ext: 'sql' },
  { value: 'html', label: 'HTML', ext: 'html' },
  { value: 'css', label: 'CSS', ext: 'css' },
  { value: 'yaml', label: 'YAML', ext: 'yaml' },
  { value: 'go', label: 'Go', ext: 'go' },
  { value: 'rust', label: 'Rust', ext: 'rs' },
  { value: 'java', label: 'Java', ext: 'java' },
  { value: 'kotlin', label: 'Kotlin', ext: 'kt' },
];

export function formatLabel(format?: string): string {
  const found = TEXT_FORMATS.find((item) => item.value === format);
  return found ? found.label : '纯文本';
}

export function formatExtension(format?: string): string {
  const found = TEXT_FORMATS.find((item) => item.value === format);
  return found ? found.ext : 'txt';
}

export function downloadTextAsFile(title: string, content: string, format?: string): void {
  const ext = formatExtension(format);
  const safeBase = (title || 'text')
    .trim()
    .replace(/[\\/:*?"<>|]+/g, '_')
    .slice(0, 64) || 'text';
  const fileName = safeBase.endsWith(`.${ext}`) ? safeBase : `${safeBase}.${ext}`;
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function escapeHtml(raw: string): string {
  return raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

const KEYWORDS_RE =
  /\b(function|return|const|let|var|if|else|for|while|do|switch|case|break|continue|class|interface|type|extends|implements|import|from|export|default|async|await|try|catch|finally|throw|new|this|super|null|undefined|true|false|def|elif|except|lambda|with|as|pass|yield|in|is|not|and|or|None|True|False|fn|pub|mod|use|struct|enum|impl|trait|match|mut|ref|package|fun|val|object|when|companion|data|sealed|select|where|insert|into|values|update|set|delete|create|table|alter|drop|join|left|right|inner|outer|on|group|by|order|limit|offset)\b/g;

/** Lightweight syntax highlighter returning safe HTML. */
export function highlightCode(raw: string, lang = 'plain'): string {
  const normalized = (lang || 'plain').toLowerCase();
  if (normalized === 'plain') return escapeHtml(raw);

  const tokens: string[] = [];
  const pushToken = (cls: string, text: string): string => {
    const id = `\u0000T${tokens.length}\u0000`;
    tokens.push(`<span class="hl-${cls}">${escapeHtml(text)}</span>`);
    return id;
  };

  let work = raw;

  // Comments & Strings first so keywords inside them are not touched
  if (normalized === 'python' || normalized === 'bash' || normalized === 'yaml' || normalized === 'sh') {
    work = work.replace(/(#[^\n]*)/g, (m) => pushToken('comment', m));
  } else if (normalized === 'sql') {
    work = work.replace(/(--[^\n]*)/g, (m) => pushToken('comment', m));
  } else if (normalized !== 'json' && normalized !== 'html') {
    work = work.replace(/(\/\/[^\n]*|\/\*[\s\S]*?\*\/)/g, (m) => pushToken('comment', m));
  }

  work = work.replace(/("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`)/g, (m) =>
    pushToken('string', m),
  );

  work = escapeHtml(work);

  work = work.replace(/\b(\d+(?:\.\d+)?)\b/g, '<span class="hl-number">$1</span>');
  if (normalized !== 'json') {
    work = work.replace(KEYWORDS_RE, '<span class="hl-keyword">$1</span>');
  }

  return work.replace(/\u0000T(\d+)\u0000/g, (_, idx) => tokens[Number(idx)] ?? '');
}

export interface MarkdownBlock {
  type: 'html' | 'code';
  html?: string;
  code?: string;
  lang?: string;
}

function renderInlineMarkdown(text: string): string {
  let out = escapeHtml(text);
  out = out.replace(/`([^`]+)`/g, '<code class="md-inline-code">$1</code>');
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  out = out.replace(/\*([^*]+)\*/g, '<em>$1</em>');
  out = out.replace(
    /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
    '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>',
  );
  return out;
}

/**
 * Parse Markdown into alternating HTML and fenced-code blocks so the Vue
 * component can attach a native "Copy Code" button to each code block.
 */
export function parseMarkdownBlocks(raw: string): MarkdownBlock[] {
  const blocks: MarkdownBlock[] = [];
  const fenceRe = /```([a-zA-Z0-9_-]*)\r?\n([\s\S]*?)```/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = fenceRe.exec(raw)) !== null) {
    if (match.index > lastIndex) {
      blocks.push({
        type: 'html',
        html: renderMarkdownSegment(raw.slice(lastIndex, match.index)),
      });
    }
    blocks.push({
      type: 'code',
      lang: (match[1] || 'plain').toLowerCase(),
      code: match[2]?.replace(/\r?\n$/, '') ?? '',
    });
    lastIndex = fenceRe.lastIndex;
  }

  if (lastIndex < raw.length) {
    blocks.push({
      type: 'html',
      html: renderMarkdownSegment(raw.slice(lastIndex)),
    });
  }

  return blocks;
}

function renderMarkdownSegment(segment: string): string {
  const lines = segment.split(/\r?\n/);
  const html: string[] = [];
  let inList = false;

  const closeList = () => {
    if (inList) {
      html.push('</ul>');
      inList = false;
    }
  };

  for (const rawLine of lines) {
    const line = rawLine.trimEnd();
    if (!line.trim()) {
      closeList();
      continue;
    }
    const heading = /^(#{1,6})\s+(.+)$/.exec(line);
    if (heading) {
      closeList();
      const level = heading[1]!.length;
      html.push(`<h${level} class="md-h${level}">${renderInlineMarkdown(heading[2]!)}</h${level}>`);
      continue;
    }
    if (/^---+$/.test(line.trim())) {
      closeList();
      html.push('<hr class="md-hr" />');
      continue;
    }
    const quote = /^>\s?(.*)$/.exec(line);
    if (quote) {
      closeList();
      html.push(`<blockquote class="md-quote">${renderInlineMarkdown(quote[1]!)}</blockquote>`);
      continue;
    }
    const bullet = /^[-*+]\s+(.+)$/.exec(line);
    if (bullet) {
      if (!inList) {
        html.push('<ul class="md-list">');
        inList = true;
      }
      html.push(`<li>${renderInlineMarkdown(bullet[1]!)}</li>`);
      continue;
    }
    closeList();
    html.push(`<p class="md-p">${renderInlineMarkdown(line)}</p>`);
  }

  closeList();
  return html.join('\n');
}
