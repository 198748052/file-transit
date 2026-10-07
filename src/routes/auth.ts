import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler, HttpError } from '../lib/http.js';
import {
  changePassword,
  clearPanelPassword,
  passwordChangedAt,
  passwordSource,
  requireAuth,
  signToken,
  verifyAdmin,
  verifyPassword,
} from '../auth.js';
import { config } from '../config.js';

export const authRouter = Router();

const loginSchema = z.object({
  username: z.string().min(1),
  password: z.string().min(1),
});

authRouter.post(
  '/login',
  asyncHandler(async (req, res) => {
    const { username, password } = loginSchema.parse(req.body ?? {});
    const ok = await verifyAdmin(username, password);
    if (!ok) throw new HttpError(401, 'invalid_credentials');
    res.json({ token: signToken(), username: config.adminUsername, expiresIn: config.jwtExpiresIn });
  }),
);

authRouter.get('/me', requireAuth, (req, res) => {
  res.json({ username: req.user?.sub ?? config.adminUsername });
});

authRouter.get('/password', requireAuth, (_req, res) => {
  res.json({ storedIn: passwordSource(), changedAt: passwordChangedAt() });
});

const changeSchema = z.object({
  currentPassword: z.string().min(1),
  newPassword: z.string().min(8),
});

authRouter.post(
  '/password',
  requireAuth,
  asyncHandler(async (req, res) => {
    const { currentPassword, newPassword } = changeSchema.parse(req.body ?? {});
    if (!(await verifyPassword(currentPassword))) throw new HttpError(403, 'wrong_password');
    if (currentPassword === newPassword) throw new HttpError(400, 'same_password');
    await changePassword(newPassword);
    res.json({ ok: true });
  }),
);

authRouter.delete(
  '/password',
  requireAuth,
  asyncHandler(async (_req, res) => {
    clearPanelPassword();
    res.json({ ok: true });
  }),
);
