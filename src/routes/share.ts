import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { db, getRow, type FileRow } from '../db.js';
import { asyncHandler, HttpError } from '../lib/http.js';
import { presignGet, presignPreview } from '../lib/r2.js';
import { actorOf, recordLog, requestSource } from '../lib/audit.js';
import { clearSharePasswordFailures, guardShareAccess, noteSharePasswordFailure } from '../lib/share-limit.js';

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

function logPasswordFailure(
  action: string,
  req: Parameters<typeof requestSource>[0],
  row: FileRow,
  reason: string,
): void {
  recordLog({
    action,
    actor: actorOf(req),
    ...requestSource(req),
    targetType: 'file',
    targetId: row.id,
    targetName: row.original_name,
    result: 'fail',
    detail: { shareCode: row.share_code, reason },
  });
}

async function verifyPassword(
  action: string,
  req: Parameters<typeof requestSource>[0],
  row: FileRow,
  password: string | undefined,
): Promise<void> {
  if (!row.password_hash) return;
  if (!password) {
    logPasswordFailure(action, req, row, 'password_required');
    throw new HttpError(400, 'password_required');
  }
  const ok = await bcrypt.compare(password, row.password_hash);
  if (!ok) {
    noteSharePasswordFailure(req, row.share_code);
    logPasswordFailure(action, req, row, 'wrong_password');
    throw new HttpError(403, 'wrong_password');
  }
  clearSharePasswordFailures(req, row.share_code);
}

// Public metadata for the download page (no key, no hash leaked).
shareRouter.get(
  '/:code',
  asyncHandler(async (req, res) => {
    const row = findByCode(req.params.code!);
    if (!row || row.status === 'deleted') throw new HttpError(404, 'not_found');
    const expired = markExpiredIfDue(row);
    const source = requestSource(req);
    recordLog({
      action: 'share.view',
      actor: actorOf(req),
      ...source,
      targetType: 'file',
      targetId: row.id,
      targetName: row.original_name,
      detail: { shareCode: row.share_code, expired, requiresPassword: row.password_hash !== null },
    });
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

    guardShareAccess(req, row.share_code, 'file');
    const source = requestSource(req);
    const { password } = downloadSchema.parse(req.body ?? {});
    await verifyPassword('share.download', req, row, password);

    const url = await presignGet(row.r2_key, row.original_name);
    db.prepare('UPDATE files SET download_count = download_count + 1, last_download_at = ? WHERE id = ?').run(
      Date.now(),
      row.id,
    );
    recordLog({
      action: 'share.download',
      actor: actorOf(req),
      ...source,
      targetType: 'file',
      targetId: row.id,
      targetName: row.original_name,
      detail: { shareCode: row.share_code, size: Number(row.size) },
    });
    res.json({ url });
  }),
);

shareRouter.post(
  '/:code/preview',
  asyncHandler(async (req, res) => {
    const row = findByCode(req.params.code!);
    if (!row || row.status === 'deleted') throw new HttpError(404, 'not_found');
    if (markExpiredIfDue(row)) throw new HttpError(410, 'expired');
    if (row.status !== 'ready') throw new HttpError(409, 'not_ready');

    guardShareAccess(req, row.share_code, 'file');
    const { password } = downloadSchema.parse(req.body ?? {});
    await verifyPassword('share.preview', req, row, password);

    const url = await presignPreview(row.r2_key, row.original_name, row.mime_type);
    recordLog({
      action: 'share.preview',
      actor: actorOf(req),
      ...requestSource(req),
      targetType: 'file',
      targetId: row.id,
      targetName: row.original_name,
      detail: { shareCode: row.share_code, size: Number(row.size) },
    });
    res.json({ url, name: row.original_name, size: Number(row.size), mimeType: row.mime_type });
  }),
);
