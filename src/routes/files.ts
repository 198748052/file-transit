import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { db, allRows, getRow, toFileDTO, type FileRow, type FolderRow } from '../db.js';
import { config } from '../config.js';
import { asyncHandler, HttpError } from '../lib/http.js';
import { requireAuth } from '../auth.js';
import { generateCode } from '../lib/codes.js';
import { resolveBaseUrl } from '../lib/base-url.js';
import { removeFileRecord, removeR2Object } from '../lib/file-store.js';
import { actorOf, clientIp, recordLog } from '../lib/audit.js';
import { buildUploadKey, isLegacyUploadKey } from '../lib/r2-key.js';
import {
  presignPut,
  presignPart,
  createMultipartUpload,
  completeMultipartUpload,
  listUploadedParts,
  headObject,
  presignGet,
  presignPreview,
} from '../lib/r2.js';

export const filesRouter = Router();
filesRouter.use(requireAuth);

const MAX_PARTS = 10_000;
const DAY_MS = 86_400_000;

function uniqueShareCode(): string {
  const stmt = db.prepare('SELECT 1 FROM files WHERE share_code = ?');
  for (let i = 0; i < 10; i++) {
    const code = generateCode(8);
    if (!stmt.get(code)) return code;
  }
  throw new HttpError(500, 'could not allocate a unique share code');
}

function getFileOr404(id: string): FileRow {
  const row = getRow<FileRow>('SELECT * FROM files WHERE id = ?', id);
  if (!row) throw new HttpError(404, 'file_not_found');
  return row;
}

/** Normalise a folder reference: '' / 'root' / null mean the drive root. */
function resolveFolderId(value: string | null | undefined): string | null {
  if (value === undefined || value === null || value === '' || value === 'root') return null;
  const folder = getRow<FolderRow>('SELECT id FROM folders WHERE id = ?', value);
  if (!folder) throw new HttpError(400, 'folder_not_found');
  return value;
}

// ─────────────────────────── GET /stats ───────────────────────────
// Storage/usage snapshot derived from DB metadata (no R2 LIST call needed).
filesRouter.get('/stats', (_req, res) => {
  const row = getRow<{
    fileCount: number;
    readyCount: number;
    uploadingCount: number;
    expiredCount: number;
    totalSize: number;
    readySize: number;
    expiredSize: number;
    uploadSize: number;
    downloadTotal: number;
  }>(
    `SELECT
       SUM(CASE WHEN status != 'deleted' THEN 1 ELSE 0 END) AS fileCount,
       SUM(CASE WHEN status = 'ready' THEN 1 ELSE 0 END) AS readyCount,
       SUM(CASE WHEN status = 'uploading' THEN 1 ELSE 0 END) AS uploadingCount,
       SUM(CASE WHEN status = 'expired' THEN 1 ELSE 0 END) AS expiredCount,
       COALESCE(SUM(CASE WHEN status != 'deleted' THEN size ELSE 0 END), 0) AS totalSize,
       COALESCE(SUM(CASE WHEN status = 'ready' THEN size ELSE 0 END), 0) AS readySize,
       COALESCE(SUM(CASE WHEN status = 'expired' THEN size ELSE 0 END), 0) AS expiredSize,
       COALESCE(SUM(CASE WHEN status = 'uploading' THEN size ELSE 0 END), 0) AS uploadSize,
       COALESCE(SUM(download_count), 0) AS downloadTotal
     FROM files`,
  );
  const collection = getRow<{ collectionCount: number }>(
    `SELECT COUNT(*) AS collectionCount FROM collections WHERE status != 'deleted'`,
  );
  const folders = getRow<{ folderCount: number }>('SELECT COUNT(*) AS folderCount FROM folders');
  res.json({
    fileCount: Number(row?.fileCount ?? 0),
    readyCount: Number(row?.readyCount ?? 0),
    uploadingCount: Number(row?.uploadingCount ?? 0),
    expiredCount: Number(row?.expiredCount ?? 0),
    totalSize: Number(row?.totalSize ?? 0),
    readySize: Number(row?.readySize ?? 0),
    expiredSize: Number(row?.expiredSize ?? 0),
    uploadSize: Number(row?.uploadSize ?? 0),
    downloadTotal: Number(row?.downloadTotal ?? 0),
    collectionCount: Number(collection?.collectionCount ?? 0),
    folderCount: Number(folders?.folderCount ?? 0),
  });
});

