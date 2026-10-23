import { allRows, db, type FileRow } from '../db.js';
import { HttpError } from './http.js';
import { copyObject, deleteObject } from './r2.js';
import { buildUploadKey, isLegacyUploadKey } from './r2-key.js';

const CONCURRENCY = 4;
const DEFAULT_LIMIT = 100;
const MAX_LIMIT = 200;
/** Recently-missing sources stay out of the candidate set so one bad row can't block a migration run. */
const MISSING_TTL_MS = 5 * 60 * 1000;
const missingSources = new Map<string, number>();

function activeMissing(): Set<string> {
  const now = Date.now();
  for (const [key, at] of missingSources) {
    if (now - at > MISSING_TTL_MS) missingSources.delete(key);
  }
  return new Set(missingSources.keys());
}

export interface MigrationCandidate {
  id: string;
  name: string;
  size: number;
  oldKey: string;
  newKey: string;
}

export interface KeyMigrationPlan {
  total: number;
  samples: MigrationCandidate[];
}

export interface KeyMigrationResult {
  migrated: number;
  skipped: number;
  failed: { id: string; key: string; reason: string }[];
  remaining: number;
  total: number;
}

/** Ready files whose current key differs from the immutable date/id layout. */
function candidates(): MigrationCandidate[] {
  const rows = allRows<FileRow>(`SELECT * FROM files WHERE status = 'ready' ORDER BY created_at ASC`);
  const missing = activeMissing();
  const out: MigrationCandidate[] = [];
  for (const row of rows) {
    if (!isLegacyUploadKey(row.r2_key)) continue;
    if (missing.has(row.r2_key)) continue;
    const newKey = buildUploadKey(row.id, row.original_name, new Date(row.created_at));
    if (newKey === row.r2_key) continue;
    out.push({
      id: row.id,
      name: row.original_name,
      size: Number(row.size ?? 0),
      oldKey: row.r2_key,
      newKey,
    });
  }
  return out;
}

export function planKeyMigration(sampleLimit = 5): KeyMigrationPlan {
  const list = candidates();
  return { total: list.length, samples: list.slice(0, sampleLimit) };
}

function isNotFound(err: unknown): boolean {
  const value = err as { name?: string; $metadata?: { httpStatusCode?: number } };
  return value?.name === 'NoSuchKey' || value?.$metadata?.httpStatusCode === 404;
}

/**
 * Copy a batch of legacy objects to their new keys (server-side), then update
 * the DB and remove the old object. Safe to re-run: already-migrated rows drop
 * out of the candidate set, and a failed cleanup only leaves an orphan object
 * that the bucket scan page will report.
 */
export async function migrateKeys(limit = DEFAULT_LIMIT): Promise<KeyMigrationResult> {
  const clamped = Math.min(Math.max(1, Math.floor(limit)), MAX_LIMIT);
  const list = candidates();
  const batch = list.slice(0, clamped);

  let migrated = 0;
  let skipped = 0;
  let cursor = 0;
  const failed: KeyMigrationResult['failed'] = [];

  async function worker(): Promise<void> {
    for (;;) {
      const index = cursor++;
      if (index >= batch.length) return;
      const item = batch[index]!;
      try {
        try {
          await copyObject(item.oldKey, item.newKey);
        } catch (err) {
          if (err instanceof HttpError) throw err;
          if (isNotFound(err)) {
            missingSources.set(item.oldKey, Date.now());
            skipped++;
            continue;
          }
          throw err;
        }

        const changed = Number(
          db
            .prepare('UPDATE files SET r2_key = ? WHERE id = ? AND r2_key = ?')
            .run(item.newKey, item.id, item.oldKey).changes,
        );
        if (changed !== 1) {
          failed.push({ id: item.id, key: item.oldKey, reason: 'db_update_failed' });
          continue;
        }

        try {
          await deleteObject(item.oldKey);
        } catch (err) {
          console.error(`Failed to remove legacy object ${item.oldKey}:`, err);
        }
        migrated++;
      } catch (err) {
        if (err instanceof HttpError) throw err;
        failed.push({
          id: item.id,
          key: item.oldKey,
          reason: err instanceof Error ? err.message : 'copy_failed',
        });
      }
    }
  }

  await Promise.all(
    Array.from({ length: Math.min(CONCURRENCY, batch.length) }, () => worker()),
  );

  return {
    migrated,
    skipped,
    failed,
    remaining: candidates().length,
    total: list.length,
  };
}
