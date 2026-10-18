import { Router } from 'express';
import { requireAuth } from '../auth.js';
import { asyncHandler } from '../lib/http.js';
import { getStatus, checkUpdate, startUpdate, getLog } from '../lib/update.js';
import { actorOf, clientIp, recordLog } from '../lib/audit.js';

export const updateRouter = Router();
updateRouter.use(requireAuth);

// GET /api/update/status — quick local snapshot + last cached remote check.
updateRouter.get(
  '/status',
  asyncHandler(async (_req, res) => {
    res.json(await getStatus());
  }),
);

// POST /api/update/check — git fetch + compare with origin (read-only).
updateRouter.post(
  '/check',
  asyncHandler(async (_req, res) => {
    res.json(await checkUpdate());
  }),
);

// POST /api/update/run — start the detached update (reset + build + pm2 restart).
updateRouter.post(
  '/run',
  asyncHandler(async (req, res) => {
    const result = await startUpdate();
    recordLog({ action: 'system.update', actor: actorOf(req), ip: clientIp(req) });
    res.json(result);
  }),
);

// GET /api/update/log — tail of the update log + current run state.
updateRouter.get(
  '/log',
  asyncHandler(async (_req, res) => {
    res.json(getLog());
  }),
);
