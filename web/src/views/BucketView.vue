<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { api, ApiError, type BucketScanObject, type BucketScanResult, type KeyMigrationPlan } from '../api';
import { formatBytes, formatDateTime } from '../utils/format';
import DriveIcon from '../components/DriveIcon.vue';

type Tab = 'objects' | 'orphans' | 'missing';

const PAGE_SIZE = 50;

const scanResult = ref<BucketScanResult | null>(null);
const loading = ref(false);
const deleting = ref(false);
const error = ref('');
const notice = ref('');
const tab = ref<Tab>('objects');
const search = ref('');
const page = ref(1);

const migration = ref<KeyMigrationPlan | null>(null);
const migrationLoading = ref(false);
const migrating = ref(false);
const migrationMsg = ref('');
const migrationOk = ref(false);
const migrationProgress = ref('');

const statusText: Record<string, string> = {
  uploading: '上传中',
  ready: '可下载',
  expired: '已过期',
};

function match(text: string): boolean {
  const q = search.value.trim().toLowerCase();
  return !q || text.toLowerCase().includes(q);
}

const filteredObjects = computed(() => {
  const list = tab.value === 'orphans' ? scanResult.value?.orphans ?? [] : scanResult.value?.objects ?? [];
  return list.filter((object) => match(object.key));
});

const filteredMissing = computed(() =>
  (scanResult.value?.missing ?? []).filter((item) => match(item.name) || match(item.key)),
);

const totals = computed(() => {
  if (tab.value === 'missing') return filteredMissing.value.length;
  return filteredObjects.value.length;
});

const totalPages = computed(() => Math.max(1, Math.ceil(totals.value / PAGE_SIZE)));

const pagedObjects = computed(() =>
  filteredObjects.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
);

const pagedMissing = computed(() =>
  filteredMissing.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
);

const rangeLabel = computed(() => {
  if (!totals.value) return '0 / 0';
  const start = (page.value - 1) * PAGE_SIZE + 1;
  const end = Math.min(page.value * PAGE_SIZE, totals.value);
  return `${start}-${end} / ${totals.value}`;
});

const hiddenObjects = computed(() =>
  scanResult.value ? Math.max(0, scanResult.value.objectCount - scanResult.value.objects.length) : 0,
);

watch([tab, search], () => {
  page.value = 1;
});

watch(totalPages, (value) => {
  if (page.value > value) page.value = value;
});

async function scan() {
  loading.value = true;
  error.value = '';
  notice.value = '';
  try {
    scanResult.value = await api.scanBucket();
    page.value = 1;
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.code === 'r2_not_configured') error.value = 'R2 尚未配置，请先到设置填写存储凭据。';
      else if (e.code.startsWith('bucket_scan_failed')) error.value = '扫描失败：' + e.code.replace('bucket_scan_failed: ', '');
      else error.value = `扫描失败：${e.code}`;
    } else {
      error.value = '扫描失败，请稍后重试。';
    }
  } finally {
    loading.value = false;
  }
}

async function loadMigration() {
  migrationLoading.value = true;
  try {
    migration.value = await api.keyMigrationStatus();
  } catch {
    /* keep the previous plan; the card shows the last known state */
  } finally {
    migrationLoading.value = false;
  }
}

async function runMigration() {
  const plan = migration.value;
  if (!plan?.total) return;
  if (
    !confirm(
      `将把 ${plan.total} 个旧对象迁移到 uploads/年/月/日/文件ID 结构。\n` +
        '迁移使用 R2 服务端复制，不消耗本机下载流量；复制成功并更新记录后才会删除旧对象。是否继续？',
    )
  ) {
    return;
  }

  migrating.value = true;
  migrationMsg.value = '';
  migrationOk.value = false;
  migrationProgress.value = '';
  let done = 0;
  let skippedTotal = 0;
  try {
    for (let i = 0; i < 500; i += 1) {
      const result = await api.migrateKeys(100);
      done += result.migrated;
      skippedTotal += result.skipped;
      migrationProgress.value = `已迁移 ${done} 个，剩余 ${result.remaining} 个`;
      if (!result.remaining) break;
      if (result.migrated === 0) {
        migrationOk.value = false;
        const reason = result.failed[0]?.reason;
        migrationMsg.value = `仍有 ${result.remaining} 个对象未能迁移${
          reason ? `（${reason}）` : '，可能源对象缺失'
        }。请先在“记录缺失”中排查。`;
        break;
      }
    }
    await loadMigration();
    if (done > 0) await scan();
    if (!migrationMsg.value) {
      migrationOk.value = true;
      migrationMsg.value = `迁移完成，共处理 ${done} 个对象${
        skippedTotal ? `，跳过 ${skippedTotal} 个源对象缺失的记录` : ''
      }。`;
    }
  } catch (e) {
    migrationOk.value = false;
    migrationMsg.value =
      e instanceof ApiError
        ? e.code === 'r2_not_configured'
          ? 'R2 尚未配置，无法迁移。'
          : `迁移失败：${e.code}`
        : '迁移失败，请稍后重试。';
  } finally {
    migrating.value = false;
    migrationProgress.value = '';
  }
}

