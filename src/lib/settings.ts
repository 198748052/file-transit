import { db, getRow } from '../db.js';
import { config } from '../config.js';

export interface EffectiveR2 {
  accountId: string;
  accessKeyId: string;
  secretAccessKey: string;
  bucket: string;
  endpoint: string;
  region: string;
}

export const R2_KEYS = {
  accountId: 'r2_account_id',
  accessKeyId: 'r2_access_key_id',
  secretAccessKey: 'r2_secret_access_key',
  bucket: 'r2_bucket',
  endpoint: 'r2_endpoint',
} as const;

export function getSetting(key: string): string | null {
  const row = getRow<{ value: string | null }>('SELECT value FROM settings WHERE key = ?', key);
  return row?.value ?? null;
}

export function setSetting(key: string, value: string | null): void {
  if (value === null || value === '') {
    db.prepare('DELETE FROM settings WHERE key = ?').run(key);
    return;
  }
  db.prepare(
    `INSERT INTO settings (key, value) VALUES (?, ?)
     ON CONFLICT(key) DO UPDATE SET value = excluded.value`,
  ).run(key, value);
}

/** A value from the DB overrides the one from .env. */
function pick(dbKey: string, envValue: string): string {
  return getSetting(dbKey) || envValue || '';
}

/** Merge DB overrides with env defaults; return null when required fields are missing. */
export function getEffectiveR2(): EffectiveR2 | null {
  const accountId = pick(R2_KEYS.accountId, config.r2.accountId);
  const accessKeyId = pick(R2_KEYS.accessKeyId, config.r2.accessKeyId);
  const secretAccessKey = pick(R2_KEYS.secretAccessKey, config.r2.secretAccessKey);
  const bucket = pick(R2_KEYS.bucket, config.r2.bucket);

  if (!accountId || !accessKeyId || !secretAccessKey || !bucket) return null;

  const endpoint = getSetting(R2_KEYS.endpoint) || config.r2.endpoint || `https://${accountId}.r2.cloudflarestorage.com`;
  return { accountId, accessKeyId, secretAccessKey, bucket, endpoint, region: 'auto' };
}

export function isR2Configured(): boolean {
  return getEffectiveR2() !== null;
}

/** Mask a secret so it can be shown in the UI without revealing it. */
export function maskSecret(value: string): string {
  if (!value) return '';
  if (value.length <= 8) return '•'.repeat(value.length);
  return `${value.slice(0, 4)}${'•'.repeat(Math.min(value.length - 8, 16))}${value.slice(-4)}`;
}
