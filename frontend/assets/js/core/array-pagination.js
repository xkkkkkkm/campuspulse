import { apiFetch } from './api.js';
import { t } from './i18n.js';
const states = new WeakMap();

export function mountPageNavigator(container, { page, count, hasMore, onChange }) {
  if (!container) return;
  let nav = container.nextElementSibling;
  if (!nav?.classList.contains('array-pager')) { nav = document.createElement('nav'); nav.className = 'array-pager'; container.after(nav); }
  nav.setAttribute('aria-label', t('列表分页'));
  nav.replaceChildren();
  const previous = document.createElement('button'), next = document.createElement('button'), label = document.createElement('span');
  previous.className = next.className = 'secondary-btn'; previous.textContent = t('上一页'); next.textContent = t('下一页');
  previous.disabled = page <= 1; next.disabled = !hasMore;
  label.textContent = t('第 {page} 页，本页 {count} 条', { page, count });
  for (const [button, value] of [[previous, page - 1], [next, page + 1]]) button.addEventListener('click', async () => { previous.disabled = next.disabled = true; try { await onChange(value); } finally { previous.disabled = page <= 1; next.disabled = !hasMore; } });
  nav.append(previous, label, next);
}

export async function loadArrayPage(path, container, refresh, size = 20) {
  let state = states.get(container);
  const query = new URL(path, 'http://local'); query.searchParams.delete('size'); query.searchParams.delete('page');
  const key = query.pathname + query.search;
  if (!state || state.key !== key) { state?.controller?.abort(); state = { page: 1, key, generation: 0 }; states.set(container, state); }
  state.controller?.abort(); state.controller = new AbortController(); const generation = ++state.generation;
  query.searchParams.set('page', state.page); query.searchParams.set('size', size);
  const items = await apiFetch(query.pathname + query.search, { signal: state.controller.signal });
  if (states.get(container) !== state || generation !== state.generation) throw new DOMException('Superseded list request', 'AbortError');
  mountPageNavigator(container, { page: state.page, count: items.length, hasMore: items.length === size, onChange: page => { state.page = page; return refresh(); } });
  return items;
}
