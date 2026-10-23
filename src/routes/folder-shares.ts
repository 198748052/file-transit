import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import {
  db,
  allRows,
  getRow,
  type FileRow,
  type FolderRow,
  type FolderShareRow,
  type FolderShareDTO,
} from '../db.js';
import { asyncHandler, HttpError } from '../lib/http.js';
import { requireAuth } from '../auth.js';
import { generateCode } from '../lib/codes.js';
import { resolveBaseUrl } from '../lib/base-url.js';
import { presignGet } from '../lib/r2.js';
import { actorOf, recordLog, requestSource } from '../lib/audit.js';

export const folderSharesRouter = Router();

const DAY_MS = 86_400_000;
const DOWNLOAD_ALL_LIMIT = 100;

// ─────────────────────────── helpers ───────────────────────────

function uniqueShareCode(): string {
  const stmt = db.prepare('SELECT 1 FROM folder_shares WHERE code = ?');
  for (let i = 0; i < 10; i++) {
    const code = generateCode(8);
    if (!stmt.get(code)) return code;
  }
  throw new HttpError(500, 'could not allocate a unique folder share code');
}

function folderMap(): Map<string, FolderRow> {
  return new Map(allRows<FolderRow>('SELECT * FROM folders').map((row) => [row.id, row]));
}

function folderPath(folderId: string | null, map: Map<string, FolderRow>): string {
  const names: string[] = [];
  const guard = new Set<string>();
  let cursor = folderId;
  while (cursor && !guard.has(cursor)) {
    guard.add(cursor);
    const folder = map.get(cursor);
    if (!folder) break;
    names.unshift(folder.name);
    cursor = folder.parent_id;
  }
  return names.join(' / ');
}

/** Path of `folderId` relative to the shared root; '' for the root itself. */
function relativePath(
  folderId: string | null,
  rootId: string,
  map: Map<string, FolderRow>,
): string {
  const names: string[] = [];
  const guard = new Set<string>();
  let cursor = folderId;
  while (cursor && cursor !== rootId && !guard.has(cursor)) {
    guard.add(cursor);
    const folder = map.get(cursor);
    if (!folder) break;
    names.unshift(folder.name);
    cursor = folder.parent_id;
  }
  return names.join(' / ');
}

const SUBTREE_CTE = `WITH RECURSIVE tree(id) AS (
  SELECT id FROM folders WHERE id = ?
  UNION ALL
  SELECT f.id FROM folders f JOIN tree t ON f.parent_id = t.id
)`;

function filesInSubtree(folderId: string): FileRow[] {
  return allRows<FileRow>(
    `${SUBTREE_CTE}
     SELECT * FROM files
      WHERE status = 'ready' AND folder_id IN (SELECT id FROM tree)
      ORDER BY created_at DESC`,
    folderId,
  );
}

function getShareOr404(id: string): FolderShareRow {
  const row = getRow<FolderShareRow>('SELECT * FROM folder_shares WHERE id = ?', id);
  if (!row) throw new HttpError(404, 'folder_share_not_found');
  return row;
}

function findByCode(code: string): FolderShareRow | undefined {
  return getRow<FolderShareRow>('SELECT * FROM folder_shares WHERE code = ?', code);
}

function markExpiredIfDue(row: FolderShareRow): boolean {
  if (row.status === 'ready' && row.expires_at !== null && row.expires_at <= Date.now()) {
    db.prepare(`UPDATE folder_shares SET status = 'expired' WHERE id = ?`).run(row.id);
    row.status = 'expired';
    return true;
  }
  return row.status === 'expired';
}

async function verifyPassword(row: FolderShareRow, password?: string): Promise<void> {
  if (!row.password_hash) return;
  if (!password) throw new HttpError(400, 'password_required');
  const ok = await bcrypt.compare(password, row.password_hash);
  if (!ok) throw new HttpError(403, 'wrong_password');
}

interface FolderShareItem {
  fileId: string;
  name: string;
  size: number;
  mimeType: string;
  path: string;
  downloadCount: number;
  status: string;
}

function itemsFor(row: FolderShareRow): FolderShareItem[] {
  const folders = folderMap();
  return filesInSubtree(row.folder_id).map((file) => ({
    fileId: file.id,
    name: file.original_name,
    size: Number(file.size),
    mimeType: file.mime_type,
    path: relativePath(file.folder_id, row.folder_id, folders),
    downloadCount: Number(file.download_count),
    status: file.status,
  }));
}

function toFolderShareDTO(
  row: FolderShareRow,
  baseUrl: string,
  folders: Map<string, FolderRow>,
  items: FolderShareItem[],
): FolderShareDTO {
  return {
    id: row.id,
    code: row.code,
    folderId: row.folder_id,
    folderPath: folderPath(row.folder_id, folders),
    name: row.name,
    status: row.status,
    createdAt: Number(row.created_at),
    expiresAt: row.expires_at === null ? null : Number(row.expires_at),
    hasPassword: row.password_hash !== null,
    downloadCount: Number(row.download_count),
    shareUrl: `${baseUrl}/f/${row.code}`,
    itemCount: items.length,
    totalSize: items.reduce((sum, item) => sum + item.size, 0),
  };
}

