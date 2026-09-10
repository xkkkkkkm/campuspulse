import { contentField, tagLabel } from '../core/content-i18n.js';
import { loadArrayPage } from '../core/array-pagination.js';
import { mountAdminTools } from '../core/admin-tools.js';
import { mountSupportTickets } from '../core/support-tickets.js';
import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { qs, qsa, escapeHtml, showMessage, formatDateTime, openUtilityModal, renderEmptyCard, userSubtitle } from './../core/ui.js';
import { apiFetch } from './../core/api.js';
import { requireAuth } from './../core/auth.js';
import { renderTags, formatTeamMemberLimit } from './../core/render.js';

export async function initAdmin() {
    requireAuth();
    const me = await apiFetch('/auth/me');
    if (me.role !== 'ADMIN') {
      showMessage(_tr('当前账号没有管理后台权限。'), _tr('无权限'));
      window.location.href = 'profile.html';
      return;
    }

    await mountSupportTickets(qs('main'), true);
    await mountAdminTools(qs('main'));
    const statUsers = qs('#statUsers');
    const statActivities = qs('#statActivities');
    const statPublishedActivities = qs('#statPublishedActivities');
    const statDisabledUsers = qs('#statDisabledUsers');
    const statTeams = qs('#statTeams');
    const statRegistrations = qs('#statRegistrations');
    const statMessages = qs('#statMessages');
    const statFeatured = qs('#statFeatured');
    const activityList = qs('#adminActivityList');
    const activityKeyword = qs('#adminActivityKeyword');
    const activityAudit = qs('#adminActivityAudit');
    const activityLifecycle = qs('#adminActivityLifecycle');
    const userList = qs('#adminUserList');
    const userKeyword = qs('#adminUserKeyword');
    const userRole = qs('#adminUserRole');
    const userStatus = qs('#adminUserStatus');
    const featuredList = qs('#adminFeaturedList');
    const tagList = qs('#adminTagList');
    const refreshBtn = qs('#adminRefreshBtn');
    const addFeaturedBtn = qs('#adminAddFeaturedBtn');
    const addTagBtn = qs('#adminAddTagBtn');
    let activityCache = new Map();
    let userCache = new Map();

    const auditLabel = (value) => {
      if (value === 'APPROVED') return _tr('已通过');
      if (value === 'PENDING') return _tr('待审核');
      if (value === 'REJECTED') return _tr('已拒绝');
      return value || '-';
    };

    const auditBadgeClass = (value) => {
      if (value === 'APPROVED') return 'badge-approved';
      if (value === 'PENDING') return 'badge-pending';
      if (value === 'REJECTED') return 'badge-rejected';
      return '';
    };

    const lifecycleLabel = (value) => {
      if (value === 'PUBLISHED') return _tr('展示中');
      if (value === 'CANCELLED') return _tr('已下架');
      return value || '-';
    };

    const lifecycleBadgeClass = (value) => {
      if (value === 'PUBLISHED') return 'badge-approved';
      if (value === 'CANCELLED') return 'badge-rejected';
      return '';
    };

    const roleLabel = (value) => {
      if (value === 'ADMIN') return _tr('管理员');
      if (value === 'ORGANIZER' || value === 'ORG') return _tr('组织者');
      if (value === 'USER') return _tr('普通用户');
      return value || '-';
    };

    function openActivityDetailModal(item) {
      if (!item) return;
      openUtilityModal({
        title: _tr('活动治理详情'),
        maxWidth: 760,
        html: _ui`
          <div class="stack-card" style="box-shadow:none; border:none; padding:0;">
            <div class="stack-card-head">
              <div>
                <div class="stack-card-title">${escapeHtml(contentField(item, 'title'))}</div>
                <div class="stack-card-meta">发布人：${escapeHtml(item.organizerName)} · ${escapeHtml(formatDateTime(item.startTime))} · ${escapeHtml(contentField(item, 'location'))}</div>
              </div>
              <div style="display:flex; gap:8px; flex-wrap:wrap; justify-content:flex-end;">
                <span class="badge-pill ${auditBadgeClass(item.auditStatus)}">${escapeHtml(auditLabel(item.auditStatus))}</span>
                <span class="badge-pill ${lifecycleBadgeClass(item.status)}">${escapeHtml(lifecycleLabel(item.status))}</span>
                ${item.featuredWeight != null ? _ui`<span class="badge-pill">精选 ${item.featuredWeight}</span>` : ''}
              </div>
            </div>
            <div style="display:grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-top: 14px;">
              <div class="stack-card" style="padding: 14px; box-shadow:none;">
                <div class="stat-label">报名人数</div>
                <div style="font-size: 24px; font-weight: 800;">${item.registeredCount || 0}</div>
              </div>
              <div class="stack-card" style="padding: 14px; box-shadow:none;">
                <div class="stat-label">收藏人数</div>
                <div style="font-size: 24px; font-weight: 800;">${item.favoriteCount || 0}</div>
              </div>
              <div class="stack-card" style="padding: 14px; box-shadow:none;">
                <div class="stat-label">关联队伍</div>
                <div style="font-size: 24px; font-weight: 800;">${item.teamCount || 0}</div>
              </div>
              <div class="stack-card" style="padding: 14px; box-shadow:none;">
                <div class="stat-label">活动 ID</div>
                <div style="font-size: 24px; font-weight: 800;">${item.id}</div>
              </div>
            </div>
            <div style="margin-top: 16px;">
              <div class="stack-card-meta">标签</div>
              <div class="tags" style="margin-top: 8px;">${renderTags(contentField(item, 'tags') || []) || _static('<span>暂无标签</span>')}</div>
            </div>
            <div class="stack-card-actions" style="margin-top: 18px;">
              <a href="activity-detail.html?id=${encodeURIComponent(item.id)}" class="secondary-btn">查看用户端详情</a>
              <a href="chat.html?userId=${encodeURIComponent(item.organizerId)}" class="secondary-btn">联系发布者</a>
            </div>
          </div>
        `,
      });
    }

    function openFeaturedModal({ activityId = 0, activityTitle = '', weight = '' } = {}) {
      openUtilityModal({
        title: activityId ? _tr('调整精选活动') : _tr('新增精选活动'),
        html: _ui`
          ${activityTitle ? _ui`<div style="font-size: 14px; color: var(--text-secondary); margin-bottom: 12px;">活动：${escapeHtml(activityTitle)}</div>` : ''}
          <input id="featuredActivityId" type="number" placeholder="活动 ID" value="${activityId || ''}">
          <input id="featuredWeight" type="number" placeholder="推荐权重，例如 80" value="${weight !== '' ? escapeHtml(weight) : ''}">
          <div id="featuredHint" class="field-hint">权重越高，越容易在首页和推荐流靠前展示。</div>
          <div class="utility-actions">
            <button id="featuredCancelBtn" class="secondary-btn" type="button">取消</button>
            <button id="featuredSubmitBtn" class="primary-btn" type="button">保存</button>
          </div>
        `,
        onOpen(modal, close) {
          const hint = modal.querySelector('#featuredHint');
          modal.querySelector('#featuredCancelBtn')?.addEventListener('click', close);
          modal.querySelector('#featuredSubmitBtn')?.addEventListener('click', async () => {
            const nextActivityId = Number(modal.querySelector('#featuredActivityId')?.value || '0');
            const nextWeight = Number(modal.querySelector('#featuredWeight')?.value || '0');
            try {
              await apiFetch('/admin/featured', {
                method: 'POST',
                body: JSON.stringify({ activityId: nextActivityId, weight: nextWeight }),
              });
              close();
              await Promise.all([loadFeatured(), loadActivities(), loadStats()]);
            } catch (err) {
        if (err.name === 'AbortError') return;
              if (hint) hint.textContent = err.message || _tr('保存失败');
            }
          });
        },
      });
    }

    async function openUserDetailModal(id) {
      const detail = await apiFetch(`/admin/users/${id}/detail`);
      const published = Array.isArray(detail.publishedActivities) ? detail.publishedActivities : [];
      const teams = Array.isArray(detail.teams) ? detail.teams : [];
      const regs = Array.isArray(detail.registrations) ? detail.registrations : [];
      openUtilityModal({
        title: _tr('用户治理详情'),
        maxWidth: 820,
        html: _ui`
          <div class="stack-card" style="box-shadow:none; border:none; padding:0;">
            <div class="stack-card-head">
              <div>
                <div class="stack-card-title">${escapeHtml(detail.nickname)}</div>
                <div class="stack-card-meta">${escapeHtml(userSubtitle(detail) || detail.username)} · ${escapeHtml(roleLabel(detail.role))} · ${escapeHtml(formatDateTime(detail.createdAt))}</div>
              </div>
              <span class="badge-pill ${detail.enabled ? 'badge-approved' : 'badge-rejected'}">${detail.enabled ? _tr('启用中') : _tr('已停用')}</span>
            </div>
            <div style="display:grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-top: 14px;">
              <div class="stack-card" style="padding: 14px; box-shadow:none;">
                <div class="stat-label">发布活动</div>
                <div style="font-size: 24px; font-weight: 800;">${detail.publishedCount || 0}</div>
              </div>
              <div class="stack-card" style="padding: 14px; box-shadow:none;">
                <div class="stat-label">报名记录</div>
                <div style="font-size: 24px; font-weight: 800;">${detail.registrationCount || 0}</div>
              </div>
              <div class="stack-card" style="padding: 14px; box-shadow:none;">
                <div class="stat-label">在队伍中</div>
                <div style="font-size: 24px; font-weight: 800;">${detail.teamCount || 0}</div>
              </div>
            </div>
            <div style="display:grid; gap: 16px; margin-top: 18px;">
              <div>
                <div class="stack-card-title" style="font-size:16px;">该用户发布的活动</div>
                <div class="stack-list" style="margin-top: 10px;">
                  ${published.length > 0 ? published.map((item) => _ui`
                    <div class="stack-card" style="padding: 14px;">
                      <div class="stack-card-head">
                        <div>
                          <div class="stack-card-title" style="font-size:16px;">${escapeHtml(contentField(item, 'title'))}</div>
                          <div class="stack-card-meta">${escapeHtml(contentField(item, 'location'))} · ${escapeHtml(formatDateTime(item.startTime))} · 报名 ${item.registeredCount}</div>
                        </div>
                        <div style="display:flex; gap:8px; flex-wrap:wrap; justify-content:flex-end;">
                          <span class="badge-pill ${lifecycleBadgeClass(item.status)}">${escapeHtml(lifecycleLabel(item.status))}</span>
                          <span class="badge-pill ${auditBadgeClass(item.auditStatus)}">${escapeHtml(auditLabel(item.auditStatus))}</span>
                        </div>
                      </div>
                      <div class="stack-card-actions" style="margin-top: 10px;">
                        <a class="secondary-btn" href="activity-detail.html?id=${encodeURIComponent(item.id)}">查看详情</a>
                      </div>
                    </div>
                  `).join('') : renderEmptyCard(_tr('该用户暂时没有发布活动。'))}
                </div>
              </div>
              <div>
                <div class="stack-card-title" style="font-size:16px;">队伍参与</div>
                <div class="stack-list" style="margin-top: 10px;">
                  ${teams.length > 0 ? teams.map((team) => `
                    <div class="stack-card" style="padding: 14px;">
                      <div class="stack-card-head">
                        <div>
                          <div class="stack-card-title" style="font-size:16px;">${escapeHtml(contentField(team, 'title'))}</div>
                          <div class="stack-card-meta">${escapeHtml(contentField(team, 'activityTitle') || _tr('自由组队'))} · ${escapeHtml(team.memberRole === 'CREATOR' ? _tr('队长') : _tr('成员'))} · ${escapeHtml(formatTeamMemberLimit(team.memberCount, team.maxMembers))}</div>
                        </div>
                        <span class="badge-pill ${lifecycleBadgeClass(team.status === 'OPEN' ? 'PUBLISHED' : 'CANCELLED')}">${escapeHtml(team.status)}</span>
                      </div>
                    </div>
                  `).join('') : renderEmptyCard(_tr('该用户暂时没有队伍参与记录。'))}
                </div>
              </div>
              <div>
                <div class="stack-card-title" style="font-size:16px;">活动报名记录</div>
                <div class="stack-list" style="margin-top: 10px;">
                  ${regs.length > 0 ? regs.map((reg) => `
                    <div class="stack-card" style="padding: 14px;">
                      <div class="stack-card-head">
                        <div>
                          <div class="stack-card-title" style="font-size:16px;">${escapeHtml(contentField(reg, 'activityTitle'))}</div>
                          <div class="stack-card-meta">${escapeHtml(formatDateTime(reg.createdAt))}</div>
                        </div>
                        <span class="badge-pill">${escapeHtml(reg.status)}</span>
                      </div>
                    </div>
                  `).join('') : renderEmptyCard(_tr('该用户暂时没有活动报名记录。'))}
                </div>
              </div>
            </div>
            <div class="stack-card-actions" style="margin-top: 18px;">
              <a href="chat.html?userId=${encodeURIComponent(detail.id)}" class="secondary-btn">联系该用户</a>
            </div>
          </div>
        `,
      });
    }

    async function loadStats() {
      const stats = await apiFetch('/admin/stats');
      if (statUsers) statUsers.textContent = String(stats.userCount || 0);
      if (statActivities) statActivities.textContent = String(stats.activityCount || 0);
      if (statPublishedActivities) statPublishedActivities.textContent = String(stats.publishedActivities || 0);
      if (statDisabledUsers) statDisabledUsers.textContent = String(stats.disabledUsers || 0);
      if (statTeams) statTeams.textContent = String(stats.teamCount || 0);
      if (statRegistrations) statRegistrations.textContent = String(stats.registrationCount || 0);
      if (statMessages) statMessages.textContent = String(stats.messageCount || 0);
      if (statFeatured) statFeatured.textContent = String(stats.featuredActivities || 0);
    }

    async function loadActivities() {
      if (!activityList) return;
      activityList.innerHTML = renderEmptyCard(_tr('正在加载活动列表...'));
      try {
        const keyword = (activityKeyword?.value || '').trim();
        const auditStatus = activityAudit?.value || '';
        const status = activityLifecycle?.value || '';
        const list = await loadArrayPage(`/admin/activities?auditStatus=${encodeURIComponent(auditStatus)}&status=${encodeURIComponent(status)}&keyword=${encodeURIComponent(keyword)}`, activityList, loadActivities);
        activityCache = new Map((list || []).map((item) => [String(item.id), item]));
        if (!list || list.length === 0) {
          activityList.innerHTML = renderEmptyCard(_tr('当前筛选条件下没有活动。'));
          return;
        }
        activityList.innerHTML = list.map((item) => {
          const isPublished = item.status === 'PUBLISHED';
          return _ui`
            <div class="stack-card">
              <div class="stack-card-head">
                <div>
                  <div class="stack-card-title">${escapeHtml(contentField(item, 'title'))}</div>
                  <div class="stack-card-meta">发布人：${escapeHtml(item.organizerName)} · 地点：${escapeHtml(contentField(item, 'location'))} · 开始：${escapeHtml(formatDateTime(item.startTime))}</div>
                </div>
                <div style="display:flex; gap:8px; flex-wrap:wrap; justify-content:flex-end;">
                  <span class="badge-pill ${auditBadgeClass(item.auditStatus)}">${escapeHtml(auditLabel(item.auditStatus))}</span>
                  <span class="badge-pill ${lifecycleBadgeClass(item.status)}">${escapeHtml(lifecycleLabel(item.status))}</span>
                  ${item.featuredWeight != null ? _ui`<span class="badge-pill">精选 ${item.featuredWeight}</span>` : ''}
                </div>
              </div>
              <div class="tags">${renderTags(contentField(item, 'tags') || [])}</div>
              <div style="display:flex; gap:10px; flex-wrap:wrap; margin-top: 12px;">
                <span class="badge-pill">报名 ${item.registeredCount || 0}</span>
                <span class="badge-pill">收藏 ${item.favoriteCount || 0}</span>
                <span class="badge-pill">队伍 ${item.teamCount || 0}</span>
                <span class="badge-pill">ID ${item.id}</span>
              </div>
              <div class="stack-card-meta" style="margin-top: 10px;">创建于 ${escapeHtml(formatDateTime(item.createdAt))}</div>
              <div class="stack-card-actions">
                <button class="secondary-btn admin-activity-detail-btn" type="button" data-id="${item.id}">治理详情</button>
                <a href="chat.html?userId=${encodeURIComponent(item.organizerId)}" class="secondary-btn">联系发布者</a>
                <button class="secondary-btn admin-featured-edit-btn" type="button" data-id="${item.id}" data-title="${escapeHtml(contentField(item, 'title'))}" data-weight="${item.featuredWeight == null ? '' : item.featuredWeight}">设为精选</button>
                <button class="secondary-btn admin-activity-toggle-btn" type="button" data-id="${item.id}" data-status="${isPublished ? 'CANCELLED' : 'PUBLISHED'}">${isPublished ? _tr('下架活动') : _tr('恢复上架')}</button>
                ${item.auditStatus !== 'APPROVED' ? _ui`<button class="secondary-btn admin-audit-btn" type="button" data-id="${item.id}" data-status="APPROVED">审核通过</button>` : ''}
                ${item.auditStatus !== 'REJECTED' ? _ui`<button class="secondary-btn admin-audit-btn" type="button" data-id="${item.id}" data-status="REJECTED">标记拒绝</button>` : ''}
              </div>
            </div>
          `;
        }).join('');
        qsa('.admin-activity-detail-btn').forEach((btn) => {
          btn.addEventListener('click', () => {
            const item = activityCache.get(btn.getAttribute('data-id') || '');
            openActivityDetailModal(item);
          });
        });
        qsa('.admin-featured-edit-btn').forEach((btn) => {
          btn.addEventListener('click', () => {
            openFeaturedModal({
              activityId: Number(btn.getAttribute('data-id') || '0'),
              activityTitle: btn.getAttribute('data-title') || '',
              weight: btn.getAttribute('data-weight') || '',
            });
          });
        });
        qsa('.admin-activity-toggle-btn').forEach((btn) => {
          btn.addEventListener('click', async () => {
            const id = Number(btn.getAttribute('data-id') || '0');
            const status = btn.getAttribute('data-status') || '';
            if (!id || !status) return;
            const reason = window.prompt(_tr('请输入操作原因'));
            if (!reason?.trim()) return;
            await apiFetch(`/admin/activities/${id}/status`, {
              method: 'POST',
              body: JSON.stringify({ status, reason }),
            });
            await Promise.all([loadActivities(), loadStats()]);
          });
        });
        qsa('.admin-audit-btn').forEach((btn) => {
          btn.addEventListener('click', async () => {
            const id = Number(btn.getAttribute('data-id') || '0');
            const auditStatus = btn.getAttribute('data-status') || '';
            if (!id || !auditStatus) return;
            const reason = window.prompt(_tr('请输入操作原因'));
            if (!reason?.trim()) return;
            await apiFetch(`/admin/activities/${id}/audit`, {
              method: 'POST',
              body: JSON.stringify({ auditStatus, reason }),
            });
            await Promise.all([loadActivities(), loadStats()]);
          });
        });
      } catch (err) {
        if (err.name === 'AbortError') return;
        activityList.innerHTML = renderEmptyCard(err.message || _tr('活动列表加载失败'));
      }
    }

    async function loadUsers() {
      if (!userList) return;
      userList.innerHTML = renderEmptyCard(_tr('正在加载用户列表...'));
      try {
        const keyword = (userKeyword?.value || '').trim();
        const role = userRole?.value || '';
        const status = userStatus?.value || '';
        const list = await loadArrayPage(`/admin/users?keyword=${encodeURIComponent(keyword)}&role=${encodeURIComponent(role)}&status=${encodeURIComponent(status)}`, userList, loadUsers);
        userCache = new Map((list || []).map((item) => [String(item.id), item]));
        if (!list || list.length === 0) {
          userList.innerHTML = renderEmptyCard(_tr('没有找到符合条件的用户。'));
          return;
        }
        userList.innerHTML = list.map((user) => _ui`
          <div class="stack-card">
            <div class="stack-card-head">
              <div>
                <div class="stack-card-title">${escapeHtml(user.nickname)}</div>
                <div class="stack-card-meta">${escapeHtml(userSubtitle(user) || user.username)} · 角色：${escapeHtml(roleLabel(user.role))}</div>
              </div>
              <span class="badge-pill ${user.enabled ? 'badge-approved' : 'badge-rejected'}">${user.enabled ? _tr('启用中') : _tr('已停用')}</span>
            </div>
            <div style="display:flex; gap:10px; flex-wrap:wrap; margin-top: 12px;">
              <span class="badge-pill">发布 ${user.publishedCount || 0}</span>
              <span class="badge-pill">报名 ${user.registrationCount || 0}</span>
              <span class="badge-pill">队伍 ${user.teamCount || 0}</span>
            </div>
            <div class="stack-card-meta">创建时间：${escapeHtml(formatDateTime(user.createdAt))}</div>
            <div class="stack-card-actions">
              <button class="secondary-btn admin-user-detail-btn" type="button" data-id="${user.id}">查看详情</button>
              <a href="chat.html?userId=${encodeURIComponent(user.id)}" class="secondary-btn">联系用户</a>
              <button class="secondary-btn admin-user-toggle-btn" type="button" data-id="${user.id}" data-enabled="${user.enabled ? '0' : '1'}">${user.enabled ? _tr('停用账号') : _tr('重新启用')}</button>
            </div>
          </div>
        `).join('');
        qsa('.admin-user-detail-btn').forEach((btn) => {
          btn.addEventListener('click', async () => {
            const id = Number(btn.getAttribute('data-id') || '0');
            if (!id) return;
            await openUserDetailModal(id);
          });
        });
        qsa('.admin-user-toggle-btn').forEach((btn) => {
          btn.addEventListener('click', async () => {
            const id = Number(btn.getAttribute('data-id') || '0');
            const enabled = btn.getAttribute('data-enabled') === '1';
            if (!id) return;
            const reason = window.prompt(_tr('请输入操作原因'));
            if (!reason?.trim()) return;
            await apiFetch(`/admin/users/${id}/status`, {
              method: 'POST',
              body: JSON.stringify({ enabled, reason }),
            });
            await loadUsers();
          });
        });
      } catch (err) {
        if (err.name === 'AbortError') return;
        userList.innerHTML = renderEmptyCard(err.message || _tr('用户列表加载失败'));
      }
    }

    async function loadFeatured() {
      if (!featuredList) return;
      try {
        const list = await loadArrayPage('/admin/featured', featuredList, loadFeatured);
        if (!list || list.length === 0) {
          featuredList.innerHTML = renderEmptyCard(_tr('还没有配置精选活动。'));
          return;
        }
        featuredList.innerHTML = list.map((item) => _ui`
          <div class="stack-card">
            <div class="stack-card-head">
              <div>
                <div class="stack-card-title">${escapeHtml(contentField(item, 'activityTitle'))}</div>
                <div class="stack-card-meta">活动 ID：${item.activityId} · 生效权重 ${item.weight}</div>
              </div>
              <span class="badge-pill">权重 ${item.weight}</span>
            </div>
            <div class="stack-card-meta">时间窗口：${escapeHtml(item.startTime ? formatDateTime(item.startTime) : _tr('长期有效'))} · ${escapeHtml(item.endTime ? formatDateTime(item.endTime) : _tr('未设置结束'))}</div>
            <div class="stack-card-actions">
              <button class="secondary-btn admin-featured-edit-btn" type="button" data-id="${item.activityId}" data-title="${escapeHtml(contentField(item, 'activityTitle'))}" data-weight="${item.weight}">调整权重</button>
              <a class="secondary-btn" href="activity-detail.html?id=${encodeURIComponent(item.activityId)}">查看活动</a>
            </div>
          </div>
        `).join('');
        qsa('.admin-featured-edit-btn').forEach((btn) => {
          btn.addEventListener('click', () => {
            openFeaturedModal({
              activityId: Number(btn.getAttribute('data-id') || '0'),
              activityTitle: btn.getAttribute('data-title') || '',
              weight: btn.getAttribute('data-weight') || '',
            });
          });
        });
      } catch (err) {
        if (err.name === 'AbortError') return;
        featuredList.innerHTML = renderEmptyCard(err.message || _tr('精选活动加载失败'));
      }
    }

    async function loadTags() {
      if (!tagList) return;
      try {
        const list = await loadArrayPage('/admin/tags', tagList, loadTags);
        if (!list || list.length === 0) {
          tagList.innerHTML = _static('<div class="field-hint">暂无标签</div>');
          return;
        }
        tagList.innerHTML = list.map((tag) => `<span class="badge-pill">${escapeHtml(tagLabel(tag))} · ${escapeHtml(tag.type)}</span>`).join('');
      } catch (err) {
        if (err.name === 'AbortError') return;
        tagList.innerHTML = `<div class="field-hint">${escapeHtml(err.message || _tr('标签加载失败'))}</div>`;
      }
    }

    function bindSearch(input, run) {
      if (!input) return;
      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') run();
      });
    }

    if (activityAudit) activityAudit.addEventListener('change', loadActivities);
    if (activityLifecycle) activityLifecycle.addEventListener('change', loadActivities);
    if (userRole) userRole.addEventListener('change', loadUsers);
    if (userStatus) userStatus.addEventListener('change', loadUsers);
    bindSearch(activityKeyword, loadActivities);
    bindSearch(userKeyword, loadUsers);

    if (refreshBtn) {
      refreshBtn.addEventListener('click', async () => {
        await Promise.all([loadStats(), loadActivities(), loadUsers(), loadFeatured(), loadTags()]);
      });
    }

    if (addFeaturedBtn) addFeaturedBtn.addEventListener('click', () => openFeaturedModal());

    if (addTagBtn) {
      addTagBtn.addEventListener('click', () => {
        openUtilityModal({
          title: _tr('新增标签'),
          html: _ui`
            <input id="adminTagName" type="text" placeholder="标签名称">
            <select id="adminTagType">
              <option value="HOME">HOME</option>
              <option value="CATEGORY">CATEGORY</option>
              <option value="OTHER">OTHER</option>
            </select>
            <div id="adminTagHint" class="field-hint">建议保持命名简洁，便于首页与活动表单复用。</div>
            <div class="utility-actions">
              <button id="adminTagCancelBtn" class="secondary-btn" type="button">取消</button>
              <button id="adminTagSubmitBtn" class="primary-btn" type="button">创建</button>
            </div>
          `,
          onOpen(modal, close) {
            const hint = modal.querySelector('#adminTagHint');
            modal.querySelector('#adminTagCancelBtn')?.addEventListener('click', close);
            modal.querySelector('#adminTagSubmitBtn')?.addEventListener('click', async () => {
              const name = (modal.querySelector('#adminTagName')?.value || '').trim();
              const type = (modal.querySelector('#adminTagType')?.value || 'CATEGORY').trim();
              try {
                await apiFetch('/admin/tags', {
                  method: 'POST',
                  body: JSON.stringify({ name, type }),
                });
                close();
                await loadTags();
              } catch (err) {
        if (err.name === 'AbortError') return;
                if (hint) hint.textContent = err.message || _tr('创建失败');
              }
            });
          },
        });
      });
    }

    await Promise.all([loadStats(), loadActivities(), loadUsers(), loadFeatured(), loadTags()]);
  }
