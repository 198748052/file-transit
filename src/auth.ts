import type { NextFunction, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { timingSafeEqual } from 'node:crypto';
import { config } from './config.js';

// Resolve the admin password hash once. If only a plaintext password is given
// (convenient for personal use), hash it at startup.
export const adminPasswordHash: string =
  config.adminPasswordHash ?? bcrypt.hashSync(config.adminPassword as string, 10);

export interface TokenPayload {
  sub: string;
}

export function signToken(): string {
  return jwt.sign({ sub: config.adminUsername } satisfies TokenPayload, config.jwtSecret, {
    expiresIn: config.jwtExpiresIn as jwt.SignOptions['expiresIn'],
  });
}

function safeEqual(a: string, b: string): boolean {
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  if (bufA.length !== bufB.length) return false;
  return timingSafeEqual(bufA, bufB);
}

export async function verifyAdmin(username: string, password: string): Promise<boolean> {
  if (!safeEqual(username, config.adminUsername)) {
    // Still run a bcrypt compare to keep timing uniform.
    await bcrypt.compare(password, adminPasswordHash);
    return false;
  }
  return bcrypt.compare(password, adminPasswordHash);
}

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      user?: TokenPayload;
    }
  }
}

export function requireAuth(req: Request, res: Response, next: NextFunction) {
  const header = req.headers.authorization;
  const token = header?.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) {
    res.status(401).json({ error: 'unauthorized' });
    return;
  }
  try {
    const payload = jwt.verify(token, config.jwtSecret) as TokenPayload;
    req.user = payload;
    next();
  } catch {
    res.status(401).json({ error: 'invalid_token' });
  }
}
