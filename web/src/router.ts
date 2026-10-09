import { createRouter, createWebHistory } from 'vue-router';
import { auth } from './auth';
import { getToken } from './api';
import LoginView from './views/LoginView.vue';
import DashboardView from './views/DashboardView.vue';
import ShareView from './views/ShareView.vue';
import SettingsView from './views/SettingsView.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: LoginView, meta: { public: true, title: '登录' } },
    { path: '/dashboard', component: DashboardView, meta: { requiresAuth: true, title: '面板' } },
    { path: '/settings', component: SettingsView, meta: { requiresAuth: true, title: '设置' } },
    { path: '/s/:code', component: ShareView, meta: { public: true, title: '文件下载' } },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
});

router.afterEach((to) => {
  const title = (to.meta as { title?: string }).title;
  document.title = title ? `${title} · 文件中转站` : '文件中转站';
});

router.beforeEach((to) => {
  if (!auth.ready) return true; // restore() resolves on first navigation
  const authed = !!getToken();
  if (to.meta.requiresAuth && !authed) return { path: '/login' };
  if (to.path === '/login' && authed) return { path: '/dashboard' };
  return true;
});