// ─────────────────────────── POST /bulk-delete ───────────────────────────
const bulkDeleteSchema = z.object({
  ids: z.array(z.string().min(1)).min(1).max(200),
});

filesRouter.post(
  '/bulk-delete',
  asyncHandler(async (req, res) => {
    const { ids } = bulkDeleteSchema.parse(req.body ?? {});
    const deleted: string[] = [];
    const failed: string[] = [];
    for (const id of ids) {
      const row = getRow<FileRow>('SELECT * FROM files WHERE id = ?', id);
      if (!row) {
        failed.push(id);
        continue;
      }
      try {
        await removeFileRecord(row);
      } catch (err) {
        console.error(`Bulk delete failed for ${id}:`, err);
        failed.push(id);
        continue;
      }
      deleted.push(id);
      recordLog({
        action: 'file.delete',
        actor: actorOf(req),
        ip: clientIp(req),
        targetType: 'file',
        targetId: id,
        targetName: row.original_name,
        detail: { size: Number(row.size), folderId: row.folder_id, via: 'bulk' },
      });
    }
    res.json({ deleted, failed });
  }),
);

// ─────────────────────────── POST /init ───────────────────────────
const initSchema = z.object({
  name: z.string().min(1).max(255),
  size: z.number().int().positive(),
  mimeType: z.string().min(1).max(255).default('application/octet-stream'),
  expiresInDays: z.number().int().positive().max(3650).nullish(),
  password: z.string().min(1).max(128).nullish(),
  folderId: z.string().min(1).max(64).nullish(),
  hash: z.string().regex(/^[0-9a-f]{64}$/i).nullish(),
});

