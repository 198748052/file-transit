import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler, HttpError } from '../lib/http.js';
import {
  changePassword,
  changeUsername,
  clearPanelPassword,
  clearPanelUsername,
  currentUsername,
  passwordChangedAt,
  passwordSource,
  requireAuth,
  signToken,
  usernameSource,
  verifyAdmin,
  verifyPassword,
} from '../auth.js';
import { config } from '../config.js';
import { clearRateLimit, isRateLimited, recordFailure } from '../lib/rate-limit.js';

export const authRouter = Router();

const loginSchema = z.object({
  username: z.string().min(1),
  password: z.string().min(1),
});

// Throttle password guessing: at most this many failures per IP per window.
const LOGIN_MAX_FAILURES = 10;
const LOGIN_WINDOW_MS = 15 * 60 * 1000;

authRouter.post(
  '/login',
  asyncHandler(async (req, res) => {
    const key = `login:${req.ip ?? req.socket.remoteAddress ?? 'unknown'}`;
    const { limited, retryAfterSec } = isRateLimited(key, LOGIN_MAX_FAILURES);
    if (limited) {
      res.setHeader('Retry-After', String(retryAfterSec));
      throw new HttpError(429, 'too_many_attempts');
    }

    const { username, password } = loginSchema.parse(req.body ?? {});
    const ok = await verifyAdmin(username, password);
    if (!ok) {
      recordFailure(key, LOGIN_WINDOW_MS);
      throw new HttpError(401, 'invalid_credentials');
    }
    clearRateLimit(key);
    res.json({ token: signToken(), username: currentUsername(), expiresIn: config.jwtExpiresIn });
  }),
);

authRouter.get('/me', requireAuth, (_req, res) => {
  res.json({ username: currentUsername() });
});

authRouter.get('/username', requireAuth, (_req, res) => {
  res.json({ username: currentUsername(), storedIn: usernameSource() });
});

const changeUsernameSchema = z.object({
  currentPassword: z.string().min(1),
  newUsername: z.string().trim().min(1).max(64),
});

authRouter.post(
  '/username',
  requireAuth,
  asyncHandler(async (req, res) => {
    const { currentPassword, newUsername } = changeUsernameSchema.parse(req.body ?? {});
    if (!(await verifyPassword(currentPassword))) throw new HttpError(403, 'wrong_password');
    if (newUsername === currentUsername()) throw new HttpError(400, 'same_username');
    changeUsername(newUsername);
    res.json({ ok: true, username: newUsername });
  }),
);

authRouter.delete(
  '/username',
  requireAuth,
  asyncHandler(async (_req, res) => {
    clearPanelUsername();
    res.json({ ok: true });
  }),
);

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
