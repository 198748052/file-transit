import { Router, type Request } from 'express';
import { z } from 'zod';
import { config } from '../config.js';
import { asyncHandler, HttpError } from '../lib/http.js';
import { requireAuth } from '../auth.js';
import { getSetting, setSetting, getEffectiveR2, getEffectiveR2ApiToken, R2_KEYS, SITE_KEYS } from '../lib/settings.js';
import { resetR2Client, testConnection, applyCors } from '../lib/r2.js';
import { clearUsageCache, fetchAccountUsage, FREE_TIER_BYTES } from '../lib/r2-usage.js';
import { resolveBaseUrl } from '../lib/base-url.js';
import { actorOf, clientIp, recordLog } from '../lib/audit.js';

export const settingsRouter = Router();
settingsRouter.use(requireAuth);

// ─────────────────────── Public site address ───────────────────────

const siteSchema = z.object({
  baseUrl: z.union([z.literal(''), z.string().trim().url().max(255)]).optional(),
});

function siteStatus(req: Request) {
  const pinned = getSetting(SITE_KEYS.baseUrl) ?? '';
  return {
    baseUrl: pinned,
    mode: (pinned ? 'pinned' : 'auto') as 'pinned' | 'auto',
    autoDetected: resolveBaseUrl(req),
  };
}

settingsRouter.get('/site', (req, res) => {
  res.json(siteStatus(req));
});

settingsRouter.put('/site', (req, res) => {
  const { baseUrl } = siteSchema.parse(req.body ?? {});
  setSetting(SITE_KEYS.baseUrl, baseUrl ?? '');
  recordLog({
    action: 'settings.site_update',
    actor: actorOf(req),
    ip: clientIp(req),
    detail: { baseUrl: baseUrl ?? '' },
  });
  res.json(siteStatus(req));
});


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
    hasUsageToken: !!getEffectiveR2ApiToken(),
    storedIn: (getSetting(R2_KEYS.accountId) ? 'db' : config.r2.accountId ? 'env' : 'none') as 'db' | 'env' | 'none',
  };
}

// ─────────────────── Account-wide R2 usage (optional) ───────────────────

function usageAccountId(): string {
  return getSetting(R2_KEYS.accountId) || config.r2.accountId || '';
}

settingsRouter.get(
  '/r2/usage',
  asyncHandler(async (req, res) => {
    const hasToken = !!getEffectiveR2ApiToken();
    const configured = hasToken && !!usageAccountId();
    if (!configured) {
      res.json({
        configured,
        hasToken,
        freeTierBytes: FREE_TIER_BYTES,
        error: hasToken ? 'r2_not_configured' : undefined,
      });
      return;
    }

    try {
      const usage = await fetchAccountUsage(req.query.refresh === '1');
      res.json({ configured, hasToken, freeTierBytes: FREE_TIER_BYTES, usage });
    } catch (err) {
      res.json({
        configured,
        hasToken,
        freeTierBytes: FREE_TIER_BYTES,
        error: err instanceof HttpError ? err.message : 'usage_fetch_failed',
      });
    }
  }),
);

const usageTokenSchema = z.object({
  token: z.string().trim().max(300).default(''),
});

settingsRouter.put('/r2/usage-token', (req, res) => {
  const { token } = usageTokenSchema.parse(req.body ?? {});
  if (token && token.length < 20) throw new HttpError(400, 'invalid_token');
  setSetting(R2_KEYS.apiToken, token);
  clearUsageCache();
  recordLog({
    action: 'settings.r2_usage_token',
    actor: actorOf(req),
    ip: clientIp(req),
    detail: { cleared: !token },
  });
  res.json({ hasToken: !!getEffectiveR2ApiToken(), configured: !!getEffectiveR2ApiToken() && !!usageAccountId() });
});

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
  asyncHandler(async (req, res) => {
    try {
      const info = await testConnection();
      recordLog({ action: 'settings.r2_test', actor: actorOf(req), ip: clientIp(req), detail: { bucket: info.bucket } });
      res.json({ ok: true, ...info });
    } catch (err) {
      const reason = describeS3Error(err);
      recordLog({
        action: 'settings.r2_test',
        actor: actorOf(req),
        ip: clientIp(req),
        result: 'fail',
        detail: { reason },
      });
      throw new HttpError(502, `r2_check_failed: ${reason}`);
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
  addOrigin(resolveBaseUrl(req));
  addOrigin(req.headers.origin ?? undefined);
  set.add('http://localhost:5173');
  set.add('http://localhost:8642');
  return [...set];
}

settingsRouter.post(
  '/r2/cors',
  asyncHandler(async (req, res) => {
    try {
      const origins = await applyCors(collectOrigins(req));
      recordLog({ action: 'settings.r2_cors', actor: actorOf(req), ip: clientIp(req), detail: { origins } });
      res.json({ ok: true, origins });
    } catch (err) {
      const reason = describeS3Error(err);
      recordLog({
        action: 'settings.r2_cors',
        actor: actorOf(req),
        ip: clientIp(req),
        result: 'fail',
        detail: { reason },
      });
      throw new HttpError(502, `r2_cors_failed: ${reason}`);
    }
  }),
);

/** Turn AWS/R2 SDK errors into a short human-readable reason. */
function describeS3Error(err: unknown): string {
  const e = err as {
    name?: string;
    message?: string;
    $metadata?: { httpStatusCode?: number };
    cause?: { code?: string; message?: string };
  };
  const code = e?.name ?? 'Error';
  const status = e?.$metadata?.httpStatusCode;
  const message = e?.message ?? '';
  const causeDetail = [e?.cause?.code, e?.cause?.message].filter(Boolean).join(': ');
  if (code === 'NoSuchBucket' || status === 404) return '桶不存在或名称错误';
  if (code === 'ForbiddenAccess' || status === 403) return '凭据无权限（AccessDenied）';
  if (code === 'InvalidAccessKeyId' || code === 'SignatureDoesNotMatch') return 'Access Key 或 Secret 不正确';
  if (/EPROTO|SSL|handshake|certificate|getaddrinfo|ENOTFOUND|ECONN|EAI_AGAIN|network|fetch|timeout/i.test(`${code} ${message} ${causeDetail}`)) {
    const reason = causeDetail || code;
    return `无法连接到 R2 端点（${reason}），请检查 Account ID / Endpoint 是否正确、网络是否可达`;
  }
  return `${code}${message ? `: ${message.slice(0, 120)}` : ''}`;
}
