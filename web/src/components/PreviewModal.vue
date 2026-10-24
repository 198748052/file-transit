<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { formatBytes } from '../utils/format';
import { previewKind } from '../utils/preview';
import DriveIcon from './DriveIcon.vue';

const props = defineProps<{
  name: string;
  url: string;
  size: number;
  mimeType: string;
}>();
const emit = defineEmits<{ (e: 'close'): void }>();

const kind = computed(() => previewKind(props.name, props.mimeType));

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close');
}

onMounted(() => window.addEventListener('keydown', onKeydown));
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown));
</script>

<template>
  <div class="modal-mask preview-mask" @click.self="emit('close')">
    <div class="preview-modal" role="dialog" aria-modal="true" :aria-label="`预览 ${name}`">
      <header class="preview-head">
        <DriveIcon name="eye" :size="18" />
        <div class="preview-title" :title="name">{{ name }}</div>
        <span class="muted text-sm preview-size">{{ formatBytes(size) }}</span>
        <div class="spacer"></div>
        <a class="btn btn-sm" :href="url" target="_blank" rel="noopener">新标签打开</a>
        <button class="btn btn-sm btn-ghost" type="button" @click="emit('close')">关闭</button>
      </header>

      <div class="preview-body">
        <img v-if="kind === 'image'" :src="url" :alt="name" class="preview-image" />
        <video v-else-if="kind === 'video'" :src="url" class="preview-video" controls autoplay playsinline></video>
        <audio v-else-if="kind === 'audio'" :src="url" class="preview-audio" controls autoplay></audio>
        <iframe
          v-else-if="kind === 'pdf'"
          :src="url"
          class="preview-frame"
          :title="`PDF 预览：${name}`"
        ></iframe>
        <iframe
          v-else-if="kind === 'text'"
          :src="url"
          class="preview-frame preview-frame-text"
          sandbox=""
          :title="`文本预览：${name}`"
        ></iframe>
        <div v-else class="preview-empty">
          <DriveIcon name="file" :size="42" />
          <p>该文件类型暂不支持在线预览</p>
          <a class="btn btn-sm" :href="url" target="_blank" rel="noopener">在新标签打开</a>
        </div>
      </div>
    </div>
  </div>
</template>
