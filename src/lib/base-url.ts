import type { Request } from 'express';
import { config } from '../config.js';
import { getSetting, SITE_KEYS } from './settings.js';

function firstHeaderValue(value?: string | string[]): string {
  const v = Array.isArray(value) ? value[0] : value;
  return (v ?? '').split(',')[0]!.trim();
}

/** The site address explicitly set in the admin panel, if any (never exposes an IP by accident). */
export function getPinnedBaseUrl(): string {
  return (getSetting(SITE_KEYS.baseUrl) ?? '').replace(/\/$/, '');
}

/**
 * Resolve the public base URL used to build share links.
 *
 * Priority: the address set in the admin panel → the incoming request host
 * (respecting reverse-proxy X-Forwarded-* headers) → APP_BASE_URL fallback.
 * Pinning the address in the panel guarantees share links never leak the
 * server IP, even if the panel is opened directly by IP:port.
 */
export function resolveBaseUrl(req: Request): string {
  const pinned = getPinnedBaseUrl();
  if (pinned) return pinned;

  const host = firstHeaderValue(req.headers['x-forwarded-host']) || req.get('host') || '';
  if (host) {
    const proto = firstHeaderValue(req.headers['x-forwarded-proto']) || req.protocol || 'http';
    return `${proto}://${host}`.replace(/\/$/, '');
  }
  return config.appBaseUrl;
}
