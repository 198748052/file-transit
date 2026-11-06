import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { db, allRows, getRow, toTextDTO, type TextRow } from '../db.js';
import { asyncHandler, HttpError } from '../lib/http.js';
import { requireAuth } from '../auth.js';
import { generateCode } from '../lib/codes.js';
import { resolveBaseUrl } from '../lib/base-url.js';
import { actorOf, recordLog, requestSource } from '../lib/audit.js';
import { clearSharePasswordFailures, guardShareAccess, noteSharePasswordFailure } from '../lib/share-limit.js';

export const textsRouter = Router();

const DAY_MS = 86_400_000;
const MAX_CONTENT = 100_000;

// ─────────────────────────── helpers ───────────────────────────

function uniqueTextCode(): string {
  const stmt = db.prepare('SELECT 1 FROM texts WHERE code = ?');
  for (let i = 0; i < 10; i++) {
    const code = generateCode(8);
    if (!stmt.get(code)) return code;
  }
  throw new HttpError(500, 'could not allocate a unique text code');
}

function getTextOr404(id: string): TextRow {
  const row = getRow<TextRow>('SELECT * FROM texts WHERE id = ?', id);
  if (!row) throw new HttpError(404, 'text_not_found');
  return row;
}

function findByCode(code: string): TextRow | undefined {
  return getRow<TextRow>('SELECT * FROM texts WHERE code = ?', code);
}

function isExpired(row: TextRow): boolean {
  return row.expires_at !== null && row.expires_at <= Date.now();
}

function isExhausted(row: TextRow): boolean {
  return (
    row.max_views !== null &&
    row.max_views !== undefined &&
    Number(row.view_count ?? 0) >= Number(row.max_views)
  );
}

/** A short title derived from the first non-empty line when none is given. */
function displayTitle(title: string | undefined, content: string): string {
  const trimmed = (title ?? '').trim();
  if (trimmed) return trimmed.slice(0, 120);
  const line = content
    .split(/\r?\n/)
    .map((part) => part.trim())
    .find((part) => part.length > 0);
  return line ? line.slice(0, 60) : '未命名文本';
}

const ALLOWED_FORMATS = new Set([
  'plain',
  'markdown',
  'json',
  'javascript',
  'typescript',
  'python',
  'bash',
  'sql',
  'html',
  'css',
  'yaml',
  'go',
  'rust',
  'java',
  'kotlin',
]);

function normalizeFormat(raw?: string): string {
  const val = (raw ?? 'plain').trim().toLowerCase();
  return ALLOWED_FORMATS.has(val) ? val : 'plain';
}

// ─────────────────────────── public share ───────────────────────────
// Declared before requireAuth so share pages stay reachable without a token.

textsRouter.get('/share/:code', (req, res) => {
  const row = findByCode(req.params.code!);
  if (!row) throw new HttpError(404, 'not_found');
  res.json({
    found: true,
    expired: isExpired(row),
    exhausted: isExhausted(row),
    requiresPassword: row.password_hash !== null,
    title: row.title,
    format: row.format || 'plain',
    charCount: row.content.length,
    createdAt: Number(row.created_at),
    updatedAt: Number(row.updated_at),
    expiresAt: row.expires_at === null ? null : Number(row.expires_at),
    viewCount: Number(row.view_count ?? 0),
    maxViews: row.max_views === null || row.max_views === undefined ? null : Number(row.max_views),
  });
});

const revealSchema = z.object({ password: z.string().max(128).optional() });

textsRouter.post(
  '/share/:code',
  asyncHandler(async (req, res) => {
    const row = findByCode(req.params.code!);
    if (!row) throw new HttpError(404, 'not_found');
    if (isExpired(row)) throw new HttpError(410, 'expired');
    if (isExhausted(row)) throw new HttpError(410, 'download_limit_reached');

    guardShareAccess(req, row.code, 'text');
    const { password } = revealSchema.parse(req.body ?? {});

    if (row.password_hash) {
      if (!password) throw new HttpError(400, 'password_required');
      const ok = await bcrypt.compare(password, row.password_hash);
      if (!ok) {
        noteSharePasswordFailure(req, row.code);
        recordLog({
          action: 'text.view',
          actor: actorOf(req),
          ...requestSource(req),
          targetType: 'text',
          targetId: row.id,
          targetName: row.title,
          result: 'fail',
          detail: { reason: 'wrong_password' },
        });
        throw new HttpError(403, 'wrong_password');
      }
      clearSharePasswordFailures(req, row.code);
    }

    const updated = db
      .prepare(
        `UPDATE texts
            SET view_count = view_count + 1
          WHERE id = ? AND (max_views IS NULL OR view_count < max_views)`,
      )
      .run(row.id);
    if (updated.changes === 0) throw new HttpError(410, 'download_limit_reached');

    recordLog({
      action: 'text.view',
      actor: actorOf(req),
      ...requestSource(req),
      targetType: 'text',
      targetId: row.id,
      targetName: row.title,
      detail: { shareCode: row.code, charCount: row.content.length },
    });
    res.json({
      content: row.content,
      format: row.format || 'plain',
      viewCount: Number(row.view_count ?? 0) + 1,
      maxViews: row.max_views === null || row.max_views === undefined ? null : Number(row.max_views),
    });
  }),
);

// ─────────────────────────── admin API ───────────────────────────

textsRouter.use(requireAuth);

