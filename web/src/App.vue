<script setup lang="ts">
import { useRouter } from 'vue-router';
import { auth } from './auth';
import DriveIcon from './components/DriveIcon.vue';

const router = useRouter();

function logout() {
  auth.logout();
  router.push('/login');
}
</script>

<template>
  <div class="app">
    <header v-if="auth.username" class="topbar">
      <RouterLink class="brand" to="/dashboard">
        <span class="brand-mark"><DriveIcon name="cloud" :size="19" /></span>
        <span class="brand-text">文件中转站</span>
      </RouterLink>
      <nav class="nav" aria-label="主导航">
        <RouterLink to="/dashboard">
          <DriveIcon name="grid" :size="16" />
          <span>云盘</span>
        </RouterLink>
        <RouterLink to="/logs">
          <DriveIcon name="clock" :size="16" />
          <span>记录</span>
        </RouterLink>
        <RouterLink to="/bucket">
          <DriveIcon name="storage" :size="16" />
          <span>存储桶</span>
        </RouterLink>
        <RouterLink to="/settings">
          <DriveIcon name="settings" :size="16" />
          <span>设置</span>
        </RouterLink>
      </nav>
      <div class="user">
        <span class="user-name" :title="auth.username">{{ auth.username }}</span>
        <button class="btn btn-ghost btn-sm" @click="logout">退出</button>
      </div>
    </header>
    <main class="container">
      <router-view />
    </main>
  </div>
</template>
