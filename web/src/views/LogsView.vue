<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { api, type ActivityLog, type LogStats } from '../api';
import { actionMeta, describeDetail } from '../utils/logs';
import { formatDateTime } from '../utils/format';
import DriveIcon from '../components/DriveIcon.vue';

const PAGE_SIZE = 30;

const logs = ref<ActivityLog[]>([]);
const stats = ref<LogStats | null>(null);
const actions = ref<{ action: string; count: number }[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(true);
const error = ref('');

const search = ref('');
const actionFilter = ref('');
const resultFilter = ref('');

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)));
const rangeStart = computed(() => (total.value === 0 ? 0 : (page.value - 1) * PAGE_SIZE + 1));
const rangeEnd = computed(() => Math.min(page.value * PAGE_SIZE, total.value));

let debounce: number | undefined;

async function load() {
  loading.value = true;
  try {
    const [logsRes, statsRes] = await Promise.all([
      api.listLogs({
        page: page.value,
        pageSize: PAGE_SIZE,
        action: actionFilter.value || undefined,
        result: (resultFilter.value || undefined) as 'ok' | 'fail' | undefined,
        q: search.value.trim() || undefined,
      }),
      api.logStats(),
    ]);
    logs.value = logsRes.logs;
    total.value = logsRes.total;
    actions.value = logsRes.actions;
    stats.value = statsRes;
    error.value = '';
  } catch {
    error.value = '加载操作记录失败';
  } finally {
    loading.value = false;
  }
}

function triggerSearch() {
  window.clearTimeout(debounce);
  debounce = window.setTimeout(() => {
    page.value = 1;
    load();
  }, 300);
}

watch([actionFilter, resultFilter], () => {
  page.value = 1;
  load();
});

function goPage(next: number) {
  if (next < 1 || next > totalPages.value) return;
  page.value = next;
  load();
}

async function clearLogs() {
  if (!confirm('确定清空全部操作记录？此操作不可恢复。')) return;
  try {
    await api.clearLogs();
    page.value = 1;
    load();
  } catch {
    alert('清空失败，请稍后重试。');
  }
}

onMounted(load);
</script>

<template>
  <div class="logs-page">
    <header class="page-head logs-head">
      <div>
        <h1>操作记录</h1>
        <p class="muted text-sm">记录上传、下载、分享、文件夹与账号设置等操作，便于回溯。</p>
      </div>
      <div class="logs-head-actions">
        <button class="btn btn-sm" @click="load">
          <DriveIcon name="refresh" :size="15" />
          刷新
        </button>
        <button class="btn btn-sm btn-danger" :disabled="!total" @click="clearLogs">
          <DriveIcon name="trash" :size="15" />
          清空
        </button>
      </div>
    </header>

    <div class="stats-grid mb-lg">
      <div class="card">
        <div class="muted text-sm">总记录</div>
        <div class="stat-value">{{ stats?.total ?? 0 }}</div>
      </div>
      <div class="card">
        <div class="muted text-sm">最近 24 小时</div>
        <div class="stat-value">{{ stats?.last24h ?? 0 }}</div>
      </div>
      <div class="card">
        <div class="muted text-sm">失败次数</div>
        <div class="stat-value">{{ stats?.failures ?? 0 }}</div>
      </div>
      <div class="card">
        <div class="muted text-sm">最早记录</div>
        <div class="stat-value logs-oldest">{{ stats?.oldestAt ? formatDateTime(stats.oldestAt).split(' ')[0] : '—' }}</div>
      </div>
    </div>

    <div class="card">
      <div class="filters logs-filters">
        <div class="search-field">
          <DriveIcon name="search" :size="17" />
          <input v-model="search" type="search" placeholder="搜索文件名 / 用户 / IP" @input="triggerSearch" />
          <button v-if="search" class="search-clear" type="button" aria-label="清除搜索" @click="search = ''; triggerSearch()">
            <DriveIcon name="close" :size="15" />
          </button>
        </div>
        <select v-model="actionFilter" aria-label="操作类型">
          <option value="">全部操作</option>
          <option v-for="item in actions" :key="item.action" :value="item.action">
            {{ actionMeta(item.action).label }}（{{ item.count }}）
          </option>
        </select>
        <select v-model="resultFilter" aria-label="结果">
          <option value="">全部结果</option>
          <option value="ok">成功</option>
          <option value="fail">失败</option>
        </select>
      </div>

      <div v-if="error" class="alert error mt-md">{{ error }}</div>
      <div v-else-if="loading" class="empty">加载中…</div>
      <div v-else-if="!logs.length" class="empty">暂无操作记录。</div>
      <div v-else class="log-list mt-md">
        <div v-for="log in logs" :key="log.id" class="log-row">
          <div class="log-time">{{ formatDateTime(log.createdAt) }}</div>
          <div class="log-main">
            <div class="log-title">
              <span class="log-badge" :class="`tone-${actionMeta(log.action).tone}`">
                {{ actionMeta(log.action).label }}
              </span>
              <span v-if="log.targetName" class="log-target" :title="log.targetName">{{ log.targetName }}</span>
              <span v-if="log.result === 'fail'" class="log-fail">失败</span>
            </div>
            <div v-if="describeDetail(log)" class="log-detail muted text-sm">{{ describeDetail(log) }}</div>
          </div>
          <div class="log-side">
            <span class="log-actor">{{ log.actor ?? '—' }}</span>
            <span class="log-ip">{{ log.ip ?? '—' }}</span>
          </div>
        </div>
      </div>

      <div v-if="total" class="logs-pager">
        <span class="muted text-sm">{{ rangeStart }}-{{ rangeEnd }} / {{ total }}</span>
        <div class="spacer"></div>
        <button class="btn btn-sm" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
        <span class="muted text-sm">{{ page }} / {{ totalPages }}</span>
        <button class="btn btn-sm" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>