async function removeKeys(keys: string[]) {
  if (!keys.length) return;
  deleting.value = true;
  error.value = '';
  notice.value = '';
  try {
    const result = await api.deleteOrphanObjects(keys);
    const parts = [`已删除 ${result.deleted.length} 个残留对象`];
    if (result.failed.length) parts.push(`${result.failed.length} 个删除失败`);
    if (result.skipped.length) parts.push(`${result.skipped.length} 个已被数据库引用，已跳过`);
    await scan();
    notice.value = parts.join('，') + '。';
  } catch (e) {
    if (e instanceof ApiError) error.value = `删除失败：${e.code}`;
    else error.value = '删除失败，请稍后重试。';
  } finally {
    deleting.value = false;
  }
}

function removeOne(object: BucketScanObject) {
  if (!confirm(`删除残留对象？该对象没有被文件记录引用，删除后不可恢复。\n\n${object.key}`)) return;
  removeKeys([object.key]);
}

function removeAll() {
  const result = scanResult.value;
  if (!result || !result.orphanCount) return;
  const keys = result.orphans.map((object) => object.key);
  if (!keys.length) return;
  const extra = result.orphanCount - keys.length;
  const suffix = extra > 0 ? `\n当前只列出 ${keys.length} 个，完成后请再次扫描处理剩余部分。` : '';
  const foreign = result.foreignCount
    ? `\n注意：其中 ${result.foreignCount} 个不在 uploads/ 目录下，可能来自其他程序，请确认后再删除。`
    : '';
  if (!confirm(`将删除 ${keys.length} 个未被数据库引用的对象，共 ${formatBytes(result.orphanBytes)}。此操作不可恢复。${foreign}${suffix}`)) return;
  removeKeys(keys);
}

onMounted(() => {
  loadMigration();
  scan();
});
</script>

