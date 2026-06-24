import { defineStore } from 'pinia';

function readJson(key) {
  try {
    const value = localStorage.getItem(key);
    return value ? JSON.parse(value) : null;
  } catch {
    localStorage.removeItem(key);
    return null;
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: readJson('user'),
    accessToken: localStorage.getItem('accessToken') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    roleCode: (localStorage.getItem('roleCode') || readJson('user')?.roleCode || 'USER').trim().toUpperCase(),
    dataScope: (localStorage.getItem('dataScope') || readJson('user')?.dataScope || 'SELF').trim().toUpperCase()
  }),
  getters: {
    isAuthed: (state) => Boolean(state.accessToken)
  },
  actions: {
    restore() {
      this.user = readJson('user');
      this.accessToken = localStorage.getItem('accessToken') || '';
      this.refreshToken = localStorage.getItem('refreshToken') || '';
      this.roleCode = (localStorage.getItem('roleCode') || this.user?.roleCode || 'USER').trim().toUpperCase();
      this.dataScope = (localStorage.getItem('dataScope') || this.user?.dataScope || 'SELF').trim().toUpperCase();
    },
    clear() {
      ['user', 'accessToken', 'refreshToken', 'roleCode', 'dataScope', 'userId', 'deptId'].forEach((key) => localStorage.removeItem(key));
      this.user = null;
      this.accessToken = '';
      this.refreshToken = '';
      this.roleCode = 'USER';
      this.dataScope = 'SELF';
    }
  }
});