textsRouter.get('/', (req, res) => {
  const q = typeof req.query.q === 'string' ? req.query.q.trim().slice(0, 120) : '';
  const now = Date.now();
  const live = '(expires_at IS NULL OR expires_at > ?)';
  const rows = q
    ? allRows<TextRow>(
        `SELECT * FROM texts
          WHERE ${live} AND (title LIKE ? OR content LIKE ?)
          ORDER BY updated_at DESC LIMIT 500`,
        now,
        `%${q}%`,
        `%${q}%`,
      )
    : allRows<TextRow>(`SELECT * FROM texts WHERE ${live} ORDER BY updated_at DESC LIMIT 500`, now);
  const baseUrl = resolveBaseUrl(req);
  res.json({ texts: rows.map((row) => toTextDTO(row, baseUrl)) });
});

const createSchema = z.object({
  title: z.string().max(120).optional(),
  content: z.string().min(1).max(MAX_CONTENT),
  format: z.string().max(32).optional(),
  expiresInDays: z.number().int().positive().max(3650).nullish(),
  password: z.string().min(1).max(128).nullish(),
  maxViews: z.number().int().positive().max(100_000).nullish(),
});

textsRouter.post(
  '/',
  asyncHandler(async (req, res) => {
    const input = createSchema.parse(req.body ?? {});
    const now = Date.now();
    const id = randomUUID();
    const title = displayTitle(input.title, input.content);
    const format = normalizeFormat(input.format);
    const expiresAt = input.expiresInDays ? now + input.expiresInDays * DAY_MS : null;
    const passwordHash = input.password ? await bcrypt.hash(input.password, 10) : null;
    const maxViews = input.maxViews ?? null;

    db.prepare(
      `INSERT INTO texts (id, code, title, content, format, created_at, updated_at, expires_at, password_hash, view_count, max_views)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)`,
    ).run(id, uniqueTextCode(), title, input.content, format, now, now, expiresAt, passwordHash, maxViews);

    recordLog({
      action: 'text.create',
      actor: actorOf(req),
      ...requestSource(req),
      targetType: 'text',
      targetId: id,
      targetName: title,
      detail: {
        charCount: input.content.length,
        format,
        expiresInDays: input.expiresInDays ?? null,
        hasPassword: !!passwordHash,
        maxViews,
      },
    });
    res.status(201).json({ text: toTextDTO(getTextOr404(id), resolveBaseUrl(req)) });
  }),
);

const patchSchema = z.object({
  title: z.string().max(120).optional(),
  content: z.string().min(1).max(MAX_CONTENT).optional(),
  format: z.string().max(32).optional(),
  expiresInDays: z.number().int().positive().max(3650).nullish(),
  password: z.string().min(1).max(128).nullish(),
  clearPassword: z.boolean().optional(),
  maxViews: z.number().int().positive().max(100_000).nullish(),
});

textsRouter.patch(
  '/:id',
  asyncHandler(async (req, res) => {
    const row = getTextOr404(req.params.id!);
    const input = patchSchema.parse(req.body ?? {});

    const content = input.content ?? row.content;
    const title =
      input.title !== undefined ? displayTitle(input.title, content) : row.title;
    const format = input.format !== undefined ? normalizeFormat(input.format) : (row.format || 'plain');

    let expiresAt = row.expires_at;
    if (input.expiresInDays !== undefined) {
      expiresAt = input.expiresInDays === null ? null : Date.now() + input.expiresInDays * DAY_MS;
    }

    let passwordHash = row.password_hash;
    if (input.clearPassword) passwordHash = null;
    else if (input.password) passwordHash = await bcrypt.hash(input.password, 10);

    let maxViews = row.max_views ?? null;
    let viewCount = Number(row.view_count ?? 0);
    if (input.maxViews !== undefined) {
      maxViews = input.maxViews;
      if (maxViews !== null && viewCount >= maxViews) {
        viewCount = 0;
      }
    }

    db.prepare(
      'UPDATE texts SET title = ?, content = ?, format = ?, updated_at = ?, expires_at = ?, password_hash = ?, view_count = ?, max_views = ? WHERE id = ?',
    ).run(title, content, format, Date.now(), expiresAt, passwordHash, viewCount, maxViews, row.id);

    const changed: string[] = [];
    if (title !== row.title) changed.push('title');
    if (content !== row.content) changed.push('content');
    if (format !== (row.format || 'plain')) changed.push('format');
    if (expiresAt !== row.expires_at) changed.push('expiry');
    if (passwordHash !== row.password_hash) changed.push('password');
    if (maxViews !== (row.max_views ?? null)) changed.push('maxViews');

    recordLog({
      action: 'text.update',
      actor: actorOf(req),
      ...requestSource(req),
      targetType: 'text',
      targetId: row.id,
      targetName: title,
      detail: { changed, charCount: content.length },
    });
    res.json({ text: toTextDTO(getTextOr404(row.id), resolveBaseUrl(req)) });
  }),
);

textsRouter.delete('/:id', (req, res) => {
  const row = getTextOr404(req.params.id!);
  db.prepare('DELETE FROM texts WHERE id = ?').run(row.id);
  recordLog({
    action: 'text.delete',
    actor: actorOf(req),
    ...requestSource(req),
    targetType: 'text',
    targetId: row.id,
    targetName: row.title,
    detail: { charCount: row.content.length },
  });
  res.json({ ok: true });
});
