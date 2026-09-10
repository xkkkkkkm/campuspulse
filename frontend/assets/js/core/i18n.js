import { messages } from './messages.js';
export const LOCALE_KEY = 'campus_pulse_locale';
export function getLocale() {
  try { const stored = globalThis.localStorage?.getItem(LOCALE_KEY); if (stored === 'zh-CN' || stored === 'en-US') return stored; } catch { /* Private storage may be unavailable. */ }
  return globalThis.navigator?.language?.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US';
}
export function t(key, values = {}) {
  const text = getLocale() === 'zh-CN' ? key : (messages[key] || key);
  return text.replace(/\{(\w+)\}/g, (match, name) => values[name] === undefined ? match : String(values[name]));
}
// Only compile-time template segments are localized; substitutions are user data.
export function ui(strings, ...values) {
  return strings.reduce((output, part, index) => output + translateStatic(part) + (index < values.length ? values[index] : ''), '');
}
export function translateStatic(source) {
  if (getLocale() === 'zh-CN') return source;
  return source.replace(/[\u3400-\u9fff][^<>${}\n"']*/g, value => messages[value.trim()] ? value.replace(value.trim(), messages[value.trim()]) : value);
}
export function initI18n() {
  document.documentElement.lang = getLocale();
  document.querySelectorAll('[data-i18n]').forEach(node => { node.textContent = t(node.dataset.i18n); });
  document.querySelectorAll('[data-i18n-placeholder]').forEach(node => { node.placeholder = t(node.dataset.i18nPlaceholder); });
  document.querySelectorAll('[data-i18n-title]').forEach(node => { node.title = t(node.dataset.i18nTitle); });
  document.querySelectorAll('[data-i18n-alt]').forEach(node => { node.alt = t(node.dataset.i18nAlt); });
  document.querySelectorAll('[data-i18n-label]').forEach(node => { node.setAttribute('aria-label', t(node.dataset.i18nLabel)); });
  if (document.getElementById('languageSwitcher')) return;
  const locale = getLocale();
  const switcher = document.createElement('div');
  switcher.id = 'languageSwitcher';
  switcher.className = 'language-switcher';
  switcher.setAttribute('role', 'group');
  switcher.setAttribute('aria-label', 'Language / 语言');
  switcher.innerHTML = `<div class="language-switcher-label" aria-hidden="true">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" focusable="false" aria-hidden="true">
      <circle cx="12" cy="12" r="9"></circle><ellipse cx="12" cy="12" rx="4" ry="9"></ellipse><path d="M3 12h18"></path>
    </svg><span>${locale === 'zh-CN' ? '语言' : 'Language'}</span>
  </div><div class="language-switcher-options"></div>`;
  const options = switcher.querySelector('.language-switcher-options');
  for (const [value, label, name] of [['zh-CN', '中文', '简体中文'], ['en-US', 'EN', 'English']]) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'language-option';
    button.dataset.locale = value;
    button.lang = value;
    button.textContent = label;
    button.setAttribute('aria-label', name);
    button.setAttribute('aria-pressed', String(locale === value));
    button.title = name;
    button.addEventListener('click', () => {
      if (value === getLocale()) return;
      localStorage.setItem(LOCALE_KEY, value);
      location.reload();
    });
    options.append(button);
  }
  (document.querySelector('.top-bar') || document.body).append(switcher);
}
