<script setup lang="ts">
import { reactive, watch } from 'vue';
import type { FileDTO } from '../api';

const props = defineProps<{ file: FileDTO }>();
const emit = defineEmits<{
  (e: 'save', payload: { name: string; expiresInDays?: number | null; password?: string | null; clearPassword?: boolean }): void;
  (e: 'close'): void;
}>();

const form = reactive({
  name: '',
  expiry: 'keep',
  password: '',
  clearPassword: false,
});

watch(
  () => props.file,
  (f) => {
    form.name = f.name;
    form.expiry = 'keep';
    form.password = '';
    form.clearPassword = false;
  },
  { immediate: true },
);

function save() {
  const payload: { name: string; expiresInDays?: number | null; password?: string | null; clearPassword?: boolean } = {
    name: form.name,
  };
  if (form.expiry !== 'keep') {
    payload.expiresInDays = form.expiry === 'never' ? null : Number(form.expiry);
  }
  if (form.clearPassword) {
    payload.clearPassword = true;
  } else if (form.password) {
    payload.password = form.password;
  }
  emit('save', payload);
}
</script>

<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="card modal">
      <h2>编辑文件</h2>
      <div class="field">
        <label>文件名</label>
        <input v-model="form.name" type="text" />
      </div>
      <div class="field">
        <label>有效期</label>
        <select v-model="form.expiry">
          <option value="keep">保持不变</option>
          <option value="never">永久</option>
          <option value="1">1 天</option>
          <option value="7">7 天</option>
          <option value="30">30 天</option>
          <option value="90">90 天</option>
        </select>
      </div>
      <div class="field">
        <label>提取码</label>
        <input v-model="form.password" type="text" placeholder="留空表示不修改" :disabled="form.clearPassword" />
        <label class="checkbox-row">
          <input v-model="form.clearPassword" type="checkbox" />
          清除现有提取码
        </label>
      </div>
      <div class="row mt-lg">
        <button class="btn btn-ghost" @click="emit('close')">取消</button>
        <button class="btn btn-primary" @click="save">保存</button>
      </div>
    </div>
  </div>
</template>
