import { allRows } from '../db.js';
import { listObjectsPage, type BucketObject } from './r2.js';

/** Hard cap so a huge bucket can't pin the process or produce a giant payload. */
const MAX_SCAN_OBJECTS = 50_000;
const MAX_RETURN_OBJECTS = 5_000;
const MAX_ORPHAN_LIST = 2_000;
const MAX_MISSING_LIST = 2_000;
const PAGE_SIZE = 1_000;

export interface ScanObject {
  key: string;
  size: number;
  lastModified: number | null;
  tracked: boolean;
  foreign: boolean;
}

export interface MissingRecord {
  id: string;
  name: string;
  key: string;
  status: string;
  size: number;
  createdAt: number;
}

export interface BucketScanResult {
  scannedAt: number;
  truncated: boolean;
  objectCount: number;
  totalSize: number;
  trackedCount: number;
  orphanCount: number;
  orphanBytes: number;
  foreignCount: number;
  missingCount: number;
  objects: ScanObject[];
  orphans: ScanObject[];
  missing: MissingRecord[];
}

interface FileKeyRow {
  id: string;
  original_name: string;
  r2_key: string;
  size: number;
  status: string;
  created_at: number;
}

function isForeign(key: string): boolean {
  return !key.startsWith('uploads/');
}

function toScanObject(object: BucketObject, tracked: boolean): ScanObject {
  return {
    key: object.key,
    size: object.size,
    lastModified: object.lastModified,
    tracked,
    foreign: !tracked && isForeign(object.key),
  };
}

/**
 * Walk the whole bucket and reconcile it with the `files` table.
 * Objects absent from the DB are leftovers; DB rows absent from the bucket are
 * missing objects (in-progress uploads are ignored on both sides).
 */
export async function scanBucket(): Promise<BucketScanResult> {
  const seen = new Map<string, BucketObject>();
  let token: string | null = null;
  let truncated = false;

  do {
    const page = await listObjectsPage('', token ?? undefined, PAGE_SIZE);
    for (const object of page.objects) {
      if (!object.key) continue;
      if (seen.has(object.key)) continue;
      seen.set(object.key, object);
      if (seen.size >= MAX_SCAN_OBJECTS) {
        truncated = true;
        break;
      }
    }
    token = truncated ? null : page.nextToken;
  } while (token);

  const rows = allRows<FileKeyRow>(
    'SELECT id, original_name, r2_key, size, status, created_at FROM files ORDER BY created_at DESC',
  );
  const known = new Set(rows.map((row) => row.r2_key));

  const objects = [...seen.values()];
  const orphans = objects.filter((object) => !known.has(object.key));
  const trackedObjects = objects.filter((object) => known.has(object.key));
  const missing = rows.filter((row) => row.status !== 'uploading' && !seen.has(row.r2_key));

  const totalSize = objects.reduce((sum, object) => sum + object.size, 0);
  const orphanBytes = orphans.reduce((sum, object) => sum + object.size, 0);

  return {
    scannedAt: Date.now(),
    truncated,
    objectCount: objects.length,
    totalSize,
    trackedCount: trackedObjects.length,
    orphanCount: orphans.length,
    orphanBytes,
    foreignCount: orphans.filter((object) => isForeign(object.key)).length,
    missingCount: missing.length,
    objects: objects
      .sort((a, b) => a.key.localeCompare(b.key))
      .slice(0, MAX_RETURN_OBJECTS)
      .map((object) => toScanObject(object, known.has(object.key))),
    orphans: orphans
      .sort((a, b) => b.size - a.size)
      .slice(0, MAX_ORPHAN_LIST)
      .map((object) => toScanObject(object, false)),
    missing: missing.slice(0, MAX_MISSING_LIST).map((row) => ({
      id: row.id,
      name: row.original_name,
      key: row.r2_key,
      status: row.status,
      size: Number(row.size ?? 0),
      createdAt: Number(row.created_at ?? 0),
    })),
  };
}

/** Fetch every R2 key currently registered in the database (including uploads in progress). */
export function knownKeys(): Set<string> {
  const rows = allRows<{ r2_key: string }>('SELECT r2_key FROM files');
  return new Set(rows.map((row) => row.r2_key));
}