<template>
  <div class="bucket-page">
    <header class="bucket-head">
      <div>
        <h1>存储桶</h1>
        <p class="muted text-sm">读取 R2 桶内的全部对象，核对哪些文件没有被面板记录引用。</p>
      </div>
      <div class="bucket-head-actions">
        <button class="btn" :disabled="loading" @click="scan">
          <DriveIcon name="refresh" :size="15" />
          {{ loading ? '扫描中…' : '重新扫描' }}
        </button>
        <button
          class="btn btn-danger"
          :disabled="loading || deleting || !scanResult?.orphanCount"
          @click="removeAll"
        >
          {{ deleting ? '删除中…' : '删除全部残留' }}
        </button>
      </div>
    </header>

    <div v-if="error" class="alert error">
      {{ error }}
      <RouterLink v-if="error.includes('尚未配置')" to="/settings#storage" class="ms-sm">去设置</RouterLink>
    </div>
    <div v-if="notice" class="alert success">{{ notice }}</div>

    <div class="card bucket-migrate">
      <div class="toolbar">
        <div>
          <p class="section-title">对象路径</p>
          <p class="muted text-sm">
            新上传使用 <code>uploads/年/月/日/文件ID.扩展名</code>，重命名与移动不再改动存储路径。
          </p>
        </div>
        <span class="badge" :class="migration?.total ? 'expired' : 'ready'">
          {{ migrationLoading ? '检查中…' : migration?.total ? `${migration.total} 个待迁移` : '已是最新' }}
        </span>
      </div>

      <div v-if="migrationMsg" class="alert" :class="migrationOk ? 'success' : 'error'">{{ migrationMsg }}</div>
      <div v-if="migrating" class="muted text-sm mb-sm">{{ migrationProgress || '准备中…' }}</div>

      <div class="row">
        <button class="btn" :disabled="migrating || migrationLoading" @click="loadMigration">重新检查</button>
        <button class="btn btn-primary" :disabled="migrating || !migration?.total" @click="runMigration">
          {{ migrating ? '迁移中…' : '开始迁移' }}
        </button>
      </div>

      <p class="hint">
        迁移通过 R2 服务端复制完成，不产生本机下载流量；复制成功并更新记录后才会删除旧对象。上传中的文件不会迁移。
      </p>
    </div>

    <div v-if="!scanResult" class="empty-drive">
      <DriveIcon name="storage" :size="48" />
      <p>{{ loading ? '正在读取存储桶…' : '还没有扫描过存储桶' }}</p>
      <p class="muted text-sm">
        扫描只读取对象列表，不会下载文件内容；对象较多时可能需要几秒。
      </p>
      <button class="btn btn-primary" :disabled="loading" @click="scan">
        {{ loading ? '扫描中…' : '开始扫描' }}
      </button>
    </div>

    <template v-else>
      <div v-if="scanResult.truncated" class="alert info">
        桶内对象过多，本次只扫描了前 50000 个，残留与缺失结果可能不完整。
      </div>

      <div v-if="scanResult.foreignCount" class="alert info">
        有 {{ scanResult.foreignCount }} 个残留对象不在 uploads/ 目录下，可能来自其他程序，删除前请先确认。
      </div>

      <div class="stats-grid bucket-stats">
        <div class="card">
          <div class="muted text-sm">桶内对象</div>
          <div class="stat-value">{{ scanResult.objectCount }}</div>
          <div class="muted text-sm">{{ formatBytes(scanResult.totalSize) }}</div>
        </div>
        <div class="card">
          <div class="muted text-sm">已登记</div>
          <div class="stat-value">{{ scanResult.trackedCount }}</div>
          <div class="muted text-sm">被文件记录引用</div>
        </div>
        <div class="card">
          <div class="muted text-sm">残留对象</div>
          <div class="stat-value">{{ scanResult.orphanCount }}</div>
          <div class="muted text-sm">{{ formatBytes(scanResult.orphanBytes) }}</div>
        </div>
        <div class="card">
          <div class="muted text-sm">记录缺失</div>
          <div class="stat-value">{{ scanResult.missingCount }}</div>
          <div class="muted text-sm">数据库有记录、桶内没有</div>
        </div>
      </div>

      <div class="bucket-toolbar">
        <div class="bucket-tabs">
          <button type="button" :class="{ active: tab === 'objects' }" @click="tab = 'objects'">
            桶内文件（{{ scanResult.objectCount }}）
          </button>
          <button type="button" :class="{ active: tab === 'orphans' }" @click="tab = 'orphans'">
            残留对象（{{ scanResult.orphanCount }}）
          </button>
          <button type="button" :class="{ active: tab === 'missing' }" @click="tab = 'missing'">
            记录缺失（{{ scanResult.missingCount }}）
          </button>
        </div>
        <div class="search-field bucket-search">
          <DriveIcon name="search" :size="17" />
          <input v-model="search" type="search" placeholder="搜索对象路径 / 文件名" />
          <button v-if="search" class="search-clear" type="button" aria-label="清除搜索" @click="search = ''">
            <DriveIcon name="close" :size="15" />
          </button>
        </div>
      </div>

      <p class="muted text-sm bucket-meta-line">
        扫描于 {{ formatDateTime(scanResult.scannedAt) }}
        <template v-if="hiddenObjects"> · 列表仅展示前 {{ scanResult.objects.length }} 个对象，另有 {{ hiddenObjects }} 个未展示</template>
      </p>

      <div v-if="tab !== 'missing'" class="bucket-list">
        <div v-if="!pagedObjects.length" class="empty">
          {{ search ? '没有匹配的对象' : tab === 'orphans' ? '没有残留对象，桶内文件都有记录。' : '桶内没有对象。' }}
        </div>
        <div v-for="object in pagedObjects" :key="object.key" class="bucket-row">
          <DriveIcon name="file" :size="18" />
          <div class="bucket-main">
            <div v-if="object.name" class="bucket-name" :title="object.name">{{ object.name }}</div>
            <div class="bucket-key" :title="object.key">{{ object.key }}</div>
            <div class="bucket-meta">
              {{ formatBytes(object.size) }} · {{ object.lastModified ? formatDateTime(object.lastModified) : '—' }}
            </div>
          </div>
          <span class="bucket-badge" :class="object.tracked ? 'tracked' : 'orphan'">
            {{ object.tracked ? '已登记' : object.foreign ? '外部' : '残留' }}
          </span>
          <button
            v-if="!object.tracked"
            class="btn btn-sm btn-danger"
            :disabled="deleting || loading"
            @click="removeOne(object)"
          >
            删除
          </button>
        </div>
      </div>

      <div v-else class="bucket-list">
        <div v-if="!pagedMissing.length" class="empty">
          {{ search ? '没有匹配的记录' : '没有缺失的记录，数据库与存储桶一致。' }}
        </div>
        <div v-for="item in pagedMissing" :key="item.id" class="bucket-row">
          <DriveIcon name="file" :size="18" />
          <div class="bucket-main">
            <div class="bucket-key" :title="item.key">{{ item.name }}</div>
            <div class="bucket-meta">
              {{ formatBytes(item.size) }} · {{ formatDateTime(item.createdAt) }} · {{ item.key }}
            </div>
          </div>
          <span class="badge" :class="item.status">{{ statusText[item.status] ?? item.status }}</span>
        </div>
      </div>

      <div v-if="totals > PAGE_SIZE" class="bucket-pager">
        <span class="muted text-sm">{{ rangeLabel }}</span>
        <div class="spacer"></div>
        <button class="btn btn-sm" :disabled="page <= 1" @click="page -= 1">上一页</button>
        <span class="muted text-sm">{{ page }} / {{ totalPages }}</span>
        <button class="btn btn-sm" :disabled="page >= totalPages" @click="page += 1">下一页</button>
      </div>

      <p class="hint bucket-note">
        残留对象是桶里存在、但文件记录里没有的对象，通常是取消上传或删除失败留下的，可以安全清理。
        记录缺失表示数据库有记录但桶里找不到对象，本页只做展示，不会自动删除记录。
      </p>
    </template>
  </div>
</template>
