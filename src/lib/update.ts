import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { existsSync, mkdirSync, openSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { config } from '../config.js';
import { HttpError } from './http.js';
import { getSetting, setSetting } from './settings.js';

const execFileP = promisify(execFile);

const LAST_CHECK_KEY = 'update.lastCheck';
const BRANCH_RE = /^[A-Za-z0-9._\-/]+$/;
const NAME_RE = /^[A-Za-z0-9._\-]+$/;
const RUNNING_STALE_MS = 30 * 60 * 1000; // treat a "running" marker older than this as dead

export interface CommitInfo {
  sha: string;
  date: number; // unix seconds
  message: string;
}

export interface UpdateStatus {
  enabled: boolean;
  branch: string;
  isGit: boolean;
  dirty: boolean;
  current: CommitInfo | null;
  remote: CommitInfo | null;
  behind: number;
  hasUpdate: boolean;
  lastCheckedAt: number | null; // unix ms
  running: boolean;
  lastRun: 'ok' | 'failed' | 'running' | 'none';
  error?: string;
}

const updateState = { running: false };

function repoDir(): string {
  return config.update.repoDir || process.cwd();
}
function dataDir(): string {
  return join(repoDir(), 'data');
}
const logPath = () => join(dataDir(), 'update.log');
const statusPath = () => join(dataDir(), 'update.status');
const scriptPath = () => join(dataDir(), 'update.sh');

async function git(args: string[]): Promise<string> {
  const { stdout } = await execFileP('git', args, {
    cwd: repoDir(),
    maxBuffer: 8 * 1024 * 1024,
    timeout: 120_000,
  });
  return stdout.trim();
}

function parseCommitLine(line: string): CommitInfo | null {
  if (!line) return null;
  const parts = line.split('\t');
  if (parts.length < 2) return null;
  return {
    sha: parts[0] ?? '',
    date: Number(parts[1]) || 0,
    message: parts.slice(2).join('\t'),
  };
}

async function isGitRepo(): Promise<boolean> {
  try {
    return (await git(['rev-parse', '--is-inside-work-tree'])) === 'true';
  } catch {
    return false;
  }
}

async function headInfo(): Promise<CommitInfo | null> {
  try {
    return parseCommitLine(await git(['log', '-1', '--format=%H%x09%ct%x09%s']));
  } catch {
    return null;
  }
}

async function remoteInfo(branch: string): Promise<CommitInfo | null> {
  try {
    return parseCommitLine(await git(['log', '-1', '--format=%H%x09%ct%x09%s', `origin/${branch}`]));
  } catch {
    return null;
  }
}

/** Tracked modifications only — untracked files never block a hard reset. */
async function isDirty(): Promise<boolean> {
  try {
    const out = await git(['status', '--porcelain']);
    if (!out) return false;
    return out.split('\n').some((l) => l.trim() && !l.startsWith('??'));
  } catch {
    return true; // unknown state → be safe and block updates
  }
}

function lastRunMarker(): UpdateStatus['lastRun'] {
  try {
    const v = readFileSync(statusPath(), 'utf8').trim();
    if (v === 'ok' || v === 'failed' || v === 'running') return v;
  } catch {
    /* no marker yet */
  }
  return 'none';
}

function markerFreshRunning(): boolean {
  if (lastRunMarker() !== 'running') return false;
  try {
    const { mtimeMs } = statSync(statusPath());
    return Date.now() - mtimeMs < RUNNING_STALE_MS;
  } catch {
    return false;
  }
}

function cachedCheck(): Partial<UpdateStatus> | null {
  const raw = getSetting(LAST_CHECK_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Partial<UpdateStatus>;
  } catch {
    return null;
  }
}

function readLastUpdate(): number | null {
  const raw = getSetting(LAST_CHECK_KEY);
  if (!raw) return null;
  try {
    const j = JSON.parse(raw) as { lastCheckedAt?: number };
    return j.lastCheckedAt ?? null;
  } catch {
    return null;
  }
}

/** Quick, network-free snapshot; combines live local git state with the last cached check. */
export async function getStatus(): Promise<UpdateStatus> {
  const isGit = await isGitRepo();
  const cache = cachedCheck() ?? {};
  const running = updateState.running || markerFreshRunning();
  return {
    enabled: config.update.enabled,
    branch: config.update.branch,
    isGit,
    dirty: isGit ? await isDirty() : false,
    current: isGit ? await headInfo() : null,
    remote: cache.remote ?? null,
    behind: cache.behind ?? 0,
    hasUpdate: !!cache.hasUpdate,
    lastCheckedAt: readLastUpdate(),
    running,
    lastRun: lastRunMarker(),
    error: cache.error,
  };
}

/** Fetch origin and compare; stores the result so the panel can show a notice offline. */
export async function checkUpdate(): Promise<UpdateStatus> {
  const base = { enabled: config.update.enabled, branch: config.update.branch };
  if (!base.enabled) throw new HttpError(400, 'update_disabled');

  const isGit = await isGitRepo();
  if (!isGit) {
    const s: UpdateStatus = {
      ...base,
      isGit: false,
      dirty: false,
      current: null,
      remote: null,
      behind: 0,
      hasUpdate: false,
      lastCheckedAt: null,
      running: updateState.running || markerFreshRunning(),
      lastRun: lastRunMarker(),
      error: 'not_git_repo',
    };
    return s;
  }

  const branch = base.branch;
  if (!BRANCH_RE.test(branch)) throw new HttpError(500, 'bad_branch');

  try {
    await git(['fetch', 'origin', '--prune']);
  } catch (err) {
    throw new HttpError(502, `update_fetch_failed: ${shortErr(err)}`);
  }

  const behind = Number(await git(['rev-list', '--count', `HEAD..origin/${branch}`]).catch(() => '0')) || 0;
  const result: UpdateStatus = {
    ...base,
    isGit: true,
    dirty: await isDirty(),
    current: await headInfo(),
    remote: await remoteInfo(branch),
    behind,
    hasUpdate: behind > 0,
    lastCheckedAt: Date.now(),
    running: updateState.running || markerFreshRunning(),
    lastRun: lastRunMarker(),
  };
  setSetting(
    LAST_CHECK_KEY,
    JSON.stringify({ remote: result.remote, behind: result.behind, hasUpdate: result.hasUpdate, lastCheckedAt: result.lastCheckedAt }),
  );
  return result;
}

/** Kick off the destructive update in a detached shell so it survives the restart it causes. */
export async function startUpdate(): Promise<{ started: true }> {
  if (!config.update.enabled) throw new HttpError(400, 'update_disabled');
  if (updateState.running || markerFreshRunning()) throw new HttpError(409, 'update_running');
  if (!(await isGitRepo())) throw new HttpError(409, 'not_git_repo');
  if (await isDirty()) throw new HttpError(409, 'working_tree_dirty');

  const { branch, pm2Name } = config.update;
  if (!BRANCH_RE.test(branch)) throw new HttpError(500, 'bad_branch');
  if (!NAME_RE.test(pm2Name)) throw new HttpError(500, 'bad_pm2_name');

  if (!existsSync(dataDir())) mkdirSync(dataDir(), { recursive: true });

  const script = buildScript(branch, pm2Name);
  writeFileSync(scriptPath(), script, 'utf8');
  try {
    writeFileSync(statusPath(), 'running', 'utf8');
  } catch {
    /* marker best-effort */
  }

  const fd = openSync(logPath(), 'a');
  const child = spawn('sh', [scriptPath()], { cwd: repoDir(), detached: true, stdio: ['ignore', fd, fd] });
  child.unref();
  updateState.running = true;
  child.on('error', () => {
    updateState.running = false;
    try {
      writeFileSync(statusPath(), 'failed', 'utf8');
    } catch {
      /* ignore */
    }
  });
  child.on('exit', () => {
    updateState.running = false;
  });
  return { started: true };
}

export function getLog(limitChars = 8000): { log: string; lastRun: UpdateStatus['lastRun']; running: boolean } {
  let log = '';
  try {
    log = readFileSync(logPath(), 'utf8').slice(-limitChars);
  } catch {
    /* no log yet */
  }
  return {
    log,
    lastRun: lastRunMarker(),
    running: updateState.running || markerFreshRunning(),
  };
}

function buildScript(branch: string, pm2Name: string): string {
  return `#!/bin/sh
# file-transit remote update — auto-generated, do not edit
STATUS="${statusPath()}"
trap 'code=$?; [ "$code" -eq 0 ] || printf "failed" > "$STATUS"' EXIT
set -e
echo "[update] $(date '+%Y-%m-%d %H:%M:%S') start · branch=${branch}"
git fetch origin --prune
git reset --hard "origin/${branch}"
npm install
npm run build
cd web && npm install && npm run build && cd ..
printf "ok" > "$STATUS"
echo "[update] build complete · restarting pm2 app '${pm2Name}'"
pm2 restart "${pm2Name}" || pm2 restart all
echo "[update] done"
`;
}

function shortErr(err: unknown): string {
  const e = err as { stderr?: string; message?: string };
  return (e?.stderr || e?.message || String(err)).trim().slice(0, 200);
}
