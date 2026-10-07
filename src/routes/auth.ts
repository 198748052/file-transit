import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler, HttpError } from '../lib/http.js';
import { signToken, verifyAdmin, requireAuth } from '../auth.js';
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