function logPasswordFailure(req: Parameters<typeof requestSource>[0], row: FolderShareRow, reason: string): void {
  recordLog({
    action: 'folder_share.download',
    actor: actorOf(req),
    ...requestSource(req),
    targetType: 'folder_share',
    targetId: row.id,
    targetName: row.name,
    result: 'fail',
    detail: { shareCode: row.code, folderId: row.folder_id, reason },
  });
}

// ─────────────────────────── public share ───────────────────────────

folderSharesRouter.get(
  '/share/:code',
  asyncHandler(async (req, res) => {
    const row = findByCode(req.params.code!);
    if (!row || row.status === 'deleted') throw new HttpError(404, 'not_found');
    const expired = markExpiredIfDue(row);
    const folders = folderMap();
    const items = expired ? [] : itemsFor(row);
    recordLog({
      action: 'folder_share.view',
      actor: actorOf(req),
      ...requestSource(req),
      targetType: 'folder_share',
      targetId: row.id,
      targetName: row.name,
      detail: { shareCode: row.code, folderId: row.folder_id, expired, requiresPassword: row.password_hash !== null },
    });
    res.json({
      found: true,
      expired,
      requiresPassword: row.password_hash !== null,
      name: row.name,
      folderPath: folders.get(row.folder_id)?.name ?? '',
      createdAt: Number(row.created_at),
      expiresAt: row.expires_at === null ? null : Number(row.expires_at),
      downloadCount: Number(row.download_count),
      status: row.status,
      items,
    });
  }),
);

const downloadSchema = z.object({ password: z.string().max(128).optional() });

folderSharesRouter.post(
  '/share/:code/file/:fileId/download',
  asyncHandler(async (req, res) => {
    const row = findByCode(req.params.code!);
    if (!row || row.status === 'deleted') throw new HttpError(404, 'not_found');
    if (markExpiredIfDue(row)) throw new HttpError(410, 'expired');
    if (row.status !== 'ready') throw new HttpError(409, 'not_ready');

    const { password } = downloadSchema.parse(req.body ?? {});
    try {
      await verifyPassword(row, password);
    } catch (err) {
      logPasswordFailure(req, row, err instanceof HttpError ? err.message : 'error');
      throw err;
    }

    const file = getRow<FileRow>(
      `${SUBTREE_CTE}
       SELECT * FROM files
        WHERE id = ? AND status = 'ready' AND folder_id IN (SELECT id FROM tree)`,
      row.folder_id,
      req.params.fileId!,
    );
    if (!file) throw new HttpError(404, 'file_not_found');

    const url = await presignGet(file.r2_key, file.original_name);
    const now = Date.now();
    db.prepare('UPDATE files SET download_count = download_count + 1, last_download_at = ? WHERE id = ?').run(
      now,
      file.id,
    );
    db.prepare('UPDATE folder_shares SET download_count = download_count + 1 WHERE id = ?').run(row.id);
    recordLog({
      action: 'folder_share.download',
      actor: actorOf(req),
      ...requestSource(req),
      targetType: 'file',
      targetId: file.id,
      targetName: file.original_name,
      detail: { shareCode: row.code, folderId: row.folder_id, folderShareName: row.name, size: Number(file.size) },
    });
    res.json({ url });
  }),
);

folderSharesRouter.post(
  '/share/:code/download-all',
  asyncHandler(async (req, res) => {
    const row = findByCode(req.params.code!);
    if (!row || row.status === 'deleted') throw new HttpError(404, 'not_found');
    if (markExpiredIfDue(row)) throw new HttpError(410, 'expired');
    if (row.status !== 'ready') throw new HttpError(409, 'not_ready');

    const { password } = downloadSchema.parse(req.body ?? {});
    try {
      await verifyPassword(row, password);
    } catch (err) {
      logPasswordFailure(req, row, err instanceof HttpError ? err.message : 'error');
      throw err;
    }

    const files = filesInSubtree(row.folder_id).slice(0, DOWNLOAD_ALL_LIMIT);
    if (files.length === 0) throw new HttpError(404, 'empty_folder');

    const now = Date.now();
    const downloads: { name: string; url: string }[] = [];
    for (const file of files) {
      downloads.push({ name: file.original_name, url: await presignGet(file.r2_key, file.original_name) });
      db.prepare('UPDATE files SET download_count = download_count + 1, last_download_at = ? WHERE id = ?').run(
        now,
        file.id,
      );
    }
    db.prepare('UPDATE folder_shares SET download_count = download_count + 1 WHERE id = ?').run(row.id);
    recordLog({
      action: 'folder_share.download_all',
      actor: actorOf(req),
      ...requestSource(req),
      targetType: 'folder_share',
      targetId: row.id,
      targetName: row.name,
      detail: { shareCode: row.code, folderId: row.folder_id, count: downloads.length },
    });
    res.json({ downloads });
  }),
);

