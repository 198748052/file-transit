<script setup lang="ts">
import { reactive, watch } from 'vue';
import type { TextDTO, TextFormPayload } from '../api';

const props = defineProps<{ text: TextDTO }>();
const emit = defineEmits<{
  (e: 'save', payload: TextFormPayload): void;
  (e: 'close'): void;
}>();

const form = reactive({
  title: '',
  content: '',
  expiry: 'keep',
  password: '',
  clearPassword: false,
});

watch(
  () => props.text,
  (text) => {
    form.title = text.title;
    form.content = text.content;
    form.expiry = 'keep';
    form.password = '';
    form.clearPassword = false;
  },
  { immediate: true },
);

function save() {
  if (!form.content.trim()) return;
  const payload: TextFormPayload = {
    title: form.title.trim(),
    content: form.content,
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
    <div class="card modal text-modal" role="dialog" aria-modal="true" aria-label="编辑文本">
      <h2>编辑文本</h2>
      <div class="field">
        <label>标题</label>
        <input v-model="form.title" type="text" maxlength="120" placeholder="留空则取第一行文字" />
      </div>
      <div class="field">
        <label>内容</label>
        <textarea v-model="form.content" class="text-content-input" rows="10" maxlength="100000"></textarea>
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
        <button class="btn btn-primary" :disabled="!form.content.trim()" @click="save">保存</button>
      </div>
    </div>
  </div>
</template>
