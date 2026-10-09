import express from 'express';
import cors from 'cors';
import { existsSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { config } from './config.js';
import { errorHandler, HttpError } from './lib/http.js';
import { authRouter } from './routes/auth.js';
import { filesRouter } from './routes/files.js';
import { shareRouter } from './routes/share.js';
import { collectionsRouter } from './routes/collections.js';
import { settingsRouter } from './routes/settings.js';
import { updateRouter } from './routes/update.js';

export function createApp() {
  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', true);

  app.use(express.json({ limit: '1mb' }));
  // Dev: the Vite frontend runs on another origin. Prod: same origin, harmless.
  app.use(cors({ origin: true }));

  app.get('/api/health', (_req, res) => {
    res.json({ ok: true, env: config.isProd ? 'production' : 'development' });
  });

  app.use('/api/auth', authRouter);
  app.use('/api/files', filesRouter);
  app.use('/api/share', shareRouter);
  app.use('/api/collections', collectionsRouter);
  app.use('/api/settings', settingsRouter);
  app.use('/api/update', updateRouter);

  // Unknown API paths → JSON 404 (must precede the SPA fallback).
  app.use('/api', (_req, _res, next) => next(new HttpError(404, 'not_found')));

  // Serve the built SPA in production.
  const distDir = resolve(process.cwd(), 'web', 'dist');
  if (existsSync(distDir)) {
    app.use(express.static(distDir));
    app.get('*', (_req, res) => res.sendFile(join(distDir, 'index.html')));
  }

  app.use(errorHandler);
  return app;
}
