import { contentField } from '../core/content-i18n.js';
import { loadArrayPage } from '../core/array-pagination.js';
import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { qs, escapeHtml } from './../core/ui.js';
import { getQueryParam, withFrom, applyBackLink } from './../core/navigation.js';
import { apiFetch } from './../core/api.js';
import { requireAuth } from './../core/auth.js';
import { mountBottomNavActive, formatTeamMemberLimit, profileActivityStatusBadge, profileTeamStatusBadge, profileListItem, profileInlineActionButton, handleProfileInlineAction } from './../core/render.js';

export async function initProfileRecords() {
    mountBottomNavActive();
    requireAuth();
    applyBackLink('#profileRecordsBack', 'profile.html');
    const kind = String(getQueryParam('kind') || 'activities');
    const titleEl = qs('#profileRecordsTitle');
    const hintEl = qs('#profileRecordsHint');
    const listEl = qs('#profileRecordsList');
    const emptyEl = qs('#profileRecordsEmpty');

    const configs = {
      favorites: {
        title: _tr('我的收藏'), hint: _tr('查看全部收藏记录'),
        load: () => loadArrayPage('/profile/favorites', listEl, renderList),
        render: a => profileListItem({ href: withFrom(`activity-detail.html?id=${a.id}`), icon: 'fa-regular fa-bookmark', title: contentField(a, 'title') })
      },
      activities: {
        title: _tr('我参加的活动'),
        hint: _tr('这里展示你已报名或已参加过的活动记录。'),
        load: () => loadArrayPage('/profile/registrations', listEl, renderList),
        render: (r) => {
          const status = String(r.status || '').toUpperCase();
          return profileListItem({
            href: withFrom(`activity-detail.html?id=${encodeURIComponent(r.activity.id)}`, 'profile-records.html'),
            icon: 'fa-solid fa-calendar-check',
            title: contentField(r.activity, 'title'),
            badge: profileActivityStatusBadge(r.status),
            actionHtml: (status === 'APPLIED' || status === 'PENDING')
              ? profileInlineActionButton(_tr('撤回报名'), 'cancel-activity-registration', r.activity.id)
              : '',
          });
        },
      },
      published: {
        title: _tr('我发布的活动'),
        hint: _tr('这里集中进入你发布活动的管理页。'),
        load: () => loadArrayPage('/profile/published', listEl, renderList),
        render: (a) => _ui`<a class="menu-item" href="${withFrom(`activity-manage.html?id=${encodeURIComponent(a.id)}`, 'profile-records.html')}"><div class="menu-item-left"><i class="fa-solid fa-bullhorn"></i>${escapeHtml(contentField(a, 'title'))}</div><span style="color: var(--text-secondary); font-size: 12px;">进入管理</span></a>`,
      },
      'teams-joined': {
        title: _tr('我参加的组队'),
        hint: _tr('已加入的队伍可进入聊天，待审批的申请会先展示状态。'),
        load: () => loadArrayPage('/profile/teams-joined', listEl, renderList),
        render: (t) => {
          const pending = String(t.participationStatus || '').toUpperCase() === 'PENDING';
          const href = pending
            ? withFrom(`team-lobby.html?teamId=${encodeURIComponent(t.id)}`, 'profile-records.html')
            : withFrom(`chat.html?teamId=${encodeURIComponent(t.id)}`, 'profile-records.html');
          return profileListItem({
            href,
            icon: 'fa-solid fa-user-group',
            title: contentField(t, 'title'),
            sub: formatTeamMemberLimit(t.members, t.maxMembers),
            badge: profileTeamStatusBadge(t),
            actionHtml: pending ? profileInlineActionButton(_tr('取消申请'), 'cancel-team-request', t.id) : '',
          });
        },
      },
      'teams-created': {
        title: _tr('我发起的组队'),
        hint: _tr('这里只进入你创建队伍的管理页。'),
        load: () => loadArrayPage('/profile/teams-created', listEl, renderList),
        render: (t) => _ui`<a class="menu-item" href="${withFrom(`team-success.html?teamId=${encodeURIComponent(t.id)}`, 'profile-records.html')}"><div class="menu-item-left"><i class="fa-solid fa-user-group"></i>${escapeHtml(contentField(t, 'title'))}</div><span style="color: var(--text-secondary); font-size: 12px;">进入管理</span></a>`,
      },
    };
    const config = configs[kind] || configs.activities;
    if (titleEl) titleEl.textContent = config.title;
    if (hintEl) hintEl.textContent = config.hint;
    const renderList = async () => {
      const items = await config.load();
      if (listEl) listEl.innerHTML = (items || []).map(config.render).join('');
      if (emptyEl) emptyEl.style.display = items && items.length ? 'none' : 'block';
    };
    if (listEl) {
      listEl.addEventListener('click', async (e) => {
        const btn = e.target.closest('.profile-inline-action');
        if (!btn) return;
        e.preventDefault();
        e.stopPropagation();
        await handleProfileInlineAction(btn, renderList);
      });
    }
    await renderList();
  }
