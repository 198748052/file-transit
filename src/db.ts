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
  folder_id: string | null;
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

  CREATE TABLE IF NOT EXISTS collections (
    id              TEXT PRIMARY KEY,
    code            TEXT NOT NULL UNIQUE,
    name            TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'ready',
    created_at      INTEGER NOT NULL,
    expires_at      INTEGER,
    password_hash   TEXT,
    download_count  INTEGER NOT NULL DEFAULT 0
  );

  CREATE TABLE IF NOT EXISTS collection_items (
    collection_id TEXT NOT NULL,
    file_id       TEXT NOT NULL,
    added_at      INTEGER NOT NULL,
    PRIMARY KEY (collection_id, file_id),
    FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE,
    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
  );

  CREATE INDEX IF NOT EXISTS idx_collection_items ON collection_items(collection_id);

  CREATE TABLE IF NOT EXISTS folders (
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    parent_id  TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (parent_id) REFERENCES folders(id) ON DELETE CASCADE
  );

  CREATE INDEX IF NOT EXISTS idx_folders_parent ON folders(parent_id);
`);

// Legacy databases predate folders: add the column in place when missing.
const fileColumns = allRows<{ name: string }>('PRAGMA table_info(files)');
if (!fileColumns.some((column) => column.name === 'folder_id')) {
  db.exec('ALTER TABLE files ADD COLUMN folder_id TEXT');
}
db.exec('CREATE INDEX IF NOT EXISTS idx_files_folder ON files(folder_id, created_at DESC)');

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
  folderId: string | null;
  shareUrl: string;
}

export function toFileDTO(row: FileRow, baseUrl: string): FileDTO {
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
    folderId: row.folder_id ?? null,
    shareUrl: `${baseUrl}/s/${row.share_code}`,
  };
}

export interface FolderRow {
  id: string;
  name: string;
  parent_id: string | null;
  created_at: number;
}

export interface FolderDTO {
  id: string;
  name: string;
  parentId: string | null;
  createdAt: number;
  fileCount: number;
  totalSize: number;
  childCount: number;
}

export function toFolderDTO(
  row: FolderRow,
  counts: { fileCount?: number; totalSize?: number; childCount?: number } = {},
): FolderDTO {
  return {
    id: row.id,
    name: row.name,
    parentId: row.parent_id,
    createdAt: Number(row.created_at),
    fileCount: Number(counts.fileCount ?? 0),
    totalSize: Number(counts.totalSize ?? 0),
    childCount: Number(counts.childCount ?? 0),
  };
}

export type CollectionStatus = 'ready' | 'expired' | 'deleted';

export interface CollectionRow {
  id: string;
  code: string;
  name: string;
  status: CollectionStatus;
  created_at: number;
  expires_at: number | null;
  password_hash: string | null;
  download_count: number;
}

export interface CollectionItemDTO {
  fileId: string;
  name: string;
  size: number;
  mimeType: string;
  status: FileStatus;
  downloadCount: number;
}

export interface CollectionDTO {
  id: string;
  code: string;
  name: string;
  status: CollectionStatus;
  createdAt: number;
  expiresAt: number | null;
  hasPassword: boolean;
  downloadCount: number;
  shareUrl: string;
  itemCount: number;
  totalSize: number;
  items?: CollectionItemDTO[];
}

export function toCollectionDTO(
  row: CollectionRow,
  baseUrl: string,
  items: CollectionItemDTO[],
): CollectionDTO {
  return {
    id: row.id,
    code: row.code,
    name: row.name,
    status: row.status,
    createdAt: Number(row.created_at),
    expiresAt: row.expires_at === null ? null : Number(row.expires_at),
    hasPassword: row.password_hash !== null,
    downloadCount: Number(row.download_count),
    shareUrl: `${baseUrl}/c/${row.code}`,
    itemCount: items.length,
    totalSize: items.reduce((sum, i) => sum + i.size, 0),
    items,
  };
}
