import { config } from '../config.js';
import { HttpError } from './http.js';
import { R2_KEYS, getEffectiveR2ApiToken, getSetting } from './settings.js';

/** Cloudflare free tier: 10 GB-month of Standard storage (decimal GB). */
export const FREE_TIER_BYTES = 10_000_000_000;

const CACHE_TTL_MS = 60_000;

export interface R2Usage {
  totalBytes: number;
  publishedBytes: number;
  pendingBytes: number;
  standardBytes: number;
  infrequentAccessBytes: number;
  objectCount: number;
  fetchedAt: number;
}

interface SizeMetrics {
  payloadSize?: number;
  metadataSize?: number;
  objects?: number;
}

interface ClassMetrics {
  published?: SizeMetrics;
  uploaded?: SizeMetrics;
}

interface MetricsResponse {
  success?: boolean;
  errors?: { code: number; message: string }[];
  result?: {
    standard?: ClassMetrics;
    infrequentAccess?: ClassMetrics;
  };
}

let cache: { key: string; at: number; usage: R2Usage } | null = null;

export function clearUsageCache(): void {
  cache = null;
}

function accountId(): string {
  return getSetting(R2_KEYS.accountId) || config.r2.accountId || '';
}

function bytesOf(metrics?: SizeMetrics): number {
  const payload = Number(metrics?.payloadSize ?? 0);
  const metadata = Number(metrics?.metadataSize ?? 0);
  return (Number.isFinite(payload) ? payload : 0) + (Number.isFinite(metadata) ? metadata : 0);
}

function objectsOf(metrics?: SizeMetrics): number {
  const value = Number(metrics?.objects ?? 0);
  return Number.isFinite(value) ? value : 0;
}

function aggregate(result: MetricsResponse['result']): R2Usage {
  const standard = result?.standard;
  const infrequentAccess = result?.infrequentAccess;

  const publishedBytes = bytesOf(standard?.published) + bytesOf(infrequentAccess?.published);
  const pendingBytes = bytesOf(standard?.uploaded) + bytesOf(infrequentAccess?.uploaded);
  const standardBytes = bytesOf(standard?.published) + bytesOf(standard?.uploaded);
  const infrequentAccessBytes = bytesOf(infrequentAccess?.published) + bytesOf(infrequentAccess?.uploaded);

  return {
    totalBytes: publishedBytes + pendingBytes,
    publishedBytes,
    pendingBytes,
    standardBytes,
    infrequentAccessBytes,
    objectCount: objectsOf(standard?.published) + objectsOf(infrequentAccess?.published),
    fetchedAt: Date.now(),
  };
}

async function requestMetrics(account: string, token: string): Promise<MetricsResponse> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 8000);
  try {
    const res = await fetch(`https://api.cloudflare.com/client/v4/accounts/${encodeURIComponent(account)}/r2/metrics`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal,
    });
    const body = (await res.json().catch(() => null)) as MetricsResponse | null;
    if (res.status === 401) throw new HttpError(502, 'usage_token_invalid');
    if (res.status === 403) throw new HttpError(502, 'usage_token_forbidden');
    if (!res.ok || !body?.success) {
      const detail = body?.errors?.[0]?.message ?? `HTTP ${res.status}`;
      throw new HttpError(502, `usage_fetch_failed: ${detail}`);
    }
    return body;
  } catch (err) {
    if (err instanceof HttpError) throw err;
    if (err instanceof Error && err.name === 'AbortError') throw new HttpError(504, 'usage_fetch_timeout');
    throw new HttpError(502, `usage_fetch_failed: ${err instanceof Error ? err.message : 'network_error'}`);
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Read account-wide R2 storage metrics (all buckets) from the Cloudflare API.
 * Results are cached for one minute; pass `force` to bypass the cache.
 */
export async function fetchAccountUsage(force = false): Promise<R2Usage> {
  const account = accountId();
  const token = getEffectiveR2ApiToken();
  if (!account) throw new HttpError(400, 'r2_not_configured');
  if (!token) throw new HttpError(400, 'usage_token_required');

  const key = `${account}:${token.slice(-8)}`;
  if (!force && cache && cache.key === key && Date.now() - cache.at < CACHE_TTL_MS) {
    return cache.usage;
  }

  const body = await requestMetrics(account, token);
  const usage = aggregate(body.result);
  cache = { key, at: Date.now(), usage };
  return usage;
}
