import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { config } from './config.js';

export type FileStatus = 'uploading' | 'ready' | 'expired' | 'deleted';

export interface FileRow {
  id: string;
  share_code: string;
  original_name: string;
  r2_key: string;
  size: number;
  mime_type: string;
  status: FileStatus;
  upload_id: string | null;
  part_size: number | null;
  created_at: number;
  expires_at: number | null;
  password_hash: string | null;
  download_count: number;
  last_download_at: number | null;
}

const dbPath = resolve(config.dbPath);
mkdirSync(dirname(dbPath), { recursive: true });

export const db = new DatabaseSync(dbPath);

type SqlParam = string | number | bigint | Uint8Array | null;

/** Run a SELECT that returns multiple rows, cast to a concrete row type. */
export function allRows<T>(sql: string, ...params: SqlParam[]): T[] {
  return db.prepare(sql).all(...params) as unknown as T[];
}

/** Run a SELECT that returns at most one row, cast to a concrete row type. */
export function getRow<T>(sql: string, ...params: SqlParam[]): T | undefined {
  return db.prepare(sql).get(...params) as unknown as T | undefined;
}

db.exec(`
  PRAGMA journal_mode = WAL;
  PRAGMA foreign_keys = ON;

  CREATE TABLE IF NOT EXISTS files (
    id              TEXT PRIMARY KEY,
    share_code      TEXT NOT NULL UNIQUE,
    original_name   TEXT NOT NULL,
    r2_key          TEXT NOT NULL,
    size            INTEGER NOT NULL DEFAULT 0,
    mime_type       TEXT NOT NULL DEFAULT 'application/octet-stream',
    status          TEXT NOT NULL DEFAULT 'uploading',
    upload_id       TEXT,
    part_size       INTEGER,
    created_at      INTEGER NOT NULL,
    expires_at      INTEGER,
    password_hash   TEXT,
    download_count  INTEGER NOT NULL DEFAULT 0,
    last_download_at INTEGER
  );

  CREATE INDEX IF NOT EXISTS idx_files_status_expires ON files(status, expires_at);
  CREATE INDEX IF NOT EXISTS idx_files_created ON files(created_at DESC);

  CREATE TABLE IF NOT EXISTS settings (
    key   TEXT PRIMARY KEY,
    value TEXT
  );
`);

/** Shape returned to the frontend (never leaks r2_key / password_hash). */
export interface FileDTO {
  id: string;
  shareCode: string;
  name: string;
  size: number;
  mimeType: string;
  status: FileStatus;
  createdAt: number;
  expiresAt: number | null;
  hasPassword: boolean;
  downloadCount: number;
  lastDownloadAt: number | null;
  shareUrl: string;
}

export function toFileDTO(row: FileRow): FileDTO {
  return {
    id: row.id,
    shareCode: row.share_code,
    name: row.original_name,
    size: Number(row.size),
    mimeType: row.mime_type,
    status: row.status,
    createdAt: Number(row.created_at),
    expiresAt: row.expires_at === null ? null : Number(row.expires_at),
    hasPassword: row.password_hash !== null,
    downloadCount: Number(row.download_count),
    lastDownloadAt: row.last_download_at === null ? null : Number(row.last_download_at),
    shareUrl: `${config.appBaseUrl}/s/${row.share_code}`,
  };
}
