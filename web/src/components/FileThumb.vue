<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { api, type FileDTO } from '../api';
import { fileKind } from '../utils/filetype';
import { previewKind } from '../utils/preview';

const props = defineProps<{ file: FileDTO }>();

const root = ref<HTMLElement | null>(null);
const thumbUrl = ref('');
const failed = ref(false);
let observer: IntersectionObserver | null = null;

const kind = computed(() => fileKind(props.file.name));
const isImage = computed(
  () => props.file.status === 'ready' && previewKind(props.file.name, props.file.mimeType) === 'image',
);
const showImage = computed(() => !!thumbUrl.value && !failed.value);

function stopObserving() {
  observer?.disconnect();
  observer = null;
}

async function load() {
  if (!isImage.value || thumbUrl.value || failed.value) return;
  try {
    const info = await api.previewFile(props.file.id);
    thumbUrl.value = info.url;
  } catch {
    failed.value = true;
  }
}

function startObserving() {
  stopObserving();
  if (!isImage.value || thumbUrl.value || failed.value) return;
  if (!('IntersectionObserver' in window)) {
    load();
    return;
  }
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        stopObserving();
        load();
      }
    },
    { rootMargin: '240px' },
  );
  if (root.value) observer.observe(root.value);
}

onMounted(startObserving);
onBeforeUnmount(stopObserving);

watch(isImage, (value) => {
  if (value) {
    startObserving();
    return;
  }
  stopObserving();
  thumbUrl.value = '';
  failed.value = false;
});
</script>

<template>
  <div ref="root" class="item-icon" :class="[`tone-${kind.tone}`, { 'has-thumb': showImage }]">
    <img v-if="showImage" :src="thumbUrl" :alt="file.name" loading="lazy" decoding="async" @error="failed = true" />
    <span v-else>{{ kind.label }}</span>
  </div>
</template>
