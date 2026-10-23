import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler, HttpError } from '../lib/http.js';
import { requireAuth } from '../auth.js';
import { knownKeys, scanBucket } from '../lib/bucket-scan.js';
import { deleteObjects } from '../lib/r2.js';
import { actorOf, clientIp, recordLog } from '../lib/audit.js';

export const bucketRouter = Router();
bucketRouter.use(requireAuth);

// ─────────────────────────── GET /scan ───────────────────────────
bucketRouter.get(
  '/scan',
  asyncHandler(async (req, res) => {
    try {
      const result = await scanBucket();
      recordLog({
        action: 'bucket.scan',
        actor: actorOf(req),
        ip: clientIp(req),
        detail: {
          objectCount: result.objectCount,
          orphanCount: result.orphanCount,
          missingCount: result.missingCount,
        },
      });
      res.json(result);
    } catch (err) {
      if (err instanceof HttpError) throw err;
      throw new HttpError(502, `bucket_scan_failed: ${err instanceof Error ? err.message : 'unknown_error'}`);
    }
  }),
);

// ─────────────────── POST /delete-orphans ───────────────────
const deleteSchema = z.object({
  keys: z.array(z.string().min(1).max(1024)).min(1).max(5000),
});

bucketRouter.post(
  '/delete-orphans',
  asyncHandler(async (req, res) => {
    const { keys } = deleteSchema.parse(req.body ?? {});
    const known = knownKeys();
    const unique = [...new Set(keys)];
    const skipped = unique.filter((key) => known.has(key));
    const deletable = unique.filter((key) => !known.has(key));

    if (!deletable.length) {
      recordLog({
        action: 'bucket.delete_orphans',
        actor: actorOf(req),
        ip: clientIp(req),
        result: 'fail',
        detail: { reason: 'no_orphans', deleted: 0, skipped: skipped.length },
      });
      res.json({ deleted: [], failed: [], skipped });
      return;
    }

    const deleted: string[] = [];
    const failed: { key: string; message: string }[] = [];
    for (let i = 0; i < deletable.length; i += 1000) {
      const chunk = deletable.slice(i, i + 1000);
      try {
        const result = await deleteObjects(chunk);
        deleted.push(...result.deleted);
        failed.push(...result.failed);
      } catch (err) {
        if (err instanceof HttpError) throw err;
        const message = err instanceof Error ? err.message : 'delete_failed';
        for (const key of chunk) failed.push({ key, message });
      }
    }

    recordLog({
      action: 'bucket.delete_orphans',
      actor: actorOf(req),
      ip: clientIp(req),
      result: failed.length ? 'fail' : 'ok',
      detail: { deleted: deleted.length, failed: failed.length, skipped: skipped.length },
    });
    res.json({ deleted, failed, skipped });
  }),
);
