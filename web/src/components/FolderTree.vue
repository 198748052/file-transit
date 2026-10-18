<script setup lang="ts">
import { ref } from 'vue';
import type { FolderDTO } from '../api';
import DriveIcon from './DriveIcon.vue';

export interface FolderNode extends FolderDTO {
  children: FolderNode[];
}

withDefaults(defineProps<{ nodes: FolderNode[]; activeId: string | null; depth?: number }>(), { depth: 0 });

const emit = defineEmits<{
  (e: 'open', id: string): void;
  (e: 'drop-files', folderId: string, files: FileList): void;
  (e: 'drop-file', folderId: string, fileId: string): void;
}>();

const open = ref<Record<string, boolean>>({});
const dragOver = ref<string | null>(null);

function toggle(id: string) {
  open.value[id] = !open.value[id];
}

function isOpen(node: FolderNode): boolean {
  return open.value[node.id] ?? true;
}

function onDragOver(e: DragEvent, node: FolderNode) {
  e.preventDefault();
  e.stopPropagation();
  dragOver.value = node.id;
}

function onDragLeave(node: FolderNode) {
  if (dragOver.value === node.id) dragOver.value = null;
}

function onDrop(e: DragEvent, node: FolderNode) {
  e.preventDefault();
  e.stopPropagation();
  dragOver.value = null;
  const fileId = e.dataTransfer?.getData('application/x-ft-file');
  if (fileId) {
    emit('drop-file', node.id, fileId);
    return;
  }
  if (e.dataTransfer?.files?.length) emit('drop-files', node.id, e.dataTransfer.files);
}
</script>

<template>
  <div class="tree-level">
    <div v-for="node in nodes" :key="node.id" class="tree-node">
      <div
        class="tree-row"
        :class="{ active: activeId === node.id, 'drag-over': dragOver === node.id }"
        :style="{ paddingLeft: 8 + depth * 14 + 'px' }"
        @dragover="onDragOver($event, node)"
        @dragleave="onDragLeave(node)"
        @drop="onDrop($event, node)"
      >
        <button
          v-if="node.children.length"
          class="tree-toggle"
          :class="{ open: isOpen(node) }"
          type="button"
          :aria-label="isOpen(node) ? '收起' : '展开'"
          @click.stop="toggle(node.id)"
        >
          <DriveIcon name="chevron" :size="13" />
        </button>
        <span v-else class="tree-toggle-space"></span>
        <button class="tree-label" type="button" :title="node.name" @click="emit('open', node.id)">
          <DriveIcon name="folder" :size="16" />
          <span class="tree-name">{{ node.name }}</span>
          <span v-if="node.fileCount || node.childCount" class="tree-count">
            {{ node.fileCount + node.childCount }}
          </span>
        </button>
      </div>

      <FolderTree
        v-if="node.children.length && isOpen(node)"
        :nodes="node.children"
        :active-id="activeId"
        :depth="depth + 1"
        @open="emit('open', $event)"
        @drop-files="(id, files) => emit('drop-files', id, files)"
        @drop-file="(id, fileId) => emit('drop-file', id, fileId)"
      />
    </div>
  </div>
</template>
