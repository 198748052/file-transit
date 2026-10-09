<script setup lang="ts">
import { ref } from 'vue';
import type { FileDTO } from '../api';
import { formatBytes, describeExpiry } from '../utils/format';

defineProps<{ files: FileDTO[] }>();
const emit = defineEmits<{
  (e: 'resume', f: FileDTO): void;
  (e: 'edit', f: FileDTO): void;
  (e: 'remove', f: FileDTO): void;
}>();

const copied = ref<Record<string, boolean>>({});

async function copyText(text: string): Promise<boolean> {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      /* fall through to the legacy path (e.g. denied permission) */
    }
  }
  try {
    const el = document.createElement('textarea');
    el.value = text;
    el.setAttribute('readonly', '');
    el.style.position = 'fixed';
    el.style.top = '-9999px';
    document.body.appendChild(el);
    el.select();
    el.setSelectionRange(0, text.length);
    const ok = document.execCommand('copy');
    document.body.removeChild(el);
    return ok;
  } catch {
    return false;
  }
}

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
  <div style="overflow-x: auto">
    <table class="table">
      <thead>
        <tr>
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
