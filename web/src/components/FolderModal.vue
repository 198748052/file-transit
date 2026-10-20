<script setup lang="ts">
import { ref } from 'vue';

const props = defineProps<{
  mode: 'create' | 'rename';
  initialName?: string;
  parentName?: string;
  count?: number;
}>();
const emit = defineEmits<{
  (e: 'save', name: string): void;
  (e: 'close'): void;
}>();

const name = ref(props.initialName ?? '');

function save() {
  const value = name.value.trim();
  if (value) emit('save', value);
}
</script>

<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="card modal" role="dialog" aria-modal="true" aria-label="文件夹">
      <h2>{{ mode === 'create' ? '新建文件夹' : '重命名文件夹' }}</h2>
      <p v-if="mode === 'create' && parentName" class="muted text-sm">创建于「{{ parentName }}」</p>
      <p v-if="mode === 'rename' && count" class="muted text-sm">文件夹内共 {{ count }} 项内容</p>
      <div class="field mt-md">
        <label>文件夹名称</label>
        <input v-model="name" type="text" maxlength="120" placeholder="例如：项目资料" @keyup.enter="save" />
      </div>
      <div class="row mt-lg">
        <button class="btn btn-ghost" @click="emit('close')">取消</button>
        <button class="btn btn-primary" :disabled="!name.trim()" @click="save">保存</button>
      </div>
    </div>
  </div>
</template>
