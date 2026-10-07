import { reactive } from 'vue';
import { api, clearToken, getToken, setToken } from './api';

interface AuthState {
  username: string | null;
  ready: boolean;
  login(username: string, password: string): Promise<void>;
  logout(): void;
  restore(): Promise<void>;
}

export const auth = reactive<AuthState>({
  username: null,
  ready: false,

  async login(username, password) {
    const res = await api.login(username, password);
    setToken(res.token);
    auth.username = res.username;
  },

  logout() {
    clearToken();
    auth.username = null;
  },

  async restore() {
    if (!getToken()) {
      auth.ready = true;
      return;
    }
    try {
      const me = await api.me();
      auth.username = me.username;
    } catch {
      clearToken();
    } finally {
      auth.ready = true;
    }
  },
});

export function isLoggedIn(): boolean {
  return !!getToken();
}
