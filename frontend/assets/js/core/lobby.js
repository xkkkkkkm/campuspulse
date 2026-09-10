import { tagLabel } from './content-i18n.js';
import { apiFetch } from './api.js';
import { qs, escapeHtml, renderEmptyCard, showToast } from './ui.js';
import { getQueryParam } from './navigation.js';
import { ensureMe } from './auth.js';
import { mountBottomNavActive, iconForCategory, renderActivityCards, renderHomeTeamCards } from './render.js';
import { createPagedLoader } from './pagination.js';
import { t, ui } from './i18n.js';

export async function initLobby(kind) {
  mountBottomNavActive();
  const team = kind === 'team';
  const list = qs(team ? '#teamList' : '#activityLobbyList');
  const search = qs(`#${kind}LobbySearchInput`), reset = qs(`#${kind}LobbySearchResetBtn`);
  const more = qs(`#${kind}LoadMoreBtn`), moreWrap = qs(`#${kind}LoadMoreWrap`);
  const categories = qs(`#${kind}CategoryList`), hint = qs(`#${kind}LobbyHint`);
  const tags = await apiFetch('/tags');
  if (team) await ensureMe();
  let tag = getQueryParam('tag') || '', keyword = '', searchTimer;
  const loader = createPagedLoader(team ? '/teams/page' : '/activities', {
    onChange({ items, total, hasMore }) {
      if (team) renderHomeTeamCards(list, items, t('当前还没有可加入的组队'));
      else renderActivityCards(list, items);
      if (!items.length) list.innerHTML = renderEmptyCard(t('没有找到符合条件的结果'));
      if (hint) hint.textContent = t('已显示 {count} 条，共 {total} 条', { count: items.length, total });
      if (moreWrap) moreWrap.style.display = hasMore ? '' : 'none';
      if (more) { more.disabled = false; more.textContent = t('查看更多'); }
      list.removeAttribute('aria-busy');
    },
    onError(error) { if (more) more.disabled = false; list.removeAttribute('aria-busy'); showToast(error.message, 'danger'); }
  });
  async function refresh() {
    if (reset) reset.style.display = keyword || tag ? '' : 'none';
    list.setAttribute('aria-busy', 'true');
    await loader.reset({ tag, keyword });
  }
  function renderCategories() {
    categories.innerHTML = [{ name: '', label: t('全部') }, ...(tags || []).map(value => ({ name: value.name, label: tagLabel(value) }))].map(value => `
      <button class="category-icon${value.name === tag ? ' active' : ''}" type="button" data-category="${escapeHtml(value.name)}" aria-pressed="${value.name === tag}"><i class="fa-solid ${value.name ? iconForCategory(value.name) : 'fa-compass'}" aria-hidden="true"></i><p>${escapeHtml(value.label)}</p></button>`).join('');
  }
  categories?.addEventListener('click', event => { const button = event.target.closest('[data-category]'); if (!button) return; tag = button.dataset.category; renderCategories(); refresh(); });
  const runSearch = () => { clearTimeout(searchTimer); keyword = search?.value.trim() || ''; refresh(); };
  qs(`#${kind}LobbySearchBtn`)?.addEventListener('click', runSearch);
  search?.addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(runSearch, 300); });
  search?.addEventListener('keydown', event => { if (event.key === 'Enter') { event.preventDefault(); runSearch(); } });
  reset?.addEventListener('click', () => { keyword = ''; tag = ''; if (search) search.value = ''; renderCategories(); refresh(); });
  more?.addEventListener('click', async () => { more.disabled = true; await loader.more(); });
  window.addEventListener('pagehide', () => { clearTimeout(searchTimer); loader.close(); }, { once: true });
  if (categories) renderCategories();
  await refresh();
}
