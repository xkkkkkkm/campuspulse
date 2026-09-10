import { contentField, tagLabel } from '../core/content-i18n.js';
import { loadArrayPage } from '../core/array-pagination.js';
import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { safePageRef } from '../core/navigation.js';
import { qs, escapeHtml, showMessage, showSuccessMessage, formatDateTime, toDatetimeLocalValue, openUtilityModal, renderReviewSection } from './../core/ui.js';
import { getQueryParam, withFrom, applyBackLink } from './../core/navigation.js';
import { apiFetch, uploadFile } from './../core/api.js';
import { ensureMe, requireAuth } from './../core/auth.js';
import { hasCustomActivityCover, buildActivityCoverUrl, deriveGradeLabel, educationLabel } from './../core/render.js';

export async function initActivityManage() {
    applyBackLink('#activityManageBackLink', 'profile.html');
    requireAuth();
    const activityId = Number(getQueryParam('id') || '0');
    if (!activityId) {
      window.location.href = 'profile.html';
      return;
    }
    const summary = qs('#activityManageSummary');
    const listEl = qs('#activityManageRegistrations');
    const titleEl = qs('#activityManageTitle');
    const hintEl = qs('#activityManageHint');
    const detailBtn = qs('#activityManageDetailBtn');
    const chatToggleBtn = qs('#activityManageChatToggleBtn');
    const deleteBtn = qs('#activityDeleteBtn');
    const editTitle = qs('#activityEditTitle');
    const editStart = qs('#activityEditStart');
    const editEnd = qs('#activityEditEnd');
    const editLocation = qs('#activityEditLocation');
    const editCoverPreview = qs('#activityEditCoverPreview');
    const editCoverUrl = qs('#activityEditCoverUrl');
    const editCoverFile = qs('#activityEditCoverFile');
    const editUploadBtn = qs('#activityEditUploadBtn');
    const editMax = qs('#activityEditMax');
    const editTeamingEnabled = qs('#activityEditTeamingEnabled');
    const editTeamingEnabledLabel = qs('#activityEditTeamingEnabledLabel');
    const editTags = qs('#activityEditTags');
    const editTagHint = qs('#activityEditTagHint');
    const editDesc = qs('#activityEditDesc');
    const editHint = qs('#activityEditHint');
    const editSaveBtn = qs('#activityEditSaveBtn');
    const editResetBtn = qs('#activityEditResetBtn');
    const me = await ensureMe();
    let detail = await apiFetch(`/activities/${activityId}`);
    const allTags = await apiFetch('/tags');
    const isManager = !!(me && (String(me.id) === String(detail.organizerId) || me.role === 'ADMIN'));
    if (!isManager) {
      showMessage(_tr('当前账号无权管理该活动。'), _tr('无权限'));
      window.location.href = 'profile.html';
      return;
    }
    let selectedEditTags = new Set(Array.isArray(detail.tags) ? detail.tags.filter(Boolean) : []);
    let currentCoverUrl = detail.coverUrl || '';
    let currentCoverFile = null;
    if (titleEl) titleEl.textContent = contentField(detail, 'title') || _tr('活动管理');
    if (detailBtn) {
      detailBtn.setAttribute('href', withFrom(`activity-detail.html?id=${encodeURIComponent(activityId)}`));
    }
    const syncChatToggleButton = () => {
      if (!chatToggleBtn) return;
      chatToggleBtn.textContent = detail.chatEnabled ? _tr('关闭群聊') : _tr('开启群聊');
    };
    syncChatToggleButton();
    if (chatToggleBtn) {
      chatToggleBtn.addEventListener('click', async () => {
        const nextEnabled = !detail.chatEnabled;
        await apiFetch(`/activities/${activityId}/chat/enable?enable=${encodeURIComponent(nextEnabled)}`, { method: 'POST' });
        detail = {
          ...detail,
          chatEnabled: nextEnabled,
        };
        syncChatToggleButton();
        renderSummary();
        showSuccessMessage(
          _tr('设置成功'),
          nextEnabled ? _tr('活动群聊已开启，报名通过的同学现在可以进入交流。') : _tr('活动群聊已关闭，入口会暂时隐藏，但历史消息仍会保留。'),
          _tr('知道了'),
        );
      });
    }
    if (deleteBtn) {
      deleteBtn.addEventListener('click', () => {
        openUtilityModal({
          title: _tr('删除活动'),
          html: _ui`
            <div style="font-size:14px; line-height:1.7; color:var(--text-secondary);">归档后停止报名，保留活动、报名、关联队伍和聊天历史。</div>
            <div class="utility-actions" style="margin-top: 16px;">
              <button type="button" class="secondary-btn" id="activityDeleteCancelBtn">取消</button>
              <button type="button" class="secondary-btn" id="activityDeleteConfirmBtn" style="background:#fff1f0; color:#d92d20;">确认删除</button>
            </div>
          `,
          onOpen(modal, close) {
            modal.querySelector('#activityDeleteCancelBtn')?.addEventListener('click', close);
            modal.querySelector('#activityDeleteConfirmBtn')?.addEventListener('click', async () => {
              await apiFetch(`/activities/${activityId}`, { method: 'DELETE' });
              close();
              window.location.href = safePageRef(getQueryParam('from'), 'profile.html');
            });
          },
        });
      });
    }

    const syncManageHint = (approvedCount = detail.participants || 0) => {
      const full = detail.maxParticipants > 0 && approvedCount >= detail.maxParticipants;
      if (hintEl) {
        hintEl.textContent = full
          ? _tr('活动当前已满员，但你仍可继续管理名单：先把不合适的已通过对象改为拒绝，再处理新的待审批报名。')
          : _tr('先看待审批申请，再决定谁可以正式参加活动。审批结果会通过站内通知告知申请人。');
      }
    };

      const renderSummary = (stats) => {
      if (!summary) return;
      const approvedCount = stats ? stats.approved : (detail.participants || 0);
      const pendingCount = stats ? stats.pending : (detail.pendingRegistrations || 0);
      const rejectedCount = stats ? stats.rejected : 0;
      const full = detail.maxParticipants > 0 && approvedCount >= detail.maxParticipants;
      summary.innerHTML = _ui`
        <div class="manage-summary-shell">
          <div class="manage-summary-header">
            <div class="manage-summary-kicker">
              <span class="badge-pill badge-approved">发布者管理</span>
              <span class="badge-pill">${escapeHtml(detail.maxParticipants > 0 ? _ui`名额 ${approvedCount}/${detail.maxParticipants}` : _ui`已通过 ${approvedCount} 人`)}</span>
            </div>
            <div class="manage-summary-title">${escapeHtml(contentField(detail, 'title') || _tr('未命名活动'))}</div>
            <div class="manage-summary-subtitle">${escapeHtml(contentField(detail, 'location') || '-')} · ${escapeHtml(formatDateTime(detail.startTime))}</div>
            <div class="manage-summary-tags">
              ${(detail.tags || []).length
                ? contentField(detail, 'tags').map((tag) => `<span class="manage-summary-tag">#${escapeHtml(tag)}</span>`).join('')
                : _static('<span class="manage-summary-tag">暂未设置标签</span>')}
              <span class="manage-summary-tag">${detail.teamingEnabled !== false ? _tr('允许活动联动组队') : _tr('已关闭活动联动组队')}</span>
              <span class="manage-summary-tag">${detail.chatEnabled ? _tr('活动群聊已开启') : _tr('活动群聊未开启')}</span>
            </div>
          </div>
          <div class="manage-summary-body">
            <div class="manage-summary-stats">
              <div class="manage-stat-card">
                <div class="manage-stat-label">已通过</div>
                <div class="manage-stat-value">${approvedCount}</div>
              </div>
              <div class="manage-stat-card">
                <div class="manage-stat-label">待审批</div>
                <div class="manage-stat-value">${pendingCount}</div>
              </div>
              <div class="manage-stat-card">
                <div class="manage-stat-label">已拒绝</div>
                <div class="manage-stat-value">${rejectedCount}</div>
              </div>
              <div class="manage-stat-card">
                <div class="manage-stat-label">名额上限</div>
                <div class="manage-stat-value">${detail.maxParticipants > 0 ? detail.maxParticipants : '∞'}</div>
              </div>
            </div>
            <div class="manage-summary-grid">
              <div class="manage-summary-block">
                <div class="manage-summary-block-title">活动时间</div>
                <div class="manage-summary-block-value">${escapeHtml(formatDateTime(detail.startTime))}${detail.endTime ? ` · ${escapeHtml(formatDateTime(detail.endTime))}` : ''}</div>
              </div>
              <div class="manage-summary-block">
                <div class="manage-summary-block-title">当前状态</div>
                <div class="manage-summary-block-value">${escapeHtml(full ? _tr('已满员，仍可调整名单') : _tr('可继续审批新报名'))}</div>
              </div>
            </div>
            <div class="manage-summary-desc">${escapeHtml(contentField(detail, 'description') || _tr('你还没有填写活动描述。'))}</div>
            ${full ? _ui`<div class="manage-status-banner">当前活动已满员。你仍可继续管理名单，先把不合适的已通过对象改为拒绝，再决定是否通过新的报名。</div>` : ''}
          </div>
        </div>
      `;
      syncManageHint(approvedCount);
    };

    const updateCoverPreview = () => {
      if (!editCoverPreview) return;
      const previewUrl = String(editCoverUrl?.value || currentCoverUrl || '').trim();
      if (previewUrl && hasCustomActivityCover(previewUrl)) {
        editCoverPreview.src = previewUrl;
        return;
      }
      editCoverPreview.src = buildActivityCoverUrl({
        version: detail.version,
        title: (editTitle?.value || detail.title || '').trim(),
        location: (editLocation?.value || detail.location || '').trim(),
        startTime: editStart?.value || detail.startTime,
        tags: Array.from(selectedEditTags),
        coverUrl: previewUrl,
      });
    };

    const renderEditTags = () => {
      if (!editTags) return;
      editTags.innerHTML = (allTags || [])
        .map((item) => {
          const name = String(item.name || '').trim();
          const active = selectedEditTags.has(name);
          return `<span class="tag-chip${active ? ' active' : ''}" data-tag="${escapeHtml(name)}">#${escapeHtml(tagLabel(item))}</span>`;
        })
        .join('');
      if (editTagHint) {
        editTagHint.textContent = selectedEditTags.size > 0 ? _ui`已选择 ${selectedEditTags.size} 个标签` : _tr('至少选择 1 个标签');
      }
      Array.from(editTags.querySelectorAll('.tag-chip')).forEach((chip) => {
        chip.addEventListener('click', () => {
          const tag = chip.dataset.tag || '';
          if (!tag) return;
          if (selectedEditTags.has(tag)) selectedEditTags.delete(tag);
          else selectedEditTags.add(tag);
          renderEditTags();
          updateCoverPreview();
        });
      });
    };

    const syncTeamingToggleLabel = () => {
      if (editTeamingEnabledLabel) {
        editTeamingEnabledLabel.textContent = editTeamingEnabled?.checked ? _tr('已开启') : _tr('已关闭');
      }
    };

    const fillEditForm = () => {
      if (titleEl) titleEl.textContent = contentField(detail, 'title') || _tr('活动管理');
      if (editTitle) editTitle.value = detail.title || '';
      if (editStart) editStart.value = toDatetimeLocalValue(detail.startTime);
      if (editEnd) editEnd.value = toDatetimeLocalValue(detail.endTime);
      if (editLocation) editLocation.value = detail.location || '';
      if (editCoverUrl) editCoverUrl.value = detail.coverUrl || '';
      if (editMax) editMax.value = detail.maxParticipants > 0 ? String(detail.maxParticipants) : '';
      if (editTeamingEnabled) editTeamingEnabled.checked = detail.teamingEnabled !== false;
      syncTeamingToggleLabel();
      if (editDesc) editDesc.value = detail.description || '';
      if (editHint) editHint.textContent = '';
      selectedEditTags = new Set(Array.isArray(detail.tags) ? detail.tags.filter(Boolean) : []);
      currentCoverUrl = detail.coverUrl || '';
      currentCoverFile = null;
      if (editCoverFile) editCoverFile.value = '';
      renderEditTags();
      updateCoverPreview();
    };

    renderSummary(null);
    fillEditForm();

    if (editUploadBtn && editCoverFile) {
      editUploadBtn.addEventListener('click', () => editCoverFile.click());
      editCoverFile.addEventListener('change', () => {
        const file = editCoverFile.files && editCoverFile.files[0];
        if (!file) {
          currentCoverFile = null;
          updateCoverPreview();
          return;
        }
        currentCoverFile = file;
        const reader = new FileReader();
        reader.onload = (evt) => {
          if (editCoverPreview && evt.target) editCoverPreview.src = evt.target.result;
        };
        reader.readAsDataURL(file);
      });
    }
    if (editCoverUrl) {
      editCoverUrl.addEventListener('input', () => {
        currentCoverFile = null;
        currentCoverUrl = editCoverUrl.value || '';
        updateCoverPreview();
      });
    }
    if (editTeamingEnabled) editTeamingEnabled.addEventListener('change', syncTeamingToggleLabel);
    if (editResetBtn) editResetBtn.addEventListener('click', fillEditForm);
    if (editSaveBtn) {
      editSaveBtn.addEventListener('click', async () => {
        const body = {
          version: detail.version,
        title: (editTitle?.value || '').trim(),
          location: (editLocation?.value || '').trim(),
          startTime: editStart?.value || null,
          endTime: editEnd?.value || null,
          maxParticipants: Number(editMax?.value || '0') || 0,
          description: (editDesc?.value || '').trim(),
          tags: Array.from(selectedEditTags),
          teamingEnabled: !!(editTeamingEnabled?.checked),
          coverUrl: String(editCoverUrl?.value || currentCoverUrl || '').trim() || null,
        };
        if (body.maxParticipants > 0 && body.maxParticipants < (detail.participants || 0)) {
          if (editHint) editHint.textContent = _tr('人数上限不能低于当前已通过人数');
          return;
        }
        if (!body.tags.length) {
          if (editHint) editHint.textContent = _tr('请至少选择 1 个活动标签');
          return;
        }
        try {
          if (editHint) editHint.textContent = '';
          let coverUrl = body.coverUrl;
          if (currentCoverFile) {
            coverUrl = (await uploadFile('/upload/cover', currentCoverFile)).url || '';
          }
          await apiFetch(`/activities/${activityId}`, {
            method: 'PUT',
            body: JSON.stringify({
              ...body,
              coverUrl: coverUrl || null,
            }),
          });
          detail = await apiFetch(`/activities/${activityId}`);
          renderSummary(null);
          fillEditForm();
          await loadRegistrations();
          showSuccessMessage(_tr('保存成功'), _tr('活动信息已更新，新的时间地点和展示内容已经生效。'), _tr('知道了'));
        } catch (err) {
          if (editHint) editHint.textContent = err.message || _tr('保存失败');
        }
      });
    }

    const registrationExpanded = { pending: false, approved: false, rejected: false };

    function openRegistrationDetail(payload, refresh) {
      const status = String(payload.status || '').toUpperCase();
      const isPending = status === 'APPLIED';
      const isApproved = status === 'APPROVED';
      const isRejected = status === 'REJECTED';
      const detailParts = [];
      const detailEdu = educationLabel(payload.educationLevel);
      const detailGrade = deriveGradeLabel(payload.studentNo);
      if (detailEdu) detailParts.push(detailEdu);
      if (payload.campus) detailParts.push(contentField(payload, 'campus'));
      if (payload.college) detailParts.push(payload.college);
      if (detailGrade) detailParts.push(detailGrade);
      if (payload.major) detailParts.push(payload.major);
      openUtilityModal({
        title: payload.nickname || _tr('报名详情'),
        maxWidth: 680,
        html: _ui`
          <div style="display:flex; align-items:center; gap:14px; margin-bottom: 16px;">
            <div style="width:64px; height:64px; border-radius:20px; overflow:hidden; border:1px solid rgba(0,0,0,0.05); flex-shrink:0;">
              <img src="${escapeHtml(payload.avatarUrl || 'assets/images/avatar.svg')}" alt="avatar" style="width:100%; height:100%; object-fit:cover;">
            </div>
            <div>
              <div style="font-size:20px; font-weight:750;">${escapeHtml(payload.nickname || _tr('未命名申请人'))}（${escapeHtml(payload.realName || _tr('未填写真实姓名'))}）</div>
              <div class="stack-card-meta">${escapeHtml(detailParts.join(' · ') || _tr('暂未完善学院年级信息'))}</div>
              <div class="stack-card-meta">提交时间：${escapeHtml(formatDateTime(payload.createdAt))}</div>
            </div>
          </div>
          <div class="stack-card" style="padding: 14px; box-shadow:none; margin-bottom: 14px;">
            <div class="stack-card-meta">报名附言</div>
            <div style="font-size:14px; color:var(--text-primary); line-height:1.7; margin-top: 8px;">${escapeHtml(payload.intro || _tr('申请人未填写报名附言。'))}</div>
          </div>
          <div class="stack-card" style="padding: 14px; box-shadow:none; margin-bottom: 14px;">
            <div class="stack-card-meta">申请人资料</div>
            <div style="display:grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 10px;">
              <div class="field-hint">学号：${escapeHtml(payload.studentNo || _tr('未填写'))}</div>
              <div class="field-hint">手机号：${escapeHtml(payload.phone || _tr('未填写'))}</div>
              <div class="field-hint">校区：${escapeHtml(contentField(payload, 'campus') || _tr('未填写'))}</div>
              <div class="field-hint">学院：${escapeHtml(payload.college || _tr('未填写'))}</div>
              <div class="field-hint">年级：${escapeHtml(detailGrade || _tr('未识别'))}</div>
              <div class="field-hint">专业：${escapeHtml(payload.major || _tr('未填写'))}</div>
            </div>
          </div>
          <div class="stack-card" style="padding: 14px; box-shadow:none;">
            <div class="stack-card-meta">个人简介</div>
            <div style="font-size:14px; color:var(--text-primary); line-height:1.7; margin-top: 8px;">${escapeHtml(payload.bio || _tr('申请人未填写个人简介。'))}</div>
          </div>
          <div class="utility-actions" style="margin-top: 16px;">
            <a href="${withFrom(`chat.html?userId=${encodeURIComponent(payload.userId)}`)}" class="secondary-btn">联系申请人</a>
            ${(isPending || isRejected) ? `<button type="button" class="secondary-btn js-activity-detail-approve" data-id="${payload.id}">${isPending ? _tr('通过报名') : _tr('改为通过')}</button>` : ''}
            ${(isPending || isApproved) ? `<button type="button" class="secondary-btn js-activity-detail-reject" data-id="${payload.id}">${isApproved ? _tr('移出活动') : _tr('拒绝报名')}</button>` : ''}
          </div>
        `,
        onOpen(modal, close) {
          modal.querySelector('.js-activity-detail-approve')?.addEventListener('click', async () => {
            try {
              await apiFetch(`/activities/${activityId}/registrations/${encodeURIComponent(payload.id)}/approve`, { method: 'POST' });
              close();
              await refresh();
            } catch (err) {
              showMessage(err.message || _tr('处理失败'), _tr('提示'));
            }
          });
          modal.querySelector('.js-activity-detail-reject')?.addEventListener('click', async () => {
            try {
              await apiFetch(`/activities/${activityId}/registrations/${encodeURIComponent(payload.id)}/reject`, { method: 'POST' });
              close();
              await refresh();
            } catch (err) {
              showMessage(err.message || _tr('处理失败'), _tr('提示'));
            }
          });
        },
      });
    }

    async function loadRegistrations() {
      if (!listEl) return;
      const registrations = await loadArrayPage(`/activities/${activityId}/registrations`, listEl, loadRegistrations);
      const grouped = {
        pending: [],
        approved: [],
        rejected: [],
      };
      (registrations || []).forEach((r) => {
        const status = String(r.status || '').toUpperCase();
        if (status === 'APPROVED') grouped.approved.push(r);
        else if (status === 'REJECTED') grouped.rejected.push(r);
        else if (status === 'APPLIED') grouped.pending.push(r);
      });
      detail = {
        ...detail,
        // Activity totals come from its detail response, independent of this page.
      };
      renderSummary({
        approved: grouped.approved.length,
        pending: grouped.pending.length,
        rejected: grouped.rejected.length,
      });

      const renderRegistrationItem = (r) => {
        const avatar = r.avatarUrl || 'assets/images/avatar.svg';
        const intro = r.intro ? escapeHtml(r.intro) : _tr('未填写介绍');
        const status = String(r.status || '').toUpperCase();
        const statusLabel = status === 'APPLIED' ? _tr('待审批') : status === 'APPROVED' ? _tr('已通过') : status === 'REJECTED' ? _tr('未通过') : status === 'CANCELLED' ? _tr('已取消') : status;
        const statusClass = status === 'APPROVED' ? 'badge-approved' : status === 'APPLIED' ? 'badge-pending' : 'badge-rejected';
        return _ui`
          <div class="manage-review-card">
            <div class="manage-review-head">
              <div class="manage-review-main">
                <div class="manage-review-avatar">
                  <img src="${escapeHtml(avatar)}" alt="avatar">
                </div>
                <div class="manage-review-info">
                  <div class="manage-review-title">${escapeHtml(r.nickname)}（${escapeHtml(r.realName)}）</div>
                  <div class="manage-review-sub">${escapeHtml(r.college)} · ${escapeHtml(r.phone)} · ${escapeHtml(formatDateTime(r.createdAt))}</div>
                </div>
              </div>
              <span class="badge-pill ${statusClass}">${escapeHtml(statusLabel)}</span>
            </div>
            <div class="manage-review-text">${intro}</div>
            <div class="stack-card-actions">
              <button type="button" class="secondary-btn js-activity-request-detail"
                data-id="${r.id}"
                data-user-id="${r.userId}"
                data-nickname="${escapeHtml(r.nickname || '')}"
                data-avatar-url="${escapeHtml(r.avatarUrl || '')}"
                data-student-no="${escapeHtml(r.studentNo || '')}"
                data-major="${escapeHtml(r.major || '')}"
                data-education-level="${escapeHtml(r.educationLevel || '')}"
                data-campus="${escapeHtml(contentField(r, 'campus') || '')}"
                data-bio="${escapeHtml(r.bio || '')}"
                data-real-name="${escapeHtml(r.realName || '')}"
                data-phone="${escapeHtml(r.phone || '')}"
                data-college="${escapeHtml(r.college || '')}"
                data-intro="${escapeHtml(r.intro || '')}"
                data-status="${escapeHtml(r.status || '')}"
                data-created-at="${escapeHtml(r.createdAt || '')}">查看申请人</button>
              <a href="${withFrom(`chat.html?userId=${encodeURIComponent(r.userId)}`)}" class="secondary-btn">联系申请人</a>
              ${(status === 'APPLIED' || status === 'REJECTED') ? `<button type="button" class="secondary-btn js-activity-approve" data-id="${r.id}">${status === 'REJECTED' ? _tr('改为通过') : _tr('通过报名')}</button>` : ''}
              ${(status === 'APPLIED' || status === 'APPROVED') ? `<button type="button" class="secondary-btn js-activity-reject" data-id="${r.id}">${status === 'APPROVED' ? _tr('移出活动') : _tr('拒绝报名')}</button>` : ''}
            </div>
          </div>
        `;
      };

      listEl.innerHTML = [
        renderReviewSection({
          sectionKey: 'pending',
          title: _tr('待审批'),
          meta: detail.maxParticipants > 0 && grouped.approved.length >= detail.maxParticipants
            ? _tr('当前活动已满员，可先调整已通过名单，再决定是否继续通过新的报名。')
            : _tr('新提交的报名申请会优先显示在这里。'),
          items: grouped.pending,
          expandedMap: registrationExpanded,
          emptyMessage: _tr('当前没有待审批的报名申请。'),
          expandText: _ui`显示全部 ${grouped.pending.length} 位待审批申请人`,
          renderItem: renderRegistrationItem,
        }),
        renderReviewSection({
          sectionKey: 'approved',
          title: _tr('已通过'),
          meta: _tr('已通过的报名人仍可改回拒绝。'),
          items: grouped.approved,
          expandedMap: registrationExpanded,
          emptyMessage: _tr('当前还没有通过的报名申请。'),
          expandText: _ui`显示全部 ${grouped.approved.length} 位已通过报名人`,
          renderItem: renderRegistrationItem,
        }),
        renderReviewSection({
          sectionKey: 'rejected',
          title: _tr('已拒绝'),
          meta: _tr('已拒绝的报名申请仍可重新改为通过。'),
          items: grouped.rejected,
          expandedMap: registrationExpanded,
          emptyMessage: _tr('当前还没有拒绝记录。'),
          expandText: _ui`显示全部 ${grouped.rejected.length} 位已拒绝报名人`,
          renderItem: renderRegistrationItem,
        }),
      ].join('');

      Array.from(listEl.querySelectorAll('.js-review-section-expand')).forEach((btn) => {
        btn.addEventListener('click', () => {
          registrationExpanded[btn.dataset.section || 'pending'] = true;
          loadRegistrations();
        });
      });

      Array.from(listEl.querySelectorAll('.js-activity-request-detail')).forEach((btn) => {
        btn.addEventListener('click', () => {
          openRegistrationDetail({
            id: btn.dataset.id || '',
            userId: btn.dataset.userId || '',
            nickname: btn.dataset.nickname || '',
            avatarUrl: btn.dataset.avatarUrl || '',
            studentNo: btn.dataset.studentNo || '',
            major: btn.dataset.major || '',
            educationLevel: btn.dataset.educationLevel || '',
            campus: btn.dataset.campus || '',
            bio: btn.dataset.bio || '',
            realName: btn.dataset.realName || '',
            phone: btn.dataset.phone || '',
            college: btn.dataset.college || '',
            intro: btn.dataset.intro || '',
            status: btn.dataset.status || '',
            createdAt: btn.dataset.createdAt || '',
          }, loadRegistrations);
        });
      });

      Array.from(listEl.querySelectorAll('.js-activity-approve')).forEach((btn) => {
        btn.addEventListener('click', async () => {
          try {
            await apiFetch(`/activities/${activityId}/registrations/${encodeURIComponent(btn.dataset.id || '')}/approve`, { method: 'POST' });
            await loadRegistrations();
          } catch (err) {
            showMessage(err.message || _tr('处理失败'), _tr('提示'));
          }
        });
      });
      Array.from(listEl.querySelectorAll('.js-activity-reject')).forEach((btn) => {
        btn.addEventListener('click', async () => {
          try {
            await apiFetch(`/activities/${activityId}/registrations/${encodeURIComponent(btn.dataset.id || '')}/reject`, { method: 'POST' });
            await loadRegistrations();
          } catch (err) {
            showMessage(err.message || _tr('处理失败'), _tr('提示'));
          }
        });
      });
    }

    await loadRegistrations();
  }