filesRouter.post(
  '/init',
  asyncHandler(async (req, res) => {
    const input = initSchema.parse(req.body ?? {});
    const id = randomUUID();
    const key = buildUploadKey(id, input.name);
    const now = Date.now();
    const shareCode = uniqueShareCode();
    const expiresAt = input.expiresInDays ? now + input.expiresInDays * DAY_MS : null;
    const passwordHash = input.password ? await bcrypt.hash(input.password, 10) : null;
    const folderId = resolveFolderId(input.folderId);
    const contentHash = input.hash?.toLowerCase() ?? null;

    // Content-addressed fast path: reuse an existing object instead of uploading
    // the same bytes again. Legacy keys are skipped so the migration job never
    // has to deal with several rows sharing one legacy object.
    const reusable = contentHash
      ? allRows<FileRow>(
          `SELECT * FROM files
            WHERE content_hash = ? AND size = ? AND status = 'ready'
            ORDER BY created_at ASC LIMIT 50`,
          contentHash,
          input.size,
        ).find((candidate) => !isLegacyUploadKey(candidate.r2_key))
      : undefined;

    if (reusable) {
      db.prepare(
        `INSERT INTO files
          (id, share_code, original_name, r2_key, size, mime_type, status, upload_id, part_size,
           created_at, expires_at, password_hash, download_count, folder_id, content_hash)
         VALUES (?, ?, ?, ?, ?, ?, 'ready', NULL, NULL, ?, ?, ?, 0, ?, ?)`,
      ).run(
        id,
        shareCode,
        input.name,
        reusable.r2_key,
        input.size,
        input.mimeType,
        now,
        expiresAt,
        passwordHash,
        folderId,
        contentHash,
      );
      const duplicate = getFileOr404(id);
      recordLog({
        action: 'file.dedup',
        actor: actorOf(req),
        ip: clientIp(req),
        targetType: 'file',
        targetId: id,
        targetName: input.name,
        detail: {
          duplicateOf: reusable.id,
          size: Number(reusable.size),
          hash: contentHash?.slice(0, 12),
          folderId,
        },
      });
      res.json({
        mode: 'duplicate',
        file: toFileDTO(duplicate, resolveBaseUrl(req)),
        duplicateOf: reusable.id,
        savedBytes: Number(reusable.size),
      });
      return;
    }

    const isMultipart = input.size > config.multipartThresholdBytes;
    const partSize = config.partSizeBytes;
    const partCount = isMultipart ? Math.ceil(input.size / partSize) : 1;
    if (partCount > MAX_PARTS) {
      throw new HttpError(400, 'file_too_large');
    }

    let uploadId: string | null = null;
    if (isMultipart) {
      uploadId = await createMultipartUpload(key, input.mimeType);
    }

    db.prepare(
      `INSERT INTO files
        (id, share_code, original_name, r2_key, size, mime_type, status, upload_id, part_size,
         created_at, expires_at, password_hash, download_count, folder_id, content_hash)
       VALUES (?, ?, ?, ?, ?, ?, 'uploading', ?, ?, ?, ?, ?, 0, ?, ?)`,
    ).run(
      id,
      shareCode,
      input.name,
      key,
      input.size,
      input.mimeType,
      uploadId,
      isMultipart ? partSize : null,
      now,
      expiresAt,
      passwordHash,
      folderId,
      contentHash,
    );

    const row = getFileOr404(id);
    const baseUrl = resolveBaseUrl(req);

    if (!isMultipart) {
      const url = await presignPut(key, input.mimeType);
      res.json({
        mode: 'single',
        file: toFileDTO(row, baseUrl),
        upload: { url, method: 'PUT', key, headers: { 'Content-Type': input.mimeType } },
      });
      return;
    }

    const partNumbers = Array.from({ length: partCount }, (_, i) => i + 1);
    const urls = await Promise.all(
      partNumbers.map(async (partNumber) => ({
        partNumber,
        url: await presignPart(key, uploadId!, partNumber),
      })),
    );

    res.json({
      mode: 'multipart',
      file: toFileDTO(row, baseUrl),
      upload: { uploadId, key, partSize, partCount, urls },
    });
  }),
);

// ─────────────── GET /:id/parts  (resume an interrupted upload) ───────────────
filesRouter.get(
  '/:id/parts',
  asyncHandler(async (req, res) => {
    const row = getFileOr404(req.params.id!);
    if (row.status !== 'uploading' || !row.upload_id || !row.part_size) {
      throw new HttpError(409, 'not_a_resumable_upload');
    }
    const partCount = Math.ceil(Number(row.size) / row.part_size);
    const uploaded = await listUploadedParts(row.r2_key, row.upload_id);
    const partNumbers = Array.from({ length: partCount }, (_, i) => i + 1);
    const urls = await Promise.all(
      partNumbers.map(async (partNumber) => ({
        partNumber,
        url: await presignPart(row.r2_key, row.upload_id!, partNumber),
      })),
    );
    res.json({ fileId: row.id, uploadId: row.upload_id, partSize: row.part_size, partCount, uploaded, urls });
  }),
);

// ─────────────── GET /:id/parts/:partNumber  (re-sign a single expired part) ───────────────
filesRouter.get(
  '/:id/parts/:partNumber',
  asyncHandler(async (req, res) => {
    const row = getFileOr404(req.params.id!);
    if (row.status !== 'uploading' || !row.upload_id) throw new HttpError(409, 'not_a_resumable_upload');
    const partNumber = Number(req.params.partNumber);
    if (!Number.isInteger(partNumber) || partNumber < 1) throw new HttpError(400, 'bad_part_number');
    const url = await presignPart(row.r2_key, row.upload_id, partNumber);
    res.json({ partNumber, url });
  }),
);

// ─────────────────────────── POST /:id/complete ───────────────────────────
const completeSchema = z.object({
  parts: z.array(z.object({ partNumber: z.number().int().positive(), etag: z.string() })).optional(),
});

