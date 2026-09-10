import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { qs, qsa, escapeHtml, showMessage, formatDateTime, notificationTypeLabel, markNotificationRead, openNotificationDetailModal } from './../core/ui.js';
import { apiFetch } from './../core/api.js';
import { requireAuth } from './../core/auth.js';
import { mountBottomNavActive } from './../core/render.js';

export async function initNotifications() {
    mountBottomNavActive();
    requireAuth();
    const listEl = qs('#notificationsPageList');
    const emptyEl = qs('#notificationsPageEmpty');
    const pageMeta = qs('#notificationsPageMeta');
    const firstBtn = qs('#notificationsFirstBtn');
    const prevBtn = qs('#notificationsPrevBtn');
    const nextBtn = qs('#notificationsNextBtn');
    const jumpInput = qs('#notificationsJumpInput');
    const jumpBtn = qs('#notificationsJumpBtn');
    const readAllBtn = qs('#notificationsReadAllBtn');
    const filterBtns = qsa('[data-notif-filter]');
    let currentStatus = 'all';
    let currentPage = 1;
    let pageSize = 10;
    let total = 0;
    let items = [];

    const setFilterActive = () => {
      filterBtns.forEach((btn) => {
        const active = btn.dataset.notifFilter === currentStatus;
        btn.classList.toggle('active', active);
        btn.style.background = active ? 'var(--accent-color)' : 'rgba(0,0,0,0.04)';
        btn.style.color = active ? '#fff' : 'var(--text-primary)';
      });
    };

    const loadNotifications = async () => {
      const data = await apiFetch(`/notifications?status=${encodeURIComponent(currentStatus)}&page=${currentPage}&size=${pageSize}`);
      items = data.items || [];
      total = Number(data.total || 0);
      pageSize = Number(data.size || pageSize);
      const pages = Math.max(1, Math.ceil(total / pageSize));
      if (pageMeta) pageMeta.textContent = _ui`第 ${currentPage} / ${pages} 页 · 共 ${total} 条`;
      if (jumpInput) {
        jumpInput.max = String(pages);
        jumpInput.value = '';
      }
      if (firstBtn) firstBtn.disabled = currentPage <= 1;
      if (prevBtn) prevBtn.disabled = currentPage <= 1;
      if (nextBtn) nextBtn.disabled = currentPage >= pages;
      if (emptyEl) emptyEl.style.display = items.length ? 'none' : 'block';
      if (!listEl) return;
      listEl.innerHTML = items.map((n) => _ui`
        <div class="stack-card" data-notif-card="${n.id}" style="padding: 18px; cursor: pointer; ${n.read ? '' : 'border-color: rgba(0,113,227,0.18); background: rgba(0,113,227,0.03);'}">
          <div style="display:flex; justify-content:space-between; gap:16px; align-items:flex-start;">
            <div style="min-width:0; flex:1;">
              <div style="display:flex; align-items:center; gap:10px; flex-wrap:wrap;">
                <div style="font-size:18px; font-weight:700; min-width:0;">${escapeHtml(n.title)}</div>
                <span class="badge-pill ${n.read ? '' : 'badge-approved'}" style="padding:6px 10px;">${n.read ? _tr('已读') : _tr('未读')}</span>
                <span class="stack-card-meta">${escapeHtml(notificationTypeLabel(n.type))}</span>
              </div>
              <div class="stack-card-meta" style="margin-top:8px;">${escapeHtml(formatDateTime(n.createdAt))}</div>
              <div style="font-size:14px; color:var(--text-secondary); line-height:1.7; margin-top:10px;">${escapeHtml(n.content || '')}</div>
            </div>
            <button type="button" class="secondary-btn js-notif-delete" data-notif-id="${n.id}" style="width:auto; padding:10px 14px; border-radius:14px;">删除</button>
          </div>
        </div>
      `).join('');

      Array.from(listEl.querySelectorAll('[data-notif-card]')).forEach((el) => {
        el.addEventListener('click', async (ev) => {
          if (ev.target.closest('.js-notif-delete')) return;
          const item = items.find((entry) => String(entry.id) === String(el.dataset.notifCard || ''));
          if (!item) return;
          await markNotificationRead(item);
          openNotificationDetailModal(item);
          loadNotifications();
        });
      });
      Array.from(listEl.querySelectorAll('.js-notif-delete')).forEach((btn) => {
        btn.addEventListener('click', async (ev) => {
          ev.preventDefault();
          ev.stopPropagation();
          try {
            await apiFetch(`/notifications/${encodeURIComponent(btn.dataset.notifId || '')}`, { method: 'DELETE' });
            if (items.length === 1 && currentPage > 1) currentPage -= 1;
            await loadNotifications();
          } catch (err) {
            showMessage(err.message || _tr('删除失败'));
          }
        });
      });
    };

    filterBtns.forEach((btn) => {
      btn.addEventListener('click', () => {
        currentStatus = btn.dataset.notifFilter || 'all';
        currentPage = 1;
        setFilterActive();
        loadNotifications();
      });
    });
    if (prevBtn) {
      prevBtn.addEventListener('click', () => {
        if (currentPage <= 1) return;
        currentPage -= 1;
        loadNotifications();
      });
    }
    if (firstBtn) {
      firstBtn.addEventListener('click', () => {
        if (currentPage <= 1) return;
        currentPage = 1;
        loadNotifications();
      });
    }
    if (nextBtn) {
      nextBtn.addEventListener('click', () => {
        if (currentPage >= Math.max(1, Math.ceil(total / pageSize))) return;
        currentPage += 1;
        loadNotifications();
      });
    }
    const jumpToPage = () => {
      const pages = Math.max(1, Math.ceil(total / pageSize));
      const target = Number((jumpInput?.value || '').trim());
      if (!Number.isInteger(target) || target < 1 || target > pages) {
        showMessage(_ui`请输入 1 到 ${pages} 之间的页码`);
        return;
      }
      if (target === currentPage) return;
      currentPage = target;
      loadNotifications();
    };
    if (jumpBtn) jumpBtn.addEventListener('click', jumpToPage);
    if (jumpInput) {
      jumpInput.addEventListener('keydown', (ev) => {
        if (ev.key === 'Enter') jumpToPage();
      });
    }
    if (readAllBtn) {
      readAllBtn.addEventListener('click', async () => {
        try {
          await apiFetch('/notifications/read-all', { method: 'POST', body: '{}' });
          await loadNotifications();
        } catch (err) {
          showMessage(err.message || _tr('操作失败'));
        }
      });
    }
    setFilterActive();
    await loadNotifications();
  }
