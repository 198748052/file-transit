import { Router } from 'express';
import { randomUUID } from 'node:crypto';
import { z } from 'zod';
import { db, allRows, getRow, toFolderDTO, type FolderRow, type FileRow } from '../db.js';
import { asyncHandler, HttpError } from '../lib/http.js';
import { requireAuth } from '../auth.js';
import { removeFileRecord } from '../lib/file-store.js';
import { actorOf, clientIp, recordLog } from '../lib/audit.js';

export const foldersRouter = Router();
foldersRouter.use(requireAuth);

const MAX_DEPTH = 32;

function getFolderOr404(id: string): FolderRow {
  const row = getRow<FolderRow>('SELECT * FROM folders WHERE id = ?', id);
  if (!row) throw new HttpError(404, 'folder_not_found');
  return row;
}

function normaliseParent(value: string | null | undefined): string | null {
  if (value === undefined || value === null || value === '' || value === 'root') return null;
  getFolderOr404(value);
  return value;
}

/** All folder ids in the subtree rooted at `id`, including `id` itself. */
function subtreeIds(id: string): string[] {
  return allRows<{ id: string }>(
    `WITH RECURSIVE tree(id) AS (
       SELECT id FROM folders WHERE id = ?
       UNION ALL
       SELECT f.id FROM folders f JOIN tree t ON f.parent_id = t.id
     )
     SELECT id FROM tree`,
    id,
  ).map((row) => row.id);
}

function assertNameFree(parentId: string | null, name: string, exceptId?: string): void {
  const clash = getRow<{ id: string }>(
    `SELECT id FROM folders WHERE parent_id IS ? AND lower(name) = lower(?)`,
    parentId,
    name,
  );
  if (clash && clash.id !== exceptId) throw new HttpError(409, 'folder_name_taken');
}

function depthOf(parentId: string | null): number {
  let depth = 0;
  let current = parentId;
  while (current && depth <= MAX_DEPTH) {
    const row = getRow<{ parent_id: string | null }>('SELECT parent_id FROM folders WHERE id = ?', current);
    if (!row) break;
    current = row.parent_id;
    depth++;
  }
  return depth;
}

// ─────────────────────────── GET / ───────────────────────────
foldersRouter.get('/', (_req, res) => {
  const rows = allRows<FolderRow>('SELECT * FROM folders ORDER BY name COLLATE NOCASE ASC');
  const fileCounts = new Map(
    allRows<{ folder_id: string; fileCount: number; totalSize: number }>(
      `SELECT folder_id, COUNT(*) AS fileCount, COALESCE(SUM(size), 0) AS totalSize
         FROM files
        WHERE status != 'deleted' AND folder_id IS NOT NULL
        GROUP BY folder_id`,
    ).map((row) => [row.folder_id, row]),
  );
  const childCounts = new Map(
    allRows<{ parent_id: string; childCount: number }>(
      `SELECT parent_id, COUNT(*) AS childCount
         FROM folders
        WHERE parent_id IS NOT NULL
        GROUP BY parent_id`,
    ).map((row) => [row.parent_id, Number(row.childCount)]),
  );

  res.json({
    folders: rows.map((row) => {
      const counts = fileCounts.get(row.id);
      return toFolderDTO(row, {
        fileCount: Number(counts?.fileCount ?? 0),
        totalSize: Number(counts?.totalSize ?? 0),
        childCount: childCounts.get(row.id) ?? 0,
      });
    }),
  });
});

// ─────────────────────────── POST / ───────────────────────────
const createSchema = z.object({
  name: z.string().trim().min(1).max(120),
  parentId: z.string().min(1).max(64).nullish(),
});

foldersRouter.post('/', (req, res) => {
  const input = createSchema.parse(req.body ?? {});
  const parentId = normaliseParent(input.parentId);
  if (depthOf(parentId) >= MAX_DEPTH) throw new HttpError(400, 'folder_nesting_too_deep');
  assertNameFree(parentId, input.name);

  const id = randomUUID();
  db.prepare('INSERT INTO folders (id, name, parent_id, created_at) VALUES (?, ?, ?, ?)').run(
    id,
    input.name,
    parentId,
    Date.now(),
  );
  recordLog({
    action: 'folder.create',
    actor: actorOf(req),
    ip: clientIp(req),
    targetType: 'folder',
    targetId: id,
    targetName: input.name,
    detail: { parentId },
  });
  res.status(201).json({ folder: toFolderDTO(getFolderOr404(id)) });
});

// ─────────────────────────── PATCH /:id ───────────────────────────
const patchSchema = z.object({
  name: z.string().trim().min(1).max(120).optional(),
  parentId: z.string().min(1).max(64).nullish(),
});

foldersRouter.patch('/:id', (req, res) => {
  const row = getFolderOr404(req.params.id!);
  const input = patchSchema.parse(req.body ?? {});
  const name = input.name ?? row.name;
  const parentId = input.parentId === undefined ? row.parent_id : normaliseParent(input.parentId);

  if (parentId !== row.parent_id) {
    if (parentId && subtreeIds(row.id).includes(parentId)) throw new HttpError(409, 'folder_cycle');
    if (depthOf(parentId) >= MAX_DEPTH) throw new HttpError(400, 'folder_nesting_too_deep');
  }
  assertNameFree(parentId, name, row.id);

  db.prepare('UPDATE folders SET name = ?, parent_id = ? WHERE id = ?').run(name, parentId, row.id);
  const renamed = name !== row.name;
  const relocated = parentId !== row.parent_id;
  recordLog({
    action: renamed ? 'folder.rename' : 'folder.move',
    actor: actorOf(req),
    ip: clientIp(req),
    targetType: 'folder',
    targetId: row.id,
    targetName: name,
    detail: { renamed, relocated, previousName: row.name, parentId },
  });
  res.json({ folder: toFolderDTO(getFolderOr404(row.id)) });
});

// ─────────────────────────── DELETE /:id ───────────────────────────
foldersRouter.delete(
  '/:id',
  asyncHandler(async (req, res) => {
    const row = getFolderOr404(req.params.id!);
    const ids = subtreeIds(row.id);
    const placeholders = ids.map(() => '?').join(',');
    const files = allRows<FileRow>(
      `SELECT * FROM files WHERE folder_id IN (${placeholders}) AND status != 'deleted'`,
      ...ids,
    );

    const failed: string[] = [];
    for (const file of files) {
      try {
        await removeFileRecord(file);
      } catch (err) {
        console.error(`Folder delete: failed to remove R2 object for ${file.id}:`, err);
        failed.push(file.id);
        continue;
      }
    }

    if (failed.length) {
      throw new HttpError(502, `folder_delete_partial:${failed.length}`);
    }

    db.prepare('DELETE FROM folders WHERE id = ?').run(row.id);
    recordLog({
      action: 'folder.delete',
      actor: actorOf(req),
      ip: clientIp(req),
      targetType: 'folder',
      targetId: row.id,
      targetName: row.name,
      detail: { deletedFolders: ids.length, deletedFiles: files.length },
    });
    res.json({ ok: true, deletedFolders: ids.length, deletedFiles: files.length });
  }),
);
