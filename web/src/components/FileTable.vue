<script setup lang="ts">
import { computed } from 'vue';
import type { FileDTO } from '../api';
import { formatBytes, describeExpiry } from '../utils/format';
import DriveIcon from './DriveIcon.vue';

const props = defineProps<{ files: FileDTO[]; selected: string[] }>();
const emit = defineEmits<{
  (e: 'resume', f: FileDTO): void;
  (e: 'download', f: FileDTO): void;
  (e: 'menu', f: FileDTO, event: MouseEvent): void;
  (e: 'toggle', id: string): void;
  (e: 'toggle-all', checked: boolean): void;
}>();

const allChecked = computed(() => props.files.length > 0 && props.files.every((f) => props.selected.includes(f.id)));

const statusText: Record<string, string> = {
  uploading: '上传中',
  ready: '可下载',
  expired: '已过期',
};

function openMenu(event: MouseEvent, file: FileDTO) {
  emit('menu', file, event);
}
</script>

<template>
  <div class="table-scroll">
    <table class="table">
      <thead>
        <tr>
          <th class="col-check">
            <input
              type="checkbox"
              :checked="allChecked"
              aria-label="全选"
              @change="emit('toggle-all', ($event.target as HTMLInputElement).checked)"
            />
          </th>
          <th>文件</th>
          <th>大小</th>
          <th>状态</th>
          <th>有效期</th>
          <th>下载</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="f in files" :key="f.id">
          <td class="col-check">
            <input
              type="checkbox"
              :checked="selected.includes(f.id)"
              :aria-label="`选择 ${f.name}`"
              @change="emit('toggle', f.id)"
            />
          </td>
          <td class="fname" :title="f.name">{{ f.name }}</td>
          <td class="meta" data-label="大小">{{ formatBytes(f.size) }}</td>
          <td class="cell-status">
            <span class="badge" :class="f.status">{{ statusText[f.status] ?? f.status }}</span>
          </td>
          <td class="meta" data-label="有效期">{{ describeExpiry(f.expiresAt) }}</td>
          <td class="meta" data-label="下载">{{ f.downloadCount }}</td>
          <td class="col-actions">
            <div class="actions">
              <button v-if="f.status === 'uploading'" class="btn btn-sm" @click="emit('resume', f)">
                继续上传
              </button>
              <button v-else class="btn btn-sm" :disabled="f.status !== 'ready'" @click="emit('download', f)">
                下载
              </button>
              <button
                class="btn btn-sm btn-ghost action-more"
                type="button"
                aria-label="更多操作"
                title="更多操作"
                @click="openMenu($event, f)"
              >
                <DriveIcon name="more" :size="16" />
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
