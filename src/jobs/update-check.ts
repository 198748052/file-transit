import cron from 'node-cron';
import { config } from '../config.js';
import { checkUpdate } from '../lib/update.js';

/** Periodically fetch + compare with origin so the panel can show an update notice.
 *  Read-only: this never pulls, builds, or restarts anything. */
export function startUpdateCheckJob(): void {
  if (!config.update.enabled) return;

  const run = async () => {
    try {
      const status = await checkUpdate();
      if (status.hasUpdate) {
        console.log(`🔔 update available: ${status.behind} commit(s) behind origin/${status.branch}`);
      }
    } catch (err) {
      console.error('update check failed:', (err as Error)?.message ?? err);
    }
  };

  cron.schedule(config.update.checkCron, run);
  const boot = setTimeout(run, 15_000);
  boot.unref?.();
  console.log(`🔄 update check scheduled: "${config.update.checkCron}"`);
}
