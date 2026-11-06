<script setup lang="ts">
import { reactive, watch } from 'vue';
import type { FileDTO } from '../api';

const props = defineProps<{ file: FileDTO }>();
const emit = defineEmits<{
  (
    e: 'save',
    payload: {
      name: string;
      expiresInDays?: number | null;
      password?: string | null;
      clearPassword?: boolean;
      maxDownloads?: number | null;
    },
  ): void;
  (e: 'close'): void;
}>();

const form = reactive({
  name: '',
  expiry: 'keep',
  password: '',
  clearPassword: false,
  maxDownloads: 'keep',
});

watch(
  () => props.file,
  (f) => {
    form.name = f.name;
    form.expiry = 'keep';
    form.password = '';
    form.clearPassword = false;
    form.maxDownloads = 'keep';
  },
  { immediate: true },
);

function save() {
  const payload: {
    name: string;
    expiresInDays?: number | null;
    password?: string | null;
    clearPassword?: boolean;
    maxDownloads?: number | null;
  } = {
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
  if (form.maxDownloads !== 'keep') {
    payload.maxDownloads = form.maxDownloads === 'unlimited' ? null : Number(form.maxDownloads);
  }
  emit('save', payload);
}
</script>

<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="card modal" role="dialog" aria-modal="true" aria-label="编辑文件">
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
      <div class="field">
        <label>下载次数上限</label>
        <select v-model="form.maxDownloads">
          <option value="keep">
            保持不变（当前：{{ file.maxDownloads ? `${file.downloadCount}/${file.maxDownloads} 次` : '不限' }}）
          </option>
          <option value="unlimited">不限</option>
          <option value="1">1 次（阅后即焚）</option>
          <option value="3">3 次</option>
          <option value="5">5 次</option>
          <option value="10">10 次</option>
          <option value="50">50 次</option>
        </select>
      </div>
      <div class="row mt-lg">
        <button class="btn btn-ghost" @click="emit('close')">取消</button>
        <button class="btn btn-primary" :disabled="!form.name.trim()" @click="save">保存</button>
      </div>
    </div>
  </div>
</template>
