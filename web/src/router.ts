import { createRouter, createWebHistory } from 'vue-router';
import { auth } from './auth';
import { getToken } from './api';
import LoginView from './views/LoginView.vue';

const DashboardView = () => import('./views/DashboardView.vue');
const ShareView = () => import('./views/ShareView.vue');
const CollectionView = () => import('./views/CollectionView.vue');
const SettingsView = () => import('./views/SettingsView.vue');
const LogsView = () => import('./views/LogsView.vue');
const BucketView = () => import('./views/BucketView.vue');

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: LoginView, meta: { public: true, title: '登录' } },
    { path: '/dashboard', component: DashboardView, meta: { requiresAuth: true, title: '面板' } },
    { path: '/settings', component: SettingsView, meta: { requiresAuth: true, title: '设置' } },
    { path: '/logs', component: LogsView, meta: { requiresAuth: true, title: '操作记录' } },
    { path: '/bucket', component: BucketView, meta: { requiresAuth: true, title: '存储桶' } },
    { path: '/s/:code', component: ShareView, meta: { public: true, title: '文件下载' } },
    { path: '/c/:code', component: CollectionView, meta: { public: true, title: '集合下载' } },
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
