import type { NextFunction, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { timingSafeEqual } from 'node:crypto';
import { config } from './config.js';
import { ADMIN_KEYS, getSetting, setSetting } from './lib/settings.js';

// Fallback hash from .env (ADMIN_PASSWORD_HASH, or ADMIN_PASSWORD hashed at
// startup). A password changed in the panel is stored in the settings table and
// takes precedence over this.
const envPasswordHash: string =
  config.adminPasswordHash ?? bcrypt.hashSync(config.adminPassword as string, 10);

export function currentPasswordHash(): string {
  return getSetting(ADMIN_KEYS.passwordHash) || envPasswordHash;
}

export function passwordSource(): 'db' | 'env' {
  return getSetting(ADMIN_KEYS.passwordHash) ? 'db' : 'env';
}

/** When the password was last changed (ms epoch), or null if never. */
export function passwordChangedAt(): number | null {
  const raw = getSetting(ADMIN_KEYS.passwordChangedAt);
  const ts = raw ? Number(raw) : 0;
  return Number.isFinite(ts) && ts > 0 ? ts : null;
}

export async function verifyPassword(password: string): Promise<boolean> {
  return bcrypt.compare(password, currentPasswordHash());
}

/** Stores a new admin password and invalidates tokens issued before now. */
export async function changePassword(newPassword: string): Promise<void> {
  setSetting(ADMIN_KEYS.passwordHash, await bcrypt.hash(newPassword, 10));
  setSetting(ADMIN_KEYS.passwordChangedAt, String(Date.now()));
}

/** Drop the panel-set password so ADMIN_PASSWORD from .env applies again. */
export function clearPanelPassword(): void {
  setSetting(ADMIN_KEYS.passwordHash, '');
  setSetting(ADMIN_KEYS.passwordChangedAt, String(Date.now()));
}

export interface TokenPayload {
  sub: string;
  /**
   * `admin_password_changed_at` at issue time. Tokens whose stamp differs from
   * the current one are dead, so changing the password revokes every session
   * immediately — including one issued in the same second.
   */
  pwdAt: number;
}

export function signToken(): string {
  return jwt.sign(
    { sub: config.adminUsername, pwdAt: passwordChangedAt() ?? 0 } satisfies TokenPayload,
    config.jwtSecret,
    { expiresIn: config.jwtExpiresIn as jwt.SignOptions['expiresIn'] },
  );
}

function safeEqual(a: string, b: string): boolean {
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  if (bufA.length !== bufB.length) return false;
  return timingSafeEqual(bufA, bufB);
}

export async function verifyAdmin(username: string, password: string): Promise<boolean> {
  const hash = currentPasswordHash();
  if (!safeEqual(username, config.adminUsername)) {
    // Still run a bcrypt compare to keep timing uniform.
    await bcrypt.compare(password, hash);
    return false;
  }
  return bcrypt.compare(password, hash);
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
    if ((payload.pwdAt ?? 0) !== (passwordChangedAt() ?? 0)) {
      res.status(401).json({ error: 'token_revoked' });
      return;
    }
    req.user = payload;
    next();
  } catch {
    res.status(401).json({ error: 'invalid_token' });
  }
}