// ─────────────────────────── admin management ───────────────────────────

folderSharesRouter.use(requireAuth);

folderSharesRouter.get('/', (req, res) => {
  const baseUrl = resolveBaseUrl(req);
  const folders = folderMap();
  const rows = allRows<FolderShareRow>(
    `SELECT * FROM folder_shares WHERE status != 'deleted' ORDER BY created_at DESC`,
  );
  res.json({
    shares: rows
      .filter((row) => folders.has(row.folder_id))
      .map((row) => toFolderShareDTO(row, baseUrl, folders, itemsFor(row))),
  });
});

const createSchema = z.object({
  folderId: z.string().min(1).max(64),
  name: z.string().trim().min(1).max(120).optional(),
  expiresInDays: z.number().int().positive().max(3650).nullish(),
  password: z.string().min(1).max(128).nullish(),
});

folderSharesRouter.post(
  '/',
  asyncHandler(async (req, res) => {
    const input = createSchema.parse(req.body ?? {});
    const folder = getRow<FolderRow>('SELECT * FROM folders WHERE id = ?', input.folderId);
    if (!folder) throw new HttpError(404, 'folder_not_found');

    const id = randomUUID();
    const code = uniqueShareCode();
    const now = Date.now();
    const name = input.name ?? folder.name;
    const expiresAt = input.expiresInDays ? now + input.expiresInDays * DAY_MS : null;
    const passwordHash = input.password ? await bcrypt.hash(input.password, 10) : null;

    db.prepare(
      `INSERT INTO folder_shares
        (id, code, folder_id, name, status, created_at, expires_at, password_hash, download_count)
       VALUES (?, ?, ?, ?, 'ready', ?, ?, ?, 0)`,
    ).run(id, code, folder.id, name, now, expiresAt, passwordHash);

    const row = getShareOr404(id);
    const folders = folderMap();
    recordLog({
      action: 'folder_share.create',
      actor: actorOf(req),
      ...requestSource(req),
      targetType: 'folder_share',
      targetId: id,
      targetName: name,
      detail: {
        shareCode: code,
        folderId: folder.id,
        folderPath: folderPath(folder.id, folders),
        expiresInDays: input.expiresInDays ?? null,
        hasPassword: !!passwordHash,
      },
    });
    res
      .status(201)
      .json({ share: toFolderShareDTO(row, resolveBaseUrl(req), folders, itemsFor(row)) });
  }),
);

const patchSchema = z.object({
  name: z.string().trim().min(1).max(120).optional(),
  expiresInDays: z.number().int().positive().max(3650).nullish(),
  password: z.string().min(1).max(128).nullish(),
  clearPassword: z.boolean().optional(),
});

folderSharesRouter.patch(
  '/:id',
  asyncHandler(async (req, res) => {
    const row = getShareOr404(req.params.id!);
    if (row.status === 'expired' || row.status === 'deleted') {
      throw new HttpError(409, 'folder_share_not_editable');
    }
    const input = patchSchema.parse(req.body ?? {});

    const name = input.name ?? row.name;
    let expiresAt = row.expires_at;
    if (input.expiresInDays !== undefined) {
      expiresAt = input.expiresInDays === null ? null : Date.now() + input.expiresInDays * DAY_MS;
    }
    let passwordHash = row.password_hash;
    if (input.clearPassword) passwordHash = null;
    else if (input.password) passwordHash = await bcrypt.hash(input.password, 10);

    db.prepare('UPDATE folder_shares SET name = ?, expires_at = ?, password_hash = ? WHERE id = ?').run(
      name,
      expiresAt,
      passwordHash,
      row.id,
    );
    const updated = getShareOr404(row.id);
    const folders = folderMap();
    recordLog({
      action: 'folder_share.update',
      actor: actorOf(req),
      ...requestSource(req),
      targetType: 'folder_share',
      targetId: row.id,
      targetName: name,
      detail: {
        shareCode: row.code,
        name: input.name !== undefined,
        expiresInDays: input.expiresInDays !== undefined,
        password: !!(input.password || input.clearPassword),
      },
    });
    res.json({ share: toFolderShareDTO(updated, resolveBaseUrl(req), folders, itemsFor(updated)) });
  }),
);

folderSharesRouter.delete('/:id', (req, res) => {
  const row = getShareOr404(req.params.id!);
  db.prepare('DELETE FROM folder_shares WHERE id = ?').run(row.id);
  recordLog({
    action: 'folder_share.delete',
    actor: actorOf(req),
    ...requestSource(req),
    targetType: 'folder_share',
    targetId: row.id,
    targetName: row.name,
    detail: { shareCode: row.code, folderId: row.folder_id },
  });
  res.json({ ok: true });
});
