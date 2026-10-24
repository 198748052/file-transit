import type { Request } from 'express';
import { HttpError } from './http.js';
import { getShareLimitSettings, type ShareLimitSettings } from './settings.js';
import { clearRateLimit, isRateLimited, recordFailure } from './rate-limit.js';
import { actorOf, clientIp, recordLog, requestSource } from './audit.js';

const MS_PER_MINUTE = 60_000;

function windowMs(settings: ShareLimitSettings): number {
  return settings.windowMinutes * MS_PER_MINUTE;
}

function ipOf(req: Request): string {
  return clientIp(req) ?? 'unknown';
}

function requestKey(req: Request): string {
  return `share:req:${ipOf(req)}`;
}

function failureKey(req: Request, code: string): string {
  return `share:fail:${code}:${ipOf(req)}`;
}

function logLimited(
  req: Request,
  code: string,
  kind: string,
  reason: 'too_many_requests' | 'too_many_attempts',
  retryAfterSec: number,
): void {
  recordLog({
    action: 'share.rate_limited',
    actor: actorOf(req),
    ...requestSource(req),
    targetType: kind,
    detail: { shareCode: code, reason, retryAfterSec },
  });
}

/**
 * Check the per-IP request budget and the per-share password failure budget
 * before a public download or preview is served. Throws 429 when blocked.
 */
export function guardShareAccess(req: Request, code: string, kind: string): void {
  const settings = getShareLimitSettings();
  if (!settings.enabled) return;

  if (settings.maxDownloads > 0) {
    const key = requestKey(req);
    const state = isRateLimited(key, settings.maxDownloads);
    if (state.limited) {
      logLimited(req, code, kind, 'too_many_requests', state.retryAfterSec);
      throw new HttpError(429, 'too_many_requests');
    }
    recordFailure(key, windowMs(settings));
  }

  const failure = isRateLimited(failureKey(req, code), settings.maxFailures);
  if (failure.limited) {
    logLimited(req, code, kind, 'too_many_attempts', failure.retryAfterSec);
    throw new HttpError(429, 'too_many_attempts');
  }
}

/** Count one wrong extraction code against this IP + share. */
export function noteSharePasswordFailure(req: Request, code: string): void {
  const settings = getShareLimitSettings();
  if (!settings.enabled) return;
  recordFailure(failureKey(req, code), windowMs(settings));
}

/** Clear the failure budget after a correct extraction code. */
export function clearSharePasswordFailures(req: Request, code: string): void {
  clearRateLimit(failureKey(req, code));
}
