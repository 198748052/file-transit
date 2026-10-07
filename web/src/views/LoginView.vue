<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { auth } from '../auth';
import { ApiError } from '../api';

const router = useRouter();
const username = ref('');
const password = ref('');
const error = ref('');
const loading = ref(false);

async function submit() {
  error.value = '';
  loading.value = true;
  try {
    await auth.login(username.value, password.value);
    router.push('/dashboard');
  } catch (e) {
    error.value = e instanceof ApiError && e.code === 'invalid_credentials' ? '用户名或密码错误' : '登录失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="center-wrap">
    <form class="card auth-card" @submit.prevent="submit">
      <div class="logo">📦</div>
      <p class="sub">文件中转站 · 管理面板</p>
      <div v-if="error" class="alert error">{{ error }}</div>
      <div class="field">
        <label>用户名</label>
        <input v-model="username" type="text" autocomplete="username" required />
      </div>
      <div class="field">
        <label>密码</label>
        <input v-model="password" type="password" autocomplete="current-password" required />
      </div>
      <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登录' }}
      </button>
    </form>
  </div>
</template>
