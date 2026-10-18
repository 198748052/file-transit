import type { FileRow } from '../db.js';
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
