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
  apiToken: 'r2_api_token',
} as const;

/**
 * An admin credential changed from the panel overrides ADMIN_USERNAME /
 * ADMIN_PASSWORD / ADMIN_PASSWORD_HASH in .env. The timestamp key still uses its
 * historical name so existing databases keep working; it now stamps any
 * credential change (username or password) and revokes issued tokens.
 */
export const ADMIN_KEYS = {
  username: 'admin_username',
  passwordHash: 'admin_password_hash',
  credentialsChangedAt: 'admin_password_changed_at',
} as const;

/** Public site address used to build share links. Overrides request-host auto-detection. */
export const SITE_KEYS = {
  baseUrl: 'site_base_url',
} as const;

/** Throttle settings for public share downloads and previews. */
export const SHARE_LIMIT_KEYS = {
  enabled: 'share_limit_enabled',
  maxFailures: 'share_limit_max_failures',
  windowMinutes: 'share_limit_window_minutes',
  maxDownloads: 'share_limit_max_downloads',
} as const;

export interface ShareLimitSettings {
  enabled: boolean;
  maxFailures: number;
  windowMinutes: number;
  maxDownloads: number;
}

function intSetting(key: string, fallback: number, min: number, max: number): number {
  const raw = getSetting(key);
  if (raw === null || raw === '') return fallback;
  const value = Number(raw);
  if (!Number.isInteger(value) || value < min || value > max) return fallback;
  return value;
}

export function getShareLimitSettings(): ShareLimitSettings {
  const enabledRaw = getSetting(SHARE_LIMIT_KEYS.enabled);
  return {
    enabled: enabledRaw === null || enabledRaw === '' ? true : enabledRaw === '1',
    maxFailures: intSetting(SHARE_LIMIT_KEYS.maxFailures, 10, 1, 100),
    windowMinutes: intSetting(SHARE_LIMIT_KEYS.windowMinutes, 15, 1, 1440),
    maxDownloads: intSetting(SHARE_LIMIT_KEYS.maxDownloads, 300, 0, 100000),
  };
}

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

/** Optional Cloudflare API token used to read account-wide R2 metrics. */
export function getEffectiveR2ApiToken(): string {
  return getSetting(R2_KEYS.apiToken) || config.r2.apiToken || '';
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
