import { contentField } from '../core/content-i18n.js';
import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { getLocale } from './i18n.js';
import { apiFetch } from './api.js';
import { requireAuth } from './auth.js';

export function qs(sel) {
    return document.querySelector(sel);
  }

export function qsa(sel) {
    return Array.from(document.querySelectorAll(sel));
  }

export function escapeHtml(s) {
    return String(s)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

export function ensureAppModal() {
    let modal = qs('#appModal');
    if (modal) return modal;
    modal = document.createElement('div');
    modal.id = 'appModal';
    modal.className = 'modal';
    modal.innerHTML = _ui`
      <div class="modal-content animate-fade-up" style="max-width: 520px;">
        <div class="modal-header">
          <h3 id="appModalTitle">提示</h3>
        </div>
        <div class="modal-body" style="text-align: center;">
          <div id="appModalMessage" style="font-size: 16px; color: var(--text-primary); line-height: 1.6;"></div>
          <button id="appModalOk" class="secondary-btn" style="width: 100%; margin-top: 18px;">关闭</button>
        </div>
      </div>
    `;
    document.body.appendChild(modal);
    const closeModal = () => {
      modal.style.display = 'none';
      if (typeof modal.__onClose === 'function') {
        const cb = modal.__onClose;
        modal.__onClose = null;
        cb();
      }
    };
    modal.addEventListener('click', (e) => {
      if (e.target === modal) closeModal();
    });
    const ok = modal.querySelector('#appModalOk');
    if (ok) ok.addEventListener('click', closeModal);
    return modal;
  }

export function showMessage(message, title = _tr('提示'), buttonText = _tr('关闭'), options = {}) {
    const modal = ensureAppModal();
    const t = modal.querySelector('#appModalTitle');
    const m = modal.querySelector('#appModalMessage');
    const ok = modal.querySelector('#appModalOk');
    if (t) t.textContent = title;
    if (m) {
      if (options.allowHtml) m.innerHTML = String(message || '');
      else m.innerHTML = escapeHtml(message || '');
    }
    if (ok) ok.textContent = buttonText || _tr('关闭');
    modal.__onClose = typeof options.onClose === 'function' ? options.onClose : null;
    modal.style.display = 'flex';
  }

export function showSuccessMessage(title, message, buttonText = _tr('完成'), onClose) {
    showMessage(`
      <div style="display:flex; flex-direction:column; align-items:center; gap: 14px; padding-top: 6px;">
        <div style="width: 64px; height: 64px; border-radius: 22px; background: linear-gradient(135deg, rgba(52,199,89,0.16), rgba(52,199,89,0.3)); display:flex; align-items:center; justify-content:center; box-shadow: inset 0 0 0 1px rgba(52,199,89,0.16);">
          <i class="fa-solid fa-check" style="font-size: 28px; color: var(--success-color);"></i>
        </div>
        <div style="font-size: 16px; color: var(--text-primary); line-height: 1.7; text-align:center;">${escapeHtml(message || '')}</div>
      </div>
    `, title, buttonText, { allowHtml: true, onClose });
  }

export function showToast(message, type = 'success', duration = 1800) {
    let holder = qs('#appToastHolder');
    if (!holder) {
      holder = document.createElement('div');
      holder.id = 'appToastHolder';
      holder.className = 'app-toast-holder';
      document.body.appendChild(holder);
    }
    const toast = document.createElement('div');
    toast.className = `app-toast app-toast-${type}`;
    toast.innerHTML = `
      <i class="fa-solid ${type === 'danger' ? 'fa-circle-exclamation' : 'fa-circle-check'}"></i>
      <span>${escapeHtml(message || '')}</span>
    `;
    holder.appendChild(toast);
    window.setTimeout(() => toast.classList.add('show'), 20);
    window.setTimeout(() => {
      toast.classList.remove('show');
      window.setTimeout(() => toast.remove(), 220);
    }, duration);
  }

export function formatDateTime(iso) {
  if (!iso) return '';
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? String(iso) : new Intl.DateTimeFormat(getLocale(), { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

export function toDatetimeLocalValue(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return String(iso || '').replace(' ', 'T').slice(0, 16);
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

export function ensureUtilityModal() {
    let modal = qs('#utilityModal');
    if (modal) return modal;
    modal = document.createElement('div');
    modal.id = 'utilityModal';
    modal.className = 'modal';
    modal.innerHTML = _ui`
      <div class="modal-content animate-fade-up" id="utilityModalContent" style="max-width: 560px;">
        <div class="modal-header" style="display:flex; align-items:center; justify-content:space-between; gap: 12px; text-align:left;">
          <h3 id="utilityModalTitle">操作</h3>
          <button id="utilityModalClose" class="utility-close" type="button"><i class="fa-solid fa-xmark"></i></button>
        </div>
        <div class="modal-body" id="utilityModalBody"></div>
      </div>
    `;
    document.body.appendChild(modal);
    modal.addEventListener('click', (e) => {
      if (e.target === modal) modal.__close?.();
    });
    const closeBtn = modal.querySelector('#utilityModalClose');
    if (closeBtn) closeBtn.addEventListener('click', () => modal.__close?.());
    return modal;
  }

export function openUtilityModal({ title, html, maxWidth = 560, onOpen, onClose }) {
    const modal = ensureUtilityModal();
    modal.__cleanup?.(); modal.__cleanup = onClose;
    modal.querySelectorAll('.array-pager').forEach(node => node.remove());
    const titleEl = modal.querySelector('#utilityModalTitle');
    const bodyEl = modal.querySelector('#utilityModalBody');
    const contentEl = modal.querySelector('#utilityModalContent');
    if (titleEl) titleEl.textContent = title || _tr('操作');
    if (bodyEl) bodyEl.innerHTML = html || '';
    if (contentEl) contentEl.style.maxWidth = `${maxWidth}px`;
    modal.style.display = 'flex';
    const close = () => { modal.style.display = 'none'; const cleanup = modal.__cleanup; modal.__cleanup = null; cleanup?.(); };
    modal.__close = close;
    if (typeof onOpen === 'function') onOpen(modal, close);
    return { modal, close };
  }

export function renderEmptyCard(message, extra = '') {
    return `
      <div class="stack-card" style="text-align:center; color: var(--text-secondary);">
        <div style="font-size: 14px; line-height: 1.7;">${escapeHtml(message || _tr('暂无数据'))}</div>
        ${extra}
      </div>
    `;
  }

export const REVIEW_PREVIEW_LIMIT = 6;

export function renderReviewSection({ sectionKey, title, meta, items, expandedMap, emptyMessage, expandText, renderItem }) {
    const expanded = !!expandedMap[sectionKey];
    const visibleItems = expanded ? items : items.slice(0, REVIEW_PREVIEW_LIMIT);
    return `
      <div class="manage-review-section">
        <div class="stack-card-head">
          <div>
            <div class="stack-card-title">${escapeHtml(title)}</div>
            <div class="stack-card-meta">${escapeHtml(meta)}</div>
          </div>
          <span class="badge-pill">${items.length}</span>
        </div>
        <div class="stack-list" style="margin-top: 14px;">
          ${visibleItems.length ? visibleItems.map(renderItem).join('') : renderEmptyCard(emptyMessage)}
        </div>
        ${items.length > REVIEW_PREVIEW_LIMIT && !expanded ? `
          <div class="utility-actions" style="margin-top: 14px;">
            <button type="button" class="secondary-btn js-review-section-expand" data-section="${escapeHtml(sectionKey)}">${escapeHtml(expandText || _ui`显示全部 ${items.length} 条`)}</button>
          </div>
        ` : ''}
      </div>
    `;
  }

export function userSubtitle(user) {
    const parts = [];
    if (user.username) parts.push(_ui`账号：${user.username}`);
    if (user.studentNo) parts.push(_ui`学号：${user.studentNo}`);
    if (user.campus) parts.push(contentField(user, 'campus'));
    if (user.college) parts.push(user.college);
    return parts.join(' · ');
  }

export function notificationTypeLabel(type) {
    const value = String(type || '').toUpperCase();
    if (value.startsWith('ACTIVITY_')) return _tr('活动通知');
    if (value.startsWith('TEAM_')) return _tr('组队通知');
    if (value.startsWith('DM_')) return _tr('私聊通知');
    return _tr('系统通知');
  }

export async function markNotificationRead(notification) {
    if (!notification || notification.read || !notification.id) return;
    await apiFetch(`/notifications/${encodeURIComponent(notification.id)}/read`, { method: 'POST', body: '{}' });
    notification.read = true;
  }

export function openNotificationDetailModal(notification) {
    openUtilityModal({
      title: notification.title || _tr('通知详情'),
      maxWidth: 620,
      html: `
        <div class="stack-card" style="padding: 16px; box-shadow:none;">
          <div class="stack-card-meta">${escapeHtml(notificationTypeLabel(notification.type))}</div>
          <div style="font-size: 14px; color: var(--text-secondary); margin-top: 8px;">${escapeHtml(formatDateTime(notification.createdAt))}</div>
          <div style="font-size: 15px; line-height: 1.8; color: var(--text-primary); margin-top: 14px; white-space: pre-wrap;">${escapeHtml(notification.content || _tr('暂无通知内容'))}</div>
        </div>
      `,
    });
  }

export function matchesKeyword(fields, keyword) {
    const q = String(keyword || '').trim().toLowerCase();
    if (!q) return true;
    return (fields || []).some((field) => String(field || '').toLowerCase().includes(q));
  }

export function getLobbyBatchSize(gridEl) {
    if (!gridEl) return 6;
    const columns = (window.getComputedStyle(gridEl).gridTemplateColumns || '')
      .split(' ')
      .map((item) => item.trim())
      .filter(Boolean).length;
    return Math.max((columns || 1) * 2, 6);
  }

export function openJoinTeamModal({ teamId, teamTitle, onSuccess }) {
    requireAuth();
    openUtilityModal({
      title: _tr('申请加入队伍'),
      html: _ui`
        <div style="font-size: 14px; color: var(--text-secondary); margin-bottom: 12px;">队伍：${escapeHtml(teamTitle || _tr('未命名队伍'))}</div>
        <textarea id="joinTeamMessage" placeholder="给队长留一句话，例如你的特长、时间安排或想加入的原因"></textarea>
        <div id="joinTeamHint" class="field-hint">提交后会先进入待审批状态，队长同意后会标记为已加入。</div>
        <div class="utility-actions">
          <button id="joinTeamCancelBtn" class="secondary-btn" type="button">取消</button>
          <button id="joinTeamSubmitBtn" class="primary-btn" type="button">提交申请</button>
        </div>
      `,
      onOpen(modal, close) {
        const hint = modal.querySelector('#joinTeamHint');
        const cancelBtn = modal.querySelector('#joinTeamCancelBtn');
        const submitBtn = modal.querySelector('#joinTeamSubmitBtn');
        if (cancelBtn) cancelBtn.addEventListener('click', close);
        if (submitBtn) {
          submitBtn.addEventListener('click', async () => {
            const message = (modal.querySelector('#joinTeamMessage')?.value || '').trim();
            try {
              submitBtn.disabled = true;
              await apiFetch(`/teams/${teamId}/join`, { method: 'POST', body: JSON.stringify({ message }) });
              close();
              showToast(_ui`您已申请加入“${teamTitle || _tr('该队伍')}”，请等待队长审核。`);
              if (typeof onSuccess === 'function') window.setTimeout(onSuccess, 900);
            } catch (err) {
              if (String(err.message || '').includes('已提交过申请')) {
                if (hint) hint.textContent = _tr('你已经提交过申请，请等待队长审核。');
              } else {
                if (hint) hint.textContent = err.message || _tr('提交失败');
              }
              submitBtn.disabled = false;
            }
          });
        }
      },
    });
  }

export function openTeamComposer({ activityId = null, activityTitle = '', defaultTitle = '', onCreated }) {
    requireAuth();
    openUtilityModal({
      title: activityId ? _tr('为活动创建队伍') : _tr('发起新的组队'),
      html: _ui`
        ${activityTitle ? _ui`<div style="font-size: 14px; color: var(--text-secondary); margin-bottom: 12px;">关联活动：${escapeHtml(activityTitle)}</div>` : ''}
        <input id="teamComposerTitle" type="text" placeholder="队伍名称" value="${escapeHtml(defaultTitle || '')}">
        <textarea id="teamComposerDesc" placeholder="描述一下队伍目标、希望招募的成员和时间安排"></textarea>
        <input id="teamComposerMax" type="number" min="0" placeholder="队伍人数上限（选填）">
        <div id="teamComposerHint" class="field-hint"></div>
        <div class="utility-actions">
          <button id="teamComposerCancelBtn" class="secondary-btn" type="button">取消</button>
          <button id="teamComposerSubmitBtn" class="primary-btn" type="button">创建队伍</button>
        </div>
      `,
      onOpen(modal, close) {
        const hint = modal.querySelector('#teamComposerHint');
        const cancelBtn = modal.querySelector('#teamComposerCancelBtn');
        const submitBtn = modal.querySelector('#teamComposerSubmitBtn');
        if (cancelBtn) cancelBtn.addEventListener('click', close);
        if (submitBtn) {
          submitBtn.addEventListener('click', async () => {
            const title = (modal.querySelector('#teamComposerTitle')?.value || '').trim();
            const description = (modal.querySelector('#teamComposerDesc')?.value || '').trim();
            const rawMax = (modal.querySelector('#teamComposerMax')?.value || '').trim();
            const maxMembers = rawMax ? Number(rawMax) : 0;
            try {
              submitBtn.disabled = true;
              const res = await apiFetch('/teams', {
                method: 'POST',
                body: JSON.stringify({ activityId, title, description, maxMembers }),
              });
              close();
              if (typeof onCreated === 'function') onCreated(res);
            } catch (err) {
              if (hint) hint.textContent = err.message || _tr('创建失败');
              submitBtn.disabled = false;
            }
          });
        }
      },
    });
  }

export function openUserSearchModal({ onSelect }) {
    requireAuth();
    openUtilityModal({
      title: _tr('发起私聊'),
      maxWidth: 620,
      html: _ui`
        <div style="display:flex; gap: 10px;">
          <input id="userSearchInput" type="text" placeholder="输入昵称、学号、账号或学院">
          <button id="userSearchBtn" class="primary-btn" type="button" style="width:auto; padding: 12px 16px;">搜索</button>
        </div>
        <div class="field-hint">可直接搜索同学昵称、学号或账号，点击后进入私聊。</div>
        <div id="userSearchHint" class="field-hint"></div>
        <div id="userSearchResults" style="margin-top: 12px;"></div>
      `,
      onOpen(modal, close) {
        const input = modal.querySelector('#userSearchInput');
        const btn = modal.querySelector('#userSearchBtn');
        const hint = modal.querySelector('#userSearchHint');
        const results = modal.querySelector('#userSearchResults');
        const render = (items) => {
          if (!results) return;
          if (!items || items.length === 0) {
            results.innerHTML = renderEmptyCard(_tr('没有找到匹配的同学，试试昵称、学号或账号关键字。'));
            return;
          }
          results.innerHTML = items.map((user) => {
            const avatar = user.avatarUrl || 'assets/images/avatar.svg';
            return _ui`
              <div class="user-search-result">
                <div class="user-search-avatar"><img src="${escapeHtml(avatar)}" alt="avatar"></div>
                <div class="user-search-main">
                  <div class="user-search-name">${escapeHtml(user.nickname)}</div>
                  <div class="user-search-sub">${escapeHtml(userSubtitle(user) || _tr('校园用户'))}</div>
                </div>
                <button class="secondary-btn user-search-select" type="button" data-user-id="${user.id}" style="width:auto; padding: 10px 12px; font-size: 14px;">发起私聊</button>
              </div>
            `;
          }).join('');
          qsa('.user-search-select').forEach((el) => {
            el.addEventListener('click', () => {
              const id = Number(el.getAttribute('data-user-id') || '0');
              if (!id) return;
              close();
              if (typeof onSelect === 'function') onSelect(id);
            });
          });
        };
        const run = async () => {
          const keyword = (input?.value || '').trim();
          try {
            if (hint) hint.textContent = _tr('搜索中...');
            const list = await apiFetch(`/users/search?q=${encodeURIComponent(keyword)}&size=12`);
            if (hint) hint.textContent = keyword ? _ui`共找到 ${list.length} 位同学` : _tr('展示最近活跃用户');
            render(list || []);
          } catch (err) {
            if (hint) hint.textContent = err.message || _tr('搜索失败');
          }
        };
        if (btn) btn.addEventListener('click', run);
        if (input) {
          input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') run();
          });
        }
        run();
      },
    });
  }
