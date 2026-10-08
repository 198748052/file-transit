import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { db, allRows, getRow, toFileDTO, type FileRow } from '../db.js';
import { config } from '../config.js';
import { asyncHandler, HttpError } from '../lib/http.js';
import { requireAuth } from '../auth.js';
import { generateCode } from '../lib/codes.js';
import { resolveBaseUrl } from '../lib/base-url.js';
import {
  presignPut,
  presignPart,
  createMultipartUpload,
  completeMultipartUpload,
  abortMultipartUpload,
  listUploadedParts,
  deleteObject,
  headObject,
} from '../lib/r2.js';

export const filesRouter = Router();
filesRouter.use(requireAuth);

const MAX_PARTS = 10_000;
const DAY_MS = 86_400_000;

function sanitizeKeyPart(name: string): string {
  return name.replace(/[^\w.\-]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 120) || 'file';
}

function buildKey(id: string, name: string): string {
  const now = new Date();
  const y = now.getUTCFullYear();
  const m = String(now.getUTCMonth() + 1).padStart(2, '0');
  return `uploads/${y}/${m}/${id}-${sanitizeKeyPart(name)}`;
}

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

// ─────────────────────────── POST /init ───────────────────────────
const initSchema = z.object({
  name: z.string().min(1).max(255),
  size: z.number().int().positive(),
  mimeType: z.string().min(1).max(255).default('application/octet-stream'),
  expiresInDays: z.number().int().positive().max(3650).nullish(),
  password: z.string().min(1).max(128).nullish(),
});

filesRouter.post(
  '/init',
  asyncHandler(async (req, res) => {
    const input = initSchema.parse(req.body ?? {});
    const id = randomUUID();
    const key = buildKey(id, input.name);
    const now = Date.now();
    const shareCode = uniqueShareCode();
    const expiresAt = input.expiresInDays ? now + input.expiresInDays * DAY_MS : null;
    const passwordHash = input.password ? await bcrypt.hash(input.password, 10) : null;

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
         created_at, expires_at, password_hash, download_count)
       VALUES (?, ?, ?, ?, ?, ?, 'uploading', ?, ?, ?, ?, ?, 0)`,
    ).run(id, shareCode, input.name, key, input.size, input.mimeType, uploadId, isMultipart ? partSize : null, now, expiresAt, passwordHash);

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
    res.json({ file: toFileDTO(getFileOr404(row.id), resolveBaseUrl(req)) });
  }),
);

// ─────────────────────────── GET / (list) ───────────────────────────
filesRouter.get('/', (req, res) => {
  const baseUrl = resolveBaseUrl(req);
  const rows = allRows<FileRow>(`SELECT * FROM files WHERE status != 'deleted' ORDER BY created_at DESC`);
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

    res.json({ file: toFileDTO(getFileOr404(row.id), resolveBaseUrl(req)) });
  }),
);

// ─────────────────────────── DELETE /:id ───────────────────────────
filesRouter.delete(
  '/:id',
  asyncHandler(async (req, res) => {
    const row = getFileOr404(req.params.id!);
    try {
      if (row.upload_id) await abortMultipartUpload(row.r2_key, row.upload_id);
      else await deleteObject(row.r2_key);
    } catch (err) {
      console.error(`Failed to delete R2 object for ${row.id}:`, err);
    }
    db.prepare(`DELETE FROM files WHERE id = ?`).run(row.id);
    res.json({ ok: true });
  }),
);
