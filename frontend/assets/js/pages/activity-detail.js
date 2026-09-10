import { contentField } from '../core/content-i18n.js';
import { t as _tr, ui as _ui, translateStatic as _static } from '../core/i18n.js';
import { qs, qsa, escapeHtml, showMessage, showToast, formatDateTime, openJoinTeamModal, openTeamComposer } from './../core/ui.js';
import { getQueryParam, withFrom, applyBackLink } from './../core/navigation.js';
import { apiFetch } from './../core/api.js';
import { ensureMe, requireAuth } from './../core/auth.js';
import { renderTags, hasCustomActivityCover, buildActivityCoverUrl, formatTeamMemberLimit, teamDisplayTitle, teamManageHref, teamPrimaryHref, openTeamDetailModal, logEvent } from './../core/render.js';

export async function initActivityDetail() {
    applyBackLink('#activityDetailBackLink', 'activity-lobby.html');
    const id = Number(getQueryParam('id') || '0');
    if (!id) {
      window.location.href = 'home.html';
      return;
    }
    const detail = await apiFetch(`/activities/${id}`);
    logEvent(id, 'IMPRESSION');

    const titleEl = qs('#activityTitle');
    const auditNoticeEl = qs('#activityAuditNotice');
    const imgEl = qs('#activityCover');
    const descEl = qs('#activityDesc');
    const tagsEl = qs('#activityTags');
    const timeEl = qs('#activityTime');
    const locEl = qs('#activityLoc');
    const quotaEl = qs('#activityQuota');
    const collectBtn = qs('#collectBtn');
    const actChatBtn = qs('#activityChatBtn');
    const actChatEnableBtn = qs('#activityChatEnableBtn');
    const contactBtn = qs('#contactOrganizerBtn');
    const cancelRegistrationBtn = qs('#cancelRegistrationBtn');
    const organizerContactBox = qs('#organizerContactBox');
    const organizerPhoneText = qs('#organizerPhoneText');
    const organizerChatLink = qs('#organizerChatLink');
    const regsModal = qs('#registrationsModal');
    const regsList = qs('#registrationsList');
    const closeRegsBtn = qs('#closeRegistrationsModalBtn');
    const teamActionModule = qs('#teamActionModule');
    const teamLinkNotice = qs('#teamLinkNotice');
    const createTeamBtn = qs('#createTeamBtn');

    if (titleEl) titleEl.textContent = contentField(detail, 'title');
    if (auditNoticeEl) {
      const audit = String(detail.auditStatus || 'APPROVED').trim().toUpperCase();
      if (audit === 'PENDING') {
        auditNoticeEl.textContent = _tr('该活动正在等待管理员审核，只有发布者和管理员可以预览。');
        auditNoticeEl.className = 'status-banner status-warning';
        auditNoticeEl.style.display = '';
      } else if (audit === 'REJECTED') {
        auditNoticeEl.textContent = _tr('该活动未通过审核。你可以修改内容后重新提交。');
        auditNoticeEl.className = 'status-banner status-danger';
        auditNoticeEl.style.display = '';
      } else {
        auditNoticeEl.style.display = 'none';
      }
    }
    if (imgEl) {
      const cover = hasCustomActivityCover(detail.coverUrl) ? detail.coverUrl : buildActivityCoverUrl(detail);
      if (cover) imgEl.src = cover;
    }
    if (descEl) descEl.textContent = contentField(detail, 'description') || '';
    if (tagsEl) tagsEl.innerHTML = renderTags(contentField(detail, 'tags') || []);
    if (timeEl) timeEl.textContent = formatDateTime(detail.startTime);
    if (locEl) locEl.textContent = contentField(detail, 'location');
    if (quotaEl) {
      const quotaBase = detail.maxParticipants > 0 ? _ui`${detail.participants} / ${detail.maxParticipants} 人` : _ui`${detail.participants} 人已通过`;
      const pendingPart = detail.pendingRegistrations > 0 ? _ui` · 待审批 ${detail.pendingRegistrations} 人` : '';
      quotaEl.textContent = `${quotaBase}${pendingPart}`;
    }
    const me = await ensureMe();
    const isOwner = me && String(me.id) === String(detail.organizerId);
    let myReg = null;
    if (me) {
      try {
        myReg = await apiFetch(`/activities/${id}/my-registration`);
      } catch (e) {
      }
    }

    if (contactBtn) {
      contactBtn.textContent = isOwner ? _tr('进入管理') : _tr('联系发布人');
      contactBtn.addEventListener('click', async () => {
        requireAuth();
        if (isOwner) {
          window.location.href = withFrom(`activity-manage.html?id=${encodeURIComponent(id)}`);
          return;
        }
        if (!detail.organizerId) {
          showMessage(_tr('未找到发布人信息'), _tr('提示'));
          return;
        }
        window.location.href = `chat.html?userId=${encodeURIComponent(detail.organizerId)}`;
      });
    }

    function setRegisterButtonState({ text, enabled = true }) {
      const btn = qs('#registerBtn');
      if (!btn) return;
      btn.textContent = text;
      btn.disabled = !enabled;
      btn.style.opacity = enabled ? '' : '0.72';
      btn.style.filter = enabled ? '' : 'grayscale(0.08)';
    }

    function syncRegistrationActions() {
      const ended = detail.endTime && new Date(detail.endTime).getTime() < Date.now();
      if (teamLinkNotice) {
        if (detail.teamingEnabled) {
          teamLinkNotice.textContent = _tr('这个活动支持围绕内容自由组队。可以先看已有队伍，也可以发起自己的队伍。');
        } else {
          teamLinkNotice.textContent = _tr('该活动当前未开放活动联动组队，活动详情页不会继续展示关联队伍。');
        }
      }
      if (teamActionModule) {
        teamActionModule.style.display = '';
      }
      if (createTeamBtn) {
        createTeamBtn.style.display = detail.teamingEnabled ? '' : 'none';
      }
      if (isOwner) {
        setRegisterButtonState({ text: _tr('你是活动发布人'), enabled: false });
        if (cancelRegistrationBtn) cancelRegistrationBtn.style.display = 'none';
        return;
      }
      if (ended) {
        setRegisterButtonState({ text: _tr('活动已结束'), enabled: false });
        if (cancelRegistrationBtn) cancelRegistrationBtn.style.display = 'none';
        return;
      }
      const status = String(myReg?.status || '').toUpperCase();
      if (!myReg || !myReg.registered || status === 'REJECTED' || status === 'CANCELLED') {
        setRegisterButtonState({ text: status === 'REJECTED' ? _tr('重新提交报名') : _tr('立即报名参与'), enabled: true });
        if (cancelRegistrationBtn) cancelRegistrationBtn.style.display = 'none';
        return;
      }
      if (status === 'APPLIED') {
        setRegisterButtonState({ text: _tr('报名审核中'), enabled: false });
        if (cancelRegistrationBtn) {
          cancelRegistrationBtn.style.display = '';
          cancelRegistrationBtn.textContent = _tr('撤回报名');
        }
        return;
      }
      if (status === 'APPROVED') {
        setRegisterButtonState({ text: _tr('已通过报名'), enabled: false });
        if (cancelRegistrationBtn) {
          cancelRegistrationBtn.style.display = '';
          cancelRegistrationBtn.textContent = _tr('取消报名');
        }
        return;
      }
      setRegisterButtonState({ text: _tr('立即报名参与'), enabled: true });
      if (cancelRegistrationBtn) cancelRegistrationBtn.style.display = 'none';
    }
    syncRegistrationActions();

    if (closeRegsBtn && regsModal) {
      closeRegsBtn.addEventListener('click', () => { regsModal.style.display = 'none'; });
    }
    if (regsModal) {
      regsModal.addEventListener('click', (e) => {
        if (e.target === regsModal) regsModal.style.display = 'none';
      });
    }
    if (me && organizerContactBox && organizerPhoneText && organizerChatLink) {
      try {
        if (myReg && myReg.registered && ['APPLIED', 'APPROVED'].includes(String(myReg.status || '').toUpperCase())) {
          const c = await apiFetch(`/activities/${id}/organizer-contact`);
          organizerChatLink.setAttribute('href', `chat.html?userId=${encodeURIComponent(c.organizerId)}`);
          if (c.phone) {
            organizerPhoneText.textContent = c.phoneVerified ? _ui`手机号：${c.phone}` : _ui`手机号：${c.phone}（未验证）`;
          } else {
            organizerPhoneText.textContent = _tr('发布人未填写手机号，可使用站内私信联系');
          }
          organizerContactBox.style.display = '';
        }
      } catch (e) {
      }
    }

    function setCollectIcon(on) {
      const icon = collectBtn ? collectBtn.querySelector('i') : null;
      if (!icon) return;
      if (on) {
        icon.classList.remove('fa-regular');
        icon.classList.add('fa-solid');
        icon.style.color = 'var(--accent-color)';
      } else {
        icon.classList.remove('fa-solid');
        icon.classList.add('fa-regular');
        icon.style.color = '#444';
      }
    }

    setCollectIcon(!!detail.favorited);
    if (collectBtn) {
      collectBtn.addEventListener('click', async () => {
        requireAuth();
        if (collectBtn.disabled) return;
        collectBtn.disabled = true;
        try {
          const r = await apiFetch(`/activities/${id}/favorite?favorited=${!detail.favorited}`, { method: 'POST' });
          detail.favorited = !!r.favorited; setCollectIcon(detail.favorited);
        } finally { collectBtn.disabled = false; }
      });
    }

    const teams = await apiFetch(`/activities/${id}/teams`);
    const teamList = qs('#teamList');
    if (teamList) {
      if (detail.teamingEnabled === false) {
        if (teamLinkNotice) {
          teamLinkNotice.textContent = _tr('该活动当前未开放活动联动组队，活动详情页不会继续展示关联队伍。');
        }
        teamList.innerHTML = '';
      } else if (!teams || teams.length === 0) {
        if (teamLinkNotice) {
          teamLinkNotice.textContent = _tr('暂时还没有围绕这场活动创建的队伍。你可以作为第一位发起者，先把队伍建起来，再邀请同学加入。');
        }
        teamList.innerHTML = '';
      } else {
        if (teamLinkNotice) teamLinkNotice.textContent = _tr('你可以先浏览已有队伍，也可以直接创建自己的队伍，与其他同学并行招募。');
        teamList.innerHTML = (teams || [])
        .map((t, idx) => {
          const full = t.maxMembers > 0 && t.members >= t.maxMembers;
          const joined = !!t.joined;
          const pending = !!t.pending;
          const isCreator = me && String(t.creatorId || '') === String(me.id);
          const displayTitle = teamDisplayTitle(t);
          const detailHtml = _ui`<button class="secondary-btn js-activity-team-detail" type="button" data-team-id="${t.id}" style="padding: 8px 16px; border-radius: 10px; font-size: 14px;">查看详情</button>`;
          const contactHtml = t.creatorId
            ? _ui`<a href="chat.html?userId=${encodeURIComponent(t.creatorId)}" class="secondary-btn" style="padding: 8px 16px; border-radius: 10px; font-size: 14px;">联系发起者</a>`
            : '';
          let actionHtml = '';
          if (isCreator) {
            actionHtml = _ui`${detailHtml}<a href="${teamManageHref(t.id)}" class="secondary-btn" style="padding: 8px 16px; border-radius: 10px; font-size: 14px;">队长管理</a>`;
          } else if (joined) {
            actionHtml = _ui`${detailHtml}<a href="${teamPrimaryHref(t, me && me.id)}" class="secondary-btn" style="padding: 8px 16px; border-radius: 10px; font-size: 14px;">进入交流</a>`;
          } else if (pending) {
            actionHtml = _ui`${detailHtml}${contactHtml}<button class="secondary-btn" type="button" disabled style="padding: 8px 16px; border-radius: 10px; font-size: 14px; opacity: 0.7;">申请审核中</button>`;
          } else {
            actionHtml = `${detailHtml}${contactHtml}<button class="join-btn ${full ? 'full' : ''}" data-team-id="${t.id}" data-team-title="${escapeHtml(displayTitle)}" ${full ? 'disabled' : ''}>${full ? _tr('名额已满') : _tr('申请加入')}</button>`;
          }
          return _ui`
            <div class="team-card animate-fade-up" style="animation-delay: ${idx * 0.05}s;">
              <div style="margin-bottom: 12px;">
                <h4 style="font-size: 18px; margin-bottom: 4px;">${escapeHtml(displayTitle)}</h4>
                  <p style="font-size: 13px; color: var(--text-secondary);">由 ${escapeHtml(t.creatorName)} 发起 · ${escapeHtml(formatTeamMemberLimit(t.members, t.maxMembers))}</p>
              </div>
              <p style="font-size: 15px; margin-bottom: 16px;">${escapeHtml(contentField(t, 'description') || '')}</p>
              <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 14px;">提交申请即可加入审核流程；如有疑问，也可以直接联系发起人沟通队伍需求。</div>
              <div class="stack-card-actions" style="margin-top: 0;">${actionHtml}</div>
            </div>
          `;
        })
        .join('');
      }
      qsa('.join-btn[data-team-id]').forEach((btn) => {
        btn.addEventListener('click', () => {
          const teamId = Number(btn.dataset.teamId || '0');
          const teamTitle = btn.dataset.teamTitle || '';
          if (!teamId) return;
          openJoinTeamModal({
            teamId,
            teamTitle,
            onSuccess: () => window.location.reload(),
          });
        });
      });
      Array.from(teamList.querySelectorAll('.js-activity-team-detail[data-team-id]')).forEach((btn) => {
        btn.addEventListener('click', async () => {
          const teamId = Number(btn.dataset.teamId || '0');
          const team = (teams || []).find((item) => Number(item.id) === Number(teamId));
          if (!team) return;
          try {
            await openTeamDetailModal(team, me);
          } catch (err) {
            showMessage(err.message || _tr('加载详情失败'), _tr('提示'));
          }
        });
      });
    }

    if (createTeamBtn) {
      createTeamBtn.addEventListener('click', () => {
        if (!detail.teamingEnabled) {
          showMessage(_tr('当前活动未开放围绕活动发起组队'), _tr('提示'));
          return;
        }
        openTeamComposer({
          activityId: id,
          activityTitle: contentField(detail, 'title'),
          onCreated: (res) => {
            window.location.href = withFrom(`team-success.html?teamId=${encodeURIComponent(res.id)}`);
          },
        });
      });
    }

    // Activity group chat
    const chatStatus = String(myReg?.status || '').toUpperCase();
    if (actChatBtn) {
      const enabledResp = await apiFetch(`/activities/${id}/chat/enabled`);
      const canEnterActivityChat = !!enabledResp.enabled && (isOwner || chatStatus === 'APPROVED');
      if (canEnterActivityChat) {
        actChatBtn.style.display = '';
        actChatBtn.addEventListener('click', () => {
          window.location.href = `chat.html?activityId=${encodeURIComponent(id)}`;
        });
      } else {
        actChatBtn.style.display = 'none';
      }
    }
    if (actChatEnableBtn) {
      if (!me || String(me.id) !== String(detail.organizerId)) {
        actChatEnableBtn.style.display = 'none';
      } else {
        actChatEnableBtn.textContent = detail.chatEnabled ? _tr('关闭群聊') : _tr('开启群聊');
        actChatEnableBtn.addEventListener('click', async () => {
          const nextEnabled = !detail.chatEnabled;
          await apiFetch(`/activities/${id}/chat/enable?enable=${encodeURIComponent(nextEnabled)}`, { method: 'POST' });
          showMessage(
            nextEnabled ? _tr('已开启活动群聊，报名通过的同学可以直接进入交流。') : _tr('活动群聊已关闭，入口将暂时隐藏，但历史消息会保留。'),
            _tr('设置成功'),
          );
          window.location.reload();
        });
      }
    }

    const registerBtn = qs('#registerBtn');
    const modal = qs('#registerModal');
    const closeModalBtn = qs('#closeRegisterModalBtn');
    const submitBtn = qs('#submitRegistrationBtn');
    const openModal = () => {
      requireAuth();
      if (registerBtn?.disabled) return;
      if (modal) modal.style.display = 'flex';
    };
    const closeModal = () => {
      if (modal) modal.style.display = 'none';
    };
    if (registerBtn) registerBtn.addEventListener('click', openModal);
    if (closeModalBtn) closeModalBtn.addEventListener('click', closeModal);
    if (modal) {
      modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
      });
    }
    if (submitBtn) {
      submitBtn.addEventListener('click', async () => {
        requireAuth();
        const realName = (qs('#regRealName')?.value || '').trim();
        const phone = (qs('#regPhone')?.value || '').trim();
        const college = (qs('#regCollege')?.value || '').trim();
        const intro = (qs('#regIntro')?.value || '').trim();
        await apiFetch(`/activities/${id}/register`, {
          method: 'POST',
          body: JSON.stringify({ realName, phone, college, intro }),
        });
        closeModal();
        showToast(_ui`您已提交“${contentField(detail, 'title') || _tr('该活动')}”的报名申请，请等待发布者审核。`);
        window.setTimeout(() => window.location.reload(), 900);
      });
    }
    if (cancelRegistrationBtn) {
      cancelRegistrationBtn.addEventListener('click', async () => {
        requireAuth();
        await apiFetch(`/activities/${id}/cancel-registration`, { method: 'POST' });
        showMessage(_tr('报名已取消'), _tr('提示'));
        window.location.reload();
      });
    }
  }
