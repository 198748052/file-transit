import { createApp } from 'vue';
import App from './App.vue';
import { router } from './router';
import { auth } from './auth';
import './styles.css';

// Restore the session before the first navigation so route guards see real state.
auth
  .restore()
  .catch(() => undefined)
  .finally(() => {
    createApp(App).use(router).mount('#app');
  });
