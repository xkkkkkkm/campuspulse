import { contentField } from '../core/content-i18n.js';
import { mountPageNavigator } from '../core/array-pagination.js';
import { getQueryParam } from '../core/navigation.js';
import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { qs, escapeHtml, formatDateTime, markNotificationRead, openNotificationDetailModal, openUserSearchModal } from './../core/ui.js';
import { withFrom } from './../core/navigation.js';
import { apiFetch } from './../core/api.js';
import { requireAuth } from './../core/auth.js';
import { mountBottomNavActive, formatTeamMemberLimit } from './../core/render.js';

export async function initMessages() {
    mountBottomNavActive();
    requireAuth();
    const page = Math.max(1, Number(getQueryParam('page') || 1));
    const dmList = qs('#dmThreadList');
    const groupList = qs('#groupChatList');
    const notifList = qs('#notifPreviewList');
    const startDmBtn = qs('#startDmBtn');
    const moreBtn = qs('#notifMoreBtn');

    const [threads, regs, activityChats, teamChats, notifPage] = await Promise.all([
      apiFetch(`/dm/threads?page=${page}&size=20`),
      apiFetch(`/profile/registrations?page=${page}&size=20`),
      apiFetch(`/profile/activity-chats?page=${page}&size=20`),
      apiFetch(`/profile/team-chats?page=${page}&size=20`),
      apiFetch('/notifications?status=all&page=1&size=4'),
    ]);

    mountPageNavigator(qs('main').lastElementChild, { page, count: threads.length + activityChats.length + teamChats.length,
      hasMore: [threads, regs, activityChats, teamChats].some(items => items.length === 20),
      onChange: next => { location.href = `messages.html?page=${next}`; }
    });

    if (dmList) {
      const threadPeerIds = new Set((threads || []).map((item) => String(item.peerId)));
      const activityContacts = [];
      (regs || []).forEach((r) => {
        const status = String(r.status || '').toUpperCase();
        const act = r.activity || {};
        if (!['APPLIED', 'APPROVED'].includes(status) || !act.organizerId) return;
        if (threadPeerIds.has(String(act.organizerId))) return;
        activityContacts.push({
          peerId: act.organizerId,
          peerNickname: act.organizerName || _tr('活动发布人'),
          peerAvatarUrl: '',
          lastMessage: _ui`来自活动「${contentField(act, 'title') || _tr('未命名活动')}」的联系入口`,
          lastCreatedAt: r.createdAt || '',
          unreadCount: 0,
          sourceLabel: _ui`活动联系 · ${contentField(act, 'title') || _tr('未命名活动')}`,
        });
        threadPeerIds.add(String(act.organizerId));
      });
      const mergedPrivateChats = [...(threads || []), ...activityContacts];
      dmList.innerHTML = mergedPrivateChats.map((t) => {
        const unread = t.unreadCount > 0 ? `<span style="background: var(--danger-color); color: white; font-size: 12px; padding: 2px 8px; border-radius: 999px;">${t.unreadCount}</span>` : '';
        const avatar = t.peerAvatarUrl || 'assets/images/avatar.svg';
        const sub = t.sourceLabel || t.lastMessage || _tr('点击进入私聊');
        return `
          <a href="${withFrom(`chat.html?userId=${encodeURIComponent(t.peerId)}`)}" class="menu-item" style="padding: 18px 24px; align-items: flex-start; gap: 14px;">
            <div style="width: 44px; height: 44px; border-radius: 16px; overflow:hidden; flex-shrink: 0; border: 1px solid rgba(0,0,0,0.05); background: rgba(0, 113, 227, 0.08);">
              ${t.peerAvatarUrl ? `<img src="${escapeHtml(avatar)}" alt="avatar" style="width: 44px; height: 44px; object-fit: cover;">` : `<div style="width:44px; height:44px; display:flex; align-items:center; justify-content:center; color: var(--accent-color);"><i class="fa-solid fa-user"></i></div>`}
            </div>
            <div style="flex-grow: 1; min-width: 0;">
              <div style="display: flex; justify-content: space-between; align-items: center; gap: 12px;">
                <div style="font-size: 16px; font-weight: 650; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${escapeHtml(t.peerNickname)} ${unread}</div>
                <div style="color: var(--text-secondary); font-size: 12px;">${escapeHtml(t.lastCreatedAt ? formatDateTime(t.lastCreatedAt) : '')}</div>
              </div>
              <div style="color: var(--text-secondary); font-size: 14px; margin-top: 6px; line-height: 1.4; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${escapeHtml(sub)}</div>
            </div>
          </a>
        `;
      }).join('') || _ui`<div style="padding: 18px 24px; color: var(--text-secondary);">暂无私聊</div>`;
    }

    if (groupList) {
      const mergedGroupChats = [
        ...((activityChats || []).map((item) => ({
          kind: 'activity',
          id: item.id,
          href: withFrom(`chat.html?activityId=${encodeURIComponent(item.id)}`),
          title: contentField(item, 'title'),
          meta: _ui`活动群聊 · ${formatDateTime(item.startTime)}`,
          desc: _tr('与已经通过报名的同学一起沟通活动安排。'),
          time: item.startTime || '',
        }))),
        ...((teamChats || []).map((item) => ({
          kind: 'team',
          id: item.id,
          href: withFrom(`chat.html?teamId=${encodeURIComponent(item.id)}`),
          title: contentField(item, 'title'),
          meta: _ui`队伍群聊 · ${formatTeamMemberLimit(item.members, item.maxMembers)}`,
          desc: '',
          time: '',
        }))),
      ];
      groupList.innerHTML = mergedGroupChats.map((item) => `
        <a href="${item.href}" class="menu-item" style="padding: 18px 24px; align-items: flex-start; gap: 14px;">
          <div style="width: 44px; height: 44px; border-radius: 16px; background: rgba(0, 113, 227, 0.10); color: var(--accent-color); display: flex; align-items: center; justify-content: center; font-size: 18px; flex-shrink: 0;">
            <i class="fa-solid ${item.kind === 'activity' ? 'fa-comments' : 'fa-user-group'}"></i>
          </div>
          <div style="flex-grow: 1; min-width: 0;">
            <div style="display:flex; justify-content: space-between; align-items:center; gap:12px;">
              <div style="font-size:16px; font-weight:650; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${escapeHtml(contentField(item, 'title'))}</div>
              <div style="color: var(--text-secondary); font-size: 12px;">${escapeHtml(item.time ? formatDateTime(item.time) : '')}</div>
            </div>
            <div style="color: var(--text-secondary); font-size: 13px; margin-top: 6px;">${escapeHtml(item.meta)}</div>
            ${item.desc ? `<div style="color: var(--text-secondary); font-size: 14px; margin-top: 4px; line-height: 1.4;">${escapeHtml(item.desc)}</div>` : ''}
          </div>
        </a>
      `).join('') || _ui`<div style="padding: 18px 24px; color: var(--text-secondary);">暂无可进入的群聊</div>`;
    }

    if (notifList) {
      const notifications = (notifPage && notifPage.items) || [];
      const renderPreview = () => {
        notifList.innerHTML = notifications.map((n) => `
          <div class="menu-item" data-notif-id="${n.id}" style="padding: 18px 24px; align-items: flex-start; gap: 14px; cursor: pointer; ${n.read ? '' : 'background: rgba(0, 113, 227, 0.03);'}">
            <div style="width: 44px; height: 44px; border-radius: 16px; background: rgba(0, 113, 227, 0.10); color: var(--accent-color); display: flex; align-items: center; justify-content: center; font-size: 18px; flex-shrink: 0;">
              <i class="fa-regular fa-bell"></i>
            </div>
            <div style="flex-grow: 1; min-width: 0;">
              <div style="display: flex; justify-content: space-between; align-items: center; gap: 12px;">
                <div style="font-size: 16px; font-weight: 650; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${escapeHtml(contentField(n, 'title'))}</div>
                <div style="color: var(--text-secondary); font-size: 12px;">${escapeHtml(formatDateTime(n.createdAt))}</div>
              </div>
              <div style="color: var(--text-secondary); font-size: 14px; margin-top: 6px; line-height: 1.4; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${escapeHtml(n.content || '')}</div>
            </div>
            ${n.read ? '' : '<span style="width:10px; height:10px; border-radius:999px; background: var(--accent-color); flex-shrink:0; margin-top: 8px;"></span>'}
          </div>
        `).join('') || _ui`<div style="padding: 18px 24px; color: var(--text-secondary);">暂无通知</div>`;
        Array.from(notifList.querySelectorAll('[data-notif-id]')).forEach((el) => {
          el.addEventListener('click', async () => {
            const current = notifications.find((item) => String(item.id) === String(el.dataset.notifId || ''));
            if (!current) return;
            await markNotificationRead(current);
            renderPreview();
            openNotificationDetailModal(current);
          });
        });
      };
      renderPreview();
    }

    if (moreBtn) {
      moreBtn.addEventListener('click', () => {
        window.location.href = withFrom('notifications.html');
      });
    }
    if (startDmBtn) {
      startDmBtn.addEventListener('click', () => {
        openUserSearchModal({
          onSelect: (userId) => {
            window.location.href = withFrom(`chat.html?userId=${encodeURIComponent(userId)}`);
          },
        });
      });
    }
  }
