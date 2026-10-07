import 'dotenv/config';
import { z } from 'zod';

const envSchema = z
  .object({
    NODE_ENV: z.enum(['development', 'production']).default('development'),
    HOST: z.string().default('0.0.0.0'),
    PORT: z.coerce.number().int().positive().default(8642),
    APP_BASE_URL: z.string().url().default('http://localhost:8642'),

    JWT_SECRET: z.string().min(8, 'JWT_SECRET must be at least 8 characters'),
    JWT_EXPIRES_IN: z.string().default('7d'),
    ADMIN_USERNAME: z.string().min(1).default('admin'),
    ADMIN_PASSWORD: z.string().min(1).optional(),
    ADMIN_PASSWORD_HASH: z.string().min(1).optional(),

    R2_ACCOUNT_ID: z.string().default(''),
    R2_ACCESS_KEY_ID: z.string().default(''),
    R2_SECRET_ACCESS_KEY: z.string().default(''),
    R2_BUCKET: z.string().default(''),
    R2_ENDPOINT: z.preprocess((v) => (v === '' ? undefined : v), z.string().url().optional()),

    MULTIPART_THRESHOLD_MB: z.coerce.number().int().min(5).default(8),
    UPLOAD_PART_SIZE_MB: z.coerce.number().int().min(5).default(8),
    PRESIGNED_PUT_TTL_SECONDS: z.coerce.number().int().positive().default(3600),
    PRESIGNED_GET_TTL_SECONDS: z.coerce.number().int().positive().default(300),

    DB_PATH: z.string().default('./data/file-transit.db'),
    EXPIRY_CRON: z.string().default('0 * * * *'),

    UPDATE_ENABLED: z.string().default('true'),
    UPDATE_BRANCH: z.string().default('main'),
    UPDATE_PM2_NAME: z.string().default('file-transit'),
    UPDATE_REPO_DIR: z.string().default(''),
    UPDATE_CHECK_CRON: z.string().default('0 */6 * * *'),
  })
  .refine((v) => v.ADMIN_PASSWORD || v.ADMIN_PASSWORD_HASH, {
    message: 'Either ADMIN_PASSWORD or ADMIN_PASSWORD_HASH must be set',
  });

const parsed = envSchema.safeParse(process.env);
if (!parsed.success) {
  console.error('❌ Invalid environment configuration:');
  for (const issue of parsed.error.issues) {
    console.error(`   - ${issue.path.join('.') || '(root)'}: ${issue.message}`);
  }
  console.error('\nCopy .env.example to .env and fill in the values.');
  process.exit(1);
}

const env = parsed.data;

export const config = {
  isProd: env.NODE_ENV === 'production',
  host: env.HOST,
  port: env.PORT,
  appBaseUrl: env.APP_BASE_URL.replace(/\/$/, ''),

  jwtSecret: env.JWT_SECRET,
  jwtExpiresIn: env.JWT_EXPIRES_IN,
  adminUsername: env.ADMIN_USERNAME,
  adminPassword: env.ADMIN_PASSWORD,
  adminPasswordHash: env.ADMIN_PASSWORD_HASH,

  r2: {
    accountId: env.R2_ACCOUNT_ID,
    accessKeyId: env.R2_ACCESS_KEY_ID,
    secretAccessKey: env.R2_SECRET_ACCESS_KEY,
    bucket: env.R2_BUCKET,
    endpoint: env.R2_ENDPOINT ?? (env.R2_ACCOUNT_ID ? `https://${env.R2_ACCOUNT_ID}.r2.cloudflarestorage.com` : ''),
    region: 'auto',
  },

  multipartThresholdBytes: env.MULTIPART_THRESHOLD_MB * 1024 * 1024,
  partSizeBytes: env.UPLOAD_PART_SIZE_MB * 1024 * 1024,
  presignedPutTtl: env.PRESIGNED_PUT_TTL_SECONDS,
  presignedGetTtl: env.PRESIGNED_GET_TTL_SECONDS,

  dbPath: env.DB_PATH,
  expiryCron: env.EXPIRY_CRON,

  update: {
    enabled: env.UPDATE_ENABLED.toLowerCase() !== 'false' && env.UPDATE_ENABLED !== '0',
    branch: env.UPDATE_BRANCH,
    pm2Name: env.UPDATE_PM2_NAME,
    repoDir: env.UPDATE_REPO_DIR || process.cwd(),
    checkCron: env.UPDATE_CHECK_CRON,
  },
} as const;

export type AppConfig = typeof config;
