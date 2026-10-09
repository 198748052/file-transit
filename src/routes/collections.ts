import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import {
  db,
  allRows,
  getRow,
  toCollectionDTO,
  type CollectionRow,
  type CollectionItemDTO,
  type FileRow,
  type FileStatus,
} from '../db.js';
import { asyncHandler, HttpError } from '../lib/http.js';
import { requireAuth } from '../auth.js';
import { generateCode } from '../lib/codes.js';
import { resolveBaseUrl } from '../lib/base-url.js';
import { presignGet } from '../lib/r2.js';

export const collectionsRouter = Router();

const DAY_MS = 86_400_000;
const MAX_ITEMS = 200;
const DOWNLOAD_ALL_LIMIT = 100;

// ─────────────────────────── helpers ───────────────────────────

function uniqueCollectionCode(): string {
  const stmt = db.prepare('SELECT 1 FROM collections WHERE code = ?');
  for (let i = 0; i < 10; i++) {
    const code = generateCode(8);
    if (!stmt.get(code)) return code;
  }
  throw new HttpError(500, 'could not allocate a unique collection code');
}

interface ItemJoinRow {
  file_id: string;
  original_name: string;
  size: number;
  mime_type: string;
  status: FileStatus;
  download_count: number;
}

/** Items of a collection. Public callers see only downloadable (ready) files. */
function itemsFor(collectionId: string, readyOnly: boolean): CollectionItemDTO[] {
  const rows = allRows<ItemJoinRow>(
    `SELECT f.id AS file_id, f.original_name, f.size, f.mime_type, f.status, f.download_count
       FROM collection_items ci
       JOIN files f ON f.id = ci.file_id
      WHERE ci.collection_id = ? ${readyOnly ? "AND f.status = 'ready'" : "AND f.status != 'deleted'"}
      ORDER BY ci.added_at ASC, f.created_at DESC`,
    collectionId,
  );
  return rows.map((r) => ({
    fileId: r.file_id,
    name: r.original_name,
    size: Number(r.size),
    mimeType: r.mime_type,
    status: r.status,
    downloadCount: Number(r.download_count),
  }));
}

function getCollectionOr404(id: string): CollectionRow {
  const row = getRow<CollectionRow>('SELECT * FROM collections WHERE id = ?', id);
  if (!row) throw new HttpError(404, 'collection_not_found');
  return row;
}

function findByCode(code: string): CollectionRow | undefined {
  return getRow<CollectionRow>('SELECT * FROM collections WHERE code = ?', code);
}

function markExpiredIfDue(row: CollectionRow): boolean {
  if (row.status === 'ready' && row.expires_at !== null && row.expires_at <= Date.now()) {
    db.prepare(`UPDATE collections SET status = 'expired' WHERE id = ?`).run(row.id);
    row.status = 'expired';
    return true;
  }
  return row.status === 'expired';
}

async function verifyCollectionPassword(row: CollectionRow, password?: string): Promise<void> {
  if (!row.password_hash) return;
  if (!password) throw new HttpError(400, 'password_required');
  const ok = await bcrypt.compare(password, row.password_hash);
  if (!ok) throw new HttpError(403, 'wrong_password');
}

// ─────────────────────────── public share ───────────────────────────

collectionsRouter.get(
  '/share/:code',
  asyncHandler(async (req, res) => {
    const row = findByCode(req.params.code!);
    if (!row || row.status === 'deleted') throw new HttpError(404, 'not_found');
    const expired = markExpiredIfDue(row);
    const items = itemsFor(row.id, !expired);
    res.json({
      found: true,
      expired,
      requiresPassword: row.password_hash !== null,
      name: row.name,
      createdAt: Number(row.created_at),
      expiresAt: row.expires_at === null ? null : Number(row.expires_at),
      downloadCount: Number(row.download_count),
      status: row.status,
      items: expired ? [] : items,
    });
  }),
);

const downloadSchema = z.object({ password: z.string().max(128).optional() });

collectionsRouter.post(
  '/share/:code/item/:fileId/download',
  asyncHandler(async (req, res) => {
    const row = findByCode(req.params.code!);
    if (!row || row.status === 'deleted') throw new HttpError(404, 'not_found');
    if (markExpiredIfDue(row)) throw new HttpError(410, 'expired');
    if (row.status !== 'ready') throw new HttpError(409, 'not_ready');

    const { password } = downloadSchema.parse(req.body ?? {});
    await verifyCollectionPassword(row, password);

    const file = getRow<FileRow>(
      `SELECT f.* FROM files f
        JOIN collection_items ci ON ci.file_id = f.id
       WHERE ci.collection_id = ? AND f.id = ?`,
      row.id,
      req.params.fileId!,
    );
    if (!file || file.status !== 'ready') throw new HttpError(404, 'file_not_found');

    const url = await presignGet(file.r2_key, file.original_name);
    const now = Date.now();
    db.prepare('UPDATE files SET download_count = download_count + 1, last_download_at = ? WHERE id = ?').run(now, file.id);
    db.prepare('UPDATE collections SET download_count = download_count + 1 WHERE id = ?').run(row.id);
    res.json({ url });
  }),
);

