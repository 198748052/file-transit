<script setup lang="ts">
import { computed, ref } from 'vue';
import type { FileDTO } from '../api';
import { formatBytes, describeExpiry } from '../utils/format';
import { copyText } from '../utils/clipboard';

const props = defineProps<{ files: FileDTO[]; selected: string[] }>();
const emit = defineEmits<{
  (e: 'resume', f: FileDTO): void;
  (e: 'edit', f: FileDTO): void;
  (e: 'remove', f: FileDTO): void;
  (e: 'download', f: FileDTO): void;
  (e: 'qr', f: FileDTO): void;
  (e: 'toggle', id: string): void;
  (e: 'toggle-all', checked: boolean): void;
}>();

const allChecked = computed(() => props.files.length > 0 && props.files.every((f) => props.selected.includes(f.id)));

const copied = ref<Record<string, boolean>>({});

async function copy(f: FileDTO) {
  if (await copyText(f.shareUrl)) {
    copied.value[f.id] = true;
    setTimeout(() => (copied.value[f.id] = false), 1500);
  } else {
    window.prompt('复制下面的链接', f.shareUrl);
  }
}

const statusText: Record<string, string> = {
  uploading: '上传中',
  ready: '可下载',
  expired: '已过期',
};
</script>

<template>
  <div class="table-scroll">
    <table class="table">
      <thead>
        <tr>
          <th class="col-check">
            <input type="checkbox" :checked="allChecked" aria-label="全选" @change="emit('toggle-all', ($event.target as HTMLInputElement).checked)" />
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
            <input type="checkbox" :checked="selected.includes(f.id)" :aria-label="`选择 ${f.name}`" @change="emit('toggle', f.id)" />
          </td>
          <td class="fname" :title="f.name">{{ f.name }}</td>
          <td>{{ formatBytes(f.size) }}</td>
          <td><span class="badge" :class="f.status">{{ statusText[f.status] ?? f.status }}</span></td>
          <td>{{ describeExpiry(f.expiresAt) }}</td>
          <td>{{ f.downloadCount }}</td>
          <td>
            <div class="actions">
              <button class="btn btn-sm" :disabled="f.status !== 'ready'" @click="copy(f)">
                {{ copied[f.id] ? '已复制' : '复制链接' }}
              </button>
              <button class="btn btn-sm" :disabled="f.status !== 'ready'" @click="emit('download', f)">下载</button>
              <button class="btn btn-sm" :disabled="f.status !== 'ready'" @click="emit('qr', f)">二维码</button>
              <button v-if="f.status === 'uploading'" class="btn btn-sm" @click="emit('resume', f)">继续上传</button>
              <button class="btn btn-sm btn-ghost" :disabled="f.status === 'expired'" @click="emit('edit', f)">编辑</button>
              <button class="btn btn-sm btn-danger" @click="emit('remove', f)">删除</button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
