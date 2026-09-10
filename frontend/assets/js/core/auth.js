import { apiFetch } from './api.js';

export const TOKEN_KEY = 'campus_pulse_token';

export function getToken() {
    return localStorage.getItem(TOKEN_KEY) || '';
  }

export function setToken(token) {
    if (!token) localStorage.removeItem(TOKEN_KEY);
    else localStorage.setItem(TOKEN_KEY, token);
  }

export function isLoggedIn() {
    return !!getToken();
  }

export async function ensureMe() {
    if (!isLoggedIn()) return null;
    try {
      const me = await apiFetch('/auth/me');
      window.__meCache = me || null;
      return me;
    } catch (e) {
      window.__meCache = null;
      if (e.status === 401) return null;
      throw e;
    }
  }

export function redirectToLogin() {
    window.location.href = 'login.html';
  }

export function requireAuth() {
    if (!isLoggedIn()) redirectToLogin();
  }
