import { Router, type Request } from 'express';
import { z } from 'zod';
import { config } from '../config.js';
import { asyncHandler, HttpError } from '../lib/http.js';
import { requireAuth } from '../auth.js';
import { getSetting, setSetting, getEffectiveR2, R2_KEYS } from '../lib/settings.js';
import { resetR2Client, testConnection, applyCors } from '../lib/r2.js';

export const settingsRouter = Router();
settingsRouter.use(requireAuth);

function secretExists(): boolean {
  return !!(getSetting(R2_KEYS.secretAccessKey) || config.r2.secretAccessKey);
}

function r2Status() {
  const eff = getEffectiveR2();
  return {
    configured: !!eff,
    accountId: eff?.accountId ?? '',
    accessKeyId: eff?.accessKeyId ?? '',
    bucket: eff?.bucket ?? '',
    endpoint: eff?.endpoint ?? '',
    hasSecret: secretExists(),
    storedIn: (getSetting(R2_KEYS.accountId) ? 'db' : config.r2.accountId ? 'env' : 'none') as 'db' | 'env' | 'none',
  };
}

settingsRouter.get('/r2', (_req, res) => {
  res.json(r2Status());
});

const putSchema = z.object({
  accountId: z.string().trim().min(1),
  accessKeyId: z.string().trim().min(1),
  secretAccessKey: z.string().trim().optional(),
  bucket: z.string().trim().min(1),
  endpoint: z.string().trim().optional(),
});

settingsRouter.put(
  '/r2',
  asyncHandler(async (req, res) => {
    const input = putSchema.parse(req.body ?? {});
    if (!secretExists() && !input.secretAccessKey) {
      throw new HttpError(400, 'secret_required');
    }
    setSetting(R2_KEYS.accountId, input.accountId);
    setSetting(R2_KEYS.accessKeyId, input.accessKeyId);
    setSetting(R2_KEYS.bucket, input.bucket);
    setSetting(R2_KEYS.endpoint, input.endpoint ?? '');
    if (input.secretAccessKey) setSetting(R2_KEYS.secretAccessKey, input.secretAccessKey);
    resetR2Client();
    res.json(r2Status());
  }),
);

settingsRouter.post(
  '/r2/test',
  asyncHandler(async (_req, res) => {
    try {
      const info = await testConnection();
      res.json({ ok: true, ...info });
    } catch (err) {
      throw new HttpError(502, `r2_check_failed: ${describeS3Error(err)}`);
    }
  }),
);

function collectOrigins(req: Request): string[] {
  const set = new Set<string>();
  const addOrigin = (v?: string) => {
    try {
      if (v) set.add(new URL(v).origin);
    } catch {
      /* ignore malformed */
    }
  };
  addOrigin(config.appBaseUrl);
  addOrigin(req.headers.origin ?? undefined);
  set.add('http://localhost:5173');
  set.add('http://localhost:3000');
  return [...set];
}

settingsRouter.post(
  '/r2/cors',
  asyncHandler(async (req, res) => {
    try {
      const origins = await applyCors(collectOrigins(req));
      res.json({ ok: true, origins });
    } catch (err) {
      throw new HttpError(502, `r2_cors_failed: ${describeS3Error(err)}`);
    }
  }),
);

/** Turn AWS/R2 SDK errors into a short human-readable reason. */
function describeS3Error(err: unknown): string {
  const e = err as { name?: string; message?: string; $metadata?: { httpStatusCode?: number } };
  const code = e?.name ?? 'Error';
  const status = e?.$metadata?.httpStatusCode;
  const message = e?.message ?? '';
  if (code === 'NoSuchBucket' || status === 404) return '桶不存在或名称错误';
  if (code === 'ForbiddenAccess' || status === 403) return '凭据无权限（AccessDenied）';
  if (code === 'InvalidAccessKeyId' || code === 'SignatureDoesNotMatch') return 'Access Key 或 Secret 不正确';
  if (/EPROTO|SSL|handshake|certificate|getaddrinfo|ENOTFOUND|ECONN|EAI_AGAIN|network|fetch/i.test(`${code} ${message}`)) {
    return '无法连接到 R2 端点，请检查 Account ID / Endpoint 是否正确、网络是否可达';
  }
  return `${code}${message ? `: ${message.slice(0, 120)}` : ''}`;
}
