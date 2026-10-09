import cron from 'node-cron';
import { db, allRows, type FileRow } from '../db.js';
import { config } from '../config.js';
import { deleteObject, abortMultipartUpload } from '../lib/r2.js';
import { pruneRateLimits } from '../lib/rate-limit.js';

const STALE_UPLOAD_MS = 24 * 60 * 60 * 1000; // abandon incomplete uploads after 24h

/**
 * Delete R2 objects whose retention window has passed.
 *
 * Both `ready` files and files already flipped to `expired` by the share page
 * are picked up, then moved to the terminal `deleted` state so the object is
 * removed exactly once and never re-processed (previously lazily-expired files
 * were skipped, leaking their R2 objects forever). On a failed R2 delete the
 * row stays `expired` and is retried on the next run.
 */
export async function deleteExpiredFiles(): Promise<number> {
  const now = Date.now();
  const rows = allRows<FileRow>(
    `SELECT * FROM files
       WHERE expires_at IS NOT NULL AND expires_at <= ?
         AND status IN ('ready', 'expired')`,
    now,
  );

  let cleaned = 0;
  for (const row of rows) {
    try {
      await deleteObject(row.r2_key);
    } catch (err) {
      console.error(`Failed to delete expired object ${row.r2_key}:`, err);
      db.prepare(`UPDATE files SET status = 'expired' WHERE id = ?`).run(row.id);
      continue;
    }
    db.prepare(`UPDATE files SET status = 'deleted' WHERE id = ?`).run(row.id);
    cleaned++;
    console.log(`🗑  expired: ${row.original_name} (${row.share_code})`);
  }
  return cleaned;
}

/** Remove upload sessions that were started but never completed. */
export async function cleanupStaleUploads(): Promise<number> {
  const cutoff = Date.now() - STALE_UPLOAD_MS;
  const rows = allRows<FileRow>(
    `SELECT * FROM files WHERE status = 'uploading' AND created_at <= ?`,
    cutoff,
  );

  for (const row of rows) {
    try {
      if (row.upload_id) await abortMultipartUpload(row.r2_key, row.upload_id);
      else await deleteObject(row.r2_key);
    } catch (err) {
      console.error(`Failed to abort stale upload ${row.r2_key}:`, err);
    }
    db.prepare(`DELETE FROM files WHERE id = ?`).run(row.id);
  }
  return rows.length;
}

export function startMaintenanceJobs(): void {
  const run = async () => {
    try {
      pruneRateLimits();
      const expired = await deleteExpiredFiles();
      const stale = await cleanupStaleUploads();
      if (expired || stale) console.log(`🧹 maintenance: expired=${expired} staleUploads=${stale}`);
    } catch (err) {
      console.error('Maintenance run failed:', err);
    }
  };

  cron.schedule(config.expiryCron, run);
  const boot = setTimeout(run, 5_000);
  boot.unref?.();
  console.log(`⏰ maintenance cron scheduled: "${config.expiryCron}"`);
}
