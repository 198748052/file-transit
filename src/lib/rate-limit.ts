/**
 * Tiny in-memory fixed-window rate limiter.
 *
 * Used to slow down password guessing against the single admin login. State is
 * per-process and keyed by client IP, which is enough for a personal deployment
 * and costs nothing beyond a Map. Expired buckets are pruned by the maintenance
 * job so the map can't grow without bound.
 */

interface Bucket {
  count: number;
  resetAt: number;
}

const buckets = new Map<string, Bucket>();

function live(key: string): Bucket | undefined {
  const bucket = buckets.get(key);
  if (bucket && bucket.resetAt <= Date.now()) {
    buckets.delete(key);
    return undefined;
  }
  return bucket;
}

/** Whether the key has reached `max` failures in the current window. */
export function isRateLimited(key: string, max: number): { limited: boolean; retryAfterSec: number } {
  const bucket = live(key);
  if (bucket && bucket.count >= max) {
    return { limited: true, retryAfterSec: Math.max(1, Math.ceil((bucket.resetAt - Date.now()) / 1000)) };
  }
  return { limited: false, retryAfterSec: 0 };
}

/** Record one failed attempt, opening a fresh window on the first failure. */
export function recordFailure(key: string, windowMs: number): void {
  const bucket = live(key);
  if (bucket) {
    bucket.count += 1;
    return;
  }
  buckets.set(key, { count: 1, resetAt: Date.now() + windowMs });
}

/** Forget a key (e.g. after a successful login). */
export function clearRateLimit(key: string): void {
  buckets.delete(key);
}

/** Drop every expired bucket; called from the periodic maintenance job. */
export function pruneRateLimits(): void {
  const now = Date.now();
  for (const [key, bucket] of buckets) {
    if (bucket.resetAt <= now) buckets.delete(key);
  }
}
