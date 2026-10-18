<script setup lang="ts">
import { computed, ref } from 'vue';
import type { FolderNode } from './FolderTree.vue';
import DriveIcon from './DriveIcon.vue';

const props = defineProps<{
  title: string;
  subtitle?: string;
  folders: FolderNode[];
  excludeIds?: string[];
  currentId: string | null;
}>();

const emit = defineEmits<{
  (e: 'move', folderId: string | null): void;
  (e: 'close'): void;
}>();

interface Option {
  id: string | null;
  name: string;
  depth: number;
  disabled: boolean;
}

const excluded = computed(() => new Set(props.excludeIds ?? []));
const selected = ref<string | null>(props.currentId);

const options = computed<Option[]>(() => {
  const out: Option[] = [{ id: null, name: '全部文件（根目录）', depth: 0, disabled: false }];
  const walk = (nodes: FolderNode[], depth: number) => {
    for (const node of nodes) {
      out.push({
        id: node.id,
        name: node.name,
        depth,
        disabled: excluded.value.has(node.id),
      });
      if (node.children.length) walk(node.children, depth + 1);
    }
  };
  walk(props.folders, 0);
  return out;
});

function choose(option: Option) {
  if (option.disabled) return;
  selected.value = option.id;
}
</script>

<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="card modal move-modal">
      <h2>{{ title }}</h2>
      <p v-if="subtitle" class="muted text-sm">{{ subtitle }}</p>
      <div class="move-list mt-md">
        <button
          v-for="option in options"
          :key="option.id ?? 'root'"
          type="button"
          class="move-option"
          :class="{ selected: selected === option.id, disabled: option.disabled }"
          :style="{ paddingLeft: 12 + option.depth * 18 + 'px' }"
          @click="choose(option)"
        >
          <DriveIcon :name="option.id ? 'folder' : 'home'" :size="17" />
          <span class="move-name">{{ option.name }}</span>
          <span v-if="option.disabled" class="move-hint">不可选择</span>
          <DriveIcon v-else-if="selected === option.id" name="check" :size="16" />
        </button>
      </div>
      <div class="row mt-lg">
        <button class="btn btn-ghost" @click="emit('close')">取消</button>
        <button class="btn btn-primary" @click="emit('move', selected)">移动到此处</button>
      </div>
    </div>
  </div>
</template>