filesRouter.post(
  '/:id/complete',
  asyncHandler(async (req, res) => {
    const row = getFileOr404(req.params.id!);
    if (row.status !== 'uploading') throw new HttpError(409, 'already_finalized');
    const { parts } = completeSchema.parse(req.body ?? {});

    if (row.upload_id) {
      if (!parts || parts.length === 0) throw new HttpError(400, 'missing_parts');
      await completeMultipartUpload(row.r2_key, row.upload_id, parts);
    }

    const head = await headObject(row.r2_key);
    if (!head) throw new HttpError(400, 'object_not_found_in_r2');

    db.prepare(`UPDATE files SET status = 'ready', size = ?, upload_id = NULL WHERE id = ?`).run(
      head.size,
      row.id,
    );
    recordLog({
      action: 'file.upload',
      actor: actorOf(req),
      ip: clientIp(req),
      targetType: 'file',
      targetId: row.id,
      targetName: row.original_name,
      detail: { size: Number(head.size), folderId: row.folder_id },
    });
    res.json({ file: toFileDTO(getFileOr404(row.id), resolveBaseUrl(req)) });
  }),
);

// ─────────────── POST /:id/abort  (cancel an in-progress upload) ───────────────
filesRouter.post(
  '/:id/abort',
  asyncHandler(async (req, res) => {
    const row = getFileOr404(req.params.id!);
    if (row.status !== 'uploading') throw new HttpError(409, 'not_uploading');
    try {
      await removeR2Object(row);
    } catch (err) {
      console.error(`Failed to abort upload ${row.id}:`, err);
      throw new HttpError(502, 'r2_abort_failed');
    }
    db.prepare('DELETE FROM files WHERE id = ?').run(row.id);
    recordLog({
      action: 'file.upload_abort',
      actor: actorOf(req),
      ip: clientIp(req),
      targetType: 'file',
      targetId: row.id,
      targetName: row.original_name,
      detail: { folderId: row.folder_id },
    });
    res.json({ ok: true });
  }),
);

// ─────────────────────────── POST /move ───────────────────────────
const moveSchema = z.object({
  ids: z.array(z.string().min(1)).min(1).max(500),
  folderId: z.string().min(1).max(64).nullish(),
});

filesRouter.post('/move', (req, res) => {
  const { ids, folderId: rawFolderId } = moveSchema.parse(req.body ?? {});
  const folderId = resolveFolderId(rawFolderId);
  const stmt = db.prepare('UPDATE files SET folder_id = ? WHERE id = ? AND status != ?');
  let moved = 0;
  for (const id of ids) {
    moved += Number(stmt.run(folderId, id, 'deleted').changes);
  }
  const target = folderId
    ? getRow<{ name: string }>('SELECT name FROM folders WHERE id = ?', folderId)?.name ?? null
    : null;
  recordLog({
    action: 'file.move',
    actor: actorOf(req),
    ip: clientIp(req),
    targetType: 'file',
    detail: { moved, folderId, folderName: target },
  });
  res.json({ moved, folderId });
});

// ─────────────────────────── GET / (list) ───────────────────────────
filesRouter.get('/', (req, res) => {
  const baseUrl = resolveBaseUrl(req);
  const raw = req.query.folderId;
  const folderParam = typeof raw === 'string' ? raw : undefined;

  let rows: FileRow[];
  if (folderParam === 'all' || folderParam === undefined) {
    rows = allRows<FileRow>(`SELECT * FROM files WHERE status != 'deleted' ORDER BY created_at DESC`);
  } else if (folderParam === '' || folderParam === 'root') {
    rows = allRows<FileRow>(
      `SELECT * FROM files WHERE status != 'deleted' AND folder_id IS NULL ORDER BY created_at DESC`,
    );
  } else {
    rows = allRows<FileRow>(
      `SELECT * FROM files WHERE status != 'deleted' AND folder_id = ? ORDER BY created_at DESC`,
      folderParam,
    );
  }
  res.json({ files: rows.map((row) => toFileDTO(row, baseUrl)) });
});

