import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { db, getRow, type FileRow } from '../db.js';
import { asyncHandler, HttpError } from '../lib/http.js';
import { presignGet } from '../lib/r2.js';

export const shareRouter = Router();

function findByCode(code: string): FileRow | undefined {
  return getRow<FileRow>('SELECT * FROM files WHERE share_code = ?', code);
}

function isExpired(row: FileRow): boolean {
  return row.expires_at !== null && row.expires_at <= Date.now();
}

/** Lazily flip a ready-but-past-expiry file to 'expired' so it can't be signed. */
function markExpiredIfDue(row: FileRow): boolean {
  if (row.status === 'ready' && isExpired(row)) {
    db.prepare(`UPDATE files SET status = 'expired' WHERE id = ?`).run(row.id);
    row.status = 'expired';
    return true;
  }
  return row.status === 'expired';
}

// Public metadata for the download page (no key, no hash leaked).
shareRouter.get(
  '/:code',
  asyncHandler(async (req, res) => {
    const row = findByCode(req.params.code!);
    if (!row || row.status === 'deleted') throw new HttpError(404, 'not_found');
    const expired = markExpiredIfDue(row);
    res.json({
      found: true,
      expired,
      requiresPassword: row.password_hash !== null,
      name: row.original_name,
      size: Number(row.size),
      mimeType: row.mime_type,
      createdAt: Number(row.created_at),
      expiresAt: row.expires_at === null ? null : Number(row.expires_at),
      downloadCount: Number(row.download_count),
      status: row.status,
    });
  }),
);

const downloadSchema = z.object({ password: z.string().max(128).optional() });

shareRouter.post(
  '/:code/download',
  asyncHandler(async (req, res) => {
    const row = findByCode(req.params.code!);
    if (!row || row.status === 'deleted') throw new HttpError(404, 'not_found');
    if (markExpiredIfDue(row)) throw new HttpError(410, 'expired');
    if (row.status !== 'ready') throw new HttpError(409, 'not_ready');

    const { password } = downloadSchema.parse(req.body ?? {});
    if (row.password_hash) {
      if (!password) throw new HttpError(400, 'password_required');
      const ok = await bcrypt.compare(password, row.password_hash);
      if (!ok) throw new HttpError(403, 'wrong_password');
    }

    const url = await presignGet(row.r2_key, row.original_name);
    db.prepare('UPDATE files SET download_count = download_count + 1, last_download_at = ? WHERE id = ?').run(
      Date.now(),
      row.id,
    );
    res.json({ url });
  }),
);
