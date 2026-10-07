<script setup lang="ts">
import { useRouter } from 'vue-router';
import { auth } from './auth';

const router = useRouter();

function logout() {
  auth.logout();
  router.push('/login');
}
</script>

<template>
  <div class="app">
    <header v-if="auth.username" class="topbar">
      <div class="brand">📦 文件中转站</div>
      <div class="user">
        <nav class="nav">
          <RouterLink to="/dashboard">面板</RouterLink>
          <RouterLink to="/settings">设置</RouterLink>
        </nav>
        <span class="user-name">{{ auth.username }}</span>
        <button class="btn btn-ghost" @click="logout">退出</button>
      </div>
    </header>
    <main class="container">
      <router-view />
    </main>
  </div>
</template>
