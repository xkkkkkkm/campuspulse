import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { apiFetch } from './api.js';
import { openUtilityModal, showToast, escapeHtml } from './ui.js';
import { t, ui } from './i18n.js';
export function mountTeamLifecycle(team, me, container) {
  if (!container || !me || team.status !== 'OPEN') return;
  const members = team.members || [];
  const captain = String(team.creatorId) === String(me.id);
  const member = members.some(value => String(value.userId ?? value.id) === String(me.id));
  if (!captain && !member) return;
  const actions = document.createElement('div'); actions.className = 'stack-card-actions';
  actions.innerHTML = captain
    ? ui`<button type="button" class="secondary-btn" data-action="transfer">转让队长</button><button type="button" class="secondary-btn" data-action="close">关闭队伍</button>`
    : ui`<button type="button" class="secondary-btn" data-action="leave">退出队伍</button>`;
  container.append(actions);
  actions.addEventListener('click', event => {
    const action = event.target.closest('[data-action]')?.dataset.action;
    if (!action) return;
    const title = t(action === 'transfer' ? _tr('转让队长') : action === 'close' ? _tr('关闭队伍') : _tr('退出队伍'));
    const candidates = members.filter(value => String(value.userId ?? value.id) !== String(me.id));
    openUtilityModal({ title, html: `<p>${escapeHtml(t(action === 'close' ? _tr('关闭后停止招募，保留队伍和聊天记录。') : action === 'leave' ? _tr('退出后需要重新申请加入。') : _tr('请选择一位现有成员接任队长。')))}</p>${action === 'transfer' ? `<label for="newCaptain">${escapeHtml(t('新队长'))}</label><select id="newCaptain">${candidates.map(value => `<option value="${Number(value.userId ?? value.id)}">${escapeHtml(value.nickname || value.username || String(value.userId ?? value.id))}</option>`).join('')}</select>` : ''}<div class="utility-actions"><button class="secondary-btn" data-cancel>${escapeHtml(t('取消'))}</button><button class="primary-btn" data-confirm>${escapeHtml(t('确认'))}</button></div>`, onOpen(modal, close) {
      modal.querySelector('[data-cancel]').addEventListener('click', close);
      modal.querySelector('[data-confirm]').addEventListener('click', async event => {
        const userId = Number(modal.querySelector('#newCaptain')?.value);
        if (action === 'transfer' && !userId) { showToast(t('队伍中没有可接任的成员'), 'danger'); return; }
        event.target.disabled = true;
        try { await apiFetch(`/teams/${team.id}/${action}`, { method: 'POST', body: action === 'transfer' ? JSON.stringify({ userId }) : '{}' }); location.reload(); }
        catch (error) { event.target.disabled = false; showToast(error.message, 'danger'); }
      });
    }});
  });
}