// ─────────────────────────── PATCH /:id ───────────────────────────
const patchSchema = z.object({
  name: z.string().min(1).max(255).optional(),
  expiresInDays: z.number().int().positive().max(3650).nullish(),
  password: z.string().min(1).max(128).nullish(),
  clearPassword: z.boolean().optional(),
});

filesRouter.patch(
  '/:id',
  asyncHandler(async (req, res) => {
    const row = getFileOr404(req.params.id!);
    if (row.status === 'expired' || row.status === 'deleted') {
      throw new HttpError(409, 'file_not_editable');
    }
    const input = patchSchema.parse(req.body ?? {});

    const name = input.name ?? row.original_name;
    let expiresAt = row.expires_at;
    if (input.expiresInDays !== undefined) {
      expiresAt = input.expiresInDays === null ? null : Date.now() + input.expiresInDays * DAY_MS;
    }
    let passwordHash = row.password_hash;
    if (input.clearPassword) passwordHash = null;
    else if (input.password) passwordHash = await bcrypt.hash(input.password, 10);

    db.prepare(
      `UPDATE files SET original_name = ?, expires_at = ?, password_hash = ? WHERE id = ?`,
    ).run(name, expiresAt, passwordHash, row.id);

    const changed: string[] = [];
    if (name !== row.original_name) changed.push('name');
    if (expiresAt !== row.expires_at) changed.push('expiresAt');
    if (passwordHash !== row.password_hash) changed.push('password');
    recordLog({
      action: name !== row.original_name ? 'file.rename' : 'file.update',
      actor: actorOf(req),
      ip: clientIp(req),
      targetType: 'file',
      targetId: row.id,
      targetName: name,
      detail: { changed, previousName: row.original_name },
    });

    res.json({ file: toFileDTO(getFileOr404(row.id), resolveBaseUrl(req)) });
  }),
);

// ─────────────────────────── DELETE /:id ───────────────────────────
filesRouter.delete(
  '/:id',
  asyncHandler(async (req, res) => {
    const row = getFileOr404(req.params.id!);
    try {
      await removeFileRecord(row);
    } catch (err) {
      console.error(`Failed to delete R2 object for ${row.id}:`, err);
      throw new HttpError(502, 'r2_delete_failed');
    }
    recordLog({
      action: 'file.delete',
      actor: actorOf(req),
      ip: clientIp(req),
      targetType: 'file',
      targetId: row.id,
      targetName: row.original_name,
      detail: { size: Number(row.size), folderId: row.folder_id },
    });
    res.json({ ok: true });
  }),
);

// ─────────────── GET /:id/preview  (inline preview URL) ───────────────
filesRouter.get(
  '/:id/preview',
  asyncHandler(async (req, res) => {
    const row = getFileOr404(req.params.id!);
    if (row.status !== 'ready') throw new HttpError(409, 'not_ready');
    const url = await presignPreview(row.r2_key, row.original_name, row.mime_type);
    res.json({ url, name: row.original_name, size: Number(row.size), mimeType: row.mime_type });
  }),
);

// ─────────────── GET /:id/download  (admin direct download) ───────────────
filesRouter.get(
  '/:id/download',
  asyncHandler(async (req, res) => {
    const row = getFileOr404(req.params.id!);
    if (row.status !== 'ready') throw new HttpError(409, 'not_ready');
    const url = await presignGet(row.r2_key, row.original_name);
    db.prepare('UPDATE files SET download_count = download_count + 1, last_download_at = ? WHERE id = ?').run(
      Date.now(),
      row.id,
    );
    recordLog({
      action: 'file.download',
      actor: actorOf(req),
      ip: clientIp(req),
      targetType: 'file',
      targetId: row.id,
      targetName: row.original_name,
      detail: { size: Number(row.size) },
    });
    res.json({ url });
  }),
);
