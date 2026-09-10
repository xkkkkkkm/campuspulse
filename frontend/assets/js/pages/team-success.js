import { contentField } from '../core/content-i18n.js';
import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { mountTeamLifecycle } from '../core/team-lifecycle.js';
import { safePageRef } from '../core/navigation.js';
import { qs, escapeHtml, showMessage, showSuccessMessage, formatDateTime, toDatetimeLocalValue, openUtilityModal, renderReviewSection } from './../core/ui.js';
import { getQueryParam, withFrom, applyBackLink } from './../core/navigation.js';
import { apiFetch } from './../core/api.js';
import { ensureMe, requireAuth } from './../core/auth.js';
import { deriveGradeLabel, educationLabel, formatTeamMemberLimit, teamDisplayTitle } from './../core/render.js';

export async function initTeamSuccess() {
    applyBackLink('#teamSuccessBackLink', 'team-lobby.html');
    requireAuth();
    const teamId = Number(getQueryParam('teamId') || '0');
    if (!teamId) {
      window.location.href = 'team-lobby.html';
      return;
    }
    const card = qs('#teamSuccessCard');
    const chatBtn = qs('#teamSuccessChatBtn');
    const activityBtn = qs('#teamSuccessActivityBtn');
    const deleteBtn = qs('#teamDeleteBtn');
    const editSection = qs('#teamEditSection');
    const editTitle = qs('#teamEditTitle');
    const editStart = qs('#teamEditStart');
    const editEnd = qs('#teamEditEnd');
    const editMax = qs('#teamEditMax');
    const editDesc = qs('#teamEditDesc');
    const editHint = qs('#teamEditHint');
    const editResetBtn = qs('#teamEditResetBtn');
    const editSaveBtn = qs('#teamEditSaveBtn');
    const requestSection = qs('#teamRequestSection');
    const requestList = qs('#teamRequestList');
    let currentRequestStats = null;
    let team = await apiFetch(`/teams/${teamId}`);
    const me = await ensureMe();
    const lifecycle = document.createElement('section'); card?.after(lifecycle); mountTeamLifecycle(team, me, lifecycle);
    const canManage = !!(me && (String(me.id) === String(team.creatorId) || me.role === 'ADMIN'));
    if (chatBtn) {
      chatBtn.setAttribute('href', `chat.html?teamId=${encodeURIComponent(teamId)}`);
    }
    if (activityBtn && team.activityId) {
      activityBtn.style.display = '';
      activityBtn.setAttribute('href', withFrom(`activity-detail.html?id=${encodeURIComponent(team.activityId)}`));
    }
    if (deleteBtn) {
      deleteBtn.style.display = canManage ? '' : 'none';
      if (canManage) {
        deleteBtn.addEventListener('click', () => {
          openUtilityModal({
            title: _tr('删除队伍'),
            html: _ui`
              <div style="font-size:14px; line-height:1.7; color:var(--text-secondary);">归档后停止招募，保留队伍、成员和聊天历史。</div>
              <div class="utility-actions" style="margin-top: 16px;">
                <button type="button" class="secondary-btn" id="teamDeleteCancelBtn">取消</button>
                <button type="button" class="secondary-btn" id="teamDeleteConfirmBtn" style="background:#fff1f0; color:#d92d20;">确认删除</button>
              </div>
            `,
            onOpen(modal, close) {
              modal.querySelector('#teamDeleteCancelBtn')?.addEventListener('click', close);
              modal.querySelector('#teamDeleteConfirmBtn')?.addEventListener('click', async () => {
                await apiFetch(`/teams/${teamId}`, { method: 'DELETE' });
                close();
                window.location.href = safePageRef(getQueryParam('from'), 'team-lobby.html');
              });
            },
          });
        });
      }
    }
    if (editSection) editSection.style.display = canManage ? '' : 'none';
    if (requestSection) requestSection.style.display = canManage ? '' : 'none';

    const fillEditForm = () => {
      if (editTitle) editTitle.value = team.title || '';
      if (editStart) editStart.value = toDatetimeLocalValue(team.startTime);
      if (editEnd) editEnd.value = toDatetimeLocalValue(team.endTime);
      if (editMax) editMax.value = team.maxMembers > 0 ? String(team.maxMembers) : '';
      if (editDesc) editDesc.value = team.description || '';
      if (editHint) editHint.textContent = '';
    };

    const renderCard = (requestStats) => {
      if (!card) return;
      const members = Array.isArray(team.members) ? team.members : [];
      const full = team.maxMembers > 0 && members.length >= team.maxMembers;
      const summaryMeta = [
        _ui`队长：${team.creatorName || '-'}`,
        _ui`状态：${team.status || 'OPEN'}`,
        formatTeamMemberLimit(members.length, team.maxMembers),
      ];
      card.innerHTML = _ui`
        <div class="manage-summary-shell">
          <div class="manage-summary-header">
            <div class="manage-summary-kicker">
              <span class="badge-pill badge-approved">队长管理</span>
              <span class="badge-pill">${escapeHtml(formatTeamMemberLimit(members.length, team.maxMembers))}</span>
            </div>
            <div class="manage-summary-title">${escapeHtml(teamDisplayTitle(team))}</div>
            <div class="manage-summary-subtitle">${escapeHtml(summaryMeta.join(' · '))}</div>
            <div class="manage-summary-tags">
              ${team.activityId && team.activityTitle ? _ui`<span class="manage-summary-tag">关联活动：${escapeHtml(contentField(team, 'activityTitle'))}</span>` : _static('<span class="manage-summary-tag">自由组队</span>')}
              ${(team.startTime || team.endTime) ? _ui`<span class="manage-summary-tag">时间窗口：${escapeHtml(team.startTime ? formatDateTime(team.startTime) : _tr('长期有效'))} · ${escapeHtml(team.endTime ? formatDateTime(team.endTime) : _tr('未设置截止'))}</span>` : _static('<span class="manage-summary-tag">时间窗口未设置</span>')}
            </div>
          </div>
          <div class="manage-summary-body">
            <div class="manage-summary-grid">
              <div class="manage-summary-block">
                <div class="manage-summary-block-title">队伍简介</div>
                <div class="manage-summary-block-value">${escapeHtml(contentField(team, 'description') || _tr('你还没有填写队伍简介。'))}</div>
              </div>
              <div class="manage-summary-block">
                <div class="manage-summary-block-title">申请进度</div>
                <div class="manage-summary-block-value">${requestStats ? _ui`待处理 ${requestStats.pending} 人 · 已通过 ${requestStats.approved} 人 · 已拒绝 ${requestStats.rejected} 人` : _tr('加载申请统计中')}</div>
              </div>
            </div>
            ${full ? _ui`<div class="manage-status-banner">当前队伍已满员。你仍然可以继续管理队伍，先把不合适的成员改为拒绝或移出，再处理新的申请。</div>` : ''}
            <div>
              <div class="manage-summary-block-title">当前成员</div>
              <div class="manage-member-list">
          ${members.length > 0
            ? members.map((member) => `<span class="manage-member-chip">${escapeHtml(member.nickname)} · ${escapeHtml(member.role === 'CREATOR' ? _tr('队长') : _tr('成员'))}</span>`).join('')
            : _static('<span class="field-hint">暂时还没有成员</span>')}
              </div>
            </div>
          </div>
        </div>
      `;
    };

    fillEditForm();
    renderCard(null);

    if (canManage && editResetBtn) {
      editResetBtn.addEventListener('click', fillEditForm);
    }
    if (canManage && editSaveBtn) {
      editSaveBtn.addEventListener('click', async () => {
        const body = {
          version: team.version,
        title: (editTitle?.value || '').trim(),
          startTime: editStart?.value || null,
          endTime: editEnd?.value || null,
          maxMembers: Number(editMax?.value || '0') || 0,
          description: (editDesc?.value || '').trim(),
        };
        const currentMembers = Array.isArray(team.members) ? team.members.length : 0;
        if (body.maxMembers > 0 && body.maxMembers < currentMembers) {
          if (editHint) editHint.textContent = _tr('队伍人数上限不能低于当前成员数');
          return;
        }
        try {
          if (editHint) editHint.textContent = '';
          await apiFetch(`/teams/${teamId}`, { method: 'PUT', body: JSON.stringify(body) });
          team = await apiFetch(`/teams/${teamId}`);
          fillEditForm();
          renderCard(currentRequestStats);
          showSuccessMessage(_tr('保存成功'), _tr('队伍信息已更新，新的时间窗口和人数上限已经生效。'), _tr('知道了'));
        } catch (err) {
          if (editHint) editHint.textContent = err.message || _tr('保存失败');
        }
      });
    }

    if (requestSection && requestList && canManage) {
      const requestExpanded = { pending: false, approved: false, rejected: false };
      let latestRequests = [];

      const loadRequests = async () => {
        latestRequests = await apiFetch(`/teams/${teamId}/requests`);
        const grouped = { pending: [], approved: [], rejected: [] };
        (latestRequests || []).forEach((item) => {
          const status = String(item.status || '').toUpperCase();
          if (status === 'APPROVED') grouped.approved.push(item);
          else if (status === 'REJECTED') grouped.rejected.push(item);
          else grouped.pending.push(item);
        });
        team = await apiFetch(`/teams/${teamId}`);
        currentRequestStats = {
          pending: grouped.pending.length,
          approved: grouped.approved.length,
          rejected: grouped.rejected.length,
        };
        fillEditForm();
        renderCard(currentRequestStats);
        renderRequests(grouped);
      };

      const renderTeamRequestItem = (item) => {
        const status = String(item.status || '').toUpperCase();
        const pending = status === 'PENDING';
        const approved = status === 'APPROVED';
        const statusClass = pending ? 'badge-pending' : approved ? 'badge-approved' : 'badge-rejected';
        const statusLabel = approved ? _tr('已通过') : status === 'REJECTED' ? _tr('已拒绝') : _tr('待处理');
        const avatar = item.avatarUrl || 'assets/images/avatar.svg';
        const parts = [];
        const edu = educationLabel(item.educationLevel);
        const grade = deriveGradeLabel(item.studentNo);
        if (edu) parts.push(edu);
        if (item.college) parts.push(item.college);
        if (grade) parts.push(grade);
        if (item.major) parts.push(item.major);
        const subtitle = parts.join(' · ') || _tr('暂未完善学院年级信息');
        return _ui`
          <div class="manage-review-card">
            <div class="manage-review-head">
              <div class="manage-review-main">
                <div class="manage-review-avatar">
                  <img src="${escapeHtml(avatar)}" alt="avatar">
                </div>
                <div class="manage-review-info">
                  <div class="manage-review-title">${escapeHtml(item.nickname)}</div>
                  <div class="manage-review-sub">${escapeHtml(subtitle)}</div>
                  <div class="manage-review-sub">${escapeHtml(formatDateTime(item.createdAt))}</div>
                </div>
              </div>
              <span class="badge-pill ${statusClass}">${escapeHtml(statusLabel)}</span>
            </div>
            <div class="manage-review-text">${escapeHtml(item.message || _tr('申请人未填写附言。'))}</div>
            <div class="stack-card-actions">
              <button type="button" class="secondary-btn js-team-request-detail" data-id="${item.id}" data-user-id="${item.userId}" data-nickname="${escapeHtml(item.nickname)}" data-avatar-url="${escapeHtml(item.avatarUrl || '')}" data-student-no="${escapeHtml(item.studentNo || '')}" data-college="${escapeHtml(item.college || '')}" data-major="${escapeHtml(item.major || '')}" data-education-level="${escapeHtml(item.educationLevel || '')}" data-message="${escapeHtml(item.message || '')}" data-status="${escapeHtml(item.status || '')}" data-created-at="${escapeHtml(item.createdAt || '')}">查看申请</button>
              <a href="${withFrom(`chat.html?userId=${encodeURIComponent(item.userId)}`)}" class="secondary-btn">联系申请人</a>
              ${(pending || status === 'REJECTED') ? `<button type="button" class="secondary-btn js-team-request-approve" data-id="${item.id}">${pending ? _tr('通过申请') : _tr('改为通过')}</button>` : ''}
              ${(pending || approved) ? `<button type="button" class="secondary-btn js-team-request-reject" data-id="${item.id}">${approved ? _tr('改为拒绝') : _tr('拒绝申请')}</button>` : ''}
            </div>
          </div>
        `;
      };

      const renderRequests = (grouped) => {
        const full = team.maxMembers > 0 && Array.isArray(team.members) && team.members.length >= team.maxMembers;
        requestList.innerHTML = [
          renderReviewSection({
            sectionKey: 'pending',
            title: _tr('待处理'),
            meta: full ? _tr('当前队伍已满员，可先调整已通过名单，再决定是否继续通过新的申请。') : _tr('优先处理新提交的申请，决定是否加入队伍。'),
            items: grouped.pending,
            expandedMap: requestExpanded,
            emptyMessage: _tr('当前没有待处理的入队申请。'),
            expandText: _ui`显示全部 ${grouped.pending.length} 位待处理申请人`,
            renderItem: renderTeamRequestItem,
          }),
          renderReviewSection({
            sectionKey: 'approved',
            title: _tr('已通过'),
            meta: _tr('已加入队伍的申请人，仍可改回拒绝。'),
            items: grouped.approved,
            expandedMap: requestExpanded,
            emptyMessage: _tr('当前还没有通过的申请人。'),
            expandText: _ui`显示全部 ${grouped.approved.length} 位已通过申请人`,
            renderItem: renderTeamRequestItem,
          }),
          renderReviewSection({
            sectionKey: 'rejected',
            title: _tr('已拒绝'),
            meta: _tr('被拒绝的申请人仍可重新改为通过。'),
            items: grouped.rejected,
            expandedMap: requestExpanded,
            emptyMessage: _tr('当前还没有拒绝记录。'),
            expandText: _ui`显示全部 ${grouped.rejected.length} 位已拒绝申请人`,
            renderItem: renderTeamRequestItem,
          }),
        ].join('');

        Array.from(requestList.querySelectorAll('.js-review-section-expand')).forEach((btn) => {
          btn.addEventListener('click', () => {
            requestExpanded[btn.dataset.section || 'pending'] = true;
            renderRequests({
              pending: latestRequests.filter((item) => String(item.status || '').toUpperCase() === 'PENDING'),
              approved: latestRequests.filter((item) => String(item.status || '').toUpperCase() === 'APPROVED'),
              rejected: latestRequests.filter((item) => String(item.status || '').toUpperCase() === 'REJECTED'),
            });
          });
        });

        Array.from(requestList.querySelectorAll('.js-team-request-detail')).forEach((btn) => {
          btn.addEventListener('click', () => {
            const payload = {
              id: btn.dataset.id || '',
              userId: btn.dataset.userId || '',
              nickname: btn.dataset.nickname || '',
              avatarUrl: btn.dataset.avatarUrl || '',
              studentNo: btn.dataset.studentNo || '',
              college: btn.dataset.college || '',
              major: btn.dataset.major || '',
              educationLevel: btn.dataset.educationLevel || '',
              message: btn.dataset.message || '',
              status: btn.dataset.status || '',
              createdAt: btn.dataset.createdAt || '',
            };
            const isPending = String(payload.status || '').toUpperCase() === 'PENDING';
            const isApproved = String(payload.status || '').toUpperCase() === 'APPROVED';
            const isRejected = String(payload.status || '').toUpperCase() === 'REJECTED';
            const detailParts = [];
            const detailEdu = educationLabel(payload.educationLevel);
            const detailGrade = deriveGradeLabel(payload.studentNo);
            if (detailEdu) detailParts.push(detailEdu);
            if (payload.college) detailParts.push(payload.college);
            if (detailGrade) detailParts.push(detailGrade);
            if (payload.major) detailParts.push(payload.major);
            openUtilityModal({
              title: payload.nickname || _tr('申请详情'),
              maxWidth: 640,
              html: _ui`
                <div style="display:flex; align-items:center; gap:14px; margin-bottom: 16px;">
                  <div style="width:64px; height:64px; border-radius:20px; overflow:hidden; border:1px solid rgba(0,0,0,0.05); flex-shrink:0;">
                    <img src="${escapeHtml(payload.avatarUrl || 'assets/images/avatar.svg')}" alt="avatar" style="width:100%; height:100%; object-fit:cover;">
                  </div>
                  <div>
                    <div style="font-size:20px; font-weight:750;">${escapeHtml(payload.nickname || _tr('未命名申请人'))}</div>
                    <div class="stack-card-meta">${escapeHtml(detailParts.join(' · ') || _tr('暂未完善学院年级信息'))}</div>
                    <div class="stack-card-meta">提交时间：${escapeHtml(formatDateTime(payload.createdAt))}</div>
                  </div>
                </div>
                <div class="stack-card" style="padding: 14px; box-shadow:none; margin-bottom: 14px;">
                  <div class="stack-card-meta">申请说明</div>
                  <div style="font-size:14px; color:var(--text-primary); line-height:1.7; margin-top: 8px;">${escapeHtml(payload.message || _tr('申请人未填写附言。'))}</div>
                </div>
                <div class="stack-card" style="padding: 14px; box-shadow:none;">
                  <div class="stack-card-meta">申请人信息</div>
                  <div style="display:grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 10px;">
                    <div class="field-hint">学号：${escapeHtml(payload.studentNo || _tr('未填写'))}</div>
                    <div class="field-hint">学院：${escapeHtml(payload.college || _tr('未填写'))}</div>
                    <div class="field-hint">年级：${escapeHtml(detailGrade || _tr('未识别'))}</div>
                    <div class="field-hint">专业：${escapeHtml(payload.major || _tr('未填写'))}</div>
                  </div>
                </div>
                <div class="utility-actions" style="margin-top: 16px;">
                  <a href="${withFrom(`chat.html?userId=${encodeURIComponent(payload.userId)}`)}" class="secondary-btn">联系申请人</a>
                  ${(isPending || isRejected) ? `<button type="button" class="secondary-btn js-team-detail-approve" data-id="${payload.id}">${isPending ? _tr('通过申请') : _tr('改为通过')}</button>` : ''}
                  ${(isPending || isApproved) ? `<button type="button" class="secondary-btn js-team-detail-reject" data-id="${payload.id}">${isApproved ? _tr('改为拒绝') : _tr('拒绝申请')}</button>` : ''}
                </div>
              `,
              onOpen(modal, close) {
                modal.querySelector('.js-team-detail-approve')?.addEventListener('click', async () => {
                  try {
                    await apiFetch(`/teams/${teamId}/requests/${encodeURIComponent(payload.id)}/approve`, { method: 'POST' });
                    close();
                    await loadRequests();
                  } catch (err) {
                    showMessage(err.message || _tr('处理失败'), _tr('提示'));
                  }
                });
                modal.querySelector('.js-team-detail-reject')?.addEventListener('click', async () => {
                  try {
                    await apiFetch(`/teams/${teamId}/requests/${encodeURIComponent(payload.id)}/reject`, { method: 'POST' });
                    close();
                    await loadRequests();
                  } catch (err) {
                    showMessage(err.message || _tr('处理失败'), _tr('提示'));
                  }
                });
              },
            });
          });
        });

        Array.from(requestList.querySelectorAll('.js-team-request-approve')).forEach((btn) => {
          btn.addEventListener('click', async () => {
            try {
              await apiFetch(`/teams/${teamId}/requests/${encodeURIComponent(btn.dataset.id || '')}/approve`, { method: 'POST' });
              await loadRequests();
            } catch (err) {
              showMessage(err.message || _tr('处理失败'), _tr('提示'));
            }
          });
        });
        Array.from(requestList.querySelectorAll('.js-team-request-reject')).forEach((btn) => {
          btn.addEventListener('click', async () => {
            try {
              await apiFetch(`/teams/${teamId}/requests/${encodeURIComponent(btn.dataset.id || '')}/reject`, { method: 'POST' });
              await loadRequests();
            } catch (err) {
              showMessage(err.message || _tr('处理失败'), _tr('提示'));
            }
          });
        });
      };

      await loadRequests();
    }
  }
