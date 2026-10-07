import { createApp } from './app.js';
import { config } from './config.js';
import { startMaintenanceJobs } from './jobs/expiry.js';

const app = createApp();

app.listen(config.port, config.host, () => {
  console.log(`🚀 file-transit listening on http://${config.host}:${config.port}`);
  console.log(`   bucket=${config.r2.bucket}  endpoint=${config.r2.endpoint}`);
  console.log(`   base url=${config.appBaseUrl}`);
});

startMaintenanceJobs();
