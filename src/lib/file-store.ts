import { db, getRow, type FileRow } from '../db.js';
import { abortMultipartUpload, deleteObject } from './r2.js';

/**
 * Abort a multipart session or delete a finished object. Throws on failure so
 * the caller can keep the DB row and let the maintenance job retry, instead of
 * dropping the record and leaking an unreferenced R2 object.
 */
export async function removeR2Object(row: FileRow): Promise<void> {
  if (row.upload_id) await abortMultipartUpload(row.r2_key, row.upload_id);
  else await deleteObject(row.r2_key);
}

/** Whether another non-deleted row still points at the same R2 object. */
export function hasOtherReferences(row: FileRow): boolean {
  const other = getRow<{ count: number }>(
    `SELECT COUNT(*) AS count FROM files
      WHERE r2_key = ? AND id != ? AND status != 'deleted'`,
    row.r2_key,
    row.id,
  );
  return Number(other?.count ?? 0) > 0;
}

/**
 * Delete a file record and its R2 object only when this was the last reference.
 * Deduplicated uploads share one object, so deleting one copy must not break
 * the others. Throws when R2 deletion fails, leaving the row for a retry.
 */
export async function removeFileRecord(row: FileRow): Promise<void> {
  if (!hasOtherReferences(row)) await removeR2Object(row);
  db.prepare('DELETE FROM files WHERE id = ?').run(row.id);
}
