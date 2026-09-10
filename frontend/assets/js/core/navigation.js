import { qs } from './ui.js';

export const ALLOWED_PAGES = new Set(['index.html', 'home.html', 'login.html', 'register.html', 'activity-lobby.html', 'activity-detail.html', 'activity-manage.html', 'team-lobby.html', 'team-success.html', 'chat.html', 'messages.html', 'notifications.html', 'profile.html', 'profile-edit.html', 'profile-records.html', 'interest-tags.html', 'publish-activity.html', 'customer-service.html', 'admin.html']);

export function safePageRef(value, fallback = 'home.html', origin = globalThis.location?.origin || 'http://localhost') {
  try {
    if (typeof value !== 'string' || /[\u0000-\u001f\u007f\\]/.test(value)) return fallback;
    const target = new URL(value.trim(), `${origin}/`);
    if (!['http:', 'https:'].includes(target.protocol) || target.origin !== origin || target.username || target.password) return fallback;
    if (!/^\/[a-z-]+\.html$/.test(target.pathname) || !ALLOWED_PAGES.has(target.pathname.slice(1))) return fallback;
    return `${target.pathname.slice(1)}${target.search}${target.hash}`;
  } catch { return fallback; }
}
export function getQueryParam(name) { return new URL(window.location.href).searchParams.get(name); }
export function currentPageRef() { return safePageRef(window.location.href); }
export function withFrom(url, from = currentPageRef()) {
  const target = new URL(safePageRef(url), window.location.origin);
  target.searchParams.set('from', safePageRef(from));
  return `${target.pathname.slice(1)}${target.search}${target.hash}`;
}
export function applyBackLink(selector, fallback = 'home.html') {
  qs(selector)?.setAttribute('href', safePageRef(getQueryParam('from'), fallback));
}
