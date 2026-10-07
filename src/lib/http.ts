import type { NextFunction, Request, RequestHandler, Response } from 'express';

export class HttpError extends Error {
  constructor(
    public status: number,
    message: string,
    public code?: string,
  ) {
    super(message);
  }
}

type AsyncFn = (req: Request, res: Response, next: NextFunction) => Promise<unknown>;

/** Wrap an async route handler so rejected promises reach the error middleware. */
export function asyncHandler(fn: AsyncFn): RequestHandler {
  return (req, res, next) => {
    fn(req, res, next).catch(next);
  };
}

export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction) {
  if (err instanceof HttpError) {
    res.status(err.status).json({ error: err.message, code: err.code });
    return;
  }
  // Zod validation errors surface as 400
  const zodLike = err as { name?: string; issues?: unknown[] };
  if (zodLike?.name === 'ZodError') {
    res.status(400).json({ error: 'validation_failed', details: zodLike.issues });
    return;
  }
  console.error('Unhandled error:', err);
  res.status(500).json({ error: 'internal_server_error' });
}
