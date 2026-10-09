<script setup lang="ts">
import { reactive, watch } from 'vue';
import type { CollectionFormPayload } from '../api';

const props = defineProps<{
  mode: 'create' | 'edit';
  initialName?: string;
  initialHasPassword?: boolean;
  fileCount?: number;
}>();
const emit = defineEmits<{
  (e: 'save', payload: CollectionFormPayload): void;
  (e: 'close'): void;
}>();

const form = reactive({
  name: props.initialName ?? '',
  expiry: 'keep',
  password: '',
  clearPassword: false,
});

watch(
  () => props.initialName,
  (name) => {
    form.name = name ?? '';
  },
);

function save() {
  const payload: CollectionFormPayload = { name: form.name.trim() };
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
      <h2>{{ mode === 'create' ? '打包分享' : '编辑集合' }}</h2>
      <p v-if="mode === 'create'" class="muted text-sm">将选中的 {{ fileCount ?? 0 }} 个文件生成一个集合分享链接。</p>

      <div class="field">
        <label>集合名称</label>
        <input v-model="form.name" type="text" maxlength="120" placeholder="例如：项目资料" @keyup.enter="save" />
      </div>
      <div class="field">
        <label>有效期</label>
        <select v-model="form.expiry">
          <option v-if="mode === 'edit'" value="keep">保持不变</option>
          <option value="never">永久</option>
          <option value="1">1 天</option>
          <option value="7">7 天</option>
          <option value="30">30 天</option>
          <option value="90">90 天</option>
        </select>
      </div>
      <div class="field">
        <label>提取码</label>
        <input v-model="form.password" type="text" placeholder="留空表示不需要" :disabled="form.clearPassword" />
        <label v-if="mode === 'edit'" class="checkbox-row">
          <input v-model="form.clearPassword" type="checkbox" />
          清除现有提取码
        </label>
      </div>
      <div class="row mt-lg">
        <button class="btn btn-ghost" @click="emit('close')">取消</button>
        <button class="btn btn-primary" :disabled="!form.name.trim()" @click="save">保存</button>
      </div>
    </div>
  </div>
</template>
