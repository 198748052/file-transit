const EXTENSION_RE = /^[a-z0-9]{1,12}$/;

/** Extract a safe, lowercase extension (".pdf") from a display name. */
export function extensionOf(name: string): string {
  const dot = name.lastIndexOf('.');
  if (dot <= 0 || dot === name.length - 1) return '';
  const ext = name.slice(dot + 1).toLowerCase();
  return EXTENSION_RE.test(ext) ? `.${ext}` : '';
}

/**
 * Immutable R2 key layout: `uploads/<yyyy>/<mm>/<dd>/<fileId><ext>`.
 *
 * The original filename is intentionally left out so renaming or moving a file
 * only touches the database. Keys never have to be rewritten after upload, and
 * the date folders keep the bucket easy to browse.
 */
export function buildUploadKey(id: string, name: string, at: Date = new Date()): string {
  const year = at.getUTCFullYear();
  const month = String(at.getUTCMonth() + 1).padStart(2, '0');
  const day = String(at.getUTCDate()).padStart(2, '0');
  return `uploads/${year}/${month}/${day}/${id}${extensionOf(name)}`;
}

/** Legacy layout: `uploads/<yyyy>/<mm>/<id>-<name>` (four path segments). */
export function isLegacyUploadKey(key: string): boolean {
  return /^uploads\/\d{4}\/\d{2}\/[^/]+$/.test(key);
}
