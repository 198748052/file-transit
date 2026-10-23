import type { Request } from 'express';
import { db } from '../db.js';
import type { LogResult } from '../db.js';

export interface LogInput {
  action: string;
  actor?: string | null;
  targetType?: string | null;
  targetId?: string | null;
  targetName?: string | null;
  detail?: unknown;
  result?: LogResult;
  ip?: string | null;
  userAgent?: string | null;
  referer?: string | null;
}

/** Client IP without the IPv4-mapped IPv6 prefix, for readable logs. */
export function clientIp(req: Request): string | null {
  const raw = req.ip ?? req.socket.remoteAddress ?? null;
  if (!raw) return null;
  return raw.startsWith('::ffff:') ? raw.slice(7) : raw;
}

/** The admin name behind the current request, or `public` for share pages. */
export function actorOf(req: Request): string {
  return req.user?.sub ?? 'public';
}

function trim(value: string | undefined | null, max: number): string | null {
  if (!value) return null;
  return value.length > max ? value.slice(0, max) : value;
}

/** Request metadata that helps trace public share access and hotlinking. */
export function requestSource(req: Request): {
  ip: string | null;
  userAgent: string | null;
  referer: string | null;
} {
  return {
    ip: clientIp(req),
    userAgent: trim(req.get('user-agent'), 512),
    referer: trim(req.get('referer') ?? req.get('referrer'), 1024),
  };
}

/**
 * Append an entry to the activity log. Best effort: a logging failure must never
 * break the request it describes.
 */
export function recordLog(input: LogInput): void {
  try {
    db.prepare(
      `INSERT INTO activity_logs
         (action, actor, target_type, target_id, target_name, detail, result, ip, user_agent, referer, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    ).run(
      input.action,
      input.actor ?? null,
      input.targetType ?? null,
      input.targetId ?? null,
      input.targetName ?? null,
      input.detail === undefined ? null : JSON.stringify(input.detail),
      input.result ?? 'ok',
      input.ip ?? null,
      input.userAgent ?? null,
      input.referer ?? null,
      Date.now(),
    );
  } catch (err) {
    console.error('Failed to write activity log:', err);
  }
}

/** Keep the log table bounded: retain the newest `keep` entries. */
export function pruneActivityLogs(keep = 5000): number {
  const result = db
    .prepare(
      `DELETE FROM activity_logs
        WHERE id <= (SELECT id FROM activity_logs ORDER BY id DESC LIMIT 1 OFFSET ?)`,
    )
    .run(keep);
  return Number(result.changes);
}
