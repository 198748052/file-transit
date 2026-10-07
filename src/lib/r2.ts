import {
  S3Client,
  CreateMultipartUploadCommand,
  UploadPartCommand,
  CompleteMultipartUploadCommand,
  AbortMultipartUploadCommand,
  ListPartsCommand,
  DeleteObjectCommand,
  GetObjectCommand,
  PutObjectCommand,
  HeadObjectCommand,
  HeadBucketCommand,
  PutBucketCorsCommand,
  type CompletedPart,
} from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { config } from '../config.js';
import { getEffectiveR2 } from './settings.js';
import { HttpError } from './http.js';

let cached: { s3: S3Client; bucket: string } | null = null;
let cachedSignature = '';

/** Lazily build (and cache) an S3 client from the currently effective R2 config. */
function currentR2(): { s3: S3Client; bucket: string } {
  const eff = getEffectiveR2();
  if (!eff) throw new HttpError(503, 'r2_not_configured');
  const signature = [eff.accountId, eff.accessKeyId, eff.secretAccessKey, eff.bucket, eff.endpoint].join('|');
  if (!cached || signature !== cachedSignature) {
    cached = {
      s3: new S3Client({
        region: eff.region,
        endpoint: eff.endpoint,
        credentials: { accessKeyId: eff.accessKeyId, secretAccessKey: eff.secretAccessKey },
      }),
      bucket: eff.bucket,
    };
    cachedSignature = signature;
  }
  return cached;
}

/** Force the cached client to rebuild (call after credentials change). */
export function resetR2Client(): void {
  cached = null;
  cachedSignature = '';
}

/** Presign a single-part PUT (client uploads bytes straight to R2). */
export function presignPut(key: string, contentType: string, ttl = config.presignedPutTtl) {
  const { s3, bucket } = currentR2();
  const cmd = new PutObjectCommand({ Bucket: bucket, Key: key, ContentType: contentType });
  return getSignedUrl(s3, cmd, { expiresIn: ttl });
}

/** Presign a GET for download. Forces attachment with the original filename. */
export function presignGet(key: string, filename: string, ttl = config.presignedGetTtl) {
  const { s3, bucket } = currentR2();
  const disposition = `attachment; filename="${sanitizeFilename(filename)}"`;
  const cmd = new GetObjectCommand({ Bucket: bucket, Key: key, ResponseContentDisposition: disposition });
  return getSignedUrl(s3, cmd, { expiresIn: ttl });
}

export async function createMultipartUpload(key: string, contentType: string) {
  const { s3, bucket } = currentR2();
  const res = await s3.send(new CreateMultipartUploadCommand({ Bucket: bucket, Key: key, ContentType: contentType }));
  if (!res.UploadId) throw new Error('R2 did not return an UploadId');
  return res.UploadId;
}

export function presignPart(key: string, uploadId: string, partNumber: number, ttl = config.presignedPutTtl) {
  const { s3, bucket } = currentR2();
  const cmd = new UploadPartCommand({ Bucket: bucket, Key: key, UploadId: uploadId, PartNumber: partNumber });
  return getSignedUrl(s3, cmd, { expiresIn: ttl });
}

export interface UploadedPart {
  partNumber: number;
  etag: string;
}

/** List already-uploaded parts — the backbone of resumable uploads. */
export async function listUploadedParts(key: string, uploadId: string): Promise<UploadedPart[]> {
  const { s3, bucket } = currentR2();
  const parts: UploadedPart[] = [];
  let marker: string | undefined;
  do {
    const res = await s3.send(new ListPartsCommand({ Bucket: bucket, Key: key, UploadId: uploadId, PartNumberMarker: marker }));
    for (const p of res.Parts ?? []) {
      if (p.PartNumber != null && p.ETag) parts.push({ partNumber: p.PartNumber, etag: p.ETag });
    }
    marker = res.IsTruncated ? res.NextPartNumberMarker : undefined;
  } while (marker);
  return parts;
}

export async function completeMultipartUpload(key: string, uploadId: string, parts: UploadedPart[]) {
  const { s3, bucket } = currentR2();
  const sorted: CompletedPart[] = parts
    .slice()
    .sort((a, b) => a.partNumber - b.partNumber)
    .map((p) => ({ PartNumber: p.partNumber, ETag: p.etag }));
  await s3.send(new CompleteMultipartUploadCommand({ Bucket: bucket, Key: key, UploadId: uploadId, MultipartUpload: { Parts: sorted } }));
}

export async function abortMultipartUpload(key: string, uploadId: string) {
  const { s3, bucket } = currentR2();
  await s3.send(new AbortMultipartUploadCommand({ Bucket: bucket, Key: key, UploadId: uploadId }));
}

export async function deleteObject(key: string) {
  const { s3, bucket } = currentR2();
  await s3.send(new DeleteObjectCommand({ Bucket: bucket, Key: key }));
}

export async function headObject(key: string): Promise<{ size: number } | null> {
  const { s3, bucket } = currentR2();
  try {
    const res = await s3.send(new HeadObjectCommand({ Bucket: bucket, Key: key }));
    return { size: Number(res.ContentLength ?? 0) };
  } catch (err: unknown) {
    if (isNotFound(err)) return null;
    throw err;
  }
}

/** Verify credentials + bucket access with a cheap HeadBucket call. */
export async function testConnection(): Promise<{ bucket: string; endpoint: string }> {
  const eff = getEffectiveR2();
  if (!eff) throw new HttpError(503, 'r2_not_configured');
  const client = new S3Client({
    region: eff.region,
    endpoint: eff.endpoint,
    credentials: { accessKeyId: eff.accessKeyId, secretAccessKey: eff.secretAccessKey },
  });
  await client.send(new HeadBucketCommand({ Bucket: eff.bucket }));
  return { bucket: eff.bucket, endpoint: eff.endpoint };
}

/** Configure bucket CORS so the browser can PUT parts and read the ETag. */
export async function applyCors(origins: string[]): Promise<string[]> {
  const eff = getEffectiveR2();
  if (!eff) throw new HttpError(503, 'r2_not_configured');
  const client = new S3Client({
    region: eff.region,
    endpoint: eff.endpoint,
    credentials: { accessKeyId: eff.accessKeyId, secretAccessKey: eff.secretAccessKey },
  });
  await client.send(
    new PutBucketCorsCommand({
      Bucket: eff.bucket,
      CORSConfiguration: {
        CORSRules: [
          {
            AllowedOrigins: origins,
            AllowedMethods: ['GET', 'PUT', 'POST', 'HEAD', 'DELETE'],
            AllowedHeaders: ['*'],
            ExposeHeaders: ['ETag', 'Content-Length'],
            MaxAgeSeconds: 3600,
          },
        ],
      },
    }),
  );
  return origins;
}

function isNotFound(err: unknown): boolean {
  const e = err as { name?: string; $metadata?: { httpStatusCode?: number } };
  return e?.name === 'NotFound' || e?.$metadata?.httpStatusCode === 404;
}

/** Strip characters that would break a Content-Disposition header. */
function sanitizeFilename(name: string): string {
  return name.replace(/["\\\r\n]/g, '_').slice(0, 200) || 'download';
}
