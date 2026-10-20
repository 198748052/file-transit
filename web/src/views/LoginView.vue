<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { auth } from '../auth';
import { ApiError } from '../api';
import DriveIcon from '../components/DriveIcon.vue';

const router = useRouter();
const route = useRoute();
const username = ref('');
const password = ref('');
const showPassword = ref(false);
const error = ref('');
const loading = ref(false);

const notice =
  route.query.pwd === 'changed'
    ? '密码已修改，请用新密码登录'
    : route.query.pwd === 'cleared'
      ? '已改回使用 .env 中的密码，请用它登录'
      : route.query.user === 'changed'
        ? '用户名已修改，请用新用户名和密码登录'
        : route.query.user === 'cleared'
          ? '已改回使用 .env 中的用户名，请重新登录'
          : '';

async function submit() {
  error.value = '';
  loading.value = true;
  try {
    await auth.login(username.value.trim(), password.value);
    router.push('/dashboard');
  } catch (e) {
    if (e instanceof ApiError && e.code === 'too_many_attempts') error.value = '尝试次数过多，请稍后再试';
    else if (e instanceof ApiError && e.code === 'invalid_credentials') error.value = '用户名或密码错误';
    else error.value = '登录失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="center-wrap">
    <form class="card auth-card" @submit.prevent="submit">
      <div class="auth-logo"><DriveIcon name="cloud" :size="30" /></div>
      <p class="sub">文件中转站 · 管理面板</p>
      <div v-if="error" class="alert error">{{ error }}</div>
      <div v-else-if="notice" class="alert success">{{ notice }}</div>
      <div class="field">
        <label for="login-username">用户名</label>
        <input
          id="login-username"
          v-model="username"
          name="username"
          type="text"
          autocomplete="username"
          autocapitalize="off"
          autocorrect="off"
          spellcheck="false"
          required
        />
      </div>
      <div class="field">
        <label for="login-password">密码</label>
        <div class="password-field">
          <input
            id="login-password"
            v-model="password"
            name="password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password"
            required
          />
          <button
            type="button"
            class="password-toggle"
            :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            :aria-pressed="showPassword"
            @click="showPassword = !showPassword"
          >
            <DriveIcon :name="showPassword ? 'eye-off' : 'eye'" :size="17" />
          </button>
        </div>
      </div>
      <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登录' }}
      </button>
    </form>
  </div>
</template>