collectionsRouter.post(
  '/share/:code/download-all',
  asyncHandler(async (req, res) => {
    const row = findByCode(req.params.code!);
    if (!row || row.status === 'deleted') throw new HttpError(404, 'not_found');
    if (markExpiredIfDue(row)) throw new HttpError(410, 'expired');
    if (row.status !== 'ready') throw new HttpError(409, 'not_ready');

    const { password } = downloadSchema.parse(req.body ?? {});
    await verifyCollectionPassword(row, password);

    const files = allRows<FileRow>(
      `SELECT f.* FROM files f
        JOIN collection_items ci ON ci.file_id = f.id
       WHERE ci.collection_id = ? AND f.status = 'ready'
       ORDER BY ci.added_at ASC, f.created_at DESC
       LIMIT ?`,
      row.id,
      DOWNLOAD_ALL_LIMIT,
    );
    if (files.length === 0) throw new HttpError(404, 'empty_collection');

    const now = Date.now();
    const downloads: { name: string; url: string }[] = [];
    for (const file of files) {
      downloads.push({ name: file.original_name, url: await presignGet(file.r2_key, file.original_name) });
      db.prepare('UPDATE files SET download_count = download_count + 1, last_download_at = ? WHERE id = ?').run(now, file.id);
    }
    db.prepare('UPDATE collections SET download_count = download_count + 1 WHERE id = ?').run(row.id);
    res.json({ downloads });
  }),
);

// ─────────────────────────── admin management ───────────────────────────

collectionsRouter.get('/', requireAuth, (req, res) => {
  const baseUrl = resolveBaseUrl(req);
  const rows = allRows<CollectionRow>(
    `SELECT * FROM collections WHERE status != 'deleted' ORDER BY created_at DESC`,
  );
  res.json({ collections: rows.map((row) => toCollectionDTO(row, baseUrl, itemsFor(row.id, false))) });
});

const createSchema = z.object({
  name: z.string().trim().min(1).max(120),
  fileIds: z.array(z.string().min(1)).min(1).max(MAX_ITEMS),
  expiresInDays: z.number().int().positive().max(3650).nullish(),
  password: z.string().min(1).max(128).nullish(),
});

collectionsRouter.post(
  '/',
  requireAuth,
  asyncHandler(async (req, res) => {
    const input = createSchema.parse(req.body ?? {});
    const ids = [...new Set(input.fileIds)];

    const ready = allRows<{ id: string }>(
      `SELECT id FROM files WHERE status = 'ready' AND id IN (${ids.map(() => '?').join(',')})`,
      ...ids,
    );
    if (ready.length === 0) throw new HttpError(400, 'no_ready_files');

    const id = randomUUID();
    const code = uniqueCollectionCode();
    const now = Date.now();
    const expiresAt = input.expiresInDays ? now + input.expiresInDays * DAY_MS : null;
    const passwordHash = input.password ? await bcrypt.hash(input.password, 10) : null;

    db.prepare(
      `INSERT INTO collections (id, code, name, status, created_at, expires_at, password_hash, download_count)
       VALUES (?, ?, ?, 'ready', ?, ?, ?, 0)`,
    ).run(id, code, input.name, now, expiresAt, passwordHash);

    const insertItem = db.prepare(
      `INSERT OR IGNORE INTO collection_items (collection_id, file_id, added_at) VALUES (?, ?, ?)`,
    );
    for (const fileId of ready.map((r) => r.id)) insertItem.run(id, fileId, now);

    const row = getCollectionOr404(id);
    res.status(201).json({ collection: toCollectionDTO(row, resolveBaseUrl(req), itemsFor(id, false)) });
  }),
);

collectionsRouter.get('/:id', requireAuth, (req, res) => {
  const row = getCollectionOr404(req.params.id!);
  res.json({ collection: toCollectionDTO(row, resolveBaseUrl(req), itemsFor(row.id, false)) });
});

const patchSchema = z.object({
  name: z.string().trim().min(1).max(120).optional(),
  expiresInDays: z.number().int().positive().max(3650).nullish(),
  password: z.string().min(1).max(128).nullish(),
  clearPassword: z.boolean().optional(),
});

collectionsRouter.patch(
  '/:id',
  requireAuth,
  asyncHandler(async (req, res) => {
    const row = getCollectionOr404(req.params.id!);
    if (row.status === 'expired' || row.status === 'deleted') throw new HttpError(409, 'collection_not_editable');
    const input = patchSchema.parse(req.body ?? {});

    const name = input.name ?? row.name;
    let expiresAt = row.expires_at;
    if (input.expiresInDays !== undefined) {
      expiresAt = input.expiresInDays === null ? null : Date.now() + input.expiresInDays * DAY_MS;
    }
    let passwordHash = row.password_hash;
    if (input.clearPassword) passwordHash = null;
    else if (input.password) passwordHash = await bcrypt.hash(input.password, 10);

    db.prepare('UPDATE collections SET name = ?, expires_at = ?, password_hash = ? WHERE id = ?').run(
      name,
      expiresAt,
      passwordHash,
      row.id,
    );
    const updated = getCollectionOr404(row.id);
    res.json({ collection: toCollectionDTO(updated, resolveBaseUrl(req), itemsFor(row.id, false)) });
  }),
);

collectionsRouter.delete('/:id', requireAuth, (req, res) => {
  const row = getCollectionOr404(req.params.id!);
  db.prepare('DELETE FROM collections WHERE id = ?').run(row.id);
  res.json({ ok: true });
});
