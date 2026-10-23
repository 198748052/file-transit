import { Router } from 'express';
import { z } from 'zod';
import { db, allRows, getRow, toActivityLogDTO, type ActivityLogRow } from '../db.js';
import { requireAuth } from '../auth.js';
import { actorOf, clientIp, recordLog } from '../lib/audit.js';

export const logsRouter = Router();
logsRouter.use(requireAuth);

const DEFAULT_PAGE_SIZE = 30;
const MAX_PAGE_SIZE = 200;

const querySchema = z.object({
  page: z.coerce.number().int().min(1).default(1),
  pageSize: z.coerce.number().int().min(1).max(MAX_PAGE_SIZE).default(DEFAULT_PAGE_SIZE),
  action: z.string().trim().min(1).max(64).optional(),
  result: z.enum(['ok', 'fail']).optional(),
  visitor: z.enum(['public', 'admin']).optional(),
  q: z.string().trim().max(120).optional(),
  from: z.coerce.number().int().nonnegative().optional(),
  to: z.coerce.number().int().nonnegative().optional(),
});

// ─────────────────────────── GET / ───────────────────────────
logsRouter.get('/', (req, res) => {
  const { page, pageSize, action, result, visitor, q, from, to } = querySchema.parse(req.query ?? {});

  const where: string[] = [];
  const params: (string | number)[] = [];
  if (action) {
    where.push('action = ?');
    params.push(action);
  }
  if (result) {
    where.push('result = ?');
    params.push(result);
  }
  if (visitor === 'public') {
    where.push("actor = 'public'");
  } else if (visitor === 'admin') {
    where.push("actor IS NOT NULL AND actor != 'public'");
  }
  if (from !== undefined) {
    where.push('created_at >= ?');
    params.push(from);
  }
  if (to !== undefined) {
    where.push('created_at <= ?');
    params.push(to);
  }
  if (q) {
    where.push(
      '(target_name LIKE ? OR actor LIKE ? OR ip LIKE ? OR action LIKE ? OR user_agent LIKE ? OR referer LIKE ?)',
    );
    const like = `%${q}%`;
    params.push(like, like, like, like, like, like);
  }
  const clause = where.length ? `WHERE ${where.join(' AND ')}` : '';

  const totalRow = getRow<{ total: number }>(`SELECT COUNT(*) AS total FROM activity_logs ${clause}`, ...params);
  const total = Number(totalRow?.total ?? 0);

  const rows = allRows<ActivityLogRow>(
    `SELECT * FROM activity_logs ${clause} ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?`,
    ...params,
    pageSize,
    (page - 1) * pageSize,
  );

  const actions = allRows<{ action: string; count: number }>(
    `SELECT action, COUNT(*) AS count FROM activity_logs GROUP BY action ORDER BY count DESC, action ASC`,
  ).map((row) => ({ action: row.action, count: Number(row.count) }));

  res.json({
    logs: rows.map(toActivityLogDTO),
    total,
    page,
    pageSize,
    actions,
  });
});

// ─────────────────────────── DELETE / (clear) ───────────────────────────
logsRouter.delete('/', (req, res) => {
  const cleared = Number(db.prepare('DELETE FROM activity_logs').run().changes);
  recordLog({
    action: 'logs.clear',
    actor: actorOf(req),
    ip: clientIp(req),
    detail: { cleared },
  });
  res.json({ ok: true, cleared });
});

// ─────────────────────────── GET /stats ───────────────────────────
logsRouter.get('/stats', (_req, res) => {
  const row = getRow<{ total: number; failures: number; last24h: number }>(
    `SELECT
       COUNT(*) AS total,
       SUM(CASE WHEN result = 'fail' THEN 1 ELSE 0 END) AS failures,
       SUM(CASE WHEN created_at >= ? THEN 1 ELSE 0 END) AS last24h
     FROM activity_logs`,
    Date.now() - 86_400_000,
  );
  const oldest = getRow<{ createdAt: number | null }>(
    'SELECT MIN(created_at) AS createdAt FROM activity_logs',
  );
  res.json({
    total: Number(row?.total ?? 0),
    failures: Number(row?.failures ?? 0),
    last24h: Number(row?.last24h ?? 0),
    oldestAt: oldest?.createdAt === null || oldest?.createdAt === undefined ? null : Number(oldest.createdAt),
  });
});
