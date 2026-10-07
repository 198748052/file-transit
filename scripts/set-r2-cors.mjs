// Configure CORS on your R2 bucket so the browser can PUT/HEAD objects directly.
// Usage:  npm run r2:cors   (reads values from .env)
import 'dotenv/config';
import { S3Client, PutBucketCorsCommand } from '@aws-sdk/client-s3';

const { R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_BUCKET, R2_ENDPOINT, APP_BASE_URL } =
  process.env;

if (!R2_ACCOUNT_ID || !R2_ACCESS_KEY_ID || !R2_SECRET_ACCESS_KEY || !R2_BUCKET) {
  console.error('Missing R2_* environment variables. Fill in .env first.');
  process.exit(1);
}

// Collect origins the frontend may run from. Browser origin rules must have no
// trailing slash and no path — reduce each to scheme://host[:port].
function toOrigin(value) {
  try {
    const u = new URL(value);
    return u.origin;
  } catch {
    return null;
  }
}

const origins = [APP_BASE_URL, 'http://localhost:5173', 'http://localhost:3000']
  .map(toOrigin)
  .filter((o, i, arr) => o && arr.indexOf(o) === i);

const client = new S3Client({
  region: 'auto',
  endpoint: R2_ENDPOINT ?? `https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com`,
  credentials: { accessKeyId: R2_ACCESS_KEY_ID, secretAccessKey: R2_SECRET_ACCESS_KEY },
});

await client.send(
  new PutBucketCorsCommand({
    Bucket: R2_BUCKET,
    CORSConfiguration: {
      CORSRules: [
        {
          AllowedOrigins: origins,
          AllowedMethods: ['GET', 'PUT', 'POST', 'HEAD', 'DELETE'],
          AllowedHeaders: ['*'],
          // Browser JS must read ETag to finish multipart uploads.
          ExposeHeaders: ['ETag', 'Content-Length'],
          MaxAgeSeconds: 3600,
        },
      ],
    },
  }),
);

console.log(`✅ CORS configured for bucket "${R2_BUCKET}" for origins:\n   ${origins.join('\n   ')}`);
