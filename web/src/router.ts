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
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/dashboard', component: DashboardView, meta: { requiresAuth: true } },
    { path: '/settings', component: SettingsView, meta: { requiresAuth: true } },
    { path: '/s/:code', component: ShareView, meta: { public: true } },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
});

router.beforeEach((to) => {
  if (!auth.ready) return true; // restore() resolves on first navigation
  const authed = !!getToken();
  if (to.meta.requiresAuth && !authed) return { path: '/login' };
  if (to.path === '/login' && authed) return { path: '/dashboard' };
  return true;
});
